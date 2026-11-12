"""Texturas dos itens e blocos das estruturas de Naruto (Stark 2026-11-12, world/NarutoSites).

Pixel art 16x16 desenhada aqui (nada copiado): guizo, pergaminhos do Céu e da Terra, pele de cobra, pergaminho de
marionete, Kubikiribōchō, asas de papel, bolsa de veneno, célula do Hashirama, pergaminho do sapo, Sharingan; e os
blocos selo de papel e fio de chakra. Rodar da raiz do repositório: python3 tools/items/naruto_loot_items.py
"""
import math
import os
import random

from PIL import Image

ROOT = 'src/main/resources/assets/battlegroundsmod/textures'


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)


class Canvas:
    def __init__(self):
        self.im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        self.px = self.im.load()

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = c

    def get(self, x, y):
        return self.px[x, y] if 0 <= x < 16 and 0 <= y < 16 else (0, 0, 0, 0)

    def outline(self, c):
        # contorno escuro em volta do que foi desenhado (estilo dos itens do Mine)
        pts = [(x, y) for x in range(16) for y in range(16) if self.px[x, y][3] == 0 and any(
            self.get(x + dx, y + dy)[3] > 0 and self.get(x + dx, y + dy) != c for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for x, y in pts:
            self.px[x, y] = c

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.im.save(path)


def scroll(cap, cap_dark, mark):
    # pergaminho enrolado, na diagonal: papel creme, as pontas coloridas, o símbolo no meio
    c = Canvas()
    paper, paper_d = hexc('#efe4c4'), hexc('#cdbf98')
    for i in range(-1, 12):
        cx, cy = 3 + i, 12 - i
        for w in range(-2, 3):
            x, y = cx + (w if w > 0 else 0), cy + (w if w < 0 else 0)
            x, y = cx + w // 2, cy + w - w // 2
            col = paper if w < 1 else paper_d
            if i < 1 or i > 9:
                col = cap if w < 1 else cap_dark
            c.put(x, y, col)
    for (x, y) in mark:
        c.put(x, y, hexc('#2a2220'))
    c.outline(hexc('#2b2016'))
    return c


def main():
    rnd = random.Random(7)
    items = os.path.join(ROOT, 'item')

    # Guizo: o sininho dourado do Teste dos Guizos, com o cordão vermelho
    c = Canvas()
    gold, gold_d, gold_l = hexc('#e8b93a'), hexc('#a8761a'), hexc('#fff0a0')
    for y in range(5, 15):
        for x in range(3, 13):
            d = math.hypot(x + 0.5 - 8, (y + 0.5 - 10) * 1.05)
            if d < 5.0:
                k = 1.15 - (x - 3) * 0.05 - (y - 5) * 0.03
                c.put(x, y, shade(gold, k))
    for x in range(4, 12):
        c.put(x, 11, gold_d)
    c.put(7, 12, hexc('#3a2a10')); c.put(8, 12, hexc('#3a2a10')); c.put(7, 13, hexc('#3a2a10')); c.put(8, 13, hexc('#3a2a10'))
    c.put(5, 7, gold_l); c.put(6, 6, gold_l); c.put(5, 8, gold_l)
    for (x, y) in ((7, 4), (8, 4), (6, 3), (9, 3), (6, 2), (9, 2), (7, 1), (8, 1)):
        c.put(x, y, hexc('#c0302a'))
    c.outline(hexc('#3b2a0a'))
    c.save(os.path.join(items, 'guizo.png'))

    # Pergaminhos do Céu (pontas azuis, 天) e da Terra (pontas ocre, 地)
    ceu = [(7, 7), (8, 6), (6, 8), (8, 8), (9, 7), (7, 9)]
    terra = [(6, 7), (7, 7), (8, 7), (7, 8), (8, 9), (9, 8)]
    scroll(hexc('#5b8fd8'), hexc('#2f5a9a'), ceu).save(os.path.join(items, 'pergaminho_ceu.png'))
    scroll(hexc('#c98a3a'), hexc('#7a5020'), terra).save(os.path.join(items, 'pergaminho_terra.png'))
    # Pergaminho de Marionete (pontas roxas, a cruz dos fios) e do Sapo (pontas laranja e vermelhas, grande)
    scroll(hexc('#8a4fa0'), hexc('#4e2a60'), [(7, 7), (8, 8), (6, 8), (8, 6), (7, 9), (9, 7)]).save(os.path.join(items, 'pergaminho_marionete.png'))
    s = scroll(hexc('#e07a2a'), hexc('#a03a20'), [(6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 9), (6, 9), (9, 9)])
    s.save(os.path.join(items, 'pergaminho_sapo.png'))

    # Pele de Cobra Gigante: a pele clara enrolada, com as escamas
    c = Canvas()
    skin, skin_d = hexc('#ddd2a6'), hexc('#a99c70')
    for t in range(0, 70):
        a = t * 0.16
        r = 1.5 + t * 0.075
        x, y = 8 + math.cos(a) * r, 8 + math.sin(a) * r * 0.85
        for dx in (0, 1):
            xx, yy = int(x) + dx, int(y)
            c.put(xx, yy, skin_d if (xx + yy) % 3 == 0 else skin)
    c.outline(hexc('#4a4028'))
    c.save(os.path.join(items, 'pele_cobra.png'))

    # Kubikiribōchō: a lâmina larga com o furo redondo perto da ponta e a meia-lua no dorso, o cabo enrolado
    # (na diagonal, como as armas do Mine: cabo embaixo à esquerda, ponta em cima à direita)
    c = Canvas()
    steel, steel_d, steel_l = hexc('#c3ccd2'), hexc('#7c868d'), hexc('#eef3f6')
    wrap, wrap_l = hexc('#2b2b33'), hexc('#55556a')
    r2 = math.sqrt(2)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - 4.5, y + 0.5 - 11.5
            u = (px - py) / r2          # ao longo da lâmina
            v = (-px - py) / r2         # pro lado do dorso
            if 0 <= u <= 13.2 and -0.6 <= v <= 3.6:
                hole = math.hypot(u - 8.6, v - 1.5) < 0.95
                moon = math.hypot(u - 11.6, v - 4.2) < 1.6
                if hole or moon:
                    continue
                col = steel_d if v < 0.3 else steel_l if v > 2.9 else steel
                c.put(x, y, col)
    for i in range(0, 4):
        x, y = 3 - i, 12 + i
        c.put(x, y, wrap if i % 2 == 0 else wrap_l)
        c.put(x + 1, y, wrap_l if i % 2 == 0 else wrap)
    c.outline(hexc('#1d2226'))
    c.save(os.path.join(items, 'kubikiribocho.png'))

    # Asas de Papel: as duas asas de papel dobrado da Konan
    c = Canvas()
    pw, pw_d, pw_s = hexc('#f5f0e2'), hexc('#d6cfba'), hexc('#b8b09a')
    for side in (-1, 1):
        for y in range(2, 14):
            span = int(6 - abs(y - 6) * 0.55) if y < 10 else int(4 - (y - 10))
            for k in range(0, max(0, span)):
                x = 8 + side * (1 + k) - (1 if side < 0 else 0)
                fold = (k + y) % 4 == 0
                c.put(x, y, pw_s if fold else (pw if k < span - 1 else pw_d))
    c.put(7, 6, hexc('#3a6ea5')); c.put(8, 6, hexc('#3a6ea5'))
    c.outline(hexc('#5a5444'))
    c.save(os.path.join(items, 'asas_de_papel.png'))

    # Bolsa de Veneno da Salamandra: o saco roxo-esverdeado com o veneno pingando
    c = Canvas()
    sac, sac_d, sac_l = hexc('#6b4a8a'), hexc('#3e2856'), hexc('#a07fc0')
    for y in range(4, 14):
        for x in range(3, 13):
            d = math.hypot(x + 0.5 - 8, (y + 0.5 - 9.5) * 1.1)
            if d < 4.8:
                c.put(x, y, sac_l if d < 1.6 and x < 8 and y < 9 else sac if d < 3.8 else sac_d)
    for (x, y) in ((7, 3), (8, 3), (7, 2), (8, 2), (8, 1)):
        c.put(x, y, hexc('#c8b070'))
    for (x, y) in ((10, 14), (10, 15), (6, 14)):
        c.put(x, y, hexc('#8fe04a'))
    c.put(6, 7, hexc('#8fe04a')); c.put(9, 11, hexc('#8fe04a'))
    c.outline(hexc('#20142e'))
    c.save(os.path.join(items, 'bolsa_veneno.png'))

    # Célula do Hashirama: um frasco com o líquido verde e o broto de madeira saindo
    c = Canvas()
    glass, glass_d = hexc('#cfe8ee', 200), hexc('#8fb5c0', 220)
    liquid, liquid_l = hexc('#4fae4a'), hexc('#9fe08a')
    for y in range(6, 15):
        for x in range(5, 11):
            edge = x in (5, 10) or y == 14
            c.put(x, y, glass_d if edge else (liquid if y > 8 else glass))
    c.put(6, 10, liquid_l); c.put(6, 11, liquid_l)
    for (x, y) in ((6, 5), (9, 5), (6, 4), (7, 4), (8, 4), (9, 4)):
        c.put(x, y, hexc('#8a6a40'))
    for (x, y) in ((8, 3), (8, 2), (9, 1), (7, 2), (6, 1), (10, 2)):
        c.put(x, y, hexc('#5aa040') if y < 3 else hexc('#7a5a30'))
    c.outline(hexc('#1e3a28'))
    c.save(os.path.join(items, 'celula_hashirama.png'))

    # Sharingan: o olho vermelho com a pupila e as três tomoe
    c = Canvas()
    red, red_d, red_l = hexc('#d22a22'), hexc('#7a0e0e'), hexc('#ff6a50')
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 7.2:
                c.put(x, y, red_d if d > 6.2 else (red_l if d < 2.2 and x < 8 and y < 8 else red))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 3.6 < d < 4.4:
                c.put(x, y, shade(red_d, 0.9))
            if d < 1.6:
                c.put(x, y, hexc('#111111'))
    for k in range(3):
        a = k * 2 * math.pi / 3 - math.pi / 2
        tx, ty = 8 + math.cos(a) * 4, 8 + math.sin(a) * 4
        for (dx, dy) in ((0, 0), (-0.6, 0), (0, -0.6), (-0.6, -0.6)):
            c.put(int(tx + dx), int(ty + dy), hexc('#111111'))
        c.put(int(tx + math.cos(a + 1.6) * 1.4), int(ty + math.sin(a + 1.6) * 1.4), hexc('#111111'))
    c.outline(hexc('#2a0606'))
    c.save(os.path.join(items, 'sharingan.png'))

    # Bloco: selo de papel (uma tira de papel com a borda vermelha e o kanji)
    blocks = os.path.join(ROOT, 'block')
    c = Canvas()
    for y in range(1, 15):
        for x in range(5, 11):
            border = x in (5, 10) or y in (1, 14)
            noise = rnd.random() < 0.15
            c.put(x, y, hexc('#b8302a') if border else (hexc('#e6dcc0') if noise else hexc('#f1e8cf')))
    for (x, y) in ((7, 3), (8, 3), (7, 4), (6, 5), (7, 5), (8, 5), (9, 5), (7, 6), (7, 7), (8, 7), (6, 8), (8, 8),
                   (7, 9), (7, 10), (6, 11), (8, 11), (9, 12), (7, 12)):
        c.put(x, y, hexc('#231c1a'))
    c.save(os.path.join(blocks, 'selo_de_papel.png'))

    # Bloco: fio de chakra (o mesmo desenho da corda, azul e brilhando)
    src = Image.open(os.path.join(blocks, 'corda.png')).convert('RGBA')
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255
            op[x, y] = (int(80 + lum * 120), int(190 + lum * 60), 255, a)
    out.save(os.path.join(blocks, 'fio_de_chakra.png'))


if __name__ == '__main__':
    main()
