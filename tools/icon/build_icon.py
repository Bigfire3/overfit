#!/usr/bin/env python3
"""Erzeugt das Overfit-App-Icon („of“ aus Inter-Glyphen als Pfade) und alle Plattform-Dateien aus EINER Quelle.

    python3 tools/icon/build_icon.py                 # Variante B5, dunkel
    python3 tools/icon/build_icon.py --variant B2    # andere Variante
    python3 tools/icon/build_icon.py --variant B1 --theme light
    python3 tools/icon/build_icon.py --weight 800 --size 0.5 --tracking -0.03 --dot   # frei

Abhängigkeiten (nur für dieses Skript, nicht für den App-Build): pip install fonttools cairosvg pillow
Alle Ausgaben werden committet; die Schrift selbst steckt nicht im Icon, nur Pfade.
"""
import argparse
import math
import os
from dataclasses import dataclass, replace

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONTS = os.path.join(os.path.dirname(__file__), "fonts")
WEIGHTS = {600: "SemiBold", 700: "Bold", 800: "ExtraBold", 900: "Black"}
N = 1024  # Kantenlänge der Master-SVG


@dataclass(frozen=True)
class Variant:
    weight: int = 700
    size: float = 0.40       # Schriftgröße als Anteil der Kante
    tracking: float = -0.02  # Laufweite (Anteil der Kante, zwischen o und f)
    baseline: float = 0.63   # Grundlinie als Anteil der Höhe
    dx: float = 0.0          # Versatz der Schrift (Anteil der Kante, negativ = links)
    dot: bool = False        # runder Punkt rechts unten auf Grundlinienhöhe
    circle: float = 0.0      # >0: Kreis mit diesem Radius (Anteil der Kante) in Schriftfarbe, Text in Grundfarbe
    anchor: str = "center"   # "center" oder "left" (linke Tintenkante bei `left`)
    left: float = 0.0        # nur anchor="left": linke Tintenkante als Anteil der Kante


VARIANTS = {
    "B1": Variant(),
    "B2": Variant(weight=900, size=0.46, tracking=-0.035),
    "B3": Variant(weight=600, size=0.36, tracking=-0.01),
    "B4": Variant(weight=800, size=0.42, tracking=-0.025, dx=-0.03, dot=True),
    "B5": Variant(weight=800, size=0.58, tracking=-0.04, anchor="left", left=0.19, baseline=0.77),  # unten links
    "B6": Variant(weight=800, size=0.28, tracking=-0.015, circle=0.30, baseline=0.58),
}
DARK = ("#000000", "#FFFFFF")   # (Grund, Schrift)
LIGHT = ("#FFFFFF", "#000000")


def f(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def layout(v: Variant):
    """Liefert (Pfad-d des Schriftzugs in Master-Koordinaten, Punkt (cx, cy, r) oder None)."""
    font = TTFont(os.path.join(FONTS, f"Inter-{WEIGHTS[v.weight]}.otf"))
    gs, cmap, hmtx = font.getGlyphSet(), font.getBestCmap(), font["hmtx"]
    upem = font["head"].unitsPerEm
    k = v.size * N / upem
    names = [cmap[ord("o")], cmap[ord("f")]]
    # Positionen in Glyph-Einheiten, Laufweite aus der Kante
    xs, x = [], 0.0
    for n in names:
        xs.append(x)
        x += hmtx[n][0] + v.tracking * N / k
    # Tinten-Bounding-Box für die Zentrierung
    x0, x1 = math.inf, -math.inf
    for n, ox in zip(names, xs):
        bp = BoundsPen(gs)
        gs[n].draw(bp)
        x0, x1 = min(x0, ox + bp.bounds[0]), max(x1, ox + bp.bounds[2])
    if v.anchor == "center":
        left = N / 2 - (x0 + x1) / 2 * k + v.dx * N
    else:  # linke Tintenkante bei v.left
        left = v.left * N - x0 * k
    base = v.baseline * N
    pen = SVGPathPen(gs, ntos=f)
    for n, ox in zip(names, xs):
        gs[n].draw(TransformPen(pen, (k, 0, 0, -k, left + ox * k, base)))
    dot = None
    if v.dot:
        r = 0.045 * N
        dot = (left + x1 * k + 0.035 * N + r, base - r, r)
    return pen.getCommands(), dot


def circle_d(cx, cy, r):
    return (f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z")


def master_svg(v, bg, fg, rx=0.0):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {N} {N}" width="{N}" height="{N}">'
            + inner_svg(v, bg, fg, rx, "c") + "</svg>\n")


def inner_svg(v, bg, fg, rx, cid):
    d, dot = layout(v)
    parts = []
    clip = f' clip-path="url(#{cid})"'
    parts.append(f'<defs><clipPath id="{cid}"><rect width="{N}" height="{N}" rx="{f(rx * N)}"/></clipPath></defs>')
    parts.append(f'<g{clip}><rect width="{N}" height="{N}" fill="{bg}"/>')
    if v.circle:
        parts.append(f'<path d="{circle_d(N / 2, N / 2, v.circle * N)}" fill="{fg}"/>')
        parts.append(f'<path d="{d}" fill="{bg}"/>')
    else:
        parts.append(f'<path d="{d}" fill="{fg}"/>')
        if dot:
            parts.append(f'<circle cx="{f(dot[0])}" cy="{f(dot[1])}" r="{f(dot[2])}" fill="{fg}"/>')
    parts.append("</g>")
    return "".join(parts)


MASKS = [("Kreis", 0.5), ("Squircle", 0.34), ("Abgerundet", 0.22), ("iOS", 0.225), ("Rechteck", 0.0)]


def previews(v):
    """Masken-Vorschauen: Android-Masken, iOS-Radius, Monochrome und helle Fassung (design/icon/previews/)."""
    cell, pad = 256, 24
    rows = [("dark", DARK, False), ("light", LIGHT, False), ("mono", ("#C9D6FF", "#1A1F36"), True)]
    w = pad + len(MASKS) * (cell + pad)
    for name, (bg, fg), _ in rows:
        body = [f'<rect width="{w}" height="{cell + 2 * pad}" fill="#808080"/>']
        for i, (_, rx) in enumerate(MASKS):
            x = pad + i * (cell + pad)
            body.append(f'<g transform="translate({x},{pad}) scale({cell / N})">' + inner_svg(v, bg, fg, rx, f"m{i}") + "</g>")
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{cell + 2 * pad}">' + "".join(body) + "</svg>"
        png(svg, w, f"design/icon/previews/masks-{name}.png", height=cell + 2 * pad)


def write(path, data, binary=False):
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb" if binary else "w") as fh:
        fh.write(data)


def png(svg, size, path, alpha=False, height=None):
    import cairosvg
    from PIL import Image
    import io
    raw = cairosvg.svg2png(bytestring=svg.encode(), output_width=size, output_height=height or size)
    im = Image.open(io.BytesIO(raw))
    if not alpha:
        im = im.convert("RGB")  # ohne Transparenz
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    write(path, buf.getvalue(), binary=True)
    return im


# ---- Android -----------------------------------------------------------------------------------
def android_vector(v, d, dot, fg, bg, mono=False):
    """Vektor 108dp: Master (1024) wird auf die sichtbare Fläche 72dp in der Mitte abgebildet (Sicherheitszone 66dp)."""
    s = 72 / N
    fill = "#FF000000" if mono else "#FF" + fg[1:]
    cut = "#FF" + bg[1:]
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           "<!-- Erzeugt von tools/icon/build_icon.py – nicht von Hand ändern. -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    out.append(f'    <group android:translateX="18" android:translateY="18" android:scaleX="{s:.6f}" android:scaleY="{s:.6f}">')
    if v.anchor != "center":
        out.append(f'        <clip-path android:pathData="M0,0H{N}V{N}H0z" />')
    if v.circle:
        cd = circle_d(N / 2, N / 2, v.circle * N)
        if mono:  # Kreis mit ausgeschnittenem Schriftzug (even-odd)
            out.append(f'        <path android:fillColor="{fill}" android:fillType="evenOdd" android:pathData="{cd}{d}" />')
        else:
            out.append(f'        <path android:fillColor="{fill}" android:pathData="{cd}" />')
            out.append(f'        <path android:fillColor="{cut}" android:pathData="{d}" />')
    else:
        out.append(f'        <path android:fillColor="{fill}" android:pathData="{d}" />')
        if dot:
            out.append(f'        <path android:fillColor="{fill}" android:pathData="{circle_d(*dot)}" />')
    out += ["    </group>", "</vector>", ""]
    return "\n".join(out)


def build_android(v, bg, fg):
    d, dot = layout(v)
    res = "app/src/main/res"
    write(f"{res}/drawable/ic_launcher_foreground.xml", android_vector(v, d, dot, fg, bg))
    write(f"{res}/drawable/ic_launcher_monochrome.xml", android_vector(v, d, dot, fg, bg, mono=True))
    # Splash (Android 12+): feste weiße Schrift (B1-Schnitt) auf Schwarz, unabhängig von der Icon-Fassung
    sv = replace(VARIANTS["B1"])
    write(f"{res}/drawable/ic_splash.xml", android_vector(sv, layout(sv)[0], None, "#FFFFFF", "#000000"))
    write(f"{res}/values/launcher_colors.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<!-- Erzeugt von tools/icon/build_icon.py -->\n'
          f'<resources>\n    <color name="launcher_background">#FF{bg[1:]}</color>\n</resources>\n')
    write(f"{res}/mipmap-anydpi-v26/ic_launcher.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<!-- minSdk 26: adaptive Icons genügen, keine Legacy-PNGs nötig. -->\n'
          '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <background android:drawable="@color/launcher_background" />\n'
          '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
          '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')


# ---- Hauptprogramm ----------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variant", default="B5", choices=sorted(VARIANTS))
    ap.add_argument("--theme", default="dark", choices=["dark", "light"])
    ap.add_argument("--weight", type=int, choices=sorted(WEIGHTS))
    ap.add_argument("--size", type=float, help="Schriftgröße als Anteil der Kante, z. B. 0.40")
    ap.add_argument("--tracking", type=float, help="Laufweite als Anteil der Kante, z. B. -0.02")
    ap.add_argument("--dot", action="store_true", help="runder Punkt rechts unten")
    a = ap.parse_args()

    v = VARIANTS[a.variant]
    ov = {k: val for k, val in (("weight", a.weight), ("size", a.size), ("tracking", a.tracking)) if val is not None}
    if a.dot:
        ov["dot"] = True
    v = replace(v, **ov)
    bg, fg = DARK if a.theme == "dark" else LIGHT

    svg = master_svg(v, bg, fg)
    write("design/icon/overfit-icon.svg", svg)
    build_android(v, bg, fg)
    previews(v)

    # Play Store 512 × 512 (Quadrat, ohne eigene Maske)
    png(svg, 512, "docs/store/icon-512.png")

    # iOS: Standard (gewählte Fassung), Dunkel, Getönt (Graustufen, Apple färbt ein)
    ios = "iosApp/Overfit/Assets.xcassets"
    png(svg, 1024, f"{ios}/AppIcon.appiconset/AppIcon-1024.png")
    dark_svg = master_svg(v, *DARK)
    png(dark_svg, 1024, f"{ios}/AppIcon.appiconset/AppIcon-Dark-1024.png")
    tinted = png(dark_svg, 1024, f"{ios}/AppIcon.appiconset/AppIcon-Tinted-1024.png").convert("L")
    import io
    buf = io.BytesIO(); tinted.save(buf, "PNG", optimize=True)
    write(f"{ios}/AppIcon.appiconset/AppIcon-Tinted-1024.png", buf.getvalue(), binary=True)
    write(f"{ios}/Contents.json", '{ "info" : { "author" : "xcode", "version" : 1 } }\n')
    write(f"{ios}/AppIcon.appiconset/Contents.json", """{
  "images" : [
    { "filename" : "AppIcon-1024.png", "idiom" : "universal", "platform" : "ios", "size" : "1024x1024" },
    { "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "AppIcon-Dark-1024.png", "idiom" : "universal", "platform" : "ios", "size" : "1024x1024" },
    { "appearances" : [ { "appearance" : "luminosity", "value" : "tinted" } ],
      "filename" : "AppIcon-Tinted-1024.png", "idiom" : "universal", "platform" : "ios", "size" : "1024x1024" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
""")
    # iOS Launch Screen: schwarz mit kleinem weißen „of“ (immer Variante B1-Schnitt, transparenter Grund)
    logo = replace(VARIANTS["B1"], size=0.40)
    ld, _ = layout(logo)
    # Logo-Fläche 120 × 120 pt: der Master-Ausschnitt wird 1:1 skaliert
    logo_svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {N} {N}"><path d="{ld}" fill="#FFFFFF"/></svg>')
    for scale in (1, 2, 3):
        png(logo_svg, 120 * scale, f"{ios}/LaunchLogo.imageset/LaunchLogo@{scale}x.png", alpha=True)
    write(f"{ios}/LaunchLogo.imageset/Contents.json", """{
  "images" : [
    { "filename" : "LaunchLogo@1x.png", "idiom" : "universal", "scale" : "1x" },
    { "filename" : "LaunchLogo@2x.png", "idiom" : "universal", "scale" : "2x" },
    { "filename" : "LaunchLogo@3x.png", "idiom" : "universal", "scale" : "3x" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
""")
    write(f"{ios}/LaunchBackground.colorset/Contents.json", """{
  "colors" : [
    { "color" : { "color-space" : "srgb", "components" : { "alpha" : "1.000", "blue" : "0.000", "green" : "0.000", "red" : "0.000" } }, "idiom" : "universal" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
""")

    # Web: Favicon (SVG mit abgerundetem Quadrat), ICO, Touch-Icon (opak, iOS rundet selbst), PWA-Größen
    write("web/favicon.svg", master_svg(v, bg, fg, rx=0.22))
    ico = png(master_svg(v, bg, fg, rx=0.22), 256, "web/.favicon-tmp.png", alpha=True)
    os.remove(os.path.join(ROOT, "web/.favicon-tmp.png"))
    ico.save(os.path.join(ROOT, "web/favicon.ico"), sizes=[(16, 16), (32, 32), (48, 48)])
    png(svg, 180, "web/apple-touch-icon.png")
    png(svg, 192, "web/icon-192.png")
    png(svg, 512, "web/icon-512.png")
    print(f"Icon {a.variant} ({a.theme}) erzeugt: {v}")


if __name__ == "__main__":
    main()
