#!/usr/bin/env python3
"""
Synthesises the original placeholder announcement chimes (mono Ogg Vorbis) used by the live announcement system.
Pure additive synthesis, no samples or third-party audio. Output is committed; this script only needs re-running if
the chimes change.

Requires numpy + soundfile (libsndfile has a Vorbis encoder):
    python3 -m venv /tmp/venv && /tmp/venv/bin/pip install numpy soundfile && /tmp/venv/bin/python tools/make_live_sounds.py
"""
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "aurelia_transit_architecture" / "sounds" / "live"


def bell(freq, length, decay):
    """A soft struck-bell tone: a few inharmonic partials with exponential decay and a short attack."""
    t = np.arange(int(RATE * length)) / RATE
    partials = ((1.0, 1.0), (2.0, 0.42), (2.76, 0.22), (4.1, 0.08), (5.4, 0.04))
    sig = sum(a * np.sin(2 * np.pi * freq * m * t) * np.exp(-decay * m * 0.55 * t) for m, a in partials)
    attack = np.minimum(1.0, t / 0.006)
    return sig * attack


def place(track, tone, start):
    i = int(RATE * start)
    track[i:i + len(tone)] += tone[:len(track) - i]


def render(notes, length):
    track = np.zeros(int(RATE * length))
    for freq, start, dur, decay in notes:
        place(track, bell(freq, dur, decay), start)
    track /= max(1e-6, np.max(np.abs(track)))
    fade = np.minimum(1.0, np.linspace(1, 0, len(track)) * 40)
    return (track * fade * 0.6).astype(np.float32)


CHIMES = {
    # two-note descending "ding-dong"
    "chime_info": render([(659.25, 0.0, 0.9, 5.5), (523.25, 0.42, 1.2, 4.5)], 1.65),
    # three short insistent pulses
    "chime_alert": render([(880.0, 0.0, 0.35, 11), (880.0, 0.26, 0.35, 11), (880.0, 0.52, 0.6, 9)], 1.15),
    # low-high-low, slightly sombre
    "chime_delay": render([(392.0, 0.0, 0.7, 6), (523.25, 0.3, 0.7, 6), (392.0, 0.62, 1.0, 5)], 1.7),
}

OUT.mkdir(parents=True, exist_ok=True)
for name, data in CHIMES.items():
    sf.write(OUT / f"{name}.ogg", data, RATE, format="OGG", subtype="VORBIS")
    print("wrote", name, f"{len(data) / RATE:.2f}s")
