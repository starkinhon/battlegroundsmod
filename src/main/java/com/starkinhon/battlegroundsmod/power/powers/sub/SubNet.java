package com.starkinhon.battlegroundsmod.power.powers.sub;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// What the SubAlmas' skills tell the clients to draw (client/fx/SubFx), through ModPayloads.SubFx. Kinds marked "every
// tick" fade on the client a few ticks after the server stops sending them; the others play on their own.
public final class SubNet {
    // Byakuya. id = caster, pos = the stream's head, a = how wide, b = how many petals (0..1). Every tick.
    public static final int PETALS = 0;
    // id = its own, pos = center, a = radius, b = how closed (0..1), c = 0 held, 1 crushed (once), 2 gone. Every tick.
    public static final int GOKEI = 1;
    // id = caster, dir = where he faced, a = ticks since it started, b = total ticks, c = 1 when it ends.
    public static final int BANKAI_BYAKUYA = 2;
    // A cut at pos along dir: a = half length, b = width, c = style (STYLE_*).
    public static final int SLASH = 3;
    // Ichigo. id = the crescent's own, pos, dir, a = size, b = 0 Shikai, 1 Kuroi; c = 1 on its last message. Every tick.
    public static final int GETSUGA = 4;
    // id = caster, a = charge 0..1, b = style (0 Getsuga, 1 Kuroi, 2 fire, 3 ice). Every tick while held.
    public static final int CHARGE = 5;
    // Rifts torn in the ground around pos: a = radius, b = how many, dir = the swing.
    public static final int RIFT = 6;
    // id = entity, a = ticks it lasts, b = style (0 Hollow mask, 1 Tensa Zangetsu, 2 Rukia's Bankai, 3 Sukuna), c = 1 stops it.
    public static final int AURA = 7;
    // A flash at pos: a = size, b = color (COLOR_*).
    public static final int IMPACT = 8;
    // Rukia. The white circle at pos (a = radius); the column rises after b ticks.
    public static final int TSUKISHIRO = 9;
    // The avalanche from pos along dir: a = length, b = width.
    public static final int HAKUREN = 10;
    // The ice blade from pos along dir: a = length.
    public static final int SHIRAFUNE = 11;
    // id = entity frozen for a ticks: b = 0 from the feet up, 1 all at once; c = 1 shatters it now.
    public static final int FREEZE = 12;
    // The ground freezing around pos: a = radius, b = ticks it lasts.
    public static final int FROST = 13;
    // Orihime / Urahara. id = owner, pos = center, dir = facing, a = ticks, b = style (0 Santen, 1 Shiten, 2 Urahara),
    // c = size. Sent once (and again with a = 0 to take it down).
    public static final int SHIELD = 14;
    // Something hit a shield at pos.
    public static final int SHIELD_HIT = 15;
    // id = entity under a dome for a ticks (b = radius).
    public static final int DOME = 16;
    // id = its own, pos, dir; c = 1 on its last message. Every tick.
    public static final int KOTEN = 17;
    // Urahara's net. id = its own, pos = center, dir = travel, a = radius, b = 0 flying, 1 wrapped; c = 1 gone. Every tick.
    public static final int NET = 18;
    // A blast at pos: a = radius, b = 0 crimson (Urahara), 1 orange (Fuga).
    public static final int EXPLOSION = 19;
    // id = sealed entity for a ticks (Rikujokoro).
    public static final int SEAL = 20;
    // Sukuna. id = the entity Cleave measures, for a ticks.
    public static final int CLEAVE_SCAN = 21;
    // The Spider Web on the ground at pos: a = radius.
    public static final int WEB = 22;
    // id = caster, pos = the arrow's tip, dir = its way; a = 0 forming in his hands, 1 flying; c = 1 gone. Every tick.
    public static final int FUGA = 23;
    // The Malevolent Shrine: id = caster, pos = center, dir = facing, a = radius, b = ticks it lasts.
    public static final int SHRINE = 24;
    // The World Cut: a giant slit at pos, along dir (the swing), a = its size.
    public static final int WORLD_CUT = 25;
    // A beam of light from pos to pos + dir: a = width, b = color (COLOR_*).
    public static final int BEAM = 26;

    public static final int STYLE_SUKUNA = 0;
    public static final int STYLE_PINK = 1;
    public static final int STYLE_CRIMSON = 2;
    public static final int STYLE_BLUE = 3;
    public static final int STYLE_GOLD = 4;

    public static final int COLOR_BLUE = 0;
    public static final int COLOR_RED = 1;
    public static final int COLOR_PINK = 2;
    public static final int COLOR_GOLD = 3;
    public static final int COLOR_ICE = 4;
    public static final int COLOR_FIRE = 5;
    public static final int COLOR_WHITE = 6;

    private static final double VIEW_DISTANCE = 160;
    private static int nextId = 1;

    private SubNet() {
    }

    public static int newId() {
        return nextId++;
    }

    public static void send(ServerLevel level, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        ModPayloads.SubFx payload = new ModPayloads.SubFx(kind, id, pos, dir, a, b, c);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void send(ServerLevel level, int kind, int id, Vec3 pos, float a, float b) {
        send(level, kind, id, pos, Vec3.ZERO, a, b, 0);
    }
}
