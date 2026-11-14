package com.starkinhon.battlegroundsmod.animal.jjmob;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

// The Jujutsu curses' own effects (client/fx/JmFx), sent through SubNet in their own range 480-499 (the next free block
// after the Dragon Ball creatures' 400-419 and the bosses' 420-479, see the faixas memory): what the preview
// (tools/mobs/jjmobs, jjfx.js) draws that no effect of the mod had. The rest of their blows use the shared ones
// (slashes, cracks, dust, blood, the Naruto creatures' stakes, cage, sound rings, crows and screens).
public final class JmNet {
    public static final int FIRST = 480;
    // a curse exorcised: id = the body, a = ticks it takes to come apart into black ash (client/fx/AshDeath style 4)
    public static final int EXORCIZE = 480;
    // a tube of flesh (tongue, cord, hair, silk, root): id = the stream, pos = its root, dir = to its tip; a = thickness,
    // b = look (0 tongue, 1 umbilical cord, 2 wet hair, 3 silk, 4 root, 5 the long arm's skin), c = 1 pulls it back
    public static final int TUBE = 481;
    // something thrown from pos to pos + dir in a ticks, arcing c blocks up: b = what (0 seed, 1 barnacle shell,
    // 2 porcelain plate, 3 lava bomb, 4 shard of the Okiku's plate)
    public static final int THROW = 482;
    // a blue will-o'-wisp: id = the one that made it, pos = where it circles, dir = from there to where it falls;
    // a = ticks it circles, b = which one (0-2, its place on the circle), c = ticks it flies down
    public static final int WISP = 483;
    // a pool of magma on the ground at pos: a = radius, b = ticks it burns
    public static final int MAGMA = 484;
    // a crack of magma running from pos along dir in a ticks, bursting at its end (b = 1 the burst is big)
    public static final int MAGMA_CRACK = 485;
    // a hexagonal basalt column rising at pos: a = height, b = radius, c = ticks it stands before it sinks
    public static final int BASALT = 486;
    // arms of the ground reaching up around pos: a = radius, b = ticks, c = how many
    public static final int HANDS = 487;
    // the masks' scream: a wave of black and purple cursed energy running on the ground from pos along dir (a = width)
    public static final int WAVE = 488;
    // sea water rising around pos up to a blocks over b ticks (the Afogado's hug); c = radius
    public static final int RISING = 489;
    // a jet of water from pos along dir over a ticks: b = 0 the tide (sea water), 1 the vomit (seaweed and fish, a
    // slippery puddle after it, c ticks)
    public static final int JET = 490;
    // a cloud of hot ash rolling from pos along dir: a = radius, b = ticks
    public static final int ASH_CLOUD = 491;
    // a field of flowers opening around pos: a = radius, b = ticks, c = colour (0 cherry, 1 wisteria, 2 maple)
    public static final int FLOWERS = 492;
    // a vine holding a foot at pos for a ticks
    public static final int VINE = 493;
    // the ground bulging and running from pos to pos + dir in a ticks (something burrowing under it)
    public static final int MOUND = 494;
    // paper talismans bursting at pos (the Kitsune's fakes; a = how many)
    public static final int PAPER = 495;
    // the stuffing of a cursed corpse bursting out at pos (the Boneco's death; a = 1 its core rolls out)
    public static final int STUFFING = 496;
    // cursed energy gathering on a blade or a fist: id = the one, pos = the point, a = ticks, b = 1 fire (a fist)
    public static final int ENERGY = 497;
    // a blow landing on a body, the curses' own (Stark 2026-11-14: no neon streaks, the "linhazinha" he hates): no
    // light, only what the blow knocks out of it. pos = where, dir = the blow's way; a = size, b = what flies (0 flesh
    // and cursed black, 1 splinters and stone, 2 water, 3 a rip: blood sprayed along the cut), c = seed
    public static final int IMPACT = 498;
    public static final int FLESH = 0;
    public static final int HARD = 1;
    public static final int WATER = 2;
    public static final int RIP = 3;
    public static final int LAST = 499;

    private JmNet() {
    }

    public static void send(ServerLevel level, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        SubNet.send(level, kind, id, pos, dir, a, b, c);
    }
}
