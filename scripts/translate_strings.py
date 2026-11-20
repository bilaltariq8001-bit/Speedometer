#!/usr/bin/env python3
"""
Machine-translates app/src/main/res/values/strings.xml into every app language that has no values-XX folder yet.

Usage:  python3 scripts/translate_strings.py            # only languages whose folder is missing
        python3 scripts/translate_strings.py --force    # redo every language in TARGETS
        python3 scripts/translate_strings.py tw yo      # only these app tags
        python3 scripts/translate_strings.py --fill-missing   # add new English strings to every translated folder

Placeholders (%1$s, %d, %%, \n) are protected and checked; a string whose translation loses one keeps its English text.
"""
import re, sys, json, time, html, urllib.request, urllib.parse, concurrent.futures as cf
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SRC = RES / "values/strings.xml"

# app tag (AppLanguage.tags) -> (translator code, [resource folders])
TARGETS = {
    "zh-TW": ("zh-TW", ["values-zh-rTW"]),
    "ro": ("ro", ["values-ro"]), "cs": ("cs", ["values-cs"]), "hu": ("hu", ["values-hu"]),
    "sv": ("sv", ["values-sv"]), "da": ("da", ["values-da"]), "fi": ("fi", ["values-fi"]),
    "no": ("no", ["values-no", "values-nb"]),
    "he": ("iw", ["values-iw", "values-he"]),
    "fil": ("tl", ["values-b+fil", "values-tl"]),
    "yi": ("yi", ["values-yi", "values-ji"]),
    "mni-Mtei": ("mni-Mtei", ["values-b+mni+Mtei", "values-b+mni"]),
    "tw": ("ak", ["values-tw", "values-ak"]),
}
for t in ("af sq am hy az eu be bs bg my ca hr et gl ka ha is ga kn kk km ku ky lo lv lt mk ml mt mn ne ps sr si "
          "sk sl so su tg tt ti tk uz cy xh yo zu as ay co eo ee fy gn ig rw la lb mg mi or om qu sm sa gd st sn sd ts").split():
    TARGETS[t] = (t, [f"values-{t}"])
for t in "bho ceb haw hmn ilo gom kri mai lus nso".split():
    TARGETS[t] = (t, [f"values-b+{t}"])

URL = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl="
PH = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc%]|\\n|…")
END_PUNCT = tuple(".!?:;。！？।۔။።෴")


def load():
    """Ordered list of ('string', name, text) and ('plurals', name, [(qty, text)])."""
    tree = ET.parse(SRC)
    out = []
    for el in tree.getroot():
        if el.get("translatable") == "false":
            continue
        if el.tag == "string":
            out.append(("string", el.get("name"), el.text or ""))
        elif el.tag == "plurals":
            out.append(("plurals", el.get("name"), [(i.get("quantity"), i.text or "") for i in el]))
    return out


def unescape(s):  # Android resource escapes -> plain text
    return s.replace("\\'", "'").replace('\\"', '"').replace("\\@", "@").replace("\\?", "?")


def escape(s):  # plain text -> Android resource text (\n tokens are already restored as \n)
    s = s.replace("\\", "\\\\").replace("\\\\n", "\\n")
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"')
    if s[:1] in ("@", "?"):
        s = "\\" + s
    return s


def protect(s):
    found = []
    def sub(m):
        found.append(m.group(0))
        return f"[{len(found) - 1}]"
    return PH.sub(sub, s), found


def restore(s, found):
    for i, tok in enumerate(found):
        pat = re.compile(r"\[\s*" + str(i) + r"\s*\]")
        if not pat.search(s):
            return None
        s = pat.sub(lambda _: tok, s, count=1)
    if re.search(r"\[\s*\d+\s*\]", s):
        return None
    return s


def call(code, texts, tries=6):
    body = urllib.parse.urlencode([("q", t) for t in texts]).encode()
    for attempt in range(tries):
        try:
            req = urllib.request.Request(URL + code, data=body, headers={"User-Agent": "Mozilla/5.0",
                                         "Content-Type": "application/x-www-form-urlencoded;charset=utf-8"})
            with urllib.request.urlopen(req, timeout=40) as r:
                data = json.loads(r.read().decode())
            res = [d if isinstance(d, str) else d[0] for d in data]
            if len(res) == len(texts):
                return [html.unescape(x) for x in res]
        except Exception as e:
            err = e
        time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"{code}: translation failed")


def fix_end(src, out):
    out = out.strip()
    if not src.rstrip().endswith(END_PUNCT) and out.endswith(END_PUNCT) and not src.rstrip().endswith("…"):
        out = out.rstrip("".join(END_PUNCT)).rstrip()
    return out or src


def translate_lang(tag):
    code, folders = TARGETS[tag]
    items = load()
    flat = []  # (key, english plain)
    for kind, name, val in items:
        if kind == "string":
            flat.append(((name, None), unescape(val)))
        else:
            for q, t in val:
                flat.append(((name, q), unescape(t)))
    prot = [protect(t) for _, t in flat]
    results = [None] * len(flat)
    B = 40
    for i in range(0, len(flat), B):
        chunk = [p[0] for p in prot[i:i + B]]
        outs = call(code, chunk)
        for j, o in enumerate(outs):
            results[i + j] = o
    fallback = 0
    final = {}
    for idx, ((key, eng), (_, found)) in enumerate(zip(flat, prot)):
        r = restore(results[idx], found)
        if r is None:  # retry alone once
            try:
                r = restore(call(code, [prot[idx][0]])[0], found)
            except Exception:
                r = None
        if r is None:
            r, fallback = eng, fallback + 1
        final[key] = fix_end(eng, r)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for kind, name, val in items:
        if kind == "string":
            lines.append(f'    <string name="{name}">{escape(final[(name, None)])}</string>')
        else:
            lines.append(f'    <plurals name="{name}">')
            for q, _ in val:
                lines.append(f'        <item quantity="{q}">{escape(final[(name, q)])}</item>')
            lines.append("    </plurals>")
    lines.append("</resources>\n")
    xml = "\n".join(lines)
    ET.fromstring(xml.encode())  # must parse
    for f in folders:
        d = RES / f
        d.mkdir(exist_ok=True)
        (d / "strings.xml").write_text(xml, encoding="utf-8")
    return tag, len(flat), fallback


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    force = "--force" in sys.argv
    tags = args or [t for t, (_, f) in TARGETS.items() if force or not (RES / f[0] / "strings.xml").exists()]
    with cf.ThreadPoolExecutor(6) as ex:
        for fut in cf.as_completed([ex.submit(translate_lang, t) for t in tags]):
            try:
                tag, n, fb = fut.result()
                print(f"{tag}: {n} strings, {fb} kept English", flush=True)
            except Exception as e:
                print("FAILED", e, flush=True)


if __name__ == "__main__" and "--fill-missing" not in sys.argv:
    main()


def fill_missing():
    """Adds strings that are in values/ but missing from any translated folder (hand-translated ones included)."""
    items = {name: val for kind, name, val in load() if kind == "string"}
    for d in sorted(RES.glob("values-*/strings.xml")):
        folder = d.parent.name[len("values-"):]
        have = {e.get("name") for e in ET.parse(d).getroot()}
        missing = [n for n in items if n not in have]
        if not missing:
            continue
        code = {"in": "id", "iw": "iw", "ji": "yi", "nb": "no", "b+fil": "tl", "tw": "ak", "b+mni+Mtei": "mni-Mtei",
                "b+mni": "mni-Mtei"}.get(folder) or folder.replace("b+", "").replace("-r", "-")
        prot = [protect(unescape(items[n])) for n in missing]
        outs = call(code, [p[0] for p in prot])
        lines = []
        for n, (_, found), o in zip(missing, prot, outs):
            r = restore(o, found) or unescape(items[n])
            lines.append(f'    <string name="{n}">{escape(fix_end(unescape(items[n]), r))}</string>')
        text = d.read_text(encoding="utf-8").replace("</resources>", "\n".join(lines) + "\n</resources>")
        ET.fromstring(text.encode())
        d.write_text(text, encoding="utf-8")
        print(f"{d.parent.name}: added {len(missing)}", flush=True)


if "--fill-missing" in sys.argv:
    fill_missing()
