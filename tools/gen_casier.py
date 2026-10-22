"""Genere les textures (PIL), modeles JSON a elements, etats de bloc, modeles d'objets et tables de butin du mod Casier de service.

Regles : UV TOUJOURS dans le repere 0-16 ; les textures font 64x64 pixels mais couvrent 16x16 unites d'UV (4 px par unite).
Usage : python gen_casier.py [dossier_apercus]    (ecrit dans src/main/resources ; les apercus sont des rendus logiciels hors jeu)
"""
import json
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "minenorth_casier")
DATA = os.path.join(RES, "data", "minenorth_casier")
MODID = "minenorth_casier"

# nom du bloc -> (couleur de peinture, libelle de la plaque, couleur d'accent de la plaque)
VARIANTS = {
    "service": ((86, 104, 128), "SERVICE", (255, 190, 60)),
}
PX = 4  # pixels par unite d'UV


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k))


def font(size):
    for f in ("C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def u(v):
    """unites d'UV -> pixels"""
    return int(round(v * PX))


# ---------------------------------------------------------------- textures

def tex_body(c, seed):
    """Tole peinte brossee : fines stries verticales, bords sombres, rivets."""
    rnd = random.Random(seed)
    im = Image.new("RGBA", (64, 64))
    px = im.load()
    for x in range(64):
        streak = 0.94 + 0.08 * rnd.random()
        for y in range(64):
            k = streak * (0.97 + 0.06 * rnd.random())
            px[x, y] = shade(c, k) + (255,)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 63], outline=shade(c, 0.62))
    d.rectangle([1, 1, 62, 62], outline=shade(c, 0.82))
    for (x, y) in ((4, 4), (59, 4), (4, 59), (59, 59), (32, 4), (32, 59)):
        d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=shade(c, 1.25), outline=shade(c, 0.55))
    return im


def tex_inside(c, seed):
    rnd = random.Random(seed)
    base = (52, 56, 62)
    im = Image.new("RGBA", (64, 64))
    px = im.load()
    for x in range(64):
        for y in range(64):
            k = 0.92 + 0.14 * rnd.random()
            px[x, y] = shade(base, k) + (255,)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 63], outline=(30, 32, 36))
    d.line([0, 32, 63, 32], fill=(40, 42, 48))
    return im


def tex_door(c, seed):
    """Deux portes de face dans la meme image : [0..5.5] x [0..16] = porte du bas, [8..13.5] x [0..16] = porte du haut.
    Symetriques gauche/droite (la poignee est un element 3D)."""
    rnd = random.Random(seed)
    im = tex_body(c, seed)
    d = ImageDraw.Draw(im)
    dark = shade(c, 0.35)
    for x0, kind in ((0.0, "bas"), (8.0, "haut")):
        X0, X1 = u(x0), u(x0 + 5.5) - 1
        # cadre biseaute
        d.rectangle([X0, 0, X1, 63], outline=shade(c, 0.55))
        d.rectangle([X0 + 1, 1, X1 - 1, 62], outline=shade(c, 1.22))
        d.rectangle([X0 + 3, 4, X1 - 3, 59], outline=shade(c, 0.78))
        # grilles d'aeration (persiennes)
        zones = ((2.0, 5.2), (10.8, 14.0)) if kind == "bas" else ((1.6, 4.8), (11.2, 13.6))
        for (va, vb) in zones:
            for yy in range(u(va), u(vb), 3):
                d.line([X0 + 6, yy, X1 - 6, yy], fill=dark)
                d.line([X0 + 6, yy + 1, X1 - 6, yy + 1], fill=shade(c, 1.3))
        # porte-etiquette
        ty = u(6.1) if kind == "haut" else u(6.0)
        d.rectangle([X0 + 5, ty, X1 - 5, ty + 7], fill=(236, 236, 226), outline=shade(c, 0.45))
        d.line([X0 + 7, ty + 3, X1 - 7, ty + 3], fill=(150, 150, 150))
    return im


def tex_plaque(c, label, accent):
    """Plaque du service dans la zone [0..12] x [0..3] unites (96 x 24 px) d'une image 128x128 (8 px/unite)."""
    im = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 95, 23], fill=(18, 22, 34), outline=accent)
    d.rectangle([2, 2, 93, 21], outline=shade(accent, 0.6))
    f = font(15)
    w = d.textlength(label, font=f)
    d.text(((96 - w) / 2, 3), label, font=f, fill=accent)
    d.rectangle([4, 20, 91, 21], fill=c)
    # visses
    for x in (5, 90):
        d.ellipse([x - 1, 11, x + 1, 13], fill=(170, 170, 170))
    return im


def tex_uni(c):
    """Textile plie (uniforme) pour l'interieur."""
    im = Image.new("RGBA", (64, 64))
    px = im.load()
    rnd = random.Random(7)
    for x in range(64):
        for y in range(64):
            band = 0.86 if (y // 6) % 2 else 1.0
            px[x, y] = shade(c, band * (0.94 + 0.1 * rnd.random())) + (255,)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 63], outline=shade(c, 0.5))
    return im


def tex_sac():
    """Sac de saisie : papier kraft, tampon rouge, ficelle (repere 0-16)."""
    rnd = random.Random(11)
    im = Image.new("RGBA", (64, 64))
    px = im.load()
    for x in range(64):
        for y in range(64):
            k = 0.93 + 0.12 * rnd.random()
            px[x, y] = shade((196, 160, 108), k) + (255,)
    d = ImageDraw.Draw(im)
    f = font(8)
    # tampon sur la face avant (north) : x 4..12 -> u (16-12)..(16-4) = 4..12 ; y 0..9 -> v 7..16
    d.rectangle([u(4.3), u(8.0), u(11.7), u(11.8)], outline=(176, 28, 28), width=1)
    w = d.textlength("SAISIE", font=f)
    d.text((u(8) - w / 2, u(8.6)), "SAISIE", font=f, fill=(176, 28, 28))
    d.rectangle([u(5), u(12.4), u(11), u(13)], fill=(150, 120, 80))
    # ficelle
    d.rectangle([0, u(1.0), 63, u(1.0) + 2], fill=(120, 20, 20))
    return im


# ---------------------------------------------------------------- modeles

def auto_uv(face, f, t):
    """UV par defaut de Minecraft (repere 0-16)."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
    }[face]


def box(f, t, tex=None, faces=None):
    """tex : texture par defaut ('#body'...) ; faces : {face: texture ou (texture, uv)}"""
    el = {"from": list(f), "to": list(t), "faces": {}}
    names = ["north", "south", "east", "west", "up", "down"]
    for n in names:
        spec = (faces or {}).get(n, tex)
        if spec is None:
            continue
        if isinstance(spec, tuple):
            ref, uv = spec
        else:
            ref, uv = spec, auto_uv(n, f, t)
        el["faces"][n] = {"uv": [round(x, 3) for x in uv], "texture": ref}
    return el


def fix_uv(el):
    for fc in el["faces"].values():
        fc["uv"] = [min(16.0, max(0.0, x)) for x in fc["uv"]]
    return el


ROT90 = {  # remplacement des faces pour une rotation de +90 degres autour de Y (sens direct)
    90: {"north": "west", "west": "south", "south": "east", "east": "north", "up": "up", "down": "down"},
    -90: {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"},
}


def rot_point(p, hinge, deg):
    import math
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, z = p[0] - hinge[0], p[2] - hinge[1]
    return (x * c + z * s + hinge[0], p[1], -x * s + z * c + hinge[1])


def swing(el, hinge, deg):
    """Applique l'ouverture d'une porte (charniere = (x, z)) : 0/45 par rotation d'element, 90 par geometrie."""
    import copy
    e = copy.deepcopy(el)
    if deg == 0:
        return e
    if abs(deg) < 90:
        e["rotation"] = {"origin": [hinge[0], 8, hinge[1]], "axis": "y", "angle": deg}
        return e
    f, t = el["from"], el["to"]
    pts = [rot_point((x, 0, z), hinge, deg) for x in (f[0], t[0]) for z in (f[2], t[2])]
    nf = [min(p[0] for p in pts), f[1], min(p[2] for p in pts)]
    nt = [max(p[0] for p in pts), t[1], max(p[2] for p in pts)]
    e["from"], e["to"] = [round(v, 3) for v in nf], [round(v, 3) for v in nt]
    m = ROT90[90 if deg > 0 else -90]
    e["faces"] = {m[k]: v for k, v in el["faces"].items()}
    return e


def casier_elements(half, stage_deg):
    """Elements d'une moitie de casier (face avant = nord, z petit). stage_deg : ouverture des portes (0, 45, 90)."""
    els = []
    # coque
    els.append(box((1, 0, 13.5), (15, 16, 14.5), faces={"south": "#body", "north": "#inside", "up": "#body", "down": "#body", "east": "#body", "west": "#body"}))
    els.append(box((1, 0, 1.5), (2, 16, 13.5), faces={"west": "#body", "east": "#inside", "north": "#body", "up": "#body", "down": "#body"}))
    els.append(box((14, 0, 1.5), (15, 16, 13.5), faces={"east": "#body", "west": "#inside", "north": "#body", "up": "#body", "down": "#body"}))
    els.append(box((7.5, 0.8, 1.5), (8.5, 15.2, 13.5), faces={"east": "#inside", "west": "#inside", "north": "#body", "up": "#inside", "down": "#inside"}))
    els.append(box((2, 0, 1.5), (14, 0.8, 13.5), faces={"up": "#inside", "down": "#body"}))
    els.append(box((2, 15.2, 1.5), (14, 16, 13.5), faces={"up": "#body", "down": "#inside"}))
    # interieur : etagere, uniforme plie, chaussures, tringle et veste
    els.append(box((2, 8, 1.8), (7.5, 8.5, 13.5), faces={"up": "#inside", "down": "#inside", "north": "#inside"}))
    els.append(box((2.6, 8.5, 3), (7, 11, 10), tex="#uni"))
    els.append(box((2.8, 0.8, 4), (6.8, 3, 10.5), tex="#inside"))
    els.append(box((8.7, 13.2, 6.8), (13.8, 13.8, 7.4), tex="#inside"))
    els.append(box((9.4, 5.5, 6.3), (13.1, 13.2, 7.9), tex="#uni"))
    if half == "upper":
        # bandeau de tete portant la plaque
        els.append(box((1, 12.4, 0.4), (15, 16, 1.5), faces={"north": ("#body", [1, 0.4, 15, 3.6]), "up": "#body", "east": "#body", "west": "#body", "south": "#body", "down": "#body"}))
        els.append(box((2, 12.9, 0.1), (14, 15.9, 0.4), faces={
            "north": ("#plate", [0, 0, 12, 3]), "up": ("#body", [0, 0, 12, 0.3]), "down": ("#body", [0, 0, 12, 0.3]),
            "east": ("#body", [0, 0, 0.3, 3]), "west": ("#body", [0, 0, 0.3, 3])}))
        top, door_tex_u = 12.4, 8.0
    else:
        top, door_tex_u = 15.6, 0.0
    # portes : deux battants, charnieres sur les bords exterieurs
    y0, y1 = 0.4, top
    v1, v2 = 16 - y1, 16 - y0
    side = ("#body", [0, 0, 1, 15])
    for (xa, xb, hinge) in ((2, 7.5, (2, 1.0)), (8.5, 14, (14, 1.0))):
        part = []
        part.append(box((xa, y0, 0.5), (xb, y1, 1.5), faces={
            "north": ("#door", [door_tex_u, v1, door_tex_u + 5.5, v2]), "south": "#inside",
            "east": side, "west": side, "up": ("#body", [0, 0, 5.5, 1]), "down": ("#body", [0, 0, 5.5, 1])}))
        # poignee (barre) et serrure
        hx = (xb - 1.3) if xa < 8 else (xa + 0.6)
        mid = 6.5 if half == "lower" else 4.5
        part.append(box((hx, mid, 0.1), (hx + 0.7, mid + 3.0, 0.5), tex="#handle"))
        part.append(box((hx - 0.2, mid + 0.3, 0.45), (hx + 0.9, mid + 0.8, 0.52), tex="#handle"))
        part.append(box((hx - 0.2, mid + 2.2, 0.45), (hx + 0.9, mid + 2.7, 0.52), tex="#handle"))
        sign = 1 if hinge[0] < 8 else -1
        for p in part:
            els.append(swing(fix_uv(p), hinge, sign * stage_deg))
    for e in els:
        fix_uv(e)
    return els


def model_json(name, els):
    return {
        "credit": "MineNorth RP - genere par tools/gen_casier.py",
        "textures": {
            "particle": f"{MODID}:block/{name}_corps", "body": f"{MODID}:block/{name}_corps", "inside": f"{MODID}:block/{name}_interieur",
            "door": f"{MODID}:block/{name}_porte", "plate": f"{MODID}:block/{name}_plaque", "uni": f"{MODID}:block/{name}_tissu",
            "handle": f"{MODID}:block/poignee"},
        "elements": els}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def tex_poignee():
    im = Image.new("RGBA", (64, 64), (198, 202, 208, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 63], outline=(90, 94, 100))
    d.line([0, 20, 63, 20], fill=(235, 238, 242))
    return im


def sac_elements():
    t = "#sac"
    return [
        box((4, 0, 5), (12, 9, 11), tex=t),
        box((3.5, 0, 5.5), (4, 8, 10.5), tex=t),
        box((12, 0, 5.5), (12.5, 8, 10.5), tex=t),
        box((5, 9, 5.5), (11, 10.5, 10.5), tex=t),
        box((6, 10.5, 6.5), (10, 12.5, 9.5), tex=t),
        box((5.5, 10, 6), (10.5, 10.8, 10), tex="#cord"),
        box((7.2, 12.5, 7.2), (8.8, 13.6, 8.8), tex="#cord"),
        box((12.5, 3, 6.6), (13.2, 7.2, 9.4), tex="#label"),
    ]


def main():
    prev = sys.argv[1] if len(sys.argv) > 1 else None
    for name, (col, label, accent) in VARIANTS.items():
        tdir = os.path.join(ASSETS, "textures", "block")
        os.makedirs(tdir, exist_ok=True)
        tex_body(col, sum(map(ord, name)) % 1000).save(os.path.join(tdir, f"casier_{name}_corps.png"))
        tex_inside(col, 3).save(os.path.join(tdir, f"casier_{name}_interieur.png"))
        tex_door(col, sum(map(ord, name)) % 997).save(os.path.join(tdir, f"casier_{name}_porte.png"))
        tex_plaque(col, label, accent).save(os.path.join(tdir, f"casier_{name}_plaque.png"))
        tex_uni(shade(col, 0.9)).save(os.path.join(tdir, f"casier_{name}_tissu.png"))
        bn = f"casier_{name}"
        for half in ("lower", "upper"):
            for stage, deg in ((0, 0), (1, 45), (2, 90)):
                write(os.path.join(ASSETS, "models", "block", f"{bn}_{half}_{stage}.json"), model_json(bn, casier_elements(half, deg)))
        # modele d'objet : les deux moities empilees (le haut decale de 16)
        lo = casier_elements("lower", 0)
        up = casier_elements("upper", 0)
        for e in up:
            e["from"][1] += 16
            e["to"][1] += 16
            if "rotation" in e:
                e["rotation"]["origin"][1] += 16
        item = model_json(bn, lo + up)
        item["display"] = {
            "gui": {"rotation": [28, 215, 0], "translation": [0, -0.5, 0], "scale": [0.42, 0.42, 0.42]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.22, 0.22, 0.22]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        }
        write(os.path.join(ASSETS, "models", "item", bn + ".json"), item)
        # etat de bloc : 4 directions x 2 moities x 4 etapes (l'etape 3 = demi-ouverte, comme l'etape 1)
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for half in ("lower", "upper"):
                for stage in range(4):
                    m = {1: 1, 3: 1}.get(stage, stage)
                    v = {"model": f"{MODID}:block/{bn}_{half}_{m}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},half={half},stage={stage}"] = v
        write(os.path.join(ASSETS, "blockstates", bn + ".json"), {"variants": variants})
        # butin : seule la moitie basse donne l'objet
        write(os.path.join(DATA, "loot_tables", "blocks", bn + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MODID}:{bn}"}],
                       "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MODID}:{bn}", "properties": {"half": "lower"}}]}]})
    tdir = os.path.join(ASSETS, "textures", "block")
    tex_poignee().save(os.path.join(tdir, "poignee.png"))
    # sac de saisie
    idir = os.path.join(ASSETS, "textures", "item")
    os.makedirs(idir, exist_ok=True)
    tex_sac().save(os.path.join(idir, "sac_saisie.png"))
    cord = Image.new("RGBA", (16, 16), (130, 24, 24, 255))
    cord.save(os.path.join(idir, "sac_cordon.png"))
    label = Image.new("RGBA", (16, 16), (240, 236, 220, 255))
    ImageDraw.Draw(label).line([2, 8, 13, 8], fill=(160, 40, 40))
    label.save(os.path.join(idir, "sac_etiquette.png"))
    sac = {"credit": "MineNorth RP", "textures": {
        "particle": f"{MODID}:item/sac_saisie", "sac": f"{MODID}:item/sac_saisie", "cord": f"{MODID}:item/sac_cordon", "label": f"{MODID}:item/sac_etiquette"},
        "elements": [fix_uv(e) for e in sac_elements()],
        "display": {
            "gui": {"rotation": [25, 215, 0], "translation": [0, -1, 0], "scale": [1.1, 1.1, 1.1]},
            "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 2], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [1, 1.5, 0], "scale": [0.65, 0.65, 0.65]}}}
    write(os.path.join(ASSETS, "models", "item", "sac_saisie.json"), sac)

    if prev:
        import rendu_modele as R
        os.makedirs(prev, exist_ok=True)
        tm = {}
        for dp, _, fs in os.walk(os.path.join(ASSETS, "textures")):
            for fn in fs:
                rel = os.path.relpath(os.path.join(dp, fn), os.path.join(ASSETS, "textures")).replace("\\", "/")[:-4]
                tm[f"{MODID}:{rel}"] = os.path.join(dp, fn)
        for name in VARIANTS:
            for half in ("lower", "upper"):
                for stage in (0, 1, 2):
                    p = os.path.join(ASSETS, "models", "block", f"casier_{name}_{half}_{stage}.json")
                    R.render(p, tm, 180 + 28, 22, os.path.join(prev, f"casier_{name}_{half}_{stage}.png"), size=260, scale=11)
            R.render(os.path.join(ASSETS, "models", "item", f"casier_{name}.json"), tm, 180 + 30, 20, os.path.join(prev, f"item_casier_{name}.png"), size=360, scale=9, center=(8, 16, 8))
        R.render(os.path.join(ASSETS, "models", "item", "sac_saisie.json"), tm, 180 + 30, 22, os.path.join(prev, "item_sac_saisie.png"), size=300, scale=16, center=(8, 7, 8))


if __name__ == "__main__":
    main()
