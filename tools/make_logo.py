#!/usr/bin/env python3
"""
Draws the logo candidates as 32x32 pixel art, pixel by pixel, and scales them up without smoothing (128 for the mod icon,
512 for Modrinth). Original artwork, no fonts or third-party marks.

    python3 tools/make_logo.py        # writes docs/branding/logo_*.png

Needs Pillow.
"""
import math
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "docs" / "branding"
N = 32


def hexc(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


SKY = [hexc(c) for c in ("#141d33", "#18233d", "#1c2a48", "#213253", "#263a5e")]


def sky(img):
    """Night-blue sky in five hard bands, a little lighter towards the horizon."""
    for y in range(N):
        band = min(len(SKY) - 1, y * len(SKY) // 26)
        for x in range(N):
            img.putpixel((x, y), SKY[band])


def put(img, x, y, color):
    if 0 <= x < N and 0 <= y < N:
        img.putpixel((x, y), color)


def line(img, x0, y0, x1, y1, color):
    steps = max(abs(x1 - x0), abs(y1 - y0)) * 2 + 1
    for i in range(steps + 1):
        t = i / steps
        put(img, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), color)


def train_shed():
    """A brick train-shed arch with radial glazing bars, the track running into it between two platforms."""
    img = Image.new("RGBA", (N, N))
    sky(img)
    brick, brick_lit, mortar = hexc("#8a3a28"), hexc("#b0503a"), hexc("#5e2619")
    glass, glass_lit, bar = hexc("#8fa9bd"), hexc("#b7cad8"), hexc("#4f5f6e")
    cx, cy = 15.5, 17.0
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if y + 0.5 <= cy:
                if 11.0 <= r < 13.5:
                    put(img, x, y, brick_lit if r < 12.2 and dx < 0 else brick)
                    if abs(r - 12.6) < 0.3 and (x + y) % 3 == 0:
                        put(img, x, y, mortar)
                elif r < 11.0:
                    angle = math.degrees(math.atan2(-dy, dx))
                    near_bar = min(abs(angle - a) for a in (45, 90, 135)) * r / 57.3 < 0.5
                    ring = abs(r - 6.0) < 0.5 or r < 2.0
                    put(img, x, y, bar if near_bar or ring else (glass_lit if dx < 0 and r > 6.5 else glass))
    # piers under the arch's springing
    for y in range(17, 27):
        for x in list(range(2, 5)) + list(range(27, 30)):
            put(img, x, y, brick_lit if x in (2, 27) else brick)
        if y % 3 == 0:
            for x in (3, 28):
                put(img, x, y, mortar)
    # the dark hall inside, ballast and platforms
    ballast, sleeper, rail = hexc("#4d4741"), hexc("#3a2a1e"), hexc("#d2d7dc")
    paving, paving_dark, tactile = hexc("#bcbcb3"), hexc("#8f8f88"), hexc("#e8c13a")
    hall = hexc("#0e1320")
    for y in range(17, 21):
        for x in range(5, 27):
            put(img, x, y, hall)
    for y in range(17, N):
        spread = (y - 17) / (N - 17)
        left_edge = 15.5 - (1.5 + 9.5 * spread)
        right_edge = 15.5 + (1.5 + 9.5 * spread)
        for x in range(5, 27):
            if left_edge <= x + 0.5 <= right_edge:
                put(img, x, y, ballast)
            elif y >= 21:
                put(img, x, y, paving if (x + y) % 5 else paving_dark)
        if y >= 21:
            for x in range(N):
                if abs(x + 0.5 - left_edge) < 0.75 or abs(x + 0.5 - right_edge) < 0.75:
                    put(img, x, y, tactile)
    for y in range(N - 1, 18, -2):
        spread = (y - 17) / (N - 17)
        half = 1.0 + 6.0 * spread
        for x in range(round(15.5 - half), round(15.5 + half)):
            put(img, x, y, sleeper)
    line(img, 15, 18, 10, 31, rail)
    line(img, 16, 18, 21, 31, rail)
    # platform fronts below the walking surface
    for x in list(range(0, 5)) + list(range(27, N)):
        for y in range(27, N):
            put(img, x, y, paving if y == 27 else paving_dark)
    return img


def wave_canopy():
    """A white wave canopy on two branching columns over a platform, a train waiting under it."""
    img = Image.new("RGBA", (N, N))
    sky(img)
    roof, roof_shade, column = hexc("#f2f2ee"), hexc("#a9b0b8"), hexc("#d3d8dc")
    body, stripe, window, nose = hexc("#e9e6df"), hexc("#c8352b"), hexc("#1d2633"), hexc("#f5c33b")
    paving, face, tactile, rail, ground = hexc("#bfbfb6"), hexc("#7d7d76"), hexc("#e8c13a"), hexc("#cfd4d9"), hexc("#3b3632")
    # train, behind the platform
    for x in range(13, N):
        for y in range(14, 24):
            slope = x - 13 < (23 - y) // 2
            if slope:
                continue
            color = body
            if y in (17, 18):
                color = window
            if y == 20:
                color = stripe
            put(img, x, y, color)
    put(img, 15, 21, nose)
    put(img, 16, 21, nose)
    for x in range(20, N, 6):
        for y in (17, 18):
            put(img, x, y, body)
    def top(x):
        return 9 + round(1.6 * math.sin((x - 1) / 29 * 2 * math.pi))

    # columns that branch into the roof like trees
    for cx in (7, 24):
        fork = max(top(cx - 3), top(cx + 4)) + 5
        for y in range(fork, 24):
            put(img, cx, y, column)
            put(img, cx + 1, y, roof_shade)
        line(img, cx, fork, cx - 3, top(cx - 3) + 3, column)
        line(img, cx + 1, fork, cx + 4, top(cx + 4) + 3, column)
        for y in range(top(cx) + 3, fork):
            put(img, cx, y, column)
            put(img, cx + 1, y, column)
    # the wave roof, two pixels thick, shaded underneath
    for x in range(1, 31):
        put(img, x, top(x), roof)
        put(img, x, top(x) + 1, roof)
        put(img, x, top(x) + 2, roof_shade)
    # platform, edge strip, rails and ground
    for x in range(N):
        put(img, x, 24, tactile)
        for y in range(25, 27):
            put(img, x, y, paving)
        for y in range(27, 29):
            put(img, x, y, face)
        put(img, x, 29, rail)
        for y in range(30, N):
            put(img, x, y, ground if (x + y) % 4 else face)
    return img


def save(name, img):
    OUT.mkdir(parents=True, exist_ok=True)
    for size in (32, 128, 512):
        img.resize((size, size), Image.NEAREST).save(OUT / f"logo_{name}_{size}.png")


if __name__ == "__main__":
    save("train_shed", train_shed())
    save("wave_canopy", wave_canopy())
    print("written to", OUT)
