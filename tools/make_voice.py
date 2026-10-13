#!/usr/bin/env python3
"""
Renders the built-in announcement voice with Piper (MIT) and the English voice "en_GB-cori-high" (Bryce Beattie, trained
on public-domain LibriVox recordings), so the audio may ship with the mod. Run it with a Python that has piper-tts:

    ~/Library/Caches/aureliaex-voice-spike/venv/bin/python tools/make_voice.py
        -> every phrase in AnnouncementText.Phrase and the numbers 0..99 into the mod's assets
           (sounds/voice/*.ogg + aurelia_voice/builtin.json; tools/assets_live.py registers the sound events)

    ~/Library/Caches/aureliaex-voice-spike/venv/bin/python tools/make_voice.py --pack "My Station Names" \\
            --stations "Central,Alpha Street" --routes "Red Line"
        -> a resource pack in run/client/resourcepacks with those station and route names, which the built-in voice cannot
           know; share it with your server's players

Models: <voice>.onnx + .onnx.json from huggingface.co/rhasspy/piper-voices (en/en_GB/cori/high) in AUR_VOICE_DIR
(default ~/Library/Caches/aureliaex-voice-spike/models). Needs ffmpeg/ffprobe and oggenc. Clips are trimmed of silence
and their length becomes duration_ms.
"""
import argparse
import json
import os
import re
import subprocess
import tempfile
import unicodedata
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = "aurelia_transit_architecture"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / MOD
TEXT = ROOT / "src" / "main" / "java" / "com" / "aureliatransit" / "architecture" / "live" / "announce" / "AnnouncementText.java"
VOICE = "en_GB-cori-high"
VOICE_DIR = Path(os.environ.get("AUR_VOICE_DIR", Path.home() / "Library" / "Caches" / "aureliaex-voice-spike" / "models"))
TRIM = "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse"
# the built-in voice sits under every resource pack, so any pack's own clip wins
BUILTIN_PRIORITY = -100


def phrases():
    """(key, text) for every Phrase constant, read from the source so the two never drift apart."""
    source = TEXT.read_text(encoding="utf-8")
    return [("phrase." + key, text) for key, text in re.findall(r'^\s+[A-Z_]+\("([a-z_]+)", "([^"]+)"\)[,;]', source, re.M)]


def normalise(name):
    """FragmentKeys.normalise in Python."""
    out, pending = [], False
    for ch in unicodedata.normalize("NFD", name):
        if unicodedata.category(ch) == "Mn":
            continue
        if ch.isalnum():
            if pending and out:
                out.append("_")
            pending = False
            out.append(ch.lower())
        else:
            pending = True
    return "".join(out)


def render(voice, text, target, work):
    raw, wav = work / "raw.wav", work / "clip.wav"
    with wave.open(str(raw), "wb") as out:
        voice.synthesize_wav(text, out)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(raw), "-af", TRIM, "-ac", "1", "-ar", "44100",
                    "-map_metadata", "-1", "-fflags", "+bitexact", str(wav)], check=True)
    subprocess.run(["oggenc", "--quiet", "-q", "4", "-o", str(target), str(wav)], check=True)
    seconds = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(wav)],
                                   check=True, capture_output=True, text=True).stdout.strip())
    return max(50, int(seconds * 1000))


def build(entries, namespace, assets, manifest, priority, sounds_json):
    from piper import PiperVoice
    voice = PiperVoice.load(str(VOICE_DIR / (VOICE + ".onnx")))
    clips = assets / "sounds" / "voice"
    clips.mkdir(parents=True, exist_ok=True)
    fragments, sounds = {}, {}
    with tempfile.TemporaryDirectory() as tmp:
        for key, text in entries:
            file = key.replace(".", "_")
            millis = render(voice, text, clips / (file + ".ogg"), Path(tmp))
            fragments[key] = {"sound": f"{namespace}:voice.{key}", "duration_ms": millis}
            sounds[f"voice.{key}"] = {"sounds": [{"name": f"{namespace}:voice/{file}", "stream": False}]}
            print(f"{key:48} {millis:5} ms  {text}")
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps({"priority": priority, "fragments": fragments}, indent=1, sort_keys=True) + "\n")
    if sounds_json:
        sounds_json.write_text(json.dumps(sounds, indent=1, sort_keys=True) + "\n")
    return len(entries)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--pack", help="build a station-names resource pack with this name instead of the built-in voice")
    parser.add_argument("--stations", default="", help="comma-separated station names (with --pack)")
    parser.add_argument("--routes", default="", help="comma-separated route names (with --pack)")
    args = parser.parse_args()

    if not args.pack:
        old = ASSETS / "sounds" / "voice"
        for clip in old.glob("*.ogg") if old.exists() else []:
            clip.unlink()
        count = build(phrases() + [("number." + str(n), str(n)) for n in range(100)], MOD, ASSETS,
                      ASSETS / "aurelia_voice" / "builtin.json", BUILTIN_PRIORITY, None)
        print(f"\n{count} clips; run python3 tools/generate_assets.py to register them")
        return

    entries = [("station." + normalise(s), s.strip()) for s in args.stations.split(",") if s.strip()]
    entries += [("route." + normalise(r), r.strip()) for r in args.routes.split(",") if r.strip()]
    if not entries:
        parser.error("--pack needs --stations and/or --routes")
    namespace = "ata_names_" + normalise(args.pack)
    pack = ROOT / "run" / "client" / "resourcepacks" / args.pack
    assets = pack / "assets" / namespace
    build(entries, namespace, assets, assets / "aurelia_voice" / "names.json", 10, assets / "sounds.json")
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": f"{args.pack}: station names for ATA announcements (Piper, voice cori, public domain)"}}, indent=2))
    print(f"\n{len(entries)} clips in {pack}")


if __name__ == "__main__":
    main()
