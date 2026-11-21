"""Build a scale corpus: the 3 original communities plus N near-duplicate copies of each,
with community names, people, policy and case numbers, amounts, hours and counts changed."""
import random
import re
import shutil
import sys
import unicodedata

from comun import CORPUS, EXP

TEMPLATES = {
    "c1-olmo14": [("Calle Olmo 14", "{full}"), ("Olmo 14", "{short}"), ("calle Olmo", "calle {street}")],
    "c2-almendros": [("Residencial Los Almendros", "Residencial {short}"), ("Los Almendros", "{short}"), ("Almendros", "{bare}")],
    "c3-puerto3": [("Avenida del Puerto 3", "{full}"), ("Avda. del Puerto 3", "{full}"), ("Puerto 3", "{short}"), ("Avenida del Puerto", "Avenida del {street}")],
}
STREETS = ["Pino", "Roble", "Haya", "Fresno", "Castaño", "Abeto", "Tilo", "Sauce"]
TREES = ["Los Olivos", "Los Pinos", "Las Acacias", "Los Naranjos", "Los Cerezos", "Las Encinas", "Los Tilos",
         "Los Sauces", "Los Robles", "Las Palmeras", "Los Limoneros", "Las Adelfas", "Los Jazmines", "Las Moreras"]
FIRST = ["Alberto", "Rosa", "Pablo", "Elena", "Diego", "Nuria", "Óscar", "Pilar", "Iván", "Sara", "Rubén", "Alicia"]
SURNAMES = ["Benítez", "Crespo", "Delgado", "Esteban", "Fuentes", "Guerrero", "Hidalgo", "Lozano", "Medina", "Nieto",
            "Ortega", "Pascual", "Quintana", "Ramos", "Santos", "Toledo", "Vallejo", "Zamora"]
AMOUNT = re.compile(r"\b(\d{1,3}(?:\.\d{3})*|\d+)(,\d{2})? euros")


def community_names(template, index, rng):
    if template == "c1-olmo14":
        street, number = (("Olmo", rng.choice([n for n in range(2, 61) if n != 14])) if index % 2 == 0
                          else (rng.choice(STREETS), rng.randint(1, 60)))
        return {"full": f"Calle {street} {number}", "short": f"{street} {number}", "street": street}
    if template == "c2-almendros":
        tree = TREES[index % len(TREES)] + ("" if index < len(TREES) else f" {index // len(TREES) + 1}")
        return {"short": tree, "bare": tree.split(" ", 1)[1]}
    avenue, number = (("Puerto", rng.choice([n for n in range(4, 40)])) if index % 2 == 0
                      else (rng.choice(["Mar", "Muelle", "Faro"]), rng.randint(1, 40)))
    return {"full": f"Avenida del {avenue} {number}", "short": f"{avenue} {number}", "street": avenue}


def match_case(found, value):
    if found.isupper():
        return value.upper()
    return value[0].lower() + value[1:] if found[0].islower() else value


def consistent(rng, cache, key, make):
    if key not in cache:
        cache[key] = make()
    return cache[key]


def new_amount(match, rng, cache):
    value = int(match.group(1).replace(".", ""))
    new = consistent(rng, cache, ("eur", value), lambda: max(1, round(value * rng.uniform(0.3, 2.5) / 10) * 10))
    text = f"{new:,}".replace(",", ".") if new >= 1000 else str(new)
    return f"{text}{match.group(2) or ''} euros"


def mutate(text, names, template, rng, cache):
    for old, new in TEMPLATES[template]:
        text = re.sub(re.escape(old), lambda m, value=new.format(**names): match_case(m.group(0), value), text, flags=re.IGNORECASE)
    text = re.sub(r"ATL-\d{4}-(\d{4})", lambda m: consistent(rng, cache, m.group(0), lambda: f"ATL-{rng.randint(1000, 9999)}-{m.group(1)}"), text)
    text = re.sub(r"\b(20\d{2})-(\d{3})\b", lambda m: consistent(rng, cache, m.group(0), lambda: f"{m.group(1)}-{rng.randint(100, 999):03d}"), text)
    text = AMOUNT.sub(lambda m: new_amount(m, rng, cache), text)
    text = re.sub(r"\b(\d{1,2}) horas\b", lambda m: consistent(rng, cache, ("h", m.group(1)), lambda: f"{rng.choice([2, 3, 6, 8, 12, 36, 72])} horas"), text)
    text = re.sub(r"\b(dos|tres|cuatro|cinco) días por semana", lambda m: consistent(rng, cache, m.group(0), lambda: rng.choice(["dos", "tres", "cuatro", "cinco"]) + " días por semana"), text)
    text = re.sub(r"máximo de \d personas", lambda m: f"máximo de {rng.choice([2, 3, 5, 6])} personas", text)
    text = re.sub(r"\b\d+ votos a favor", lambda m: f"{rng.randint(10, 50)} votos a favor", text)
    text = re.sub(r"\b(\d{2}):00\b", lambda m: consistent(rng, cache, ("t", m.group(1)), lambda: f"{rng.randint(9, 23):02d}:00"), text)
    return re.sub(r"\b(D\.|Dña\.) ([A-ZÁÉÍÓÚÑ][\wáéíóúñ]+(?: [A-ZÁÉÍÓÚÑ][\wáéíóúñ]+){1,2})",
                  lambda m: m.group(1) + " " + consistent(rng, cache, m.group(2), lambda: f"{rng.choice(FIRST)} {rng.choice(SURNAMES)} {rng.choice(SURNAMES)}"), text)


def slug(text):
    plain = "".join(c for c in unicodedata.normalize("NFD", text.lower()) if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", "-", plain).strip("-")


def build(target, copies):
    if target.exists():
        shutil.rmtree(target)
    shutil.copytree(CORPUS, target)
    rng = random.Random(7)
    for template in TEMPLATES:
        for index in range(copies):
            names = community_names(template, index, rng)
            folder = target / f"{template[:2]}-{slug(names.get('full', names['short']))}"
            if folder.exists():
                folder = folder.with_name(folder.name + f"-{index}")
            folder.mkdir()
            cache = {}
            for source in sorted((CORPUS / template).glob("*.txt")):
                text = mutate(source.read_text(encoding="utf-8"), names, template, rng, cache)
                name = re.sub(r"\b(20\d{2})-(\d{3})\b", lambda m: cache.get(m.group(0), m.group(0)), source.name)
                (folder / name).write_text(text, encoding="utf-8")


def leaks(target):
    pattern = re.compile(r"Olmo 14\b|Almendros|Puerto 3\b|Luis Ferrer|Marta Quiroga|ATL-4471|ATL-4417|ATL-5120|ATL-3982", re.IGNORECASE)
    return [str(p.relative_to(target)) for p in target.rglob("*.txt")
            if p.parts[-2] not in TEMPLATES and pattern.search(p.read_text(encoding="utf-8"))]


if __name__ == "__main__":
    target = EXP.parent / (sys.argv[2] if len(sys.argv) > 2 else "escala") / "corpus"
    build(target, int(sys.argv[1]) if len(sys.argv) > 1 else 30)
    files = list(target.rglob("*.txt"))
    words = sum(len(p.read_text(encoding="utf-8").split()) for p in files)
    print(f"comunidades={len(list(target.iterdir()))} documentos={len(files)} palabras={words}")
    print("fugas:", leaks(target)[:10] or "ninguna")
