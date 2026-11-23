"""Converts INEC's "Clasificador Geográfico Estadístico" (CGE) workbook into the location catalog of the backend.

MEJ-01/MEJ-03 (Jira RHE-86). The workbook is a printed-style listing: provinces, then each canton with its
parishes in two columns, some names wrapped onto the next row, and Quito's parishes listed twice (by parish and
again by zone). This script turns it into a tree of provinces, cantons and parishes with their official codes and
official names, exactly as INEC publishes them. The rules are documented in docs/catalogs/ubicaciones.md:

- jurisdictions marked with a single asterisk changed over time (moved to another canton or province, became
  urban, or were suppressed) and are left out; a double asterisk is only a footnote mark and is removed;
- the canton seat (parish code 50) is kept only in cantons without urban parishes; where urban parishes exist,
  50 groups them and choosing it would be ambiguous;
- the "zonas en estudio" (province code 90) are not a province nor a canton and are left out;
- repeated codes keep their first occurrence.

Usage (Python 3.10+, xlrd 2.0.1):
    python tools/catalogs/inec_cge_to_json.py cge2025.xls backend/src/main/resources/catalogs/ec-locations-cge2025.json
"""

import json
import re
import sys

import xlrd

SOURCE = {
    "publisher": "INEC - Dirección de Cartografía Estadística y Operaciones de Campo (DICA)",
    "title": "Clasificador Geográfico Estadístico 2025",
    "version": "CGE 2025",
    "cutoffDate": "2024-12-31",
    "url": "https://aplicaciones2.ecuadorencifras.gob.ec/SIN/descargas/cge2025.xls",
}

SECTION_HEADINGS = ("COMPRENDE", "Y LAS", "CON SUS", "ZONA", "ODENANZA", "ORDENANZA")
SEAT_SUFFIX = re.compile(r",\s*CABECERA CANTONAL.*$")
ZONES_UNDER_STUDY = "90"


def code(value):
    if value in ("", None):
        return None
    if isinstance(value, float):
        return f"{int(value):02d}"
    text = str(value).strip()
    return text.zfill(2) if text.isdigit() else None


def clean(name):
    return re.sub(r"\s+", " ", name).replace("( ", "(").replace(" )", ")").strip()


def changed(name):
    """A single leading asterisk marks a jurisdiction that changed; two asterisks are a footnote mark."""
    return name.startswith("*") and not name.startswith("**")


def read(path):
    sheet = xlrd.open_workbook(path).sheet_by_index(0)
    provinces, cantons, parishes = {}, {}, []
    pending = [None, None]
    for r in range(sheet.nrows):
        row = sheet.row_values(r) + [""] * 10
        province, canton, name = code(row[1]), code(row[2]), clean(str(row[4]))
        upper = name.upper()
        if province and not canton and code(row[3]) is None and upper.startswith(("PROVINCIA", "ZONAS")):
            provinces[province] = name
            pending = [None, None]
            continue
        if province and canton and code(row[3]) is None and "CANT" in upper[:12]:
            cantons[(province, canton)] = name
            pending = [None, None]
            continue
        for column, base in enumerate((1, 5)):
            p, c, q = code(row[base]), code(row[base + 1]), code(row[base + 2])
            text = clean(str(row[base + 3]))
            if p and c and q and text:
                item = {"p": p, "c": c, "q": q, "name": text, "row": r}
                parishes.append(item)
                pending[column] = item
            elif (not (p or c or q) and text and pending[column] is not None and pending[column]["row"] == r - 1
                  and not text.upper().startswith(SECTION_HEADINGS) and not re.match(r"^[A-Z]\.\d", text)):
                pending[column]["name"] = clean(pending[column]["name"] + " " + text)
                pending[column]["row"] = r
    return provinces, cantons, parishes


def build(provinces, cantons, parishes):
    current_cantons = {key: name for key, name in cantons.items()
                       if not changed(name) and key[0] != ZONES_UNDER_STUDY}
    seen, kept = set(), []
    for item in parishes:
        key = (item["p"], item["c"], item["q"])
        if changed(item["name"]) or key in seen or (item["p"], item["c"]) not in current_cantons:
            continue
        seen.add(key)
        kept.append(item)
    with_urban = {(i["p"], i["c"]) for i in kept if int(i["q"]) < 50}

    tree = []
    for p in sorted(provinces):
        if p == ZONES_UNDER_STUDY:
            continue
        province_name = re.sub(r"^PROVINCIA (DEL|DE) ", "", provinces[p])
        entry = {"code": p, "name": province_name, "cantons": []}
        for (cp, cc) in sorted(k for k in current_cantons if k[0] == p):
            canton_name = re.sub(r"^\**CANT[OÓ]N ", "", current_cantons[(cp, cc)])
            canton = {"code": cp + cc, "name": canton_name, "parishes": []}
            for item in sorted((i for i in kept if i["p"] == cp and i["c"] == cc), key=lambda i: i["q"]):
                q = int(item["q"])
                if q == 50:
                    if (cp, cc) in with_urban:
                        continue
                    name, kind = SEAT_SUFFIX.sub("", item["name"]).rstrip(" .,") + " (CABECERA CANTONAL)", "SEAT"
                else:
                    name, kind = item["name"].lstrip("*"), ("URBAN" if q < 50 else "RURAL")
                canton["parishes"].append({"code": cp + cc + item["q"], "name": name, "type": kind})
            entry["cantons"].append(canton)
        tree.append(entry)
    return tree


def main():
    source, target = sys.argv[1], sys.argv[2]
    tree = build(*read(source))
    counts = {
        "provinces": len(tree),
        "cantons": sum(len(p["cantons"]) for p in tree),
        "parishes": sum(len(c["parishes"]) for p in tree for c in p["cantons"]),
    }
    for kind in ("SEAT", "URBAN", "RURAL"):
        counts[kind.lower()] = sum(1 for p in tree for c in p["cantons"] for q in c["parishes"] if q["type"] == kind)
    with open(target, "w", encoding="utf-8", newline="\n") as out:
        json.dump({"source": SOURCE, "counts": counts, "provinces": tree}, out, ensure_ascii=False, indent=1)
        out.write("\n")
    print(json.dumps(counts))


if __name__ == "__main__":
    main()
