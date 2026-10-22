# -*- coding: utf-8 -*-
"""Menottes, cle, menottes artisanales et sac de jute (mod Police) : textures + modeles JSON a elements + apercu logiciel.

Usage : python gen_menottes.py [dossier_apercus]
 - ecrit les textures et modeles dans src/main/resources/assets/minenorthpolice/
 - rend des apercus (rasteriseur logiciel : memes UV 0-16, memes faces que Minecraft) pour verifier les formes sans lancer le jeu.
Rappel : dans un modele JSON, les UV sont TOUJOURS dans le repere 0-16 (la taille reelle de la texture est ignoree).
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "minenorthpolice")
rnd = random.Random(1789)


def clamp(v):
    return max(0, min(255, int(v)))


# ----------------------------------------------------------------------------- atlas de materiaux (tuiles 8x8 dans 64x64)
def noisy(base, amp):
    return tuple(clamp(c + rnd.randint(-amp, amp)) for c in base)


def t_chrome(x, y):
    # reflet horizontal : clair en haut, sombre au milieu, clair en bas
    k = [1.0, 0.6, 0.2, -0.2, -0.3, 0.0, 0.5, 0.9][y]
    c = noisy((190, 198, 212), 5)
    return tuple(clamp(v + 28 * k) for v in c)


def t_chrome_hi(x, y):
    return noisy((228, 233, 242), 6)


def t_steel_dark(x, y):
    return noisy((88, 94, 108), 7)


def t_lock_face(x, y):
    c = t_chrome(x, y)
    if (x - 3.5) ** 2 + (y - 2.6) ** 2 <= 2.4 or (3 <= x <= 4 and 3 <= y <= 6):   # trou de serrure
        return noisy((16, 16, 20), 3)
    return c


def t_teeth(x, y):
    return noisy((30, 32, 40), 4) if x % 2 == 0 else t_chrome(x, y)


def t_black(x, y):
    return noisy((26, 26, 30), 3)


def t_rib(x, y):
    # serflex : fines nervures transversales, plastique noir
    return noisy((52, 52, 58), 3) if x % 3 == 0 else noisy((22, 22, 26), 3)


def t_rust(x, y):
    c = noisy((150, 78, 38), 14)
    if rnd.random() < 0.18:
        c = noisy((196, 112, 52), 10)
    return c


def t_rust_dark(x, y):
    return noisy((96, 52, 32), 10)


def t_chain_black(x, y):
    return noisy((44, 44, 52), 6)


def t_blue(x, y):
    return noisy((34, 66, 160), 6)


def t_red(x, y):
    return noisy((178, 34, 44), 6)


def t_jute_a(x, y):
    base = (192, 152, 90) if ((x // 2) + (y // 2)) % 2 == 0 else (172, 132, 74)    # armure toile : fils croises
    if x % 2 == 0 and ((x // 2) + (y // 2)) % 2 == 0:
        base = (206, 166, 102)
    return noisy(base, 8)


def t_jute_b(x, y):
    base = (150, 112, 62) if ((x // 2) + (y // 2)) % 2 == 0 else (130, 96, 52)
    return noisy(base, 8)


def t_jute_seam(x, y):
    return noisy((88, 62, 36), 6) if y in (3, 4) and x % 4 < 3 else t_jute_b(x, y)


def t_rope(x, y):
    return noisy((222, 196, 140), 8) if (x + y) % 4 < 2 else noisy((170, 142, 94), 8)


def t_brass(x, y):
    return noisy((200, 158, 62), 8) if y < 5 else noisy((150, 114, 42), 8)


def t_white(x, y):
    return noisy((238, 238, 232), 4)


TILES = [("chrome", t_chrome), ("chrome_hi", t_chrome_hi), ("steel_dark", t_steel_dark), ("lock", t_lock_face), ("teeth", t_teeth),
         ("black", t_black), ("rib", t_rib), ("rust", t_rust), ("rust_dark", t_rust_dark), ("chain_black", t_chain_black),
         ("blue", t_blue), ("red", t_red), ("jute_a", t_jute_a), ("jute_b", t_jute_b), ("jute_seam", t_jute_seam),
         ("rope", t_rope), ("brass", t_brass), ("white", t_white)]
TILE_IDX = {n: i for i, (n, _) in enumerate(TILES)}


def make_atlas():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    for i, (_, fn) in enumerate(TILES):
        cx, cy = (i % 8) * 8, (i // 8) * 8
        for y in range(8):
            for x in range(8):
                px[cx + x, cy + y] = fn(x, y) + (255,)
    return im


def uv_of(mat):
    """Tuile -> UV 0-16 (l'atlas fait 64 px = 16 unites : une tuile de 8 px = 2 unites), avec une marge d'un demi-pixel."""
    i = TILE_IDX[mat]
    u, v = (i % 8) * 2, (i // 8) * 2
    return [u + 0.125, v + 0.125, u + 1.875, v + 1.875]


# ----------------------------------------------------------------------------- constructeur de modeles
class Model:
    def __init__(self, texture):
        self.texture = texture
        self.els = []

    def box(self, x1, y1, z1, x2, y2, z2, mat, rot=None, name=None, top=None, front=None):
        """Boite. rot = (axe, angle, origine) ; top/front = materiaux differents pour la face haute / la face sud (+z)."""
        faces = {}
        for f in ("north", "south", "east", "west", "up", "down"):
            m = mat
            if f == "up" and top:
                m = top
            if f == "south" and front:
                m = front
            faces[f] = {"uv": uv_of(m), "texture": "#t"}
        el = {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}
        if name:
            el["name"] = name
        if rot:
            axis, ang, org = rot
            el["rotation"] = {"origin": list(org), "axis": axis, "angle": ang}
        self.els.append(el)
        return el

    def ring_xz(self, cx, cz, r, th, y1, y2, mat, gap=None, name="anneau"):
        """Anneau octogonal dans le plan horizontal : 4 cotes droits + 4 diagonales a 45 deg. gap = cote laisse ouvert ('n','s','e','w')."""
        a = r * 0.4142 * 1.0          # demi-longueur d'un cote droit (octogone regulier de rayon exterieur r)
        for side, (x1, z1, x2, z2) in {
            "n": (cx - a, cz - r, cx + a, cz - r + th),
            "s": (cx - a, cz + r - th, cx + a, cz + r),
            "w": (cx - r, cz - a, cx - r + th, cz + a),
            "e": (cx + r - th, cz - a, cx + r, cz + a)}.items():
            if side != gap:
                self.box(x1, y1, z1, x2, y2, z2, mat, name=name)
        ln = 2 * a + th * 0.9
        for sx, sz in ((1, -1), (1, 1), (-1, 1), (-1, -1)):
            ccx = cx + sx * (r - th / 2) * 0.7071 * 0.93
            ccz = cz + sz * (r - th / 2) * 0.7071 * 0.93
            ang = 45 if sx * sz > 0 else -45
            self.box(ccx - ln / 2, y1, ccz - th / 2, ccx + ln / 2, y2, ccz + th / 2, mat, rot=("y", ang, (ccx, (y1 + y2) / 2, ccz)), name=name)

    def ring_xy(self, cx, cy, r, th, z1, z2, mat, name="anneau"):
        a = r * 0.4142
        for (x1, y1, x2, y2) in ((cx - a, cy + r - th, cx + a, cy + r), (cx - a, cy - r, cx + a, cy - r + th),
                                 (cx - r, cy - a, cx - r + th, cy + a), (cx + r - th, cy - a, cx + r, cy + a)):
            self.box(x1, y1, z1, x2, y2, z2, mat, name=name)
        ln = 2 * a + th * 0.9
        for sx, sy in ((1, 1), (1, -1), (-1, -1), (-1, 1)):
            ccx = cx + sx * (r - th / 2) * 0.7071 * 0.93
            ccy = cy + sy * (r - th / 2) * 0.7071 * 0.93
            ang = -45 if sx * sy > 0 else 45
            self.box(ccx - ln / 2, ccy - th / 2, z1, ccx + ln / 2, ccy + th / 2, z2, mat, rot=("z", ang, (ccx, ccy, (z1 + z2) / 2)), name=name)

    def link_flat(self, cx, cz, y, mat, length=2.6, w=1.5, th=0.45, name="maillon"):
        """Maillon couche a plat (plan horizontal), ovale ouvert au centre."""
        h = th
        self.box(cx - length / 2, y, cz - w / 2, cx + length / 2, y + h, cz - w / 2 + th, mat, name=name)
        self.box(cx - length / 2, y, cz + w / 2 - th, cx + length / 2, y + h, cz + w / 2, mat, name=name)
        self.box(cx - length / 2, y, cz - w / 2 + th, cx - length / 2 + th, y + h, cz + w / 2 - th, mat, name=name)
        self.box(cx + length / 2 - th, y, cz - w / 2 + th, cx + length / 2, y + h, cz + w / 2 - th, mat, name=name)

    def link_up(self, cx, cz, y, mat, length=2.6, w=1.5, th=0.45, name="maillon"):
        """Maillon dresse sur la tranche (plan vertical x-y)."""
        z1, z2 = cz - th / 2, cz + th / 2
        self.box(cx - length / 2, y, z1, cx + length / 2, y + th, z2, mat, name=name)
        self.box(cx - length / 2, y + w - th, z1, cx + length / 2, y + w, z2, mat, name=name)
        self.box(cx - length / 2, y + th, z1, cx - length / 2 + th, y + w - th, z2, mat, name=name)
        self.box(cx + length / 2 - th, y + th, z1, cx + length / 2, y + w - th, z2, mat, name=name)

    def json(self, display):
        return {"credit": "MineNorth RP", "textures": {"t": "minenorthpolice:item/menottes_atlas", "particle": "minenorthpolice:item/menottes_atlas"},
                "elements": self.els, "display": display}


def display_flat(gui_rot=(30, 225, 0), gui_scale=0.95, hand_scale=0.6):
    return {
        "gui": {"rotation": list(gui_rot), "translation": [0, 0, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6] * 3},
        "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, 0], "scale": [1.0] * 3},
        "thirdperson_righthand": {"rotation": [75, 0, 0], "translation": [0, 3, 1], "scale": [hand_scale] * 3},
        "thirdperson_lefthand": {"rotation": [75, 0, 0], "translation": [0, 3, 1], "scale": [hand_scale] * 3},
        "firstperson_righthand": {"rotation": [20, -30, 0], "translation": [1, 3, 2], "scale": [0.7] * 3},
        "firstperson_lefthand": {"rotation": [20, 30, 0], "translation": [1, 3, 2], "scale": [0.7] * 3},
        "head": {"translation": [0, 10, 0], "scale": [1.0] * 3},
    }


def display_upright(gui_rot=(25, 220, 0), gui_scale=0.95, hand_scale=0.65):
    d = display_flat(gui_rot, gui_scale, hand_scale)
    d["ground"] = {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6] * 3}
    d["fixed"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0] * 3}
    d["thirdperson_righthand"] = {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [hand_scale] * 3}
    d["thirdperson_lefthand"] = {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [hand_scale] * 3}
    d["firstperson_righthand"] = {"rotation": [0, -15, 0], "translation": [1, 3, 2], "scale": [0.7] * 3}
    d["firstperson_lefthand"] = {"rotation": [0, 15, 0], "translation": [1, 3, 2], "scale": [0.7] * 3}
    return d


# ----------------------------------------------------------------------------- les quatre objets
def cuff_unit(m, cx, cz, side, ring_mat, lock_mat, open_jaw=False):
    """Un bracelet : anneau chrome, charniere cylindrique, boitier de serrure avec trou de cle, cliquet (dents)."""
    y1, y2 = 7.0, 9.6
    m.ring_xz(cx, cz, 2.9, 0.75, y1, y2, ring_mat, name="bracelet")
    # bague interieure plus fine (epaisseur du metal visible a l'interieur)
    m.box(cx - 1.4, y1 + 0.5, cz - 2.2, cx + 1.4, y2 - 0.5, cz - 2.0, "steel_dark", name="interieur")
    m.box(cx - 1.4, y1 + 0.5, cz + 2.0, cx + 1.4, y2 - 0.5, cz + 2.2, "steel_dark", name="interieur")
    # boitier de serrure (cote exterieur oppose a la chaine) avec trou de cle sur la face haute
    lx = cx + side * 2.55
    m.box(lx - 0.9, y1 - 0.25, cz - 1.5, lx + 0.9, y2 + 0.35, cz + 1.5, "chrome_hi", top="lock", name="serrure")
    m.box(lx - 0.5, y2 + 0.35, cz - 0.7, lx + 0.5, y2 + 0.6, cz + 0.7, "steel_dark", name="vis")
    # charniere : petit cylindre (boite) cote nord
    m.box(cx - 0.8, y1 - 0.15, cz - 3.2, cx + 0.8, y2 + 0.15, cz - 2.6, "steel_dark", name="charniere")
    # cliquet : dents dans le bracelet
    for k in range(3):
        m.box(cx - 1.1 + k * 0.8, y1 + 0.3, cz + 1.75, cx - 0.7 + k * 0.8, y2 - 0.3, cz + 2.1, "teeth", name="cliquet")
    if open_jaw:
        m.box(cx - 0.6, y1 + 0.2, cz + 2.9, cx + 0.6, y2 - 0.2, cz + 3.6, "chrome", rot=("y", 22.5, (cx, 8, cz + 2.9)), name="machoire")


def build_menottes_police():
    m = Model("menottes_atlas")
    cuff_unit(m, 3.4, 8.0, -1, "chrome", "chrome_hi")
    cuff_unit(m, 12.6, 8.0, 1, "chrome", "chrome_hi", open_jaw=True)
    # chaine : 4 maillons alternes a plat / sur la tranche, relies aux charnieres
    xs = [6.5, 7.9, 9.3]
    m.box(5.9, 7.9, 7.55, 6.7, 8.7, 8.45, "steel_dark", name="embase")
    m.box(9.3, 7.9, 7.55, 10.1, 8.7, 8.45, "steel_dark", name="embase")
    m.link_flat(6.9, 8.0, 8.05, "chrome", length=2.4, w=1.4)
    m.link_up(8.0, 8.0, 7.6, "chrome_hi", length=2.2, w=1.5)
    m.link_flat(9.2, 8.0, 8.05, "chrome", length=2.4, w=1.4)
    return m


def build_menottes_artisanales():
    m = Model("menottes_atlas")
    # deux colliers serflex noirs (plastique nervure) fermes par leur tete a cliquet, avec la queue qui depasse
    for cx, sgn in ((3.6, -1), (12.4, 1)):
        m.ring_xz(cx, 8.0, 2.9, 0.7, 7.3, 8.9, "rib", name="collier")
        hx = cx + sgn * -2.6 if False else cx - sgn * 2.55      # tete du cote exterieur
        m.box(hx - 0.9, 7.0, 6.9, hx + 0.9, 9.2, 9.1, "black", top="teeth", name="tete")
        tx1, tx2 = (hx - 3.2, hx - 0.9) if sgn > 0 else (hx + 0.9, hx + 3.2)
        m.box(tx1, 7.6, 9.0, tx2, 8.4, 9.6, "rib", name="queue")
    # chaine rouillee
    m.box(6.2, 7.6, 7.55, 7.0, 8.5, 8.45, "rust_dark", name="attache")
    m.box(9.0, 7.6, 7.55, 9.8, 8.5, 8.45, "rust_dark", name="attache")
    m.link_flat(7.1, 8.0, 7.8, "rust", length=2.0, w=1.3, th=0.5)
    m.link_up(8.0, 8.0, 7.45, "chain_black", length=2.0, w=1.4, th=0.5)
    m.link_flat(8.9, 8.0, 7.8, "rust", length=2.0, w=1.3, th=0.5)
    # petit cadenas laiton accroche a la chaine
    m.box(7.2, 5.4, 8.7, 8.8, 7.0, 9.7, "brass", name="cadenas")
    m.box(7.5, 7.0, 9.0, 8.5, 7.8, 9.4, "steel_dark", name="anse")
    return m


def build_cle():
    m = Model("menottes_atlas")
    # anneau de tete (plan x-y), tige, panneton a dents, etiquette bleue
    m.ring_xy(8.0, 12.0, 3.0, 1.1, 7.2, 8.8, "chrome")
    m.box(7.35, 3.0, 7.2, 8.65, 9.2, 8.8, "chrome_hi", name="tige")
    m.box(8.65, 3.0, 7.3, 10.5, 4.2, 8.7, "chrome", name="panneton")
    m.box(8.65, 5.0, 7.3, 9.9, 6.0, 8.7, "chrome", name="panneton")
    m.box(8.65, 7.0, 7.3, 10.0, 7.8, 8.7, "teeth", name="panneton")
    m.box(7.35, 2.0, 7.2, 8.65, 3.0, 8.8, "steel_dark", name="bout")
    m.box(5.6, 8.4, 7.0, 7.2, 10.2, 9.0, "blue", rot=("z", 22.5, (6.4, 9.3, 8)), name="etiquette")
    m.box(5.9, 8.9, 6.9, 6.5, 9.5, 9.1, "white", rot=("z", 22.5, (6.4, 9.3, 8)), name="oeillet")
    return m


def build_sac():
    m = Model("menottes_atlas")
    # corps de sac legerement bombe : trois etages, coutures, fond renforce
    m.box(3.6, 0.4, 4.0, 12.4, 2.0, 12.0, "jute_b", name="fond")
    m.box(3.1, 2.0, 3.5, 12.9, 7.0, 12.5, "jute_a", name="ventre")
    m.box(3.6, 7.0, 4.0, 12.4, 9.0, 12.0, "jute_a", name="epaule")
    m.box(4.8, 9.0, 5.2, 11.2, 10.4, 10.8, "jute_a", name="col")
    # coutures verticales aux angles
    for (x, z) in ((3.0, 3.4), (12.2, 3.4), (3.0, 12.2), (12.2, 12.2)):
        m.box(x, 2.0, z, x + 0.8, 7.0, z + 0.8, "jute_seam", name="couture")
    # cordon de serrage noue et ses deux brins
    m.box(4.5, 9.6, 4.9, 11.5, 10.5, 11.1, "rope", name="cordon")
    m.box(7.2, 8.2, 4.4, 8.2, 9.8, 5.0, "rope", rot=("x", 22.5, (7.7, 9.8, 4.7)), name="brin")
    m.box(8.6, 8.4, 4.4, 9.4, 9.8, 5.0, "rope", rot=("x", -22.5, (9.0, 9.8, 4.7)), name="brin")
    # gueule froncee : quatre plis qui s'evasent
    m.box(5.2, 10.4, 5.4, 10.8, 12.6, 6.6, "jute_b", rot=("x", -22.5, (8, 10.4, 6)), name="pli")
    m.box(5.2, 10.4, 9.4, 10.8, 12.6, 10.6, "jute_b", rot=("x", 22.5, (8, 10.4, 10)), name="pli")
    m.box(5.4, 10.4, 5.2, 6.6, 12.8, 10.8, "jute_a", rot=("z", 22.5, (6, 10.4, 8)), name="pli")
    m.box(9.4, 10.4, 5.2, 10.6, 12.8, 10.8, "jute_a", rot=("z", -22.5, (10, 10.4, 8)), name="pli")
    # pastille de repere sur le ventre (etiquette de sac)
    m.box(6.6, 3.2, 12.5, 9.4, 5.6, 12.7, "white", name="etiquette")
    m.box(7.0, 3.6, 12.7, 9.0, 3.9, 12.8, "red", name="marque")
    m.box(7.0, 4.6, 12.7, 9.0, 4.9, 12.8, "red", name="marque")
    return m


# ----------------------------------------------------------------------------- textures portees (couches sur le joueur)
def tile_texture(size, fn, seed):
    r = random.Random(seed)
    im = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    px = im.load()
    for y in range(size):
        for x in range(size):
            px[x, y] = fn(x, y, r) + (255,)
    return im


def metal_fn(base_light, base_dark):
    def fn(x, y, r):
        k = 0.5 + 0.5 * math.sin(y * 0.9 + x * 0.15)
        return tuple(clamp(a + (b - a) * k + r.randint(-5, 5)) for a, b in zip(base_light, base_dark))
    return fn


def burlap_fn(x, y, r):
    cell = ((x // 2) + (y // 2)) % 2
    base = (186, 146, 86) if cell == 0 else (160, 120, 66)
    if x % 2 == 0 and cell == 0:
        base = (204, 164, 100)
    return tuple(clamp(v + r.randint(-9, 9)) for v in base)


def make_sac_worn():
    im = tile_texture(64, burlap_fn, 7)
    d = ImageDraw.Draw(im)
    px = im.load()
    r = random.Random(3)
    # couture verticale sombre sur le dos (zone "derriere" du cube de 10x10x10 deplie en 0..40 x 0..20)
    for y in range(10, 20):
        for x in (31, 32):
            px[x, y] = (96, 68, 38, 255)
    # cordon de serrage : zones de corde (0,40)..(32,48) et (0,50)..
    for y in range(24, 48):
        for x in range(0, 46):
            if y >= 32 or True:
                c = (222, 196, 140) if (x + y) % 4 < 2 else (170, 142, 94)
                px[x, y] = tuple(clamp(v + r.randint(-6, 6)) for v in c) + (255,)
    # plis sombres sur le haut
    for x in range(10, 20):
        for y in range(0, 10):
            if (x + y) % 5 == 0:
                px[x, y] = (110, 80, 44, 255)
    return im


def write_resources(outdir=None):
    os.makedirs(os.path.join(RES, "textures", "item"), exist_ok=True)
    os.makedirs(os.path.join(RES, "textures", "entity"), exist_ok=True)
    os.makedirs(os.path.join(RES, "models", "item"), exist_ok=True)
    atlas = make_atlas()
    atlas.save(os.path.join(RES, "textures", "item", "menottes_atlas.png"))
    # modeles
    models = {
        "menottes_police": (build_menottes_police(), display_flat((40, 225, 0), 0.95)),
        "menottes_artisanales": (build_menottes_artisanales(), display_flat((40, 225, 0), 0.95)),
        "cle_menottes": (build_cle(), display_upright((20, 215, 0), 1.0)),
        "sac_de_jute": (build_sac(), display_upright((25, 215, 0), 0.9)),
    }
    for name, (mod, disp) in models.items():
        with open(os.path.join(RES, "models", "item", name + ".json"), "w", encoding="utf-8") as f:
            json.dump(mod.json(disp), f, indent=1)
    # textures portees
    tile_texture(32, metal_fn((214, 220, 232), (120, 128, 144)), 1).save(os.path.join(RES, "textures", "entity", "menottes_police.png"))
    def rust_fn(x, y, r):
        c = (150, 78, 38) if r.random() > 0.2 else (60, 52, 48)
        return tuple(clamp(v + r.randint(-12, 12)) for v in c)
    tile_texture(32, rust_fn, 2).save(os.path.join(RES, "textures", "entity", "menottes_artisanales.png"))
    make_sac_worn().save(os.path.join(RES, "textures", "entity", "sac_jute.png"))
    os.makedirs(os.path.join(RES, "textures", "gui"), exist_ok=True)
    tile_texture(64, burlap_fn, 11).save(os.path.join(RES, "textures", "gui", "sac_vue.png"))
    return models, atlas


# ----------------------------------------------------------------------------- apercu logiciel (meme mapping UV que Minecraft)
def rot_matrix(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = (math.cos(math.radians(rx)), math.sin(math.radians(rx)), math.cos(math.radians(ry)), math.sin(math.radians(ry)),
                              math.cos(math.radians(rz)), math.sin(math.radians(rz)))
    X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    mm = lambda a, b: [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mm(Z, mm(Y, X))   # le vecteur subit X puis Y puis Z (rotationXYZ de JOML)


def mul(M, v):
    return [M[0][0] * v[0] + M[0][1] * v[1] + M[0][2] * v[2], M[1][0] * v[0] + M[1][1] * v[1] + M[1][2] * v[2], M[2][0] * v[0] + M[2][1] * v[1] + M[2][2] * v[2]]


FACES = {
    # face : (normale, 4 coins dans l'ordre haut-gauche, haut-droit, bas-droit, bas-gauche vus de l'exterieur) -- conventions de Minecraft
    "north": ((0, 0, -1), lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])]),
    "south": ((0, 0, 1), lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])]),
    "east": ((1, 0, 0), lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])]),
    "west": ((-1, 0, 0), lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])]),
    "up": ((0, 1, 0), lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])]),
    "down": ((0, -1, 0), lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])]),
}


def render_model(model, atlas, rot, scale, size, bg=(52, 56, 76), view_flip=False):
    """Rasterise les elements d'un modele JSON. rot = (rx, ry, rz) du bloc d'affichage (ex. gui : 30, 225, 0)."""
    img = Image.new("RGB", (size, size), bg)
    zbuf = [[-1e9] * size for _ in range(size)]
    px = img.load()
    tex = atlas.convert("RGBA")
    tw, th = tex.size
    tp = tex.load()
    R = rot_matrix(*rot)
    light = (0.35, 0.8, 0.45)
    ln = math.sqrt(sum(c * c for c in light))
    light = tuple(c / ln for c in light)
    for el in model["elements"]:
        a, b = el["from"], el["to"]
        rr = el.get("rotation")
        for fname, fdata in el["faces"].items():
            normal, corners = FACES[fname]
            quad = [list(map(float, c)) for c in corners(a, b)]
            nrm = list(map(float, normal))
            if rr:
                o = rr["origin"]
                ang = rr["angle"]
                ax = {"x": (ang, 0, 0), "y": (0, ang, 0), "z": (0, 0, ang)}[rr["axis"]]
                Mr = rot_matrix(*ax)
                quad = [[o[i] + mul(Mr, [q[0] - o[0], q[1] - o[1], q[2] - o[2]])[i] for i in range(3)] for q in quad]
                nrm = mul(Mr, nrm)
            # centre du modele (8,8,8), puis rotation d'affichage
            quad = [mul(R, [(q[0] - 8) * scale, (q[1] - 8) * scale, (q[2] - 8) * scale]) for q in quad]
            nrm = mul(R, nrm)
            if nrm[2] <= 0:        # face tournee a l'opposé du spectateur (qui regarde depuis +z)
                continue
            shade = 0.45 + 0.55 * max(0, nrm[0] * light[0] + nrm[1] * light[1] + nrm[2] * light[2])
            u1, v1, u2, v2 = fdata["uv"]
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            pts = [((size / 2) + q[0] * size / 18.0, (size / 2) - q[1] * size / 18.0, q[2]) for q in quad]
            for tri in ((0, 1, 2), (0, 2, 3)):
                fill_tri([pts[i] for i in tri], [uvs[i] for i in tri], tp, tw, th, px, zbuf, size, shade)
    return img


def fill_tri(p, uv, tp, tw, th, px, zbuf, size, shade):
    minx = max(0, int(math.floor(min(v[0] for v in p))))
    maxx = min(size - 1, int(math.ceil(max(v[0] for v in p))))
    miny = max(0, int(math.floor(min(v[1] for v in p))))
    maxy = min(size - 1, int(math.ceil(max(v[1] for v in p))))
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = p
    den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(den) < 1e-9:
        return
    for y in range(miny, maxy + 1):
        for x in range(minx, maxx + 1):
            cx, cy = x + 0.5, y + 0.5
            l0 = ((y1 - y2) * (cx - x2) + (x2 - x1) * (cy - y2)) / den
            l1 = ((y2 - y0) * (cx - x2) + (x0 - x2) * (cy - y2)) / den
            l2 = 1 - l0 - l1
            if l0 < -1e-6 or l1 < -1e-6 or l2 < -1e-6:
                continue
            z = l0 * z0 + l1 * z1 + l2 * z2
            if z <= zbuf[y][x]:
                continue
            u = l0 * uv[0][0] + l1 * uv[1][0] + l2 * uv[2][0]
            v = l0 * uv[0][1] + l1 * uv[1][1] + l2 * uv[2][1]
            tx = min(tw - 1, max(0, int(u / 16.0 * tw)))
            ty = min(th - 1, max(0, int(v / 16.0 * th)))
            c = tp[tx, ty]
            if c[3] < 128:
                continue
            zbuf[y][x] = z
            px[x, y] = (clamp(c[0] * shade), clamp(c[1] * shade), clamp(c[2] * shade))


def previews(models, atlas, outdir):
    os.makedirs(outdir, exist_ok=True)
    sheet = Image.new("RGB", (4 * 360, 2 * 360), (30, 32, 44))
    for i, (name, (mod, disp)) in enumerate(models.items()):
        js = mod.json(disp)
        g = disp["gui"]
        a = render_model(js, atlas, g["rotation"], g["scale"][0] * 1.0, 360)
        b = render_model(js, atlas, (60, 200, 0), 1.0, 360)
        sheet.paste(a, (i * 360, 0))
        sheet.paste(b, (i * 360, 360))
        a.save(os.path.join(outdir, name + "_gui.png"))
    sheet.save(os.path.join(outdir, "objets.png"))


# ----------------------------------------------------------------------------- apercu du joueur menotte / encapuchonne (pose + couches)
class Part:
    def __init__(self, name, origin, size, pivot, color_tile):
        self.name, self.origin, self.size, self.pivot, self.tile = name, origin, size, pivot, color_tile


def player_preview(path, yaw, pitch, hooded=True):
    """Squelette de joueur (boites) avec la pose d'une personne menottee (bras derriere le dos) + couches menottes / sac. Echelle : 1 unite modele."""
    # Parametres de pose (identiques a CuffClient.java)
    TH, PH = 0.65, 0.70
    size = 520
    img = Image.new("RGB", (size, size), (52, 56, 76))
    zbuf = [[-1e9] * size for _ in range(size)]
    px = img.load()
    skin = Image.new("RGBA", (16, 16), (214, 160, 120, 255))
    shirt = Image.new("RGBA", (16, 16), (60, 110, 190, 255))
    pants = Image.new("RGBA", (16, 16), (50, 56, 80, 255))
    atlas_t = make_atlas()
    R = rot_matrix(pitch, yaw, 0)
    S = 9.0

    def draw_box(x1, y1, z1, x2, y2, z2, tex, xform, texs=None):
        """xform : fonction point(model) -> point(monde). Les axes du modele : y vers le BAS, z+ = derriere ; on retourne y et z a l'affichage."""
        pts = {}
        for fname, (nrm, corners) in FACES.items():
            q = [list(map(float, c)) for c in corners((x1, y1, z1), (x2, y2, z2))]
            q = [xform(c) for c in q]
            n0 = xform([nrm[0] * 100 + 0.0, nrm[1] * 100, nrm[2] * 100])
            c0 = xform([0, 0, 0])
            n = [n0[i] - c0[i] for i in range(3)]
            qd = [mul(R, [v[0], -v[1], -v[2]]) for v in q]      # y et z du modele -> haut et avant (le joueur regarde vers -z modele = +z ecran apres rotation)
            nn = mul(R, [n[0], -n[1], -n[2]])
            if nn[2] <= 0:
                continue
            ln = math.sqrt(sum(c * c for c in nn)) or 1
            shade = 0.45 + 0.55 * max(0, (nn[0] * 0.35 + nn[1] * 0.8 + nn[2] * 0.45) / ln)
            tw, th = tex.size
            tp = tex.load()
            P = [((size / 2) + v[0] * S, (size / 2) - v[1] * S - 8 * S, v[2]) for v in qd]
            uvs = [(2.0, 2.0), (14.0, 2.0), (14.0, 14.0), (2.0, 14.0)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                fill_tri([P[i] for i in tri], [uvs[i] for i in tri], tp, tw, th, px, zbuf, size, shade)

    def rotx(a):
        c, s = math.cos(a), math.sin(a)
        return lambda v: [v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c]

    def roty(a):
        c, s = math.cos(a), math.sin(a)
        return lambda v: [v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c]

    ident = lambda v: v

    def compose(*fs):
        def f(v):
            for g in reversed(fs):
                v = g(v)
            return v
        return f

    def shift(dx, dy, dz):
        return lambda v: [v[0] + dx, v[1] + dy, v[2] + dz]

    # corps (pivot en haut du torse, y vers le bas ; torse y 0..12)
    body = shift(0, -12, 0)           # on place le torse avec le haut a y = -12 + ... (hauteur totale 32)
    def at(px_, py_, pz_, rot=ident):
        return compose(shift(px_, py_, pz_), rot)
    base_y = 0
    # jambes
    draw_box(-2, 0, -2, 2, 12, 2, pants, compose(shift(-1.9, 12, 0)))
    draw_box(-2, 0, -2, 2, 12, 2, pants, compose(shift(1.9, 12, 0)))
    draw_box(-4, 0, -2, 4, 12, 2, shirt, compose(shift(0, 0, 0)))
    # bras : pivot a l'epaule ; rotation ZYX de vanilla = X d'abord, puis Y (rotation autour de l'axe vertical), puis Z
    def arm_x(sign):
        # sign = -1 bras droit du personnage (x modele -5), +1 bras gauche
        return compose(shift(sign * 5, 2, 0), roty(sign * -PH), rotx(TH))
    for sgn in (-1, 1):
        draw_box(-2 + 0, 0, -2, 2, 12, 2, skin if False else shirt, arm_x(sgn))
        # mains
        draw_box(-2, 9.4, -2, 2, 12, 2, skin, arm_x(sgn))
    # tete
    draw_box(-4, -8, -4, 4, 0, 4, skin, ident)
    # ----- couches : menottes aux poignets + chaine
    for sgn in (-1, 1):
        draw_box(-2.6, 8.4, -2.6, 2.6, 10.2, 2.6, atlas_t.crop((0, 0, 8, 8)).resize((16, 16)), arm_x(sgn))
    draw_box(-1.5, 8.4, 3.6, 1.5, 9.4, 4.6, atlas_t.crop((0, 0, 8, 8)).resize((16, 16)), ident)
    if hooded:
        sac = make_sac_worn()
        draw_box(-5, -9.5, -5, 5, 0.5, 5, sac.crop((0, 0, 40, 20)).resize((16, 16)), ident)
        draw_box(-3, 0, -3, 3, 2, 3, atlas_t.crop((8 * 7, 8 * 1, 8 * 7 + 8, 16)).resize((16, 16)), ident)
    img.save(path)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "apercus")
    models, atlas = write_resources()
    previews(models, atlas, out)
    views = [("joueur_arriere", 180, 8), ("joueur_face", 0, 8), ("joueur_cote", 70, 12)]
    sheet = Image.new("RGB", (520 * 3, 520))
    for i, (n, yw, pt) in enumerate(views):
        player_preview(os.path.join(out, n + ".png"), yw, pt, hooded=True)
        sheet.paste(Image.open(os.path.join(out, n + ".png")), (i * 520, 0))
    sheet.save(os.path.join(out, "joueur.png"))
    print("ok ->", out)


if __name__ == "__main__":
    main()
