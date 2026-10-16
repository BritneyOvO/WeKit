#!/usr/bin/env python3
"""Check the signed APK contract for a WeKit Xposed module build."""

import argparse
from collections import Counter
import os
from pathlib import Path
import re
import subprocess
import zipfile


REQUIRED = (
    "AndroidManifest.xml", "classes.dex", "resources.arsc",
    "assets/xposed_init", "lib/arm64-v8a/libwekit_native.so",
)

# Module-installer artifacts belong to a root-manager module ZIP, never to the APK.
FORBIDDEN = (
    "module.prop", "customize.sh", "uninstall.sh", "sepolicy.rule", "config.sh",
    "action.sh", "verify.sh", "post-fs-data.sh", "service.sh",
    "META-INF/com/google/android/update-binary",
    "META-INF/com/google/android/updater-script",
)


def check_apk(apk, tools):
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        assert not [name for name, count in Counter(names).items() if count != 1], "duplicate ZIP entries"
        assert archive.testzip() is None, "ZIP CRC failed"
        for name in REQUIRED:
            assert archive.getinfo(name).file_size > 0, f"missing or empty: {name}"
        assert not any(name in names for name in FORBIDDEN), "installer files must not be packaged"
        assert not any(name.startswith("webroot/") for name in names), "module webroot must not be packaged"
        assert not any(
            name.endswith(".apk") or name.endswith("dex.list") or name.startswith(("payload/", "zygisk/"))
            for name in names
        ), "nested payload or packaged module"
        dex = [name for name in names if name.endswith(".dex")]
        assert set(dex) == {"classes.dex", *(f"classes{i}.dex" for i in range(2, len(dex) + 1))}, "noncanonical or duplicate DEX"
        assert {name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')} == {"arm64-v8a"}
        modern = any(name.startswith("META-INF/xposed/") for name in names)
        assert modern == ("standard" in apk.name), "flavor entrypoint mismatch"

    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    assert package, "unreadable APK badging"
    subprocess.run([str(tools / "apksigner"), "verify", "--verbose", str(apk)], check=True, stdout=subprocess.DEVNULL)
    subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk)], check=True, stdout=subprocess.DEVNULL)
    print(f"PASS {apk.name}: signed/aligned, {len(dex)} DEX, {package.group(1)} {package.group(3)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, nargs="+")
    parser.add_argument("--build-tools", type=Path)
    args = parser.parse_args()
    tools = args.build_tools
    if tools is None:
        sdk = Path(os.environ["ANDROID_HOME"])
        stable = [path for path in (sdk / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", path.name)]
        tools = max(stable, key=lambda path: tuple(map(int, path.name.split('.'))))
    for apk in args.apk:
        check_apk(apk.resolve(), tools.resolve())


if __name__ == "__main__":
    main()
