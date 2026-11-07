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

    // ---- Naruto (client/fx/NrFx). "Stream" = sent every tick, c = 1 on the last message. ----
    // The Rasengan (stream): id = its own, pos, a = radius, b = how much (0..1); c = 2 while it forms in the palm (the
    // chakra spiraling in); dir = where it grinds (zero when it doesn't).
    public static final int RASENGAN = 61;
    // A shadow clone's puff of white smoke at pos: a = size.
    public static final int POOF = 62;
    // The wind drill behind someone launched (stream): id = them, pos = their middle, a = how much.
    public static final int DRILL = 63;
    // The Rasenshuriken (stream): id = its own, pos, a = how grown the blades are (0..1), b = their reach.
    public static final int RASENSHURIKEN = 64;
    // Its sphere of wind needles at pos: a = radius, b = ticks it lasts, dir.x = the ground's height.
    public static final int WIND_SPHERE = 65;
    // Nature energy coming from far into id (stream): a = how much.
    public static final int SAGE_GATHER = 66;
    // Sage Mode switching on at pos (the feet): a wave of nature, leaves and dust.
    public static final int SAGE_BURST = 67;
    // Glowing eyes on id for a ticks (a = 0 stops them): b = 0 Sage (orange), 1 Sharingan, 2 Ino's purple, 3 Kakashi's
    // Mangekyo (the left eye only).
    public static final int EYES = 68;
    // Kawazu Kumite: the wave of the punch from pos to pos + dir.
    public static final int KAWAZU = 69;
    // A ball of lightning in the hand (stream): id = its own, pos, a = radius, b = 0 Chidori, 1 Raikiri (paler, finer);
    // dir = a point on the ground the bolts scratch (zero = none).
    public static final int CHIDORI = 70;
    // The Chidori's trail from pos to pos + dir: bolts along it and a burned line on the ground a blocks under it.
    public static final int BOLT_TRAIL = 71;
    // Lightning bursting out of a body hit at pos, going on along dir.
    public static final int PIERCE = 72;
    // One Chidori Senbon needle from pos to pos + dir.
    public static final int SENBON = 73;
    // Sparks running over id, paralyzed, for a ticks.
    public static final int PARALYZE = 74;
    // Storm clouds gathering round pos (high in the sky): a = ticks until the strike, b = radius.
    public static final int STORM = 75;
    // The Kirin: a dragon of lightning from pos (the sky) down to pos + dir.
    public static final int KIRIN = 76;
    // Sasuke's afterimage: from pos (the feet he left) a red streak to pos + dir, a = his height.
    public static final int AFTERIMAGE = 77;
    // Gaara's sand wall rising round pos (his feet) toward dir.
    public static final int SAND_WALL = 78;
    // The Sand Hand (stream): id = its own, pos = its foot on the ground, dir = the way it faces, a = how far out of the
    // ground (0..1), b = how far it tips forward (0..1); c = 2 closed into a fist.
    public static final int SAND_HAND = 79;
    // The Sand Coffin's shell (stream): id = its own, pos = the feet of the one inside, a = how grown (0..1), b = how
    // crushed (0..1), dir.x = how big (their height / 1.8).
    public static final int SAND_SHELL = 80;
    // Sand bursting out of pos: a = size.
    public static final int SAND_BURST = 81;
    // The sand platform under id (stream): pos = under the feet, a = how formed, b = how melted, dir.x = the ground.
    public static final int SAND_PLAT = 82;
    // Sand running along the ground from pos to pos + dir in a ticks (b = 1: gathering in round pos from a dir.length radius).
    public static final int SAND_FLOW = 83;
    // A shadow stretched along the ground (stream): id = its own, pos = from, dir = to - from, a = how far (0..1), b = width.
    public static final int SHADOW = 84;
    // The shadow standing up round id's body (stream): a = how high (0..1).
    public static final int BODY_SHADOW = 85;
    // The shadow hand closing on id's neck (stream): a = ticks since it started closing.
    public static final int NECK_HAND = 86;
    // Kage Nui's shadow spikes round pos (stream): id = its own, a = how grown (0..1), b = seed.
    public static final int SPIKES = 87;
    // A paper bomb stuck in the ground at pos (stream): id = its own.
    public static final int TAG = 88;
    // A kunai thrown from pos to pos + dir in a ticks.
    public static final int KUNAI = 89;
    // An explosion of fire in the Katon's style at pos: a = radius, c = seed, dir.x = the ground's height.
    public static final int FIRE_BOOM = 90;
    // Ino's spirit form (stream): id = its own, pos = its feet, dir = where it faces, a = how visible.
    public static final int SPIRIT = 91;
    // The Shinranshin's purple wave from pos along dir.
    public static final int MIND_WAVE = 92;
    // Purple threads spinning over id's head (confused) for a ticks.
    public static final int CONFUSED = 93;
    // A purple thread (stream): id = its own, from pos to pos + dir, a = how far it reached (0..1), b = how visible.
    public static final int THREAD = 94;
    // A pulse running along a thread from pos to pos + dir.
    public static final int PULSE = 95;
    // The lightning hound (stream): id = its own, pos = its head, dir = the way it runs.
    public static final int RAIJU = 96;
    // Kamui's spiral (stream): id = its own, pos = center, dir = its axis (toward Kakashi), a = how open (0..1).
    public static final int KAMUI = 97;
    // Screen effects for id only (the local player): a = preset (DbFx.screen).
    public static final int SELF_SCREEN = 98;
    // Dust thrown up from the ground at pos (something slammed into it): a = size.
    public static final int DUST = 99;

    // ---------- Jujutsu (power/powers/sub/jj, client/fx/JjFx) ----------
    // The Divergent Fist's aura (stream): id = the entity, a = how strong (0..1), b = 0 the right fist, 1 both; it lags
    // behind the fist in echoes when the fist moves.
    public static final int DIV_AURA = 100;
    // The Divergent Fist: id = Itadori, pos = where it landed, dir = the punch; the second impact comes a ticks later.
    public static final int DIVERGENT = 101;
    // The Black Flash's ring closing on the fist (stream): id = Itadori, pos = the fist, a = how closed (0..1).
    public static final int BF_RING = 102;
    // The Manji Kick's guard (stream): id = Itadori, a = how up (0..1).
    public static final int GUARD = 103;
    // A burst of cursed energy (ink tongues opening out) at pos along dir: a = size, b = color (0 cyan, 1 gold).
    public static final int INK_BURST = 104;
    // A swing's arc at pos (the middle of it), facing dir: a = radius, b = color (Db.*), c = 1 a vertical swing.
    public static final int ARC = 105;
    // Cracks on the ground at pos: a = radius, b = how many, c = 1 with rubble and dust.
    public static final int CRACKS = 106;
    // Speed lines behind a dashing entity (stream): id = the entity, a = color (0 cyan, 1 red, 2 air).
    public static final int DASH_TRAIL = 107;
    // Choso. The ball of blood between his palms (stream): id = Choso, pos = between the hands, a = radius, b = how
    // compressed (0..1).
    public static final int BLOOD_BALL = 110;
    // The Piercing Blood: the line from pos to pos + dir.
    public static final int BLOOD_BEAM = 111;
    // Supernova's orbs (stream): id = Choso, pos = his middle, a = which orbs are up (bits), b = the angle of the first.
    public static final int BLOOD_ORBS = 112;
    // An orb took a hit and burst at pos.
    public static final int ORB_POP = 113;
    // The orbs burst into needles: pos = center, a = which orbs (bits), b = the angle of the first.
    public static final int SUPERNOVA_BURST = 114;
    // The blood rope (stream): id = its own, pos = the hand, dir = to the end, a = 0 flying, 1 wrapped round the end,
    // b = the bound one's height.
    public static final int ROPE = 115;
    // Red Scale: id = the entity, a = ticks it lasts (0 stops it).
    public static final int SCALE = 116;
    // Blood splashing out of pos along dir: a = size.
    public static final int BLOOD_SPLASH = 117;
    // Nanami. The 7:3 lines over the target: id = the target, a = ticks (0 takes them off).
    public static final int RATIO = 120;
    // The critical hit on the 7:3 point: pos, dir = the blow.
    public static final int RATIO_CRIT = 121;
    // The 7:3 point on the ground glowing where the fist will land (stream): id = Nanami, pos = the point, a = how lit.
    public static final int GROUND_MARK = 122;
    // The fist in the ground: pos = the point, a = how far the shockwave runs.
    public static final int SLAM = 123;
    // Overtime's gold energy: id = the entity, a = ticks (0 stops it).
    public static final int GOLD_AURA = 124;
    // Nobara. A nail (stream): id = its own, pos = its tip, dir = where it points, a = 0 flying, 1 stuck.
    public static final int NAIL = 130;
    // Resonance's spike of energy at pos along dir: a = size.
    public static final int RESONANCE = 131;
    // The straw doll in her left hand (stream): id = Nobara, a = 1 once the nail is hammered in.
    public static final int DOLL = 132;
    // The thread of Resonance from pos to pos + dir.
    public static final int RESO_LINE = 133;
    // Todo. A clap: id = who claps, pos = the hands, a = 1 a real one (the swap), 0 the fake.
    public static final int CLAP = 140;
    // A swap: the flash where someone was (pos = feet).
    public static final int SWAP = 141;
    // Inumaki. Rings of voice from pos to pos + dir: a = size, b = 0 violet, 1 lilac.
    public static final int VOICE = 150;
    // The seal marks lit on his face: id = Inumaki, a = ticks.
    public static final int SEAL_MARKS = 151;
    // Rings round someone stopped by "Stop": id = the target, a = ticks.
    public static final int HOLD_RINGS = 152;
    // "Get crushed": the unseen weight coming down on pos: a = radius.
    public static final int CRUSH = 153;
    // Coughing blood: id = Inumaki, a = ticks.
    public static final int COUGH = 154;
    // Asleep: id = the target, a = ticks (0 wakes).
    public static final int SLEEP = 155;
    // Miwa. The light blue line of a cut at pos along dir: a = half length, b = width.
    public static final int MIWA_LINE = 160;
    // Simple Domain's circle (stream): id = Miwa, pos = center, a = how open (0..1), b = radius.
    public static final int SIMPLE_DOMAIN = 161;
    // The draw's sweep round her: pos = her middle, dir = forward, a = radius.
    public static final int MIWA_ARC = 162;
    // The streak of the Line Cut from pos to pos + dir.
    public static final int DASH_LINE = 163;
    // Simple Domain undone: specks rising from the circle at pos, a = radius.
    public static final int DOMAIN_POP = 164;

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
