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


# walk フレームの脚・頭の範囲(x0, y0, x1, y1。x1・y1 は含まない)
LEG_TOP = 39                    # 脚が始まる行。この行以降を脚として扱い、足先は全脚とも行 44 で接地
NEAR_HIND = (8, LEG_TOP, 18, 45)    # 手前の後ろ足
NEAR_FRONT = (22, LEG_TOP, 30, 45)  # 手前の前足
FAR_OFFSET = 5                  # 奥側の脚は手前の脚を右へこの分ずらして描く
FAR_SHADE = 0.78                # 奥側の脚は少し暗くする
STRIDE = 2                      # 脚の前後の振り幅(足先。付け根ほど小さくなる)
HEAD = (12, 0, 43, 27)          # 頭(首元のスカーフの手前まで)


def _leg_sprite(im: Image.Image, box) -> dict[tuple[int, int], tuple]:
    x0, y0, x1, y1 = box
    return {(x, y): im.getpixel((x, y)) for y in range(y0, y1) for x in range(x0, x1) if im.getpixel((x, y))[3] > 0}


def _shade(p, k: float):
    return (int(p[0] * k), int(p[1] * k), int(p[2] * k), p[3])


def _draw_leg(im: Image.Image, sprite, dx_offset: int, dx_stride: int, shade: float = 1.0) -> None:
    """脚を描く。dx_stride は前後の振り(+が前=右)で、付け根(上端)ほど小さく足先ほど大きい。"""
    rows = 45 - LEG_TOP
    for (x, y), p in sprite.items():
        dx = dx_offset + round(dx_stride * (y - LEG_TOP + 1) / rows)
        im.putpixel((x + dx, y), _shade(p, shade) if shade != 1.0 else p)


def _move_pixels(im: Image.Image, box, dx: int, dy: int) -> None:
    """box 内の不透明画素を (dx, dy) だけ動かす。元の場所は透明にする。"""
    x0, y0, x1, y1 = box
    src = im.copy()
    pixels = [(x, y, src.getpixel((x, y))) for y in range(y0, y1) for x in range(x0, x1) if src.getpixel((x, y))[3] > 0]
    for x, y, _ in pixels:
        im.putpixel((x, y), (0, 0, 0, 0))
    for x, y, p in pixels:
        im.putpixel((x + dx, y + dy), p)


def walk_frames(walk: Image.Image) -> tuple[Image.Image, Image.Image, Image.Image]:
    """歩行の 3 コマ(walk_mid: 4 本とも接地の基本 / walk_a / walk_b)。

    元の walk ポーズは手前の脚 2 本と、地面に届かない「伸ばした手」だったため、
    脚を切り離して奥側の脚(前・後ろ)を描き足し、4 本すべてを足先の高さ(行 44)にそろえる。
    a: 手前の前足と奥の後ろ足が前、手前の後ろ足と奥の前足が後ろ / b: その逆(斜め歩き)。
    頭は a・b で 1px 下げる(walk_mid が元の高さ)。
    """
    near_hind = _leg_sprite(walk, NEAR_HIND)
    near_front = _leg_sprite(walk, NEAR_FRONT)

    body = walk.copy()
    body.paste((0, 0, 0, 0), (0, LEG_TOP, body.width, body.height))  # 脚と伸ばした手を除去

    def compose(front_dir: int, head_down: bool) -> Image.Image:
        im = body.copy()
        # 奥側 → 手前の順に描く(手前の脚が奥の脚を隠す)
        _draw_leg(im, near_hind, FAR_OFFSET, STRIDE * front_dir, FAR_SHADE)   # 奥の後ろ足: 手前の前足と同位相
        _draw_leg(im, near_front, FAR_OFFSET, -STRIDE * front_dir, FAR_SHADE)  # 奥の前足: 手前の後ろ足と同位相
        _draw_leg(im, near_hind, 0, -STRIDE * front_dir)
        _draw_leg(im, near_front, 0, STRIDE * front_dir)
        if head_down:
            _move_pixels(im, HEAD, 0, 1)
        return im

    return compose(0, False), compose(1, True), compose(-1, True)


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

    walk_mid, walk_a, walk_b = walk_frames(Image.open(FRAMES / "walk.png").convert("RGBA"))
    walk_mid.save(FRAMES / "walk_mid.png", optimize=True)
    walk_a.save(FRAMES / "walk_a.png", optimize=True)
    walk_b.save(FRAMES / "walk_b.png", optimize=True)


if __name__ == "__main__":
    main()
