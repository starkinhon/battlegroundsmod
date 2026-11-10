"""Namek's buildings (Stark, 2026-11-10): Freeza's ship landed with his soldiers' pods around it, the Capsule Corp ship
that landed there, a Namekian village and Guru's house on its mesa.

python3 tools/structures/namek.py [preview_dir] [names...]
-> dragonball/<name>.nbt; origin (0,0,0) = the center of the floor (first air above the ground)."""
import math, os, sys, random
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
import vox
from vox import Vox
from dragonball import OUT
from dragonball2 import solid, carve, ladder, door, trapdoor

PREV = sys.argv[1] if len(sys.argv) > 1 else os.path.join(OUT, '..', '..', '..', '..', '..', '..', 'build', 'previews')
vox.COLORS.update({'purple_stained_glass': (127, 63, 178), 'magenta_stained_glass': (178, 76, 216),
                   'battlegroundsmod:namek_sand': (212, 186, 140), 'namek_sand': (212, 186, 140),
                   'orange_terracotta': (161, 83, 37), 'brown_terracotta': (77, 51, 35), 'iron_bars': (150, 150, 150)})


def pod(v, cx, cz, rnd, sunk=0.6):
    """A soldier's attack pod: white ball, the round purple window, half sunk in a little crater."""
    for x in range(-3, 4):
        for z in range(-3, 4):
            if x * x + z * z <= 9 + rnd.random() * 3:
                v.set(cx + x, -1, cz + z, 'battlegroundsmod:namek_sand' if rnd.random() < 0.7 else 'coarse_dirt')
    cy = 1.0 - sunk
    for x in range(-2, 3):
        for y in range(-1, 4):
            for z in range(-2, 3):
                if x * x + (y - cy) ** 2 + z * z <= 2.1 ** 2 and y >= 0:
                    v.set(cx + x, y, cz + z, 'white_concrete')
    fx = 1 if rnd.random() < 0.5 else -1
    v.set(cx + 2 * fx, 1, cz, 'purple_stained_glass'); v.set(cx + 2 * fx, 1, cz + 1, 'purple_stained_glass')
    v.set(cx + 2 * fx, 2, cz, 'purple_stained_glass')
    v.set(cx, 0, cz + 2, 'black_concrete'); v.set(cx, 0, cz - 2, 'black_concrete')


def freeza_ship():
    """Freeza's ship (refs): a wide flat saucer. Gray top with the round opening in the middle, two bands of orange-brown
    ribbed panels, the purple glass dome on the front, the black belt with dark-blue orbs, the cream-yellow ovals all
    round it, white claw legs down to the ground. Inside: Freeza's hall with the hover-throne. Pods all around."""
    v = Vox(); rnd = random.Random(1990)
    B = 4                                                            # bottom of the hull above the ground
    R = 18
    # hull: underside bowl, belt, flat dome
    def hull(X, Y, Z):
        r = np.hypot(X, Z)
        under = (Y >= B) & (Y < B + 3) & (r <= R - 3 + (Y - B) * 1.2)
        belt = (Y >= B + 3) & (Y <= B + 6) & (r <= R + 0.5 - np.abs(Y - (B + 4.5)) * 0.4)
        top = (Y > B + 6) & ((X / R) ** 2 + (Z / R) ** 2 + ((Y - B - 6) / 8.0) ** 2 <= 1)
        return under | belt | top
    def col(x, y, z, s):
        if not s:
            return 'smooth_stone'
        r = math.hypot(x, z); a = math.atan2(z, x)
        if B + 3 <= y <= B + 6:
            return 'black_concrete'
        if y < B + 3:
            return 'light_gray_concrete'
        # the top: two orange-brown ribbed bands (gray gaps at front and back), gray elsewhere
        if 9.5 < r < 16.6 and y <= B + 11:
            gap = min(abs(a), abs(abs(a) - math.pi)) < 0.45
            if not gap:
                return 'brown_terracotta' if int(a * 40) % 2 else 'orange_terracotta'
        if 16.6 <= r or (9.0 < r <= 9.5):
            return 'white_concrete'
        return 'light_gray_concrete'
    solid(v, hull, (-R - 2, R + 2, B, B + 15, -R - 2, R + 2), col, fill=True)
    # the hall inside, open to the round hatch on top
    carve(v, lambda X, Y, Z: (np.hypot(X, Z) <= R - 4) & (Y >= B + 1) & (Y <= B + 9), (-R, R, B + 1, B + 9, -R, R))
    carve(v, lambda X, Y, Z: (np.hypot(X, Z) <= 4.5) & (Y >= B + 9), (-5, 5, B + 9, B + 16, -5, 5))
    for x in range(-6, 7):
        for z in range(-6, 7):
            d = math.hypot(x, z)
            if 4.5 < d <= 5.6:
                top = max([y for y in range(B, B + 16) if v.get(x, y, z) not in (None, 'minecraft:air')] or [B + 12])
                v.set(x, top, z, 'white_concrete')                         # rim of the hatch
    for x in range(-(R - 4), R - 3):
        for z in range(-(R - 4), R - 3):
            d = math.hypot(x, z)
            if d <= R - 4:
                v.set(x, B, z, 'purple_concrete' if d < 3 else 'gray_concrete' if (int(d) % 4) else 'light_gray_concrete')
    # Freeza's hover-throne in the middle, consoles around, lights
    v.set(0, B + 1, 0, 'white_concrete'); v.set(0, B + 2, 0, 'quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    v.set(-1, B + 2, 0, 'purple_concrete'); v.set(-1, B + 3, 0, 'purple_concrete'); v.set(0, B + 2, 1, 'white_concrete'); v.set(0, B + 2, -1, 'white_concrete')
    for k in range(8):
        a = k * math.pi / 4
        x, z = round(11 * math.cos(a)), round(11 * math.sin(a))
        v.set(x, B + 1, z, 'cyan_terracotta'); v.set(x, B + 2, z, 'lime_stained_glass')
        v.set(round(8 * math.cos(a + 0.4)), B + 9, round(8 * math.sin(a + 0.4)), 'sea_lantern')
    # purple glass dome on the front (+x)
    for x in range(-4, 5):
        for y in range(-4, 5):
            for z in range(-4, 5):
                if x * x + y * y + z * z <= 3.3 ** 2:
                    p = (13 + x, B + 9 + y, z)
                    if v.get(*p) not in (None, 'minecraft:air') or (x > 0 and y >= 0):
                        v.set(*p, 'purple_stained_glass' if x * x + y * y + z * z > 1.5 else 'magenta_stained_glass')
    # orbs on the belt and the cream ovals under it
    for k in range(20):
        a = k * 2 * math.pi / 20
        rr = R + 0.6
        x, z = round(rr * math.cos(a)), round(rr * math.sin(a))
        v.set(x, B + 5, z, 'blue_concrete' if k % 2 else 'light_blue_stained_glass')
    for k in range(14):
        a = (k + 0.5) * 2 * math.pi / 14
        for dy, w in ((2, 0.0), (3, 0.6), (4, 0.6), (5, 0.0)):
            for t in (-w, 0, w):
                rr = R + 1.4
                x, z = round(rr * math.cos(a + t / rr)), round(rr * math.sin(a + t / rr))
                v.set(x, B - 1 + dy, z, 'yellow_concrete' if dy in (3, 4) and t == 0 else 'white_terracotta')
    # white claw legs from the belt down to the ground
    for k in range(10):
        a = k * 2 * math.pi / 10
        for i in range(B + 3):
            rr = R - 1 + i * 0.5
            x, z = round(rr * math.cos(a)), round(rr * math.sin(a))
            v.set(x, B + 2 - i, z, 'white_concrete' if i < B + 2 else 'light_gray_concrete')
    # door and ramp on the front-left (-z)
    for y in range(B + 1, B + 4):
        for x in (-1, 0, 1):
            for z in range(-R - 2, -R + 5):
                v.set(x, y, z, 'air')
    for i in range(B):
        for x in (-1, 0, 1):
            v.set(x, B - 1 - i, -R - 1 - i, 'smooth_stone_slab[type=top,waterlogged=false]' if i else 'light_gray_concrete')
            v.set(x, B - 1 - i, -R - 1 - i, 'light_gray_concrete')
    # soldiers' pods scattered round the clearing
    for k in range(7):
        a = k * 2 * math.pi / 7 + rnd.random() * 0.4
        d = 25 + rnd.random() * 6
        pod(v, round(d * math.cos(a)), round(d * math.sin(a)), rnd)
    return v


def namek_capsule():
    """The Capsule Corp ship landed on Namek: white ball, black cap and black belt with round portholes, four legs with
    round feet, the door with its ramp; inside, the gravity machine."""
    v = Vox(); rnd = random.Random(7)
    cy, R = 9, 6.4
    def ball(X, Y, Z):
        return X ** 2 + (Y - cy) ** 2 + Z ** 2 <= R ** 2
    def col(x, y, z, s):
        yy = y - cy
        if yy >= 4:
            return 'black_concrete'
        if -1 <= yy <= 1:
            a = math.atan2(z, x)
            return 'light_blue_stained_glass' if yy == 0 and (a * 6 / math.pi) % 2 < 0.7 else 'black_concrete'
        return 'white_concrete'
    solid(v, ball, (-7, 7, cy - 7, cy + 7, -7, 7), col, air_inside=True)
    v.disc(0, cy - 4, 0, 4.2, 'light_gray_concrete')
    for y in range(cy - 3, cy + 3):
        v.disc(0, y, 0, 0.8, 'white_concrete' if y != cy else 'sea_lantern')
    v.set(0, cy - 3, 2, 'red_concrete'); v.set(-3, cy - 3, -1, 'white_bed[facing=north,occupied=false,part=foot]')
    v.set(-3, cy - 3, -2, 'white_bed[facing=north,occupied=false,part=head]')
    for lx, lz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        for i in range(5):
            v.set(round(lx * (4 + i * 0.6)), cy - 5 - i if cy - 5 - i >= 0 else 0, round(lz * (4 + i * 0.6)), 'light_gray_concrete')
        fx, fz = round(lx * 6.8), round(lz * 6.8)
        v.disc(fx, 0, fz, 1.3, 'light_blue_concrete')
    # door (+z) and ramp
    for y in range(cy - 3, cy):
        for x in (-1, 0, 1):
            for z in range(4, 8):
                v.set(x, y, z, 'air')
    for i in range(6):
        for x in (-1, 0, 1):
            v.set(x, cy - 4 - i, 6 + i, 'light_gray_concrete')
    return v


def namek_house(v, cx, cz, r, h, rnd, door_dir):
    def dome(X, Y, Z):
        return ((X - cx) / r) ** 2 + ((Z - cz) / r) ** 2 + (Y / h) ** 2 <= 1
    solid(v, lambda X, Y, Z: dome(X, Y, Z) & (Y >= 0), (cx - r - 1, cx + r + 1, 0, h + 1, cz - r - 1, cz + r + 1),
          lambda x, y, z, s: 'white_concrete' if y > 0 else 'smooth_quartz', air_inside=True)
    v.disc(cx, -1, cz, r - 0.5, 'smooth_quartz')
    # round windows round the dome
    for k in range(6):
        a = k * math.pi / 3 + 0.3
        wx, wz = cx + (r - 0.3) * math.cos(a), cz + (r - 0.3) * math.sin(a)
        for x in range(round(wx) - 1, round(wx) + 2):
            for z in range(round(wz) - 1, round(wz) + 2):
                if v.get(x, 2, z) == 'minecraft:white_concrete':
                    v.set(x, 2, z, 'light_blue_stained_glass')
    # the little dome on top
    v.disc(cx, h, cz, 1.6, 'white_concrete'); v.set(cx, h + 1, cz, 'white_concrete')
    dx, dz = door_dir
    for y in (0, 1, 2):
        for k in range(r - 2, r + 2):
            x, z = cx + dx * k, cz + dz * k
            if y < 2 or k >= r - 1:
                v.set(x, y, z, 'air')
    v.set(cx, 0, cz, 'red_carpet') if rnd.random() < 0.5 else v.set(cx + 1, 0, cz, 'white_carpet')
    v.set(cx, h - 2, cz, 'lantern[hanging=true,waterlogged=false]')


def namek_village():
    v = Vox(); rnd = random.Random(42)
    spots = [(0, 0, 6, 7), (14, 6, 4, 5), (-13, 8, 5, 6), (8, -15, 4, 5), (-10, -12, 4, 5), (20, -8, 3, 4), (-22, -2, 3, 4)]
    for cx, cz, r, h in spots:
        d = math.hypot(cx, cz) or 1
        namek_house(v, cx, cz, r, h, rnd, (0, 1) if (cx, cz) == (0, 0) else (round(-cx / d), round(-cz / d)) if abs(cx) > abs(cz) or True else (0, 1))
    for x in range(-24, 25):
        for z in range(-24, 25):
            if v.get(x, -1, z) is None and (abs(x) <= 1 or abs(z) <= 1) and math.hypot(x, z) < 22:
                v.set(x, -1, z, 'battlegroundsmod:namek_sand')
    return v


def guru_house():
    """Guru's house on top of its mesa: a tall white dome on a round base with a ring of round windows, two small domes
    beside it; inside, the Grand Elder's huge seat."""
    v = Vox(); rnd = random.Random(3)
    def main(X, Y, Z):
        r = np.hypot(X, Z)
        base = (Y >= 0) & (Y <= 4) & (r <= 8)
        dome = (Y > 4) & ((X / 8.0) ** 2 + (Z / 8.0) ** 2 + ((Y - 4) / 9.0) ** 2 <= 1)
        return base | dome
    solid(v, main, (-9, 9, 0, 14, -9, 9), lambda x, y, z, s: 'white_concrete', air_inside=True)
    v.disc(0, -1, 0, 8, 'smooth_quartz')
    for k in range(10):
        a = k * math.pi / 5
        x, z = round(8 * math.cos(a)), round(8 * math.sin(a))
        v.set(x, 3, z, 'light_blue_stained_glass')
    for sx in (-1, 1):
        namek_house(v, sx * 12, 3, 3, 4, rnd, (0, 1))
    v.disc(0, 14, 0, 1.4, 'white_concrete'); v.set(0, 15, 0, 'white_concrete')
    for y in range(0, 3):
        for x in (-1, 0, 1):
            for z in range(6, 10):
                v.set(x, y, z, 'air')
    # the Grand Elder's seat
    v.box(-2, 0, -5, 2, 0, -3, 'purple_wool')
    v.box(-2, 1, -6, 2, 3, -6, 'white_concrete'); v.box(-3, 1, -5, -3, 2, -3, 'white_concrete'); v.box(3, 1, -5, 3, 2, -3, 'white_concrete')
    v.set(0, 10, 0, 'lantern[hanging=true,waterlogged=false]')
    return v


BUILDINGS = {'freeza_ship': freeza_ship, 'namek_capsule': namek_capsule, 'namek_village': namek_village,
             'guru_house': guru_house}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)
    only = sys.argv[2:] or list(BUILDINGS)
    for name in only:
        v = BUILDINGS[name]()
        size, origin = v.save_nbt(os.path.join(OUT, name + '.nbt'))
        for a in (0, 2):
            v.render(os.path.join(PREV, f'{name}_{a}.png'), scale=7, angle=a, title=f'{name} {size}')
        print(name, 'size', size, 'origin', origin, 'blocks', len(v.b))
