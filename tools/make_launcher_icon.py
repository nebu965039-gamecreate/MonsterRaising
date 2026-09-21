"""normal フレームから仮のランチャーアイコン(192x192)を生成する暫定ツール(Phase 1)。"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main" / "assets" / "characters" / "fox" / "frames" / "normal.png"
OUT = ROOT / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"

fox = Image.open(SRC).convert("RGBA").resize((192, 192), Image.NEAREST)
icon = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
icon.alpha_composite(fox)
OUT.parent.mkdir(parents=True, exist_ok=True)
icon.save(OUT, optimize=True)
print(OUT)
