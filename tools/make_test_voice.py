#!/usr/bin/env python3
"""
Builds a LOCAL TEST voice pack for the live announcements with macOS `say`, so the speakers and help points can be
heard during development. Apple's licence does not allow redistributing audio made with its system voices: the pack is
written under run/ (git-ignored) and must never be committed, shipped in the jar or uploaded anywhere.

It speaks every phrase in AnnouncementText.Phrase and the numbers 0..99; give station and route names to add those too.
Each clip is trimmed of silence and its length becomes duration_ms.

Requires macOS `say`, ffmpeg/ffprobe and oggenc (vorbis-tools).
    python3 tools/make_test_voice.py [--voice Daniel] [--stations "Central,Alpha Street"] [--routes "Red Line"]
Then enable "ATA Test Voice (local only)" in Options > Resource Packs and check with /aurelia_live voices.
"""
import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXT = ROOT / "src" / "main" / "java" / "com" / "aureliatransit" / "architecture" / "live" / "announce" / "AnnouncementText.java"
PACK = ROOT / "run" / "client" / "resourcepacks" / "ATA Test Voice (local only)"
NS = "ata_test_voice"
TRIM = "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse"


def phrases():
    """(key, text) for every Phrase enum constant, read from the source so the two never drift apart."""
    source = TEXT.read_text(encoding="utf-8")
    return [("phrase." + key, text) for key, text in re.findall(r'^\s+[A-Z_]+\("([a-z_]+)", "([^"]+)"\)[,;]', source, re.M)]


def normalise(name):
    """Mirror of FragmentKeys.normalise for plain ASCII names (enough for test names)."""
    import unicodedata
    decomposed = unicodedata.normalize("NFD", name)
    out, pending = [], False
    for ch in decomposed:
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


def render(text, voice, target, work):
    aiff = work / "clip.aiff"
    subprocess.run(["say", "-v", voice, "-o", str(aiff), text], check=True)
    wav = work / "clip.wav"
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(aiff), "-af", TRIM, "-ac", "1", "-ar", "44100", "-map_metadata", "-1", "-fflags", "+bitexact", str(wav)], check=True)
    subprocess.run(["oggenc", "--quiet", "-q", "5", "-o", str(target), str(wav)], check=True)
    seconds = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(wav)],
                                   check=True, capture_output=True, text=True).stdout.strip())
    return max(50, int(seconds * 1000))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--voice", default="Daniel")
    parser.add_argument("--stations", default="", help="comma-separated station names")
    parser.add_argument("--routes", default="", help="comma-separated route names")
    args = parser.parse_args()
    if sys.platform != "darwin":
        sys.exit("needs macOS `say`")

    entries = phrases() + [("number." + str(n), str(n)) for n in range(100)]
    entries += [("station." + normalise(s), s.strip()) for s in args.stations.split(",") if s.strip()]
    entries += [("route." + normalise(r), r.strip()) for r in args.routes.split(",") if r.strip()]

    sounds_dir = PACK / "assets" / NS / "sounds" / "voice"
    sounds_dir.mkdir(parents=True, exist_ok=True)
    (PACK / "assets" / NS / "aurelia_voice").mkdir(parents=True, exist_ok=True)
    sounds, fragments = {}, {}
    with tempfile.TemporaryDirectory() as tmp:
        for key, text in entries:
            file = key.replace(".", "_")
            millis = render(text, args.voice, sounds_dir / (file + ".ogg"), Path(tmp))
            sounds[key] = {"sounds": [NS + ":voice/" + file]}
            fragments[key] = {"sound": NS + ":" + key, "duration_ms": millis}
            print(f"{key:48} {millis:5} ms  {text}")

    (PACK / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "ATA test voice (macOS say) - local testing only, do not share"}}, indent=2))
    (PACK / "assets" / NS / "sounds.json").write_text(json.dumps(sounds, indent=1))
    (PACK / "assets" / NS / "aurelia_voice" / "test.json").write_text(json.dumps({"priority": 1, "fragments": fragments}, indent=1))
    print(f"\n{len(entries)} clips in {PACK}")


if __name__ == "__main__":
    main()
