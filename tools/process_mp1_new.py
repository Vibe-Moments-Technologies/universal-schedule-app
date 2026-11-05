# -*- coding: utf-8 -*-
"""
Новая сьёмка Малой Пироговской (репорт владельца): 6 этажей (0..5),
файлы «Геометрия»+«Маркеры» в new-map-mp-1/. Собираем в
files/maps/mp-1-new/ как floor_0..floor_5.svg — старые карты
(по умолчанию) не трогаем. Новая версия включается тумблером
в меню отладки (репорт: «старую не убирай, по умолчанию — старая,
новую включать в отладке»).

Формат выгрузки идентичен process_official_maps.py: тот же генератор,
viewBox общий на все этажи, комнаты — g[aria-label]+path, маркеры —
готовый inner-SVG. Пороги размеров комнат оставлены от официального
скрипта.
"""
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = REPO_ROOT / "new-map-mp-1"
TARGET_DIR = REPO_ROOT / "shared" / "src" / "commonMain" / "composeResources" / "files" / "maps" / "mp-1-new"

FLOORS = [0, 1, 2, 3, 4, 5]


def polygon_centroid(pts):
    n = len(pts)
    if n < 3:
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        return sum(xs) / len(xs), sum(ys) / len(ys)

    if pts[0] != pts[-1]:
        pts = pts + [pts[0]]
        n += 1

    area = 0.0
    cx = 0.0
    cy = 0.0
    for i in range(n - 1):
        x0, y0 = pts[i]
        x1, y1 = pts[i + 1]
        cross = (x0 * y1 - x1 * y0)
        area += cross
        cx += (x0 + x1) * cross
        cy += (y0 + y1) * cross
    area *= 0.5
    if abs(area) < 1e-4:
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        return sum(xs) / len(xs), sum(ys) / len(ys)
    cx /= (6.0 * area)
    cy /= (6.0 * area)
    return cx, cy


def clean_room_label(raw):
    name = raw.replace("Помещение ", "").strip()
    if name.startswith("Туалет "):
        suffix = name[7:].strip()
        return f"WC {suffix}"
    elif name == "Туалет":
        return "WC"
    if "," in name:
        parts = [p.strip() for p in name.split(",")]
        if len(parts) > 1 and len(name) > 16:
            return parts[0]
    return name


def extract_inner(svg_str):
    inner = re.sub(r'^\s*<svg[^>]*>', '', svg_str, flags=re.IGNORECASE)
    inner = re.sub(r'</svg>\s*$', '', inner, flags=re.IGNORECASE)
    return inner.strip()


def process_floor(geom_path, markers_path, out_svg_path):
    geom_content = geom_path.read_text(encoding="utf-8").strip()
    markers_content = markers_path.read_text(encoding="utf-8").strip()

    vb_match = re.search(r'viewBox="([^"]+)"', geom_content)
    if not vb_match:
        vb_match = re.search(r'viewBox="([^"]+)"', markers_content)
    viewbox = vb_match.group(1) if vb_match else "0 0 1000 1000"

    geom_inner = extract_inner(geom_content)
    markers_inner = extract_inner(markers_content)

    room_matches = re.finditer(r'<g\b[^>]*aria-label="([^"]+)"[^>]*>\s*<path\b[^>]*\bd="([^"]+)"[^>]*>', geom_inner)
    labels = []
    seen_labels = set()

    for m in room_matches:
        raw_label = m.group(1)
        d = m.group(2)
        label_text = clean_room_label(raw_label)
        if not label_text:
            continue

        coords = re.findall(r'([-+]?\d+(?:\.\d+)?)[,\s]+([-+]?\d+(?:\.\d+)?)', d)
        pts = [(float(x), float(y)) for x, y in coords]
        if len(pts) < 3:
            continue

        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        w = max(xs) - min(xs)
        h = max(ys) - min(ys)

        min_dim = min(w, h)
        # Новая сьёмка в ~2x координатах (viewBox 12k против 5.5k старых):
        # пороги масштабируем вдвое, чтобы фильтр «мелких» комнат работал
        # как в официальных картах.
        if min_dim < 220:
            continue

        cx, cy = polygon_centroid(pts)

        if not (min(xs) <= cx <= max(xs) and min(ys) <= cy <= max(ys)):
            cx = (min(xs) + max(xs)) / 2.0
            cy = (min(ys) + max(ys)) / 2.0

        if min_dim >= 800:
            fs = 76
        elif min_dim >= 500:
            fs = 60
        elif min_dim >= 360:
            fs = 48
        else:
            fs = 40

        key = (round(cx, -1), round(cy, -1), label_text)
        if key in seen_labels:
            continue
        seen_labels.add(key)

        safe_text = label_text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")
        labels.append(
            f'<text x="{cx:.1f}" y="{cy:.1f}" font-size="{fs}" class="room-label" text-anchor="middle" dominant-baseline="central">{safe_text}</text>'
        )

    labels_markup = "\n    ".join(labels)

    svg_out = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}" width="100%" height="100%" preserveAspectRatio="xMidYMid meet">
  <g id="geometry">
    {geom_inner}
  </g>
  <g id="room-labels">
    {labels_markup}
  </g>
  <g id="markers">
    {markers_inner}
  </g>
</svg>"""

    out_svg_path.parent.mkdir(parents=True, exist_ok=True)
    out_svg_path.write_text(svg_out, encoding="utf-8")

    return len(labels)


def main():
    if not SOURCE_DIR.is_dir():
        print(f"Directory not found: {SOURCE_DIR}")
        return
    for floor in FLOORS:
        geom_file = SOURCE_DIR / f"Малая Пироговская 1_Этаж_{floor}_Геометрия.txt"
        markers_file = SOURCE_DIR / f"Малая Пироговская 1_Этаж_{floor}_Маркеры.txt"
        if not geom_file.exists() or not markers_file.exists():
            print(f"Missing files for floor {floor}")
            continue
        out_file = TARGET_DIR / f"floor_{floor}.svg"
        lbl_count = process_floor(geom_file, markers_file, out_file)
        size_kb = out_file.stat().st_size / 1024
        print(f"mp-1-new / floor {floor} -> {out_file.name} ({size_kb:.1f} KB, {lbl_count} labels)")


if __name__ == "__main__":
    main()
