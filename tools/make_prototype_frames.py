"""normal フレームから、未制作フレームの仮絵(プロトタイプ)を生成する暫定ツール(Phase 1)。

  eat_1 / eat_2 : 食事(口元に餌を持たせる。eat_2 は齧った状態)
  dirty         : 清潔度 0 用(煤+アホ毛、設計書 4.6 / 10.2.2)
本番ドット絵に差し替えるまでの仮素材。make_demo_frames.py の後に実行する。
"""
from pathlib import Path

from PIL import Image

FRAMES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "characters" / "fox" / "frames"

OUTLINE = (74, 40, 24, 255)
APPLE = (214, 48, 48, 255)
APPLE_HI = (255, 140, 120, 255)
LEAF = (72, 160, 64, 255)
FLESH = (250, 236, 200, 255)
SOOT = (58, 50, 48, 255)
HAIR = (74, 40, 24, 255)


def put(im: Image.Image, pts, color):
    for x, y in pts:
        im.putpixel((x, y), color)


def apple(im: Image.Image, ox: int, oy: int, bitten: bool):
    rows = [".rr.", "rRrr", "rrrr", "rrrr", ".rr."]
    for dy, row in enumerate(rows):
        for dx, c in enumerate(row):
            if c == ".":
                continue
            if bitten and dx >= 2 and dy >= 1:
                im.putpixel((ox + dx, oy + dy), FLESH if dx == 2 else (0, 0, 0, 0))
                continue
            im.putpixel((ox + dx, oy + dy), APPLE_HI if c == "R" else APPLE)
    put(im, [(ox + 1, oy - 1)], LEAF)
    put(im, [(ox + 2, oy - 2)], LEAF)


def main() -> None:
    normal = Image.open(FRAMES / "normal.png").convert("RGBA")

    e1 = normal.copy()
    apple(e1, 34, 24, bitten=False)
    e1.save(FRAMES / "eat_1.png", optimize=True)

    e2 = normal.copy()
    apple(e2, 34, 24, bitten=True)
    e2.save(FRAMES / "eat_2.png", optimize=True)

    d = normal.copy()
    # 煤(頬・胴体の汚れ)
    put(d, [(22, 24), (23, 24), (23, 25), (30, 21), (31, 21), (31, 22), (20, 36), (21, 36), (21, 37), (28, 39), (29, 39)], SOOT)
    # アホ毛(耳の間の頭頂に数本)
    put(d, [(24, 8), (24, 7), (25, 6), (26, 6)], HAIR)
    put(d, [(27, 9), (28, 8), (28, 7), (27, 6)], HAIR)
    put(d, [(21, 9), (20, 8), (19, 8)], HAIR)
    d.save(FRAMES / "dirty.png", optimize=True)


if __name__ == "__main__":
    main()
