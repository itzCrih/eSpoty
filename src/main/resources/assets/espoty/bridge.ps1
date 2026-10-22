param([int]$Port = 0)

$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# CoreAudio Native Audio Master Controller for Smooth Volume Slider
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

[ComImport]
[Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IMMDeviceEnumerator {
    int NotImpl1();
    [PreserveSig]
    int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice ppDevice);
}

[ComImport]
[Guid("D666063F-1587-4E43-81F1-B948E807363F")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IMMDevice {
    [PreserveSig]
    int Activate(ref Guid iid, int dwClsCtx, IntPtr pActivationParams, [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
}

[ComImport]
[Guid("5CDF2C82-841E-4546-9722-0CF74078229A")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IAudioEndpointVolume {
    int NotImpl1();
    int NotImpl2();
    int GetChannelCount(out int pnChannelCount);
    int SetMasterVolumeLevel(float fLevelDB, ref Guid pguidEventContext);
    int SetMasterVolumeLevelScalar(float fLevel, ref Guid pguidEventContext);
    int GetMasterVolumeLevel(out float pfLevelDB);
    [PreserveSig]
    int GetMasterVolumeLevelScalar(out float pfLevel);
    int SetMute(bool bMute, ref Guid pguidEventContext);
    int GetMute(out bool pbMute);
}

[ComImport]
[Guid("BCDE0395-E52F-467C-8E3D-C4579291433E")]
internal class MMDeviceEnumerator {}

public class AudioMaster {
    public static float Get() {
        try {
            var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumerator());
            IMMDevice speakers;
            enumerator.GetDefaultAudioEndpoint(0, 1, out speakers);
            var iid = typeof(IAudioEndpointVolume).GUID;
            object o;
            speakers.Activate(ref iid, 0x17, IntPtr.Zero, out o);
            var aepv = (IAudioEndpointVolume)o;
            float vol = 0;
            aepv.GetMasterVolumeLevelScalar(out vol);
            return vol;
        } catch { return 1.0f; }
    }
    public static void Set(float vol) {
        try {
            var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumerator());
            IMMDevice speakers;
            enumerator.GetDefaultAudioEndpoint(0, 1, out speakers);
            var iid = typeof(IAudioEndpointVolume).GUID;
            object o;
            speakers.Activate(ref iid, 0x17, IntPtr.Zero, out o);
            var aepv = (IAudioEndpointVolume)o;
            Guid g = Guid.Empty;
            aepv.SetMasterVolumeLevelScalar(vol, ref g);
        } catch {}
    }
}
"@

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' }

function Await($WinRtTask, $ResultType) {
    if (-not $WinRtTask) { return $null }
    try {
        $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
        $netTask = $asTask.Invoke($null, @($WinRtTask))
        $netTask.Wait(400) | Out-Null
        return $netTask.Result
    } catch {
        return $null
    }
}

[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.MediaPlaybackAutoRepeatMode, Windows.Media, ContentType = WindowsRuntime] | Out-Null

$asyncOp = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
$manager = Await $asyncOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])

function Get-SpotifySession {
    if (-not $manager) {
        try {
            $script:asyncOp = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
            $script:manager = Await $script:asyncOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
        } catch {}
    }
    if (-not $manager) { return $null }
    try {
        $sessions = $manager.GetSessions()
        foreach ($s in $sessions) {
            if ($s.SourceAppUserModelId -like "*Spotify*") {
                return $s
            }
        }
        $cur = $manager.GetCurrentSession()
        if ($cur -and ($cur.SourceAppUserModelId -like "*Spotify*")) {
            return $cur
        }
    } catch {}
    return $null
}

if ($Port -le 0) {
    $s = Get-SpotifySession
    if ($s) {
        $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        Write-Output "Standalone session found: $($props.Title) by $($props.Artist)"
    } else {
        Write-Output "No Spotify session found"
    }
    Write-Output "Volume is: $([math]::Round(([AudioMaster]::Get()) * 100))%"
    exit 0
}

try {
    $client = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $Port)
    $stream = $client.GetStream()
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $writer = New-Object System.IO.StreamWriter($stream, $utf8NoBom)
    $reader = New-Object System.IO.StreamReader($stream, $utf8NoBom)

    while ($client.Connected) {
        $hasCommand = $false

        # Process any pending incoming commands from Java immediately
        while ($stream.DataAvailable) {
            $cmd = $reader.ReadLine()
            if ($null -eq $cmd) { break }
            $cmd = $cmd.Trim()
            if ($cmd -eq "quit" -or $cmd -eq "exit") {
                $client.Close()
                exit 0
            }

            $hasCommand = $true
            try {
                if ($cmd.StartsWith("vol:")) {
                    $vInt = [int]($cmd.Substring(4))
                    if ($vInt -lt 0) { $vInt = 0 }
                    if ($vInt -gt 100) { $vInt = 100 }
                    [AudioMaster]::Set($vInt / 100.0)
                } else {
                    $s = Get-SpotifySession
                    if ($s) {
                        if ($cmd -eq "toggle") { 
                            Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null
                        } elseif ($cmd -eq "play") { 
                            Await ($s.TryPlayAsync()) ([bool]) | Out-Null
                        } elseif ($cmd -eq "pause") { 
                            Await ($s.TryPauseAsync()) ([bool]) | Out-Null
                        } elseif ($cmd -eq "next") { 
                            Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null
                        } elseif ($cmd -eq "prev") { 
                            Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null
                        } elseif ($cmd.StartsWith("seek:")) {
                            $ms = [long]($cmd.Substring(5))
                            Await ($s.TryChangePlaybackPositionAsync($ms * 10000)) ([bool]) | Out-Null
                        } elseif ($cmd -eq "shuffletoggle") {
                            $pinfo = $s.GetPlaybackInfo()
                            $curShuf = if ($pinfo) { $pinfo.IsShuffleActive } else { $false }
                            Await ($s.TryChangeShuffleActiveAsync(-not $curShuf)) ([bool]) | Out-Null
                        } elseif ($cmd -eq "repeatcycle") {
                            $pinfo = $s.GetPlaybackInfo()
                            $curRep = if ($pinfo -and $pinfo.AutoRepeatMode) { $pinfo.AutoRepeatMode.ToString() } else { "None" }
                            $nextRep = [Windows.Media.MediaPlaybackAutoRepeatMode]::None
                            if ($curRep -eq "None") {
                                $nextRep = [Windows.Media.MediaPlaybackAutoRepeatMode]::List
                            } elseif ($curRep -eq "List") {
                                $nextRep = [Windows.Media.MediaPlaybackAutoRepeatMode]::Track
                            } else {
                                $nextRep = [Windows.Media.MediaPlaybackAutoRepeatMode]::None
                            }
                            Await ($s.TryChangeAutoRepeatModeAsync($nextRep)) ([bool]) | Out-Null
                        } elseif ($cmd -eq "refresh") {
                            $script:manager = $null
                        }
                    }
                }
            } catch {}
        }

        # Query and send current media state with ultra-precise timeline calculation
        try {
            $s = Get-SpotifySession
            if ($s) {
                $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $timeline = $s.GetTimelineProperties()
                $playback = $s.GetPlaybackInfo()
                $ctrls = if ($playback) { $playback.Controls } else { $null }

                $title = if ($props -and $props.Title) { $props.Title } else { "" }
                $artist = if ($props -and $props.Artist) { $props.Artist } else { "" }
                $album = if ($props -and $props.AlbumTitle) { $props.AlbumTitle } else { "" }
                $playing = if ($playback) { ($playback.PlaybackStatus -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing) } else { $false }
                
                # Precise position calculation using LastUpdatedTime to eliminate timeline freezing and lyrics lag
                $nowUtc = [DateTimeOffset]::UtcNow
                $pos = 0
                $dur = 0
                if ($timeline) {
                    $rawPos = $timeline.Position.TotalMilliseconds
                    $lut = $timeline.LastUpdatedTime
                    $delta = 0
                    if ($playing -and $lut -and $lut.Year -gt 2000) {
                        $delta = ($nowUtc - $lut).TotalMilliseconds
                        if ($delta -lt 0) { $delta = 0 }
                    }
                    $pos = [math]::Round($rawPos + $delta)
                    if ($timeline.EndTime) {
                        $dur = [math]::Round($timeline.EndTime.TotalMilliseconds)
                        if ($dur -gt 0 -and $pos -gt $dur) {
                            $pos = $dur
                        }
                    }
                }

                $shuf = if ($playback) { [bool]$playback.IsShuffleActive } else { $false }
                $rep = if ($playback -and $playback.AutoRepeatMode) { $playback.AutoRepeatMode.ToString().ToLower() } else { "none" }

                # Controls capability flags reported by Spotify via Windows SMTC
                $canPlay = if ($ctrls) { [bool]($ctrls.IsPlayPauseToggleEnabled -or $ctrls.IsPlayEnabled -or $ctrls.IsPauseEnabled) } else { $true }
                $canNext = if ($ctrls) { [bool]$ctrls.IsNextEnabled } else { $true }
                $canPrev = if ($ctrls) { [bool]$ctrls.IsPreviousEnabled } else { $true }
                $canShuf = if ($ctrls) { [bool]$ctrls.IsShuffleEnabled } else { $true }
                $canRep  = if ($ctrls) { [bool]$ctrls.IsRepeatEnabled } else { $true }
                $canSeek = if ($ctrls) { [bool]$ctrls.IsPlaybackPositionEnabled } else { $true }

                # Read current audio master volume percent
                $curVol = [math]::Round(([AudioMaster]::Get()) * 100)

                $obj = [PSCustomObject]@{
                    running = $true
                    title = $title
                    artist = $artist
                    album = $album
                    pos = $pos
                    dur = $dur
                    playing = $playing
                    shuffle = $shuf
                    repeat = $rep
                    vol = $curVol
                    canPlayPause = $canPlay
                    canNext = $canNext
                    canPrev = $canPrev
                    canShuffle = $canShuf
                    canRepeat = $canRep
                    canSeek = $canSeek
                }
                $json = ConvertTo-Json -Compress $obj
                $writer.WriteLine($json)
                $writer.Flush()
            } else {
                $curVol = [math]::Round(([AudioMaster]::Get()) * 100)
                $writer.WriteLine('{"running":false,"vol":' + $curVol + '}')
                $writer.Flush()
            }
        } catch {
            $writer.WriteLine('{"running":false}')
            $writer.Flush()
        }

        # Smooth high-performance polling interval (150ms for instant responsiveness without lag)
        Start-Sleep -Milliseconds 150
    }
} catch {
    exit 1
}
