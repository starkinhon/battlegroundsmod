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
    // id = entity frozen (IceShell): pos = where the ice starts (the feet, or the point it spreads from), dir.x = ticks
    // to cover the body, dir.y = how far it reaches in blocks for a 1.8-tall body (0 = its height), a = ticks it lasts,
    // b = 0 from the feet up, 1 out from pos; c = 0 starts it, 1 shatters it now, 2 thaws it now.
    public static final int FREEZE = 12;
    // The ground freezing around pos: a = radius, b = ticks it lasts.
    public static final int FROST = 13;
    // Urahara's shield. id = owner, pos = center, dir = facing, a = ticks, b = style (2, the only one left),
    // c = size. Sent once (and again with a = 0 to take it down).
    public static final int SHIELD = 14;
    // Something hit a shield at pos.
    public static final int SHIELD_HIT = 15;
    // 16 and 17 were Orihime's (removed from the game); left unused.
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
    // Rukia's Bankai armor on id (the program over time is Rukia.BANKAI_*); c = 1 stops it early (thaws).
    public static final int ICE_ARMOR = 27;
    // A crystal of ice standing at pos toward dir: a = height, b = ticks it stays.
    public static final int ICE_SPIKE = 28;
    // A Getsuga's hit: a rip along the cut at pos, dir = where it flew, a = how wide, b = 1 Kuroi, c = 1 on the ground.
    public static final int RIP = 29;

    // ---- Dragon Ball (client/fx/DbFx; colors are Db.*). "Stream" = sent every tick, c = 1 on the last message. ----
    // A ki beam (stream): id = its own, pos = from, dir = to - from, a = width, b = color.
    public static final int KI_BEAM = 30;
    // A ki ball (stream): id = its own, pos, a = radius, b = color + 16 * style (0 plain, 1 charging with sparks, 2 with a
    // trail behind it).
    public static final int KI_BALL = 31;
    // A ki blast: pos, dir.x = the ground's height, a = radius, b = color, c = seed.
    public static final int KI_BOOM = 32;
    // The ki aura on id (mod style, fire of glow): a = ticks, b = color, c = size, dir.x = strength; a = 0 stops it.
    public static final int KI_AURA = 33;
    // The saw disc (stream): id = its own, pos, dir = its normal, a = radius, b = color.
    public static final int SAW = 34;
    // The Genki Dama (stream): id = its own, pos, a = radius, b = 1 while the light still gathers, dir = Goku's feet.
    public static final int GENKI = 35;
    // Its blast: pos, dir.x = the ground's height, a = size.
    public static final int GENKI_BOOM = 36;
    // The Death Ball (stream): id = its own, pos, a = radius.
    public static final int DEATH_BALL = 37;
    // Its blast (the Supernova): pos, dir.x = the ground's height, a = size.
    public static final int SUPERNOVA = 38;
    // The Big Bang ball made of tongues (stream): id = its own, pos, a = radius, b = how much (0..1).
    public static final int BIG_BANG = 39;
    // Its blast of tongues: pos, dir.x = the ground's height, a = radius.
    public static final int BIG_BANG_BOOM = 40;
    // A hit flash (the Visualizador's nrHit): pos, a = size, b = color, c = seed.
    public static final int HIT = 41;
    // The Death Beam: from pos to pos + dir.
    public static final int DEATH_BEAM = 42;
    // The Makankosappo: from pos to pos + dir, a = ticks it stays.
    public static final int MAKANKO = 43;
    // Bolts crackling (stream): id = its own, pos, a = how much (0..1), b = color, dir = the feet when they come off the
    // ground round a body (Final Flash), else zero (round a point, the Makankosappo's fingers).
    public static final int SPARKS = 44;
    // Telekinesis (stream): id = the one held, dir = Freeza's hand, a = how much (0..1).
    public static final int TELEKINESIS = 45;
    // The Taiyoken's flash from pos (the head) toward dir.
    public static final int SOLAR = 46;
    // Dizzy stars round id's head for a ticks; id = the local player also whitens the screen.
    public static final int DIZZY = 47;
    // The Mafuba's gale (stream): pos = the hands, dir = where it goes (the target's middle), a = how much.
    public static final int GALE = 48;
    // The Mafuba's green spiral round a target (stream): id = its own, pos = center, a = radius, b = how much.
    public static final int SPIRAL = 49;
    // The Mafuba's tornado (stream): id = its own, pos = its foot, a = height, b = radius.
    public static final int TORNADO = 50;
    // The Mafuba's jar at pos (id = its own): c = 0 open, waiting; c = 1 sealed for a ticks, then it breaks (b = 1) or
    // stays shut and fades (b = 0).
    public static final int JAR = 51;
    // A streak of speed from pos to pos + dir: a = width, b = color, c = ticks.
    public static final int STREAK = 52;
    // Piccolo's arm stretched from id's shoulder to pos (stream).
    public static final int ARM = 53;
    // The Kakusandan: the ball splits at pos and rains on dir (the middle of the area, on the ground), a = area,
    // b = ticks the rain spreads over, c = seed (Db.rainSpot / rainTime).
    public static final int RAIN = 54;
    // One of Vegeta's ki shots: from pos to pos + dir in a ticks, b = color; it bursts there.
    public static final int SHOT = 55;
    // Smoke piling up round id (stream): a = how much.
    public static final int SMOKE_COVER = 56;
    // Goku's Instant Transmission: a streak of light and a ripple at pos.
    public static final int VANISH = 57;
    // Max Power: the ground cracks and the shirt bursts at pos (the feet).
    public static final int FLEX = 58;
    // Screen effects for whoever is close to pos: a = preset (DbFx.screen).
    public static final int SCREEN = 59;
    // A scar torn in the ground from pos along dir: a = width, b = ticks it stays.
    public static final int SCAR = 60;

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
