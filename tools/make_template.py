"""パーツ制作用の輪郭のみの雛型(下絵)を生成する。

入力: 48x48 のフレーム(normal / walk_mid)。出力: art/fox/template/
  outline_<pose>_48.png       輪郭のみ・48x48・透明背景(そのままレイヤーの下敷きにできる)
  outline_<pose>_x10.png      同・10 倍拡大(ドット単位で描き込むとき用)
  outline_<pose>_x10_grid.png 拡大 + 1px グリッド(8px ごとに太線)・接地線・中心線つき(白背景の確認用)
  guide_<pose>_x10.png        パーツ区分の目安(色分け+名称)。輪郭には含まれない参考図
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
FRAMES = ROOT / "app" / "src" / "main" / "assets" / "characters" / "fox" / "frames"
OUT = ROOT / "art" / "fox" / "template"
SIZE = 48
SCALE = 10
OUTLINE = (32, 64, 200, 255)
GROUND_ROW = 44  # 足先が接地する行(この行の下端が地面)

# パーツ区分の目安(x0, y0, x1, y1 は含まない)。設計書 3.2 の 8 パーツに対応。厳密な境界ではなく目安。
GUIDE = {
    "normal": [
        ("head", (12, 0, 43, 29), (255, 120, 120)),
        ("tail", (8, 24, 19, 40), (255, 190, 80)),
        ("torso", (18, 29, 36, 40), (120, 200, 120)),
        ("arm_l", (17, 30, 23, 40), (120, 160, 255)),
        ("arm_r", (30, 30, 36, 40), (120, 160, 255)),
        ("leg_l", (17, 40, 26, 45), (200, 130, 255)),
        ("leg_r", (26, 40, 37, 45), (200, 130, 255)),
    ],
    "walk_mid": [
        ("head", (12, 0, 43, 29), (255, 120, 120)),
        ("tail", (2, 24, 15, 40), (255, 190, 80)),
        ("torso", (10, 29, 32, 39), (120, 200, 120)),
        ("leg_hind", (8, 39, 22, 45), (200, 130, 255)),
        ("leg_front", (22, 39, 36, 45), (200, 130, 255)),
    ],
}


def outline_of(im: Image.Image) -> Image.Image:
    """不透明画素のうち、上下左右のいずれかが透明(または画像外)のものだけを残す。"""
    px = im.convert("RGBA").load()
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                continue
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if not (0 <= nx < w and 0 <= ny < h) or px[nx, ny][3] == 0:
                    out.putpixel((x, y), OUTLINE)
                    break
    return out


def scaled(im: Image.Image) -> Image.Image:
    return im.resize((im.width * SCALE, im.height * SCALE), Image.NEAREST)


def grid_image(outline: Image.Image) -> Image.Image:
    big = Image.new("RGBA", (SIZE * SCALE, SIZE * SCALE), (255, 255, 255, 255))
    d = ImageDraw.Draw(big)
    for i in range(SIZE + 1):
        heavy = i % 8 == 0
        c = (150, 150, 150, 255) if heavy else (225, 225, 225, 255)
        d.line([(i * SCALE, 0), (i * SCALE, SIZE * SCALE)], fill=c)
        d.line([(0, i * SCALE), (SIZE * SCALE, i * SCALE)], fill=c)
    d.line([(SIZE * SCALE // 2, 0), (SIZE * SCALE // 2, SIZE * SCALE)], fill=(80, 160, 255, 255), width=2)  # 中心線
    gy = (GROUND_ROW + 1) * SCALE
    d.line([(0, gy), (SIZE * SCALE, gy)], fill=(255, 60, 60, 255), width=2)  # 接地線
    d.text((4, gy + 2), "ground", fill=(255, 60, 60, 255))
    big.alpha_composite(scaled(outline))
    return big


def guide_image(pose: str, frame: Image.Image, outline: Image.Image) -> Image.Image:
    mask = frame.getchannel("A")
    big = grid_image(outline)
    layer = Image.new("RGBA", big.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for name, (x0, y0, x1, y1), rgb in GUIDE[pose]:
        for y in range(y0, y1):
            for x in range(x0, x1):
                if mask.getpixel((x, y)) > 0:
                    d.rectangle([x * SCALE, y * SCALE, (x + 1) * SCALE - 1, (y + 1) * SCALE - 1], fill=rgb + (90,))
    big.alpha_composite(layer)
    d = ImageDraw.Draw(big)
    for name, (x0, y0, x1, y1), rgb in GUIDE[pose]:
        d.text((x0 * SCALE + 3, y0 * SCALE + 3), name, fill=(0, 0, 0, 255))
    return big


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for pose in GUIDE:
        frame = Image.open(FRAMES / f"{pose}.png").convert("RGBA")
        outline = outline_of(frame)
        outline.save(OUT / f"outline_{pose}_48.png")
        scaled(outline).save(OUT / f"outline_{pose}_x10.png")
        grid_image(outline).save(OUT / f"outline_{pose}_x10_grid.png")
        guide_image(pose, frame, outline).save(OUT / f"guide_{pose}_x10.png")
        print(pose, "->", OUT)


if __name__ == "__main__":
    main()
