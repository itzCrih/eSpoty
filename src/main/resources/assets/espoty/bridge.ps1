param([int]$Port = 0)

$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# CoreAudio Native Audio Controller for Spotify process volume (never touches Windows master endpoint)
Add-Type -TypeDefinition @"
using System;
using System.Diagnostics;
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
[Guid("77AA99A0-1BD6-484F-BC0A-70C5D217D568")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IAudioSessionManager2 {
    int NotImpl1();
    int NotImpl2();
    [PreserveSig]
    int GetSessionEnumerator(out IAudioSessionEnumerator SessionEnum);
}

[ComImport]
[Guid("E2F560DC-CC84-4366-9EC8-0D7791C6E1C3")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IAudioSessionEnumerator {
    [PreserveSig]
    int GetCount(out int SessionCount);
    [PreserveSig]
    int GetSession(int SessionIndex, out IAudioSessionControl Session);
}

[ComImport]
[Guid("F4B41553-E727-4FA0-BD20-FB40F5F1A6F0")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IAudioSessionControl {
    int NotImpl1();
    int NotImpl2();
    int NotImpl3();
    int NotImpl4();
    int NotImpl5();
    int NotImpl6();
    int NotImpl7();
}

[ComImport]
[Guid("BFB7FF88-7239-4FC9-8FA2-00F950E2531C")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
internal interface IAudioSessionControl2 {
    int NotImpl1();
    int NotImpl2();
    int NotImpl3();
    int NotImpl4();
    int NotImpl5();
    int NotImpl6();
    int NotImpl7();
    [PreserveSig]
    int GetSessionIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string pRetVal);
    [PreserveSig]
    int GetSessionInstanceIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string pRetVal);
    [PreserveSig]
    int GetProcessId(out int pRetVal);
    [PreserveSig]
    int IsSystemSoundsSession();
    [PreserveSig]
    int SetDuckingPreference(bool optOut);
}

[ComImport]
[Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface ISimpleAudioVolume {
    [PreserveSig]
    int SetMasterVolume(float fLevel, ref Guid EventContext);
    [PreserveSig]
    int GetMasterVolume(out float pfLevel);
    [PreserveSig]
    int SetMute(bool bMute, ref Guid EventContext);
    [PreserveSig]
    int GetMute(out bool pbMute);
}

[ComImport]
[Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
internal class MMDeviceEnumerator {}

public class SpotifyAppVolume {
    private static bool IsSpotifySession(IAudioSessionControl2 ctl2) {
        if (ctl2 == null) return false;
        try {
            int pid;
            ctl2.GetProcessId(out pid);
            if (pid > 0) {
                try {
                    var p = Process.GetProcessById(pid);
                    if (p.ProcessName.ToLower().Contains("spotify")) return true;
                } catch {}
            }
        } catch {}
        try {
            string sessId;
            ctl2.GetSessionIdentifier(out sessId);
            if (sessId != null && sessId.ToLower().Contains("spotify")) return true;
        } catch {}
        try {
            string instId;
            ctl2.GetSessionInstanceIdentifier(out instId);
            if (instId != null && instId.ToLower().Contains("spotify")) return true;
        } catch {}
        return false;
    }

    public static float Get() {
        try {
            var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumerator());
            IMMDevice speakers;
            enumerator.GetDefaultAudioEndpoint(0, 1, out speakers);
            var iid = typeof(IAudioSessionManager2).GUID;
            object o;
            speakers.Activate(ref iid, 0x17, IntPtr.Zero, out o);
            var mgr = (IAudioSessionManager2)o;
            IAudioSessionEnumerator sessionEnum;
            mgr.GetSessionEnumerator(out sessionEnum);
            int count;
            sessionEnum.GetCount(out count);
            for (int i = 0; i < count; i++) {
                IAudioSessionControl ctl;
                sessionEnum.GetSession(i, out ctl);
                var ctl2 = ctl as IAudioSessionControl2;
                if (IsSpotifySession(ctl2)) {
                    var volCtl = ctl as ISimpleAudioVolume;
                    if (volCtl != null) {
                        float v;
                        volCtl.GetMasterVolume(out v);
                        return v;
                    }
                }
            }
        } catch {}
        return -1.0f;
    }

    public static bool Set(float vol) {
        bool found = false;
        try {
            var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumerator());
            IMMDevice speakers;
            enumerator.GetDefaultAudioEndpoint(0, 1, out speakers);
            var iid = typeof(IAudioSessionManager2).GUID;
            object o;
            speakers.Activate(ref iid, 0x17, IntPtr.Zero, out o);
            var mgr = (IAudioSessionManager2)o;
            IAudioSessionEnumerator sessionEnum;
            mgr.GetSessionEnumerator(out sessionEnum);
            int count;
            sessionEnum.GetCount(out count);
            for (int i = 0; i < count; i++) {
                IAudioSessionControl ctl;
                sessionEnum.GetSession(i, out ctl);
                var ctl2 = ctl as IAudioSessionControl2;
                if (IsSpotifySession(ctl2)) {
                    var volCtl = ctl as ISimpleAudioVolume;
                    if (volCtl != null) {
                        Guid g = Guid.Empty;
                        volCtl.SetMasterVolume(vol, ref g);
                        volCtl.SetMute(vol <= 0.001f, ref g);
                        found = true;
                    }
                }
            }
        } catch {}
        return found;
    }
}
"@

# Locate Spotify CLI helper bundled with Spotify Desktop
$cliPath = Join-Path $env:APPDATA "Spotify\spotify_cli.exe"
if (-not (Test-Path $cliPath)) {
    $alt = Join-Path $env:LOCALAPPDATA "Spotify\spotify_cli.exe"
    if (Test-Path $alt) { $cliPath = $alt } else { $cliPath = $null }
}

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

# State Cache for fast lookups
$script:lastTrackKey = ""
$script:currentArtUrl = ""
$script:isLiked = $false
$script:currentUri = ""
$script:canShuffle = $true
$script:canRepeat = $true
$script:currentVolume = 80
$script:volCheckCounter = 0

# Read initial active volume from Spotify
$initVol = [SpotifyAppVolume]::Get()
if ($initVol -ge 0 -and $initVol -le 1.0) {
    $script:currentVolume = [int][math]::Round($initVol * 100)
}
if ($cliPath) {
    try {
        $dev = & $cliPath devices --format json 2>$null | ConvertFrom-Json
        if ($dev -and $dev.devices) {
            foreach ($d in $dev.devices) {
                if ($d.is_active -and $null -ne $d.volume) {
                    $script:currentVolume = [int]$d.volume
                    break
                }
            }
        }
    } catch {}
}

function Update-SpotifyMetadata($title, $artist) {
    if (-not $cliPath) { return }
    try {
        $npJson = & $cliPath now-playing --format json 2>$null | ConvertFrom-Json
        if ($npJson -and $npJson.currently_playing -and $npJson.currently_playing.uri) {
            $script:currentUri = $npJson.currently_playing.uri

            # Query real Spotify album artwork CDN url and upgrade to 640x640 HD
            $luJson = & $cliPath lookup $script:currentUri --format json 2>$null | ConvertFrom-Json
            if ($luJson -and $luJson.entities -and $luJson.entities.Count -gt 0) {
                $rawArt = $luJson.entities[0].image_url
                if ($rawArt) {
                    $script:currentArtUrl = $rawArt.Replace("ab67616d00004851", "ab67616d0000b273").Replace("ab67616d00001e02", "ab67616d0000b273")
                }
            }

            # Query if track is in user's liked library
            $cntJson = & $cliPath library contains $script:currentUri --format json 2>$null | ConvertFrom-Json
            if ($cntJson -and $cntJson.contains) {
                $script:isLiked = [bool]($cntJson.contains.($script:currentUri))
            }

            # Probe if shuffle and repeat are allowed in current context safely
            $sSession = Get-SpotifySession
            $wasShuf = if ($sSession) { ($sSession.GetPlaybackInfo()).IsShuffleActive } else { $false }
            $probe = & $cliPath shuffle on 2>&1
            if ($LASTEXITCODE -ne 0) {
                $script:canShuffle = $false
                $script:canRepeat = $false
            } else {
                $script:canShuffle = $true
                $script:canRepeat = $true
                if (-not $wasShuf) {
                    & $cliPath shuffle off 2>$null | Out-Null
                }
            }
        }
    } catch {}
}

function Send-SpotifyQueue {
    if (-not $cliPath -or -not $writer) { return }
    try {
        $qRaw = & $cliPath queue --format json 2>$null
        if ($qRaw) {
            $writer.WriteLine('{"type":"queue","data":' + $qRaw + '}')
            $writer.Flush()
        }
    } catch {}
}

if ($Port -le 0) {
    $s = Get-SpotifySession
    if ($s) {
        $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        Write-Output "Standalone session found: $($props.Title) by $($props.Artist)"
        Update-SpotifyMetadata $props.Title $props.Artist
        Write-Output "Track URI: $script:currentUri"
        Write-Output "Art URL: $script:currentArtUrl"
        Write-Output "Liked: $script:isLiked"
        Write-Output "CanShuffle: $script:canShuffle"
    } else {
        Write-Output "No Spotify session found"
    }
    Write-Output "Volume is: $([math]::Round(([SpotifyAppVolume]::Get()) * 100))%"
    exit 0
}

try {
    $client = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $Port)
    $stream = $client.GetStream()
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $writer = New-Object System.IO.StreamWriter($stream, $utf8NoBom)
    $reader = New-Object System.IO.StreamReader($stream, $utf8NoBom)

    while ($client.Connected) {
        # Process any pending incoming commands from Java immediately
        while ($stream.DataAvailable) {
            $cmd = $reader.ReadLine()
            if ($null -eq $cmd) { break }
            $cmd = $cmd.Trim()
            if ($cmd -eq "quit" -or $cmd -eq "exit") {
                $client.Close()
                exit 0
            }

            try {
                if ($cmd.StartsWith("vol:")) {
                    $vInt = [int]($cmd.Substring(4))
                    if ($vInt -lt 0) { $vInt = 0 }
                    if ($vInt -gt 100) { $vInt = 100 }
                    $script:currentVolume = $vInt
                    $script:volCheckCounter = 0
                    $vFloat = [math]::Round($vInt / 100.0, 2)

                    # 1. Update Spotify application audio session volume via Windows CoreAudio immediately
                    [SpotifyAppVolume]::Set($vFloat) | Out-Null

                    # 2. Also update Spotify's internal application volume via CLI asynchronously without blocking
                    if ($cliPath) {
                        Start-Process -FilePath $cliPath -ArgumentList "volume $vFloat" -WindowStyle Hidden -ErrorAction SilentlyContinue
                    }
                } elseif ($cmd -eq "togglelike") {
                    if ($cliPath -and $script:currentUri) {
                        if ($script:isLiked) {
                            & $cliPath library remove $script:currentUri 2>$null | Out-Null
                            $script:isLiked = $false
                        } else {
                            & $cliPath library add $script:currentUri 2>$null | Out-Null
                            $script:isLiked = $true
                        }
                    }
                } elseif ($cmd -eq "queue:list") {
                    Send-SpotifyQueue
                } elseif ($cmd.StartsWith("queue:remove:")) {
                    if ($cliPath) {
                        $pos = $cmd.Substring(13)
                        & $cliPath queue remove $pos 2>$null | Out-Null
                        Send-SpotifyQueue
                    }
                } elseif ($cmd.StartsWith("queue:move:")) {
                    if ($cliPath) {
                        $parts = $cmd.Substring(11).Split(":")
                        if ($parts.Length -eq 2) {
                            $from = $parts[0]
                            $to = $parts[1]
                            & $cliPath queue move $from $to 2>$null | Out-Null
                            Send-SpotifyQueue
                        }
                    }
                } elseif ($cmd.StartsWith("queue:play:")) {
                    if ($cliPath) {
                        $uri = $cmd.Substring(11)
                        & $cliPath play $uri 2>$null | Out-Null
                        Send-SpotifyQueue
                    }
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
                            if ($script:canShuffle) {
                                $pinfo = $s.GetPlaybackInfo()
                                $curShuf = if ($pinfo) { $pinfo.IsShuffleActive } else { $false }
                                Await ($s.TryChangeShuffleActiveAsync(-not $curShuf)) ([bool]) | Out-Null
                            }
                        } elseif ($cmd -eq "repeatcycle") {
                            if ($script:canRepeat) {
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
                            }
                        } elseif ($cmd -eq "refresh") {
                            $script:manager = $null
                            $script:lastTrackKey = ""
                        }
                    }
                }
            } catch {}
        }

        # Query and send current media state
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
                
                # Check for track change to update Spotify CLI metadata
                $trackKey = "$title - $artist"
                if ($trackKey -ne $script:lastTrackKey -and $title -ne "") {
                    $script:lastTrackKey = $trackKey
                    Update-SpotifyMetadata $title $artist
                    Send-SpotifyQueue
                }

                # Precise position calculation using LastUpdatedTime
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

                # Controls capability flags reported by Spotify
                $canPlay = if ($ctrls) { [bool]($ctrls.IsPlayPauseToggleEnabled -or $ctrls.IsPlayEnabled -or $ctrls.IsPauseEnabled) } else { $true }
                $canNext = if ($ctrls) { [bool]$ctrls.IsNextEnabled } else { $true }
                $canPrev = if ($ctrls) { [bool]$ctrls.IsPreviousEnabled } else { $true }
                $canSeek = if ($ctrls) { [bool]$ctrls.IsPlaybackPositionEnabled } else { $true }

                # Periodically re-sync with active Spotify application volume (every ~25 ticks / 3.75s)
                $script:volCheckCounter++
                if ($script:volCheckCounter -ge 25) {
                    $script:volCheckCounter = 0
                    $vGet = [SpotifyAppVolume]::Get()
                    if ($vGet -ge 0 -and $vGet -le 1.0) {
                        $script:currentVolume = [int][math]::Round($vGet * 100)
                    }
                }

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
                    vol = $script:currentVolume
                    art = $script:currentArtUrl
                    liked = $script:isLiked
                    canPlayPause = $canPlay
                    canNext = $canNext
                    canPrev = $canPrev
                    canShuffle = $script:canShuffle
                    canRepeat = $script:canRepeat
                    canSeek = $canSeek
                }
                $json = ConvertTo-Json -Compress $obj
                $writer.WriteLine($json)
                $writer.Flush()
            } else {
                $writer.WriteLine('{"running":false,"vol":' + $script:currentVolume + '}')
                $writer.Flush()
            }
        } catch {
            $writer.WriteLine('{"running":false}')
            $writer.Flush()
        }

        # Polling interval
        Start-Sleep -Milliseconds 150
    }
} catch {
    exit 1
}
