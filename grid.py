import json, io
chunks = json.load(io.open('chunks.json', encoding='utf-8'))
days = [('MON', 694.7, 589.8), ('TUE', 589.8, 472.0), ('WED', 472.0, 348.9),
        ('THU', 348.9, 224.3), ('FRI', 224.3, 106.6), ('SAT', 106.6, 0)]
groups = [('PI26ob1-1', 150, 295), ('IO26ob1-1', 295, 405), ('OrgConf26ob1-1', 405, 596)]
out = []
for gname, gx0, gx1 in groups:
    out.append('=' * 20 + ' ' + gname)
    for dname, dy1, dy0 in days:
        cell = [c for c in chunks if gx0 <= c['x'] < gx1 and dy0 < c['y'] <= dy1 + 8]
        cell.sort(key=lambda c: (-c['y'], c['x']))
        if cell:
            out.append('--- ' + dname)
            for c in cell:
                out.append(f"  y={c['y']:6.1f} x={c['x']:6.1f} {c['text']}")
io.open('cells.txt', 'w', encoding='utf-8').write('\n'.join(out))
print('done', len(out))
