#!/usr/bin/env python3
from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ABI = "arm64-v8a"

# Independent runtime surfaces that must survive the emergency LiteRT media-core restore.
REQUIRED_NATIVE = {
    "libLiteRt.so",
    "libLiteRtClGlAccelerator.so",
    "liblitert_jni.so",
    "liblitertlm_jni.so",
    "libstable_diffusion_core.so",
    "libvisual_creation_jni.so",
    "libvosk.so",
    "libffmpegkit.so",
    "libimage_processing_util_jni.so",
}

# These source/runtime entry points existed at the MCP250 protected product boundary. The audit is
# intentionally string-based over all DEX files so it remains independent of R8/class numbering.
REQUIRED_DEX = {
    b"com/google/ai/edge/gallery/customtasks/aikeyboard": "AI keyboard",
    b"com/google/ai/edge/gallery/customtasks/musicgeneration": "local music generation",
    b"com/google/ai/edge/gallery/customtasks/tinygarden": "TinyGarden image generation",
    b"com/google/ai/edge/gallery/customtasks/videoqa": "local video QA",
    b"com/google/ai/edge/gallery/customtasks/visionnarration": "vision narration",
    b"com/google/ai/edge/gallery/customtasks/visualcreation": "visual creation",
    b"com/google/ai/edge/litert/Environment": "LiteRT Environment Java API",
    b"AgentTextToolCallFallback": "MCP256+ textual tool dispatch",
    b"onHostToolDispatchAccepted": "MCP259 host/runtime continuation authority",
    b"tool_continuation": "MCP206/MCP259 fresh tool continuation",
    b"xlsx_create": "MCP258 Excel creation backend",
}

# Pre-Office product skills explicitly called out in the regression report. Office skills may be
# added, but none of these established skills may disappear from the final APK.
REQUIRED_SKILLS = {
    "agnes-omni",
    "anysearch-search",
    "edge-tts",
    "exa-search",
    "file-workspace",
    "langsearch-search",
    "long-text-writer",
    "media-toolbox",
    "minimax-omni",
    "tavily-search",
    "weather-query",
    "web-page-extract",
}

# These engines are deliberately independent from the pinned media LiteRT core at the ELF level.
# If they ever start directly linking libLiteRt.so, the compatibility story must be reviewed again.
MUST_NOT_DIRECTLY_LINK_LITERT_CORE = {
    "liblitertlm_jni.so": "LiteRT-LM / chat / Agent / AI keyboard",
    "libstable_diffusion_core.so": "Local Dream / Stable Diffusion",
    "libvisual_creation_jni.so": "visual creation JNI",
    "libvosk.so": "local speech recognition",
}


def run(*args: str) -> str:
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def symbols(path: Path, *, undefined: bool) -> set[str]:
    out = run("readelf", "-Ws", str(path))
    result: set[str] = set()
    for line in out.splitlines():
        parts = line.split()
        if len(parts) < 8 or not parts[0].endswith(":"):
            continue
        ndx = parts[6]
        if (ndx == "UND") != undefined:
            continue
        name = parts[7].split("@", 1)[0]
        if name:
            result.add(name)
    return result


def needed_libraries(path: Path) -> set[str]:
    out = run("readelf", "-d", str(path))
    return set(re.findall(r"Shared library: \[([^]]+)\]", out))


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: audit_mcp260_core_surface.py <apk>")
    apk = Path(sys.argv[1]).resolve()
    if not apk.is_file():
        raise RuntimeError(f"APK not found: {apk}")
    if shutil.which("readelf") is None:
        raise RuntimeError("readelf is required")

    with tempfile.TemporaryDirectory(prefix="mcp260-surface-") as tmp_name:
        tmp = Path(tmp_name)
        with zipfile.ZipFile(apk) as zf:
            names = set(zf.namelist())
            native_names = {
                Path(name).name
                for name in names
                if name.startswith(f"lib/{ABI}/") and name.endswith(".so")
            }
            missing_native = sorted(REQUIRED_NATIVE - native_names)
            if missing_native:
                raise RuntimeError(
                    "MCP260 core surface missing native libraries: " + ", ".join(missing_native)
                )

            dex_members = sorted(
                name for name in names if re.fullmatch(r"classes\d*\.dex", Path(name).name)
            )
            if not dex_members:
                raise RuntimeError("APK contains no classes*.dex")
            dex_blob = b"".join(zf.read(name) for name in dex_members)
            missing_dex = [
                label for needle, label in REQUIRED_DEX.items() if needle not in dex_blob
            ]
            if missing_dex:
                raise RuntimeError(
                    "MCP260 core surface missing DEX entry points: " + ", ".join(missing_dex)
                )

            missing_skills = sorted(
                skill
                for skill in REQUIRED_SKILLS
                if f"assets/skills/{skill}/SKILL.md" not in names
            )
            if missing_skills:
                raise RuntimeError(
                    "MCP260 core surface missing established Agent skills: "
                    + ", ".join(missing_skills)
                )

            for name in names:
                if name.startswith(f"lib/{ABI}/") and name.endswith(".so"):
                    zf.extract(name, tmp)

        lib_dir = tmp / "lib" / ABI
        core = lib_dir / "libLiteRt.so"
        core_exports = {s for s in symbols(core, undefined=False) if s.startswith("LiteRt")}
        if "LiteRtCreateModelFromFd" not in core_exports:
            raise RuntimeError("final APK libLiteRt.so does not export LiteRtCreateModelFromFd")

        # Whole-APK ABI closure: every direct libLiteRt.so consumer must have all of its LiteRt*
        # imports satisfied by the exact core that will load on the phone. This catches the same
        # failure class that escaped MCP251-MCP259, including future compiler/plugin/JNI consumers.
        checked_consumers: list[str] = []
        for so in sorted(lib_dir.glob("*.so")):
            try:
                needed = needed_libraries(so)
            except subprocess.CalledProcessError:
                continue
            if "libLiteRt.so" not in needed:
                continue
            required = {s for s in symbols(so, undefined=True) if s.startswith("LiteRt")}
            missing = sorted(required - core_exports)
            if missing:
                raise RuntimeError(
                    f"Whole-APK LiteRT ABI mismatch for {so.name}: missing " + ", ".join(missing)
                )
            checked_consumers.append(f"{so.name}:{len(required)}")

        for lib, label in MUST_NOT_DIRECTLY_LINK_LITERT_CORE.items():
            needed = needed_libraries(lib_dir / lib)
            if "libLiteRt.so" in needed:
                raise RuntimeError(
                    f"{label} unexpectedly directly links libLiteRt.so via {lib}; review required"
                )

        print(
            "MCP260 whole-app surface audit passed; "
            f"native={len(REQUIRED_NATIVE)}; dex={len(REQUIRED_DEX)}; "
            f"legacy_skills={len(REQUIRED_SKILLS)}; "
            "direct_core_consumers=" + ",".join(checked_consumers)
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
