"""Crops the Head-view renders from studio_thumbs.mjs to the city pictures: the building, 288 px wide, JPEG."""
import glob
import os
import sys

from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
for path in sorted(glob.glob(os.path.join(src, "*.png"))):
    image = Image.open(path).convert("RGB")
    width, height = image.size
    image = image.crop((0, int(height * 0.05), width, int(height * 0.63)))
    image = image.resize((288, int(288 * image.size[1] / image.size[0])), Image.LANCZOS)
    image.save(os.path.join(dst, os.path.basename(path)[:-4] + ".jpg"), quality=74, optimize=True)
