# -*- coding: utf-8 -*-
"""Plot (cone) de chantier et radar portable (sur trepied) du mod Police : textures + modeles de bloc a elements + apercu logiciel.
Reutilise le constructeur de gen_menottes.py. Rappel : UV toujours dans le repere 0-16 (la taille de la texture est ignoree).
Usage : python gen_chantier.py [dossier_apercus]"""
import json
import os
import random
import sys

from PIL import Image

import gen_menottes as G

rnd = random.Random(77)
n = G.noisy


def t_orange(x, y):
    return n((238, 112, 24), 8)


def t_orange_dark(x, y):
    return n((196, 84, 16), 8)


def t_reflect(x, y):          # bande blanche retroreflechissante : fines micro-billes
    return n((244, 244, 238), 5) if (x + y) % 2 == 0 else n((226, 228, 224), 5)


def t_rubber(x, y):
    return n((26, 26, 30), 4)


def t_grey(x, y):
    return n((150, 154, 162), 7)


def t_grey_dark(x, y):
    return n((84, 88, 98), 6)


def t_housing(x, y):
    return n((214, 216, 222), 5) if y < 6 else n((196, 198, 206), 5)


def t_lens(x, y):
    d = (x - 3.5) ** 2 + (y - 3.5) ** 2
    if d < 3:
        return (170, 220, 255)
    if d < 8:
        return n((34, 90, 170), 5)
    return n((14, 16, 22), 3)


def t_screen(x, y):           # ecran LCD vert avec trois chiffres a segments
    c = (14, 40, 22)
    if y in (2, 3, 4, 5) and x in (1, 2, 4, 5, 7):
        c = (96, 255, 140)
    if y in (2, 5) and x in (3, 6):
        c = (96, 255, 140)
    return c


def t_led_red(x, y):
    return n((230, 40, 40), 6)


def t_led_green(x, y):
    return n((60, 220, 90), 6)


def t_yellow(x, y):
    return n((246, 200, 40), 6)


def t_label(x, y):
    return n((30, 60, 150), 5) if y in (3, 4) else n((236, 236, 240), 4)


def t_vent(x, y):
    return n((40, 42, 50), 3) if y % 2 == 0 else t_housing(x, y)


TILES = [("orange", t_orange), ("orange_dark", t_orange_dark), ("reflect", t_reflect), ("rubber", t_rubber), ("grey", t_grey),
         ("grey_dark", t_grey_dark), ("housing", t_housing), ("lens", t_lens), ("screen", t_screen), ("led_red", t_led_red),
         ("led_green", t_led_green), ("yellow", t_yellow), ("label", t_label), ("vent", t_vent)]


def use_tiles():
    G.TILE_IDX = {nm: i for i, (nm, _) in enumerate(TILES)}


def atlas():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for i, (_, fn) in enumerate(TILES):
        cx, cy = (i % 8) * 8, (i // 8) * 8
        for y in range(8):
            for x in range(8):
                px[cx + x, cy + y] = fn(x, y) + (255,)
    return im


def octa(m, cx, cz, r, y1, y2, mat, name):
    """Prisme octogonal : 4 rectangles croises a 0, 45, 90 et 135 degres (leurs extremites forment les 8 cotes)."""
    a = r * 0.4142
    m.box(cx - r, y1, cz - a, cx + r, y2, cz + a, mat, name=name)
    m.box(cx - a, y1, cz - r, cx + a, y2, cz + r, mat, name=name)
    for ang in (45, -45):
        m.box(cx - r, y1, cz - a, cx + r, y2, cz + a, mat, rot=("y", ang, (cx, (y1 + y2) / 2, cz)), name=name)


def build_cone():
    use_tiles()
    m = G.Model("plot")
    # embase carree en caoutchouc, coins coupes, avec ses quatre pieds
    octa(m, 8, 8, 6.6, 0, 1.3, "rubber", "embase")
    m.box(3.0, 0.0, 3.0, 13.0, 0.3, 13.0, "rubber", name="semelle")
    # corps : paliers coniques, orange / bande retroreflechissante / orange / bande / pointe
    tiers = [(1.3, 4.6, 4.3, "orange"), (4.6, 6.4, 3.75, "reflect"), (6.4, 9.2, 3.3, "orange"), (9.2, 10.9, 2.8, "reflect"),
             (10.9, 14.0, 2.35, "orange"), (14.0, 15.2, 1.2, "orange_dark")]
    for (y1, y2, r, mat) in tiers:
        octa(m, 8, 8, r, y1, y2, mat, "palier")
    # bague orange sombre sous les bandes (relief) et bouchon
    octa(m, 8, 8, 3.95, 4.35, 4.65, "orange_dark", "bague")
    octa(m, 8, 8, 2.95, 9.0, 9.25, "orange_dark", "bague")
    return m


def build_radar():
    use_tiles()
    m = G.Model("radar")
    # --- trepied : 3 branches ecartees (deux de cote, une vers l'arriere), pieds en caoutchouc, tete orientable
    m.box(7.0, 5.0, 7.0, 9.0, 7.4, 9.0, "grey_dark", name="rotule")
    m.box(6.2, 6.4, 6.2, 9.8, 7.6, 9.8, "grey", name="plateau")
    m.box(7.2, 4.4, 7.2, 8.8, 5.2, 8.8, "grey", name="manchon")
    m.box(3.6, 0.6, 7.4, 4.6, 5.4, 8.6, "grey_dark", rot=("z", 22.5, (4.1, 5.4, 8.0)), name="jambe")
    m.box(11.4, 0.6, 7.4, 12.4, 5.4, 8.6, "grey_dark", rot=("z", -22.5, (11.9, 5.4, 8.0)), name="jambe")
    m.box(7.4, 0.6, 11.4, 8.6, 5.4, 12.4, "grey_dark", rot=("x", -22.5, (8.0, 5.4, 11.9)), name="jambe")
    m.box(2.9, 0.0, 7.2, 4.5, 0.9, 8.8, "rubber", name="pied")
    m.box(11.5, 0.0, 7.2, 13.1, 0.9, 8.8, "rubber", name="pied")
    m.box(7.2, 0.0, 12.6, 8.8, 0.9, 14.2, "rubber", name="pied")
    m.box(5.2, 2.6, 7.6, 10.8, 3.0, 8.4, "grey", name="entretoise")
    m.box(9.8, 6.4, 7.4, 11.4, 7.2, 8.6, "grey_dark", name="manette")
    m.box(11.4, 6.2, 7.4, 12.0, 7.4, 8.6, "rubber", name="bouton")
    # --- boitier : corps clair, face avant noire avec deux objectifs, dessus avec ecran, poignee, antenne
    m.box(4.0, 7.6, 3.2, 12.0, 12.6, 12.6, "housing", name="boitier", top="housing")
    m.box(3.6, 7.6, 3.6, 4.0, 12.2, 12.2, "grey", name="flanc")
    m.box(12.0, 7.6, 3.6, 12.4, 12.2, 12.2, "grey", name="flanc")
    m.box(4.2, 7.8, 2.6, 11.8, 12.4, 3.2, "rubber", name="face")
    # objectifs (anneau noir + verre bleu), a l'avant (face nord, z faible)
    for cx in (6.3, 9.7):
        m.ring_xy(cx, 10.0, 1.7, 0.5, 1.6, 2.7, "rubber", name="objectif")
        m.box(cx - 1.15, 8.85, 1.9, cx + 1.15, 11.15, 2.65, "lens", name="verre")
    m.box(6.8, 11.9, 2.2, 9.2, 12.4, 2.7, "grey_dark", name="visee")
    # dessus : ecran de lecture de vitesse incline, boutons, voyants
    m.box(5.0, 12.6, 6.4, 11.0, 13.2, 11.8, "rubber", name="cadre")
    m.box(5.4, 13.2, 6.8, 10.6, 13.4, 11.4, "screen", name="ecran")
    m.box(5.0, 12.6, 4.2, 7.0, 13.0, 5.6, "led_green", name="voyant")
    m.box(7.6, 12.6, 4.2, 9.6, 13.0, 5.6, "led_red", name="voyant")
    m.box(10.2, 12.6, 4.2, 11.0, 13.0, 5.6, "yellow", name="bouton")
    # poignee de transport + antenne
    m.box(6.0, 13.2, 11.6, 6.8, 15.0, 12.4, "grey_dark", name="poignee")
    m.box(9.2, 13.2, 11.6, 10.0, 15.0, 12.4, "grey_dark", name="poignee")
    m.box(6.0, 15.0, 11.6, 10.0, 15.8, 12.4, "rubber", name="poignee")
    m.box(11.0, 12.6, 4.0, 11.6, 15.6, 4.6, "grey_dark", name="antenne")
    m.box(10.9, 15.6, 3.9, 11.7, 16.0, 4.7, "led_red", name="balise")
    # arriere : grille d'aeration et plaque
    m.box(5.0, 8.4, 12.6, 11.0, 11.6, 12.9, "vent", name="grille")
    m.box(6.0, 7.8, 12.6, 10.0, 8.3, 12.9, "label", name="plaque")
    return m


def display_block():
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.7, 0.7, 0.7]},
            "fixed": {"scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}


def write(name, model, tex_name, folder="block"):
    res = G.RES
    js = {"credit": "MineNorth RP", "parent": "block/block",
          "textures": {"t": "minenorthpolice:block/" + tex_name, "particle": "minenorthpolice:block/" + tex_name},
          "elements": model.els, "display": display_block()}
    with open(os.path.join(res, "models", folder, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(js, f, indent=1)
    return js


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(G.HERE, "apercus")
    os.makedirs(out, exist_ok=True)
    use_tiles()
    at = atlas()
    os.makedirs(os.path.join(G.RES, "textures", "block"), exist_ok=True)
    at.save(os.path.join(G.RES, "textures", "block", "chantier_atlas.png"))
    cone, radar = build_cone(), build_radar()
    jc = write("plot_chantier", cone, "chantier_atlas")
    jr = write("radar_fixe", radar, "chantier_atlas")
    sheet = Image.new("RGB", (4 * 360, 360), (52, 56, 76))
    for i, (js, rot) in enumerate(((jc, (30, 225, 0)), (jc, (20, 200, 0)), (jr, (30, 225, 0)), (jr, (25, 150, 0)))):
        sheet.paste(G.render_model(js, at, rot, 1.0, 360), (i * 360, 0))
    sheet.save(os.path.join(out, "chantier.png"))
    print("ok")


if __name__ == "__main__":
    main()
