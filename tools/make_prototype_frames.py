"""normal フレームから、未制作フレームの仮絵(プロトタイプ)を生成する暫定ツール(Phase 1)。

  eat_1 / eat_2 : 食事(口元に餌を持たせる。eat_2 は齧った状態)
  dirty         : 清潔度 0 用(煤+アホ毛、設計書 4.6 / 10.2.2)
  idle_up       : idle の揺れ用。頭・胴体・尻尾を 1px 上げ、足元は固定した差分フレーム
本番ドット絵に差し替えるまでの仮素材。make_demo_frames.py の後に実行する。
"""
from pathlib import Path

from PIL import Image

FRAMES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "characters" / "fox" / "frames"

# normal フレームで足(ブーツ)が始まる行の直前。この行以上(上側)を 1px 上げ、これより下は固定する。
IDLE_BODY_BOTTOM = 39


def idle_up(normal: Image.Image) -> Image.Image:
    out = normal.copy()
    w, _ = normal.size
    body = normal.crop((0, 1, w, IDLE_BODY_BOTTOM + 1))  # 元の 1..39 行目
    # 頭・胴体を 1px 上へ。境界の行は元の行を複製して隙間を埋める(1px 伸びる)
    out.paste((0, 0, 0, 0), (0, 0, w, IDLE_BODY_BOTTOM + 1))
    out.paste(body, (0, 0))
    out.paste(normal.crop((0, IDLE_BODY_BOTTOM, w, IDLE_BODY_BOTTOM + 1)), (0, IDLE_BODY_BOTTOM))
    return out


# walk フレームの手足・頭の範囲(x0, y0, x1, y1。x1・y1 は含まない)
HIND_LEG = (8, 39, 18, 45)      # 後ろ足(付け根から足先まで)
FRONT_LEG = (22, 39, 30, 45)    # 手前の前足
HAND = (30, 37, 35, 42)         # 前へ伸ばした手
HEAD = (12, 0, 43, 27)          # 頭(首元のスカーフの手前まで)


def _shear_pixels(im: Image.Image, box, dx_total: int) -> None:
    """box 内の脚を前後(左右)に振る。付け根(上端)ほど小さく、足先(下端)ほど大きく動かす。
    向き(dx_total の符号)は + が右(前)、- が左(後ろ)。動かして空いた場所は隣の画素で埋めて脚を繋げる。"""
    x0, y0, x1, y1 = box
    src = im.copy()
    rows = y1 - y0
    for y in range(y0, y1):
        dx = round(dx_total * (y - y0 + 1) / rows)
        if dx == 0:
            continue
        for x in range(x0, x1):
            im.putpixel((x, y), (0, 0, 0, 0))
        for x in range(x0, x1):
            p = src.getpixel((x, y))
            if p[3] > 0:
                im.putpixel((x + dx, y), p)


def _move_pixels(im: Image.Image, box, dx: int, dy: int, refill: bool = False) -> None:
    """box 内の不透明画素を (dx, dy) だけ動かす。元の場所は透明にする。
    refill=True なら、空いた場所を動かす方向の反対隣の画素で埋める(胴体との継ぎ目用)。"""
    x0, y0, x1, y1 = box
    src = im.copy()
    pixels = [(x, y, src.getpixel((x, y))) for y in range(y0, y1) for x in range(x0, x1) if src.getpixel((x, y))[3] > 0]
    for x, y, _ in pixels:
        im.putpixel((x, y), (0, 0, 0, 0))
    for x, y, p in pixels:
        im.putpixel((x + dx, y + dy), p)
    if refill:
        sx, sy = (dx > 0) - (dx < 0), (dy > 0) - (dy < 0)
        for x, y, _ in pixels:
            if im.getpixel((x, y))[3] == 0:
                q = src.getpixel((x - sx, y - sy))
                if q[3] > 0:
                    im.putpixel((x, y), q)


def walk_frames(walk: Image.Image) -> tuple[Image.Image, Image.Image]:
    """歩行の 2 コマ。手足を前後に、左右逆位相で振る。頭は 1px 下げる(間に挟む walk が元の高さ)。
    a: 前足が前・後ろ足が後ろ・手は後ろ / b: 前足が後ろ・後ろ足が前・手は前
    """
    a = walk.copy()
    _shear_pixels(a, HIND_LEG, -2)
    _shear_pixels(a, FRONT_LEG, 2)
    _move_pixels(a, HAND, -1, 0)
    _move_pixels(a, HEAD, 0, 1)
    b = walk.copy()
    _shear_pixels(b, HIND_LEG, 2)
    _shear_pixels(b, FRONT_LEG, -2)
    _move_pixels(b, HAND, 1, 0, refill=True)
    _move_pixels(b, HEAD, 0, 1)
    return a, b


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

    idle_up(normal).save(FRAMES / "idle_up.png", optimize=True)

    walk_a, walk_b = walk_frames(Image.open(FRAMES / "walk.png").convert("RGBA"))
    walk_a.save(FRAMES / "walk_a.png", optimize=True)
    walk_b.save(FRAMES / "walk_b.png", optimize=True)


if __name__ == "__main__":
    main()
