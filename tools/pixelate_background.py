"""写真(AIで生成したイラストなど)を、背景アセット用のドット絵に変換するツール。

設計書10.3.0節の基準解像度(216x384px, 9:16)に合わせて、次の順で処理する。
  1. 目標の縦横比になるよう、中央を基準に切り出す(はみ出す部分をトリミング。
     設計書の「高さ基準で拡大し、幅は中央基準でトリミングする」表示方針と揃える)
  2. なめらかなフィルター(Lanczos)で縮小する
     ※ここでニアレストネイバーを使うと、写真の縮小ではノイズが増えるだけでドット絵らしくならない。
       ニアレストネイバー・整数倍の補間なし拡大は、アプリの「表示時」の仕事(SpritePlayer等)であり、
       このツールが作るのは、その元になる小さい画像そのもの
  3. 色数を減らして(既定32色)、ドット絵らしいフラットな配色にする

使い方:
  1 枚だけ変換:   python tools/pixelate_background.py 入力.png 出力.png
  まとめて変換:   python tools/pixelate_background.py 入力1.png 入力2.png ... -o 出力ディレクトリ
  倍のサイズで:   python tools/pixelate_background.py 入力.png 出力.png --scale 2  (432x768になる)
  減色しない:     python tools/pixelate_background.py 入力.png 出力.png --colors 0
"""
import argparse
from pathlib import Path

from PIL import Image

BASE_WIDTH = 216
BASE_HEIGHT = 384
DEFAULT_COLORS = 32


def crop_to_aspect(im: Image.Image, target_w: int, target_h: int) -> Image.Image:
    """目標の縦横比になるよう、中央基準で切り出す(設計書10.3.0節: 高さ基準で拡大し、幅を中央トリミング)。"""
    w, h = im.size
    target_ratio = target_w / target_h
    ratio = w / h
    if ratio > target_ratio:  # 横に余分がある → 幅を詰める
        new_w = round(h * target_ratio)
        x0 = (w - new_w) // 2
        return im.crop((x0, 0, x0 + new_w, h))
    if ratio < target_ratio:  # 縦に余分がある → 高さを詰める
        new_h = round(w / target_ratio)
        y0 = (h - new_h) // 2
        return im.crop((0, y0, w, y0 + new_h))
    return im


def pixelate(im: Image.Image, width: int, height: int, colors: int) -> Image.Image:
    im = crop_to_aspect(im, width, height)
    im = im.resize((width, height), Image.Resampling.LANCZOS)
    if colors <= 0:
        return im
    has_alpha = im.mode == "RGBA"
    alpha = im.split()[-1] if has_alpha else None
    out = im.convert("RGB").quantize(colors=colors, method=Image.Quantize.MEDIANCUT).convert("RGB")
    if has_alpha:
        out = out.convert("RGBA")
        out.putalpha(alpha)
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("inputs", nargs="+", type=Path, help="変換する画像(複数指定可)")
    parser.add_argument("-o", "--output", type=Path, required=True, help="出力先。入力が1枚ならファイル名、複数ならディレクトリ")
    parser.add_argument("--width", type=int, default=BASE_WIDTH, help=f"出力の幅(既定 {BASE_WIDTH}px)")
    parser.add_argument("--height", type=int, default=BASE_HEIGHT, help=f"出力の高さ(既定 {BASE_HEIGHT}px)")
    parser.add_argument("--scale", type=int, default=1, help="幅・高さに掛ける整数倍(既定1倍。例: 2 なら432x768になる)")
    parser.add_argument("--colors", type=int, default=DEFAULT_COLORS, help=f"減色後の色数(既定{DEFAULT_COLORS}。0で減色しない)")
    args = parser.parse_args()

    width = args.width * args.scale
    height = args.height * args.scale

    if len(args.inputs) == 1 and args.output.suffix:
        targets = [(args.inputs[0], args.output)]
    else:
        args.output.mkdir(parents=True, exist_ok=True)
        targets = [(src, args.output / src.name) for src in args.inputs]

    for src, dst in targets:
        im = Image.open(src)
        out = pixelate(im, width, height, args.colors)
        dst.parent.mkdir(parents=True, exist_ok=True)
        out.save(dst)
        colors_note = "元のまま" if args.colors <= 0 else f"{args.colors}色に減色"
        print(f"{src} -> {dst} ({out.size[0]}x{out.size[1]}, {colors_note})")


if __name__ == "__main__":
    main()
