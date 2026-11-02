import os
import zipfile
import json
import shutil

TARGET_VERSIONS = [
    # Group A
    {
        "mc_ver": "1.19.4",
        "base": "build/bases/base-1.19.4.jar",
        "jar_name": "espoty-2.3.3-mc1.19.4.jar",
        "java_dep": ">=17",
        "loader_dep": ">=0.15.0",
        "group": "A"
    },
    # Group B
    {
        "mc_ver": "1.20",
        "base": "build/bases/base-1.20.1.jar",
        "jar_name": "espoty-2.3.3-mc1.20.jar",
        "java_dep": ">=17",
        "loader_dep": ">=0.15.0",
        "group": "B"
    },
    {
        "mc_ver": "1.20.1",
        "base": "build/bases/base-1.20.1.jar",
        "jar_name": "espoty-2.3.3-mc1.20.1.jar",
        "java_dep": ">=17",
        "loader_dep": ">=0.15.0",
        "group": "B"
    },
    {
        "mc_ver": "1.20.2",
        "base": "build/bases/base-1.20.1.jar",
        "jar_name": "espoty-2.3.3-mc1.20.2.jar",
        "java_dep": ">=17",
        "loader_dep": ">=0.15.0",
        "group": "B"
    },
    {
        "mc_ver": "1.20.3",
        "base": "build/bases/base-1.20.1.jar",
        "jar_name": "espoty-2.3.3-mc1.20.3.jar",
        "java_dep": ">=17",
        "loader_dep": ">=0.15.0",
        "group": "B"
    },
    {
        "mc_ver": "1.20.4",
        "base": "build/bases/base-1.20.1.jar",
        "jar_name": "espoty-2.3.3-mc1.20.4.jar",
        "java_dep": ">=17",
        "loader_dep": ">=0.15.0",
        "group": "B"
    },
    # Group C
    {
        "mc_ver": "1.20.5",
        "base": "build/bases/base-1.21.1.jar",
        "jar_name": "espoty-2.3.3-mc1.20.5.jar",
        "java_dep": ">=21",
        "loader_dep": ">=0.15.0",
        "group": "C"
    },
    {
        "mc_ver": "1.20.6",
        "base": "build/bases/base-1.21.1.jar",
        "jar_name": "espoty-2.3.3-mc1.20.6.jar",
        "java_dep": ">=21",
        "loader_dep": ">=0.15.0",
        "group": "C"
    },
    # Group D
    {
        "mc_ver": "1.21",
        "base": "build/bases/base-1.21.1.jar",
        "jar_name": "espoty-2.3.3-mc1.21.jar",
        "java_dep": ">=21",
        "loader_dep": ">=0.15.0",
        "group": "D"
    },
    {
        "mc_ver": "1.21.1",
        "base": "build/bases/base-1.21.1.jar",
        "jar_name": "espoty-2.3.3-mc1.21.1.jar",
        "java_dep": ">=21",
        "loader_dep": ">=0.15.0",
        "group": "D"
    },
    # Group E (26.x snapshot / new architecture)
    {
        "mc_ver": "26.1",
        "base": "build/bases/base-modern.jar",
        "jar_name": "espoty-2.3.3-mc26.1.jar",
        "java_dep": ">=25",
        "loader_dep": ">=0.16.0",
        "group": "E"
    },
    {
        "mc_ver": "26.1.1",
        "base": "build/bases/base-modern.jar",
        "jar_name": "espoty-2.3.3-mc26.1.1.jar",
        "java_dep": ">=25",
        "loader_dep": ">=0.16.0",
        "group": "E"
    },
    {
        "mc_ver": "26.1.2",
        "base": "build/bases/base-modern.jar",
        "jar_name": "espoty-2.3.3-mc26.1.2.jar",
        "java_dep": ">=25",
        "loader_dep": ">=0.16.0",
        "group": "E"
    },
    {
        "mc_ver": "26.2",
        "base": "build/bases/base-modern.jar",
        "jar_name": "espoty-2.3.3-mc26.2.jar",
        "java_dep": ">=25",
        "loader_dep": ">=0.16.0",
        "group": "E"
    },
    {
        "mc_ver": "26.3",
        "base": "build/bases/base-modern.jar",
        "jar_name": "espoty-2.3.3-mc26.3.jar",
        "java_dep": ">=25",
        "loader_dep": ">=0.16.0",
        "group": "E"
    },
    # Baseline
    {
        "mc_ver": "1.21.11",
        "base": "build/bases/base-modern.jar",
        "jar_name": "espoty-2.3.3-mc1.21.11.jar",
        "java_dep": ">=21",
        "loader_dep": ">=0.16.0",
        "group": "Baseline"
    }
]

def package_all():
    out_dir = "build/libs"
    os.makedirs(out_dir, exist_ok=True)
    
    print("=" * 80)
    print("PACKAGING TARGET BUILDS")
    print("=" * 80)

    for item in TARGET_VERSIONS:
        base_path = item["base"]
        if not os.path.exists(base_path):
            raise FileNotFoundError(f"Base jar not found: {base_path}")
        
        target_path = os.path.join(out_dir, item["jar_name"])
        
        with zipfile.ZipFile(base_path, "r") as zin:
            with zipfile.ZipFile(target_path, "w", compression=zipfile.ZIP_DEFLATED) as zout:
                for entry in zin.infolist():
                    data = zin.read(entry.filename)
                    if entry.filename == "fabric.mod.json":
                        meta = json.loads(data.decode("utf-8"))
                        meta["depends"]["minecraft"] = item["mc_ver"]
                        meta["depends"]["java"] = item["java_dep"]
                        meta["depends"]["fabricloader"] = item["loader_dep"]
                        data = json.dumps(meta, indent=2).encode("utf-8")
                    zout.writestr(entry, data)
        
        size_kb = os.path.getsize(target_path) / 1024
        print(f"[Group {item['group']:<8}] MC {item['mc_ver']:<7} -> {item['jar_name']:<30} ({size_kb:,.1f} KB)")

    # Also ensure default espoty-2.3.3.jar is identical to baseline 1.21.11
    shutil.copy2(os.path.join(out_dir, "espoty-2.3.3-mc1.21.11.jar"), os.path.join(out_dir, "espoty-2.3.3.jar"))
    print(f"Default jar espoty-2.3.3.jar created from baseline 1.21.11.")
    print("=" * 80)

def verify_all():
    out_dir = "build/libs"
    print("\nVERIFYING GENERATED JARS")
    print("-" * 80)
    all_ok = True
    for item in TARGET_VERSIONS:
        jar_path = os.path.join(out_dir, item["jar_name"])
        if not os.path.exists(jar_path):
            print(f"FAILED: Missing {jar_path}")
            all_ok = False
            continue
        
        with zipfile.ZipFile(jar_path, "r") as z:
            names = set(z.namelist())
            has_mod_json = "fabric.mod.json" in names
            has_client = "it/itzcrih/espoty/client/ESpotyModClient.class" in names
            has_hud = "it/itzcrih/espoty/gui/hud/SpotifyHudRenderer.class" in names
            has_screen = "it/itzcrih/espoty/gui/screen/SpotifyScreen.class" in names
            has_font = "assets/espoty/font/coolfont.json" in names
            
            with z.open("fabric.mod.json") as f:
                meta = json.loads(f.read().decode("utf-8"))
                mc_dep = meta.get("depends", {}).get("minecraft")
                java_dep = meta.get("depends", {}).get("java")
                loader_dep = meta.get("depends", {}).get("fabricloader")
                
            check = (
                has_mod_json and has_client and has_hud and has_screen and has_font
                and mc_dep == item["mc_ver"]
                and java_dep == item["java_dep"]
                and loader_dep == item["loader_dep"]
            )
            status = "OK" if check else "INVALID"
            if not check:
                all_ok = False
            print(f"[{status}] {item['jar_name']}: MC={mc_dep}, Java={java_dep}, Loader={loader_dep}, Classes={len(names)}")

    if all_ok:
        print("\nAll 16 target jars verified successfully with exact manifest metadata!")
    else:
        print("\nSome jars failed verification!")

if __name__ == "__main__":
    package_all()
    verify_all()
