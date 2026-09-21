"""art/fox/poses の高解像度ポーズ画像を、48x48 のドット絵風フレームへ変換する暫定ツール(Phase 1)。

本番用ドット絵が用意できるまでの仮アセット生成用。手順:
  1. 半透明のもや(alpha 低)を除去するため alpha を二値化
  2. 本体(大きい連結成分)の外接矩形の和で切り出し、同一倍率で 48x48 に縮小(ポーズ間の位置ずれを防ぐ)。
     本体の外にはみ出す小さな飛び散りエフェクトは除去する
  3. 全ポーズ共通の減色パレット(既定 20 色)に置き換え
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

SIZE = 48
MARGIN = 2
COLORS = 20
ALPHA_CUT = 128
MAIN_RATIO = 0.10  # 最大成分の面積比がこれ以上なら本体とみなす

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "fox" / "poses"
OUT = ROOT / "app" / "src" / "main" / "assets" / "characters" / "fox" / "frames"


def binarize(im: Image.Image) -> Image.Image:
    a = im.getchannel("A").point(lambda v: 255 if v >= ALPHA_CUT else 0)
    im = im.copy()
    im.putalpha(a)
    return im


def components(im: Image.Image):
    """(ラベル画像, 成分ごとの面積, 成分ごとの (y0,y1,x0,x1))"""
    mask = np.array(im.getchannel("A")) > 0
    labels, n = ndimage.label(mask)
    areas = ndimage.sum(mask, labels, range(1, n + 1))
    slices = ndimage.find_objects(labels)
    return labels, areas, [(s[0].start, s[0].stop, s[1].start, s[1].stop) for s in slices]


def main() -> None:
    poses = {p.stem: binarize(Image.open(p).convert("RGBA")) for p in sorted(SRC.glob("*.png"))}
    comps = {k: components(im) for k, im in poses.items()}
    boxes = []
    for k, (_, areas, bbs) in comps.items():
        main_bbs = [b for a, b in zip(areas, bbs) if a >= areas.max() * MAIN_RATIO and a >= 200]
        boxes.append((min(b[2] for b in main_bbs), min(b[0] for b in main_bbs),
                      max(b[3] for b in main_bbs), max(b[1] for b in main_bbs)))
    box = (
        min(b[0] for b in boxes), min(b[1] for b in boxes),
        max(b[2] for b in boxes), max(b[3] for b in boxes),
    )
    # 本体の外にはみ出すエフェクト断片(小成分)を除去
    for k, im in poses.items():
        labels, areas, bbs = comps[k]
        arr = np.array(im)
        for i, (a, (y0, y1, x0, x1)) in enumerate(zip(areas, bbs), start=1):
            small = a < areas.max() * MAIN_RATIO
            inside = x0 >= box[0] and y0 >= box[1] and x1 <= box[2] and y1 <= box[3]
            if small and not inside:
                arr[labels == i, 3] = 0
        poses[k] = Image.fromarray(arr)
    w, h = box[2] - box[0], box[3] - box[1]
    scale = (SIZE - MARGIN * 2) / max(w, h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    ox, oy = (SIZE - nw) // 2, SIZE - MARGIN - nh  # 下端揃え(足元を固定)

    small = {}
    for name, im in poses.items():
        crop = im.crop(box).convert("RGBa").resize((nw, nh), Image.BOX).convert("RGBA")
        canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        canvas.alpha_composite(crop, (ox, oy))
        small[name] = canvas

    # 共通パレットを全ポーズの不透明画素から作る
    strip = Image.new("RGB", (SIZE * len(small), SIZE), (0, 0, 0))
    for i, im in enumerate(small.values()):
        rgb = Image.new("RGB", (SIZE, SIZE), (0, 0, 0))
        rgb.paste(im.convert("RGB"), mask=im.getchannel("A").point(lambda v: 255 if v >= ALPHA_CUT else 0))
        strip.paste(rgb, (SIZE * i, 0))
    pal_img = strip.quantize(colors=COLORS, method=Image.Quantize.MAXCOVERAGE, dither=Image.Dither.NONE)

    OUT.mkdir(parents=True, exist_ok=True)
    for name, im in small.items():
        a = im.getchannel("A").point(lambda v: 255 if v >= ALPHA_CUT else 0)
        q = im.convert("RGB").quantize(palette=pal_img, dither=Image.Dither.NONE).convert("RGBA")
        q.putalpha(a)
        q.save(OUT / f"{name}.png", optimize=True)
        print(f"{name}: {OUT / (name + '.png')}")
    print(f"crop={box} scale={scale:.4f} out={nw}x{nh}@({ox},{oy})")


if __name__ == "__main__":
    sys.exit(main())
