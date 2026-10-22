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


def t_tube(x, y):             # tube de trepied noir mat avec un liseré brillant
    return n((70, 72, 80), 5) if x == 1 else n((22, 22, 26), 4)


def t_emit(x, y):             # module emetteur : anneaux concentriques sombres et une pastille centrale
    d = (x - 3.5) ** 2 + (y - 3.5) ** 2
    if d < 2:
        return n((120, 130, 150), 6)
    if d < 6:
        return n((18, 20, 26), 3)
    if d < 11:
        return n((58, 62, 74), 4)
    return n((30, 32, 40), 3)


def t_cable(x, y):
    return n((16, 16, 18), 3)


def t_vent(x, y):
    return n((40, 42, 50), 3) if y % 2 == 0 else t_housing(x, y)


TILES = [("orange", t_orange), ("orange_dark", t_orange_dark), ("reflect", t_reflect), ("rubber", t_rubber), ("grey", t_grey),
         ("grey_dark", t_grey_dark), ("housing", t_housing), ("lens", t_lens), ("screen", t_screen), ("led_red", t_led_red),
         ("led_green", t_led_green), ("yellow", t_yellow), ("label", t_label), ("vent", t_vent), ("tube", t_tube), ("emit", t_emit), ("cable", t_cable)]


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


def tube(m, x, y1, y2, z, mat="tube", w=1.2, rot=None, name="tube"):
    m.box(x - w / 2, y1, z - w / 2, x + w / 2, y2, z + w / 2, mat, rot=rot, name=name)


def build_radar_lower():
    """Moitie basse (y 0-16) : trepied en tubes noirs (3 pieds ecartes), collier de reglage, colonne centrale et cables qui trainent au sol."""
    use_tiles()
    m = G.Model("radar")
    top = 12.4
    # pieds : deux sur les cotes (rotation Z), un vers l'arriere (rotation X) ; pied en caoutchouc au bout
    tube(m, 8.0, top - 13.6, top, 8.0, rot=("z", 22.5, (8.0, top, 8.0)), w=1.3, name="pied")
    tube(m, 8.0, top - 13.6, top, 8.0, rot=("z", -22.5, (8.0, top, 8.0)), w=1.3, name="pied")
    tube(m, 8.0, top - 13.6, top, 8.0, rot=("x", -22.5, (8.0, top, 8.0)), w=1.3, name="pied")
    for fx, fz in ((2.9, 8.0), (13.1, 8.0), (8.0, 13.1)):
        m.box(fx - 1.0, 0.0, fz - 1.0, fx + 1.0, 0.9, fz + 1.0, "rubber", name="semelle")
    # moyeu, collier de serrage et manette de blocage
    m.box(6.2, top - 1.6, 6.2, 9.8, top + 0.9, 9.8, "grey_dark", name="moyeu")
    G.Model.ring_xz(m, 8.0, 8.0, 2.2, 0.7, top + 0.9, top + 2.1, "grey", name="collier")
    m.box(9.6, top + 0.2, 7.3, 11.6, top + 1.2, 8.7, "grey", name="manette")
    m.box(11.6, top - 0.2, 7.2, 12.5, top + 1.6, 8.8, "rubber", name="poignee")
    # colonne centrale : tube coulissant + mecanisme de reglage (cremaillere et molette)
    tube(m, 8.0, top + 2.1, 16.0, 8.0, w=2.0, name="colonne")
    m.box(8.9, top + 2.4, 7.2, 9.5, 16.0, 8.8, "grey_dark", name="cremaillere")
    m.box(6.0, top + 4.4, 7.2, 7.1, top + 5.6, 8.8, "grey", name="molette")
    m.box(5.4, top + 4.5, 7.5, 6.0, top + 5.5, 8.5, "rubber", name="molette")
    # cables : sortent de l'arriere de la colonne, descendent et trainent au sol en boucle
    m.box(8.0, 4.2, 9.1, 8.6, 16.0, 9.7, "cable", name="cable")
    m.box(8.0, 1.8, 9.6, 8.6, 4.4, 10.2, "cable", name="cable")
    m.box(8.0, 0.0, 10.1, 8.6, 1.9, 10.7, "cable", name="cable")
    m.box(8.0, 0.0, 10.6, 8.6, 0.6, 13.6, "cable", name="cable")
    m.box(8.0, 0.0, 13.0, 11.0, 0.6, 13.6, "cable", name="cable")
    m.box(10.4, 0.0, 10.4, 11.0, 0.6, 13.6, "cable", name="cable")
    m.box(7.9, 0.0, 10.0, 8.7, 1.6, 10.8, "rubber", name="serre-cable")
    return m


def rounded_body(m, x1, x2, y1, y2, z1, z2, mat, top_mat=None):
    """Boitier arrondi : corps, quatre aretes verticales chanfreinees (cubes tournes a 45 degres), plaques haute et basse plus petites (biseau)."""
    m.box(x1, y1 + 0.5, z1, x2, y2 - 0.5, z2, mat, name="boitier")
    for cx in (x1, x2):
        for cz in (z1, z2):
            m.box(cx - 0.9, y1 + 0.5, cz - 0.9, cx + 0.9, y2 - 0.5, cz + 0.9, mat, rot=("y", 45, (cx, (y1 + y2) / 2, cz)), name="arete")
    m.box(x1 + 0.7, y2 - 0.55, z1 + 0.7, x2 - 0.7, y2, z2 - 0.7, top_mat or mat, name="biseau")
    m.box(x1 + 0.7, y1, z1 + 0.7, x2 - 0.7, y1 + 0.55, z2 - 0.7, mat, name="biseau")


def build_radar_upper():
    """Moitie haute (y 16-32) : boitier gris clair arrondi, deux grandes ouvertures rondes a l'avant (vitre sombre / module emetteur), petit boitier carre au-dessus."""
    use_tiles()
    m = G.Model("radar")
    # fixation : platine sur la colonne et deux bras en fourche
    m.box(6.2, 16.0, 6.2, 9.8, 17.4, 9.8, "grey_dark", name="platine")
    m.box(4.2, 17.4, 6.8, 5.4, 20.6, 9.2, "grey_dark", name="bras")
    m.box(10.6, 17.4, 6.8, 11.8, 20.6, 9.2, "grey_dark", name="bras")
    m.box(7.2, 17.4, 8.6, 8.8, 19.0, 10.0, "grey_dark", name="verin")
    # boitier principal large et bas
    rounded_body(m, 1.2, 14.8, 18.6, 26.0, 4.0, 12.4, "housing")
    # pare-soleil au-dessus des ouvertures
    m.box(1.8, 25.0, 3.0, 14.2, 25.7, 4.2, "grey", name="visiere")
    # deux grandes ouvertures circulaires cote a cote (face avant = nord, z faible) : collerette noire + fond
    left, right, cy = 5.0, 11.0, 22.2
    for cx in (left, right):
        G.Model.ring_xy(m, cx, cy, 2.9, 0.7, 2.7, 4.2, "rubber", name="collerette")
        G.Model.ring_xy(m, cx, cy, 2.25, 0.35, 3.3, 4.2, "grey_dark", name="bague")
    m.box(left - 2.2, cy - 2.2, 3.6, left + 2.2, cy + 2.2, 4.0, "lens", name="vitre")
    m.box(left - 1.6, cy + 1.0, 3.4, left - 0.4, cy + 1.6, 3.6, "reflect" if False else "housing", name="reflet")
    m.box(right - 2.2, cy - 2.2, 3.6, right + 2.2, cy + 2.2, 4.0, "emit", name="emetteur")
    m.box(right - 0.7, cy - 0.7, 3.2, right + 0.7, cy + 0.7, 3.7, "grey", name="cone")
    # voyants et plaque sous les ouvertures
    m.box(2.2, 19.2, 3.5, 3.4, 19.8, 4.0, "led_green", name="voyant")
    m.box(12.6, 19.2, 3.5, 13.8, 19.8, 4.0, "led_red", name="voyant")
    m.box(5.6, 19.0, 3.7, 10.4, 19.8, 4.0, "label", name="plaque")
    # petit boitier carre au-dessus, avec sa fenetre sombre au centre
    rounded_body(m, 5.8, 10.2, 26.0, 30.4, 6.2, 10.4, "housing")
    m.box(6.7, 27.0, 5.9, 9.3, 29.2, 6.4, "grey_dark", name="cadre")
    m.box(7.1, 27.4, 5.6, 8.9, 28.8, 6.2, "lens", name="fenetre")
    m.box(7.4, 30.4, 7.4, 8.6, 31.0, 8.6, "led_red", name="balise")
    # arriere : grille d'aeration, prise de cable, poignee
    m.box(3.0, 20.0, 12.4, 13.0, 24.6, 12.8, "vent", name="grille")
    m.box(7.4, 18.8, 12.4, 8.6, 19.8, 13.2, "rubber", name="prise")
    m.box(7.4, 17.0, 12.6, 8.6, 18.9, 13.0, "cable", name="cable")
    m.box(4.0, 26.0, 8.4, 5.0, 27.4, 11.6, "grey_dark", name="poignee")
    m.box(11.0, 26.0, 8.4, 12.0, 27.4, 11.6, "grey_dark", name="poignee")
    return m


def display_block():
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.7, 0.7, 0.7]},
            "fixed": {"scale": [0.6, 0.6, 0.6]}, "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}


def shifted(els, dy):
    import copy
    out = copy.deepcopy(els)
    for e in out:
        e["from"][1] += dy
        e["to"][1] += dy
        if "rotation" in e:
            e["rotation"]["origin"][1] += dy
    return out


def write(name, model, tex_name, folder="block"):
    res = G.RES
    js = {"credit": "MineNorth RP", "parent": "block/block",
          "textures": {"t": "minenorthpolice:block/" + tex_name, "particle": "minenorthpolice:block/" + tex_name},
          "elements": model.els, "display": display_block()}
    with open(os.path.join(res, "models", folder, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(js, f, indent=1)
    return js


def display_tall():
    """Objet de 2 blocs de haut (32 unites) : reduit pour tenir dans l'icone, recentre en hauteur."""
    return {"gui": {"rotation": [28, 225, 0], "translation": [0, -3.4, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -3.2, 0], "scale": [0.4, 0.4, 0.4]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.25, 0.25, 0.25]},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 0.5, 0], "scale": [0.3, 0.3, 0.3]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 0.5, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 1, 0], "scale": [0.34, 0.34, 0.34]},
            "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 1, 0], "scale": [0.34, 0.34, 0.34]},
            "head": {"translation": [0, 8, 0], "scale": [0.5, 0.5, 0.5]}}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(G.HERE, "apercus")
    os.makedirs(out, exist_ok=True)
    use_tiles()
    at = atlas()
    os.makedirs(os.path.join(G.RES, "textures", "block"), exist_ok=True)
    at.save(os.path.join(G.RES, "textures", "block", "chantier_atlas.png"))
    cone = build_cone()
    lower, upper = build_radar_lower(), build_radar_upper()
    jc = write("plot_chantier", cone, "chantier_atlas")
    write("radar_fixe_lower", lower, "chantier_atlas")
    up_block = G.Model("radar")
    up_block.els = shifted(upper.els, -16)
    write("radar_fixe_upper", up_block, "chantier_atlas")
    full = G.Model("radar")
    full.els = lower.els + upper.els
    js = {"credit": "MineNorth RP", "parent": "block/block",
          "textures": {"t": "minenorthpolice:block/chantier_atlas", "particle": "minenorthpolice:block/chantier_atlas"},
          "elements": full.els, "display": display_tall()}
    with open(os.path.join(G.RES, "models", "item", "radar_mobile.json"), "w", encoding="utf-8") as f:
        json.dump(js, f, indent=1)
    # apercus : cone, radar entier (3 angles), boitier de pres, trepied
    sheet = Image.new("RGB", (4 * 420, 2 * 420), (52, 56, 76))
    views = [(jc, (30, 225, 0), 1.0), (js, (20, 200, 0), 0.55), (js, (20, 150, 0), 0.55), (js, (15, 180, 0), 0.55),
             (js, (10, 215, 0), 1.0), (js, (28, 225, 0), 0.5), (js, (0, 180, 0), 0.55), (js, (60, 200, 0), 0.55)]
    for i, (j, rot, sc) in enumerate(views):
        im = G.render_model(j, at, rot, sc, 420, center=(8, 16, 8) if j is js else (8, 8, 8))
        if j is js and sc == 1.0:
            pass
        sheet.paste(im, ((i % 4) * 420, (i // 4) * 420))
    sheet.save(os.path.join(out, "chantier.png"))
    print("ok")


if __name__ == "__main__":
    main()
