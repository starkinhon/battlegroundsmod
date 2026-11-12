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
    // A shadow stretched along the ground (stream): id = its own, pos = from, dir = to - from, a = how far it ran (0..1), then
    // 1..2 = the blot that catches at the end growing; b = width (negative: his own shadow stretched by the flare). c = 1
    // with dir zero ends it where it was.
    public static final int SHADOW = 84;
    // The shadow arm climbing round id's body in a spiral (stream): a = how high (0..1).
    public static final int BODY_SHADOW = 85;
    // The shadow hand closing on id's neck (stream): a = ticks since it started closing.
    public static final int NECK_HAND = 86;
    // A Kage Nui needle (stream): id = its own, pos = the target's feet, dir = toward his shadow, a = how grown (0..1), b = seed.
    public static final int SPIKES = 87;
    // A paper bomb at pos (stream): id = its own, a = size (1 normal), b = how far the seal has burnt (0..1).
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
    // The cursed energy on the fists (stream): id = the entity, a = how strong (0..1), b = 0 the right fist, 1 both, 2 the
    // whole body (the Divergent delay). Drawn as the approved shell aura, in blue.
    public static final int DIV_AURA = 100;
    // The Divergent Fist: id = Itadori, pos = where it landed, dir = the punch; the second impact comes a ticks later
    // (b = 1: only the second impact, the stored delay going off; c = height above the ground).
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
    // Itadori. The Divergent Fist's delay stored on someone (stream): id = the target, a = how strong (0..1).
    public static final int DIV_MARK = 108;
    // A "cut here" loop of the Corte de Tesoura (stream): id = the mark, pos = its middle, dir.x = radius, dir.y = 1 on an
    // entity (dir.z = its id, the loops follow the body), a = how far the scissors have closed (0..1).
    public static final int CUT_LOOP = 109;
    // The scissors snapped shut at pos: a = radius, b = 1 on an entity (blood), 0 on a block.
    public static final int CUT_SNAP = 118;
    // Golpe na Alma: the soul knocked out of someone and sealed (timed): id = the target, a = ticks.
    public static final int SOUL = 119;
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
    // Choso (kit of 2026-11-11). A pool of his blood on the ground (timed): id = the pool, pos = on the ground, a = ticks,
    // b = radius.
    public static final int BLOOD_POOL = 125;
    // A sustained Piercing Blood that sweeps (stream): id = the beam, pos = between his palms, dir = to its end, a = width.
    public static final int BLOOD_SWEEP = 126;
    // The Perfurante Maximo's charge (stream): id = Choso, a = how far (0..1): his blood leaves the body in drops that
    // spiral into the ball between his palms.
    public static final int MAX_CHARGE = 127;
    // Blood spikes bursting out of the ground at pos: a = radius, b = how many, c = how tall.
    public static final int GROUND_SPIKES = 128;
    // The Blood Meteorite in flight (stream): id = the meteorite, pos = where it is, a = radius.
    public static final int METEOR = 129;
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
    // Inumaki. The voice: ONE cone of ink rings from pos to pos + dir (forja/inumaki-kit VOICE_F, souls_ki kind 17): id = the
    // speaker, a = the cone's radius at the end, b = style (Inumaki.V_*), c = the word written in the air (Inumaki.W_*, 0 none).
    public static final int VOICE = 150;
    // The seal marks lit on his face: id = Inumaki, a = ticks.
    public static final int SEAL_MARKS = 151;
    // The ink rings closing on someone stopped by "Stop" (souls_ki kind 18): id = the target, a = ticks.
    public static final int HOLD_RINGS = 152;
    // "Get crushed": the ground giving way at pos: a = radius, b = 1 a critical (he was frozen).
    public static final int CRUSH = 153;
    // Coughing blood: id = Inumaki, a = ticks.
    public static final int COUGH = 154;
    // Asleep: id = the target, a = ticks (0 wakes).
    public static final int SLEEP = 155;
    // Miwa. The light blue line of a cut at pos along dir: a = half length, b = width.
    public static final int MIWA_LINE = 160;
    // Simple Domain's circle (stream, kinds 25 and 26): id = its own, pos = center, a = how open (0..1), b = radius,
    // dir = (x, flashes so far, z): the last cut's direction and a count, so each new cut lights the circle where it was.
    public static final int SIMPLE_DOMAIN = 161;
    // The draw's sweep round her: pos = her middle, dir = forward, a = radius.
    public static final int MIWA_ARC = 162;
    // The streak of the Line Cut from pos to pos + dir.
    public static final int DASH_LINE = 163;
    // Simple Domain undone: specks rising from the circle at pos, a = radius.
    public static final int DOMAIN_POP = 164;
    // Sasuke's Katon ball (stream): id = its own, pos = its center, dir = where its tail runs (unit), a = radius,
    // b = the tail's length (in radii), c = 1 ends it. Drawn by NrFx with the Naruto effects.
    public static final int KATON = 165;
    // The Katon's blast at pos (on the ground): a = radius, dir.x = the ground's height, c = seed.
    public static final int KATON_BOOM = 166;
    // Shikamaru's Xeque-mate: the shadow flooding round pos (stream): id = its own, a = radius, c = 1 ends it.
    public static final int SHADOW_SEA = 167;
    // Shikamaru's signal flare (stream): pos = the light, a = how bright, b = 1 while it still climbs, c = 1 ends it.
    public static final int FLARE = 168;
    // Naruto's Henge Armadilha: a = 0 the rock (a clone waiting), 1 the Fuuma Shuriken (dir = where it flies); stream
    public static final int HENGE = 240;
    // 180-199 belong to the Naruto kits (Naruto, Kakashi, Gaara): NrFx draws them. The Jujutsu kits use 169-176, other
    // threads 200-239; keep new kinds out of those ranges.
    public static final int NARUTO_MIN = 240, NARUTO_MAX = 259;
    // Kakashi's Perfect Susanoo (stream): id = caster, pos = its feet, dir = (yaw in radians, arms forward 0..1, throw 0..1),
    // a = how far it has risen (0..1.15), b = opacity; c = 1 gone.
    public static final int KK_SUSANOO = 241;
    // A Kamui shuriken (stream): id = its own, pos, dir = the way it flies (zero while held), a = size in blocks, b = opacity.
    public static final int KK_SHURIKEN = 242;
    // The Kamui swallowing a ball of space (stream): id = its own, pos = center, dir = axis, a = radius in blocks,
    // b = how far it has swallowed (0..1), c = 1 gone. Big or small (X Kamui, the shuriken's hit, Shift+X round himself).
    public static final int KK_VORTEX = 243;
    // The ninken seal on the ground (stream): pos, a = opacity, b = 1 it lit up (dogs out); c = 1 gone.
    public static final int KK_SEAL = 244;
    // The eight ninken holding a target (stream): id = the target, pos = its feet, a = ticks since they came out of the
    // ground (from the seal at dir), c = 1 they vanish in smoke.
    public static final int KK_DOGS = 245;
    // Sennen Goroshi: the calligraphy title on the screens of who sees it, and the target turned into a star (pos) when it
    // is high up: a = 0 the title, 1 the star.
    public static final int KK_SENNEN = 246;
    // The Raiju hound (stream): id = its own, pos = its chest on the ground, dir = where it runs, a = gait phase,
    // b = jaw open 0..1; c = 1 gone.
    public static final int KK_HOUND = 247;
    // Gaara's sand hand (stream): id = its own, pos = the ground under the wrist, dir = (lean, curl 0..1, wrist height in
    // page units), a = yaw (atan2 of the way it faces), b = how far it has crumbled (0..1), c = -(rise + 1) while it lives
    // (rise 0..1), 1 gone.
    public static final int GA_HAND = 248;
    // The sand coffin (stream): id = the target, pos = its feet, dir = (scale, squeeze 0..1, -), a = how far the sand has
    // risen (0..1), b = how far it has crumbled; c = 1 gone (dir.y > 0.5: it burst, fades fast).
    public static final int GA_COFFIN = 249;
    // A stream of sand from A (pos) to B (pos + dir): id = its own, a = how far it has run (0..1), b = crumbled, c = radius in
    // blocks (< 1) or 1 gone.
    public static final int GA_STREAM = 250;
    // The flying sand platform (stream): id = the rider, pos = its feet, dir = (yaw, pitch, bank) in radians, a = how far it
    // has formed (0..1), b = crumbled; c = 1 gone. The speed comes from how it moves.
    public static final int GA_PLAT = 251;
    // Quicksand on the ground (stream): id = its own, pos = center, dir.x = radius in blocks, a = opacity, b = hot 0..1
    // (someone sinking); c = 1 gone.
    public static final int GA_QUICK = 252;
    // Sabaku Taiso, the wave of sand (stream): id = its own, pos = center, dir = (radius in page units, crumbled, squash in
    // y), a = how far it has grown, b = how far it has curled over (radians); c = 1 gone.
    public static final int GA_WAVE = 253;
    // Loose sand grains (shot): pos, dir = direction times strength (blocks/s), a = count, b = cone (mode 0) or ring radius
    // in blocks (mode 1), c = mode.
    public static final int GA_GRAINS = 254;

    // Saitama (Cara de um Soco), drawn by client/fx/OpmFx. Each serious move is one message at its start: the client plays
    // the whole approved page from it (pos = his feet, dir = forward, a = seed), in step with the server's ticks.
    public static final int OPM_SOCO = 200;
    // The Saltos: pos = the target's feet, b = the target's id (the hops go round it).
    public static final int OPM_SALTOS = 201;
    // The Mesa: pos = his feet, dir = forward (one of the four sides), a = seed, c = 1: the ground really turns over.
    public static final int OPM_MESA = 202;
    public static final int OPM_ESPIRRO = 203;
    // Socos Consecutivos: id = Saitama, a = the target's id (-1 none).
    public static final int OPM_SOCOS = 204;
    // A small hit flash at pos: a = size, b = 1 with a little shock wave, 2 with a short impact frame too.
    public static final int OPM_FLASH = 205;
    // The dust ring on the ground at pos: a = radius, b = seconds.
    public static final int OPM_RING = 206;
    // Someone thrown by him: id = them, pos = where from. Gone far over the horizon, a star twinkles where they went.
    public static final int OPM_LAUNCH = 207;
    // Modo Serio on (a = 1, b = ticks) or off (a = 0): id = Saitama.
    public static final int OPM_SERIO = 208;
    // His words over his head: id = Saitama, a = which (0 "ok.", 1 "Ha...", 2 "Ha... ha...!", 3 "ATCHIM!", 4 "... foi mal.").
    public static final int OPM_SAY = 209;
    // Small craters of the Saltos (the dent's wave on the ground): pos, a = strength.
    public static final int OPM_DENT = 210;
    public static final int OPM_LAST = 219;
    // Luffy (client/fx/LuffyFx). The charge of a held key: id, a = key (0 Z, 1 X, 2 C, 3 V), b = gear (0 base .. 4 G5),
    // c = 0 started, 1 went up a gear, 2 over (released or broken).
    public static final int LUFFY_CHARGE = 220;
    // A move goes off: id, pos = his feet, dir = toward the target, a = key, b = gear, c = the target's id (-1 none).
    public static final int LUFFY_FIRE = 221;
    // Gear 5 (the Tambores da Libertacao): id, a = 1 on / 0 off, b = ticks left; a = 2 the hunger after it (b ticks).
    public static final int LUFFY_G5 = 222;
    // A crater the blow opened: pos, a = radius, b = how many pieces fly, c = 0 earth, 1 rubber (bouncing).
    public static final int LUFFY_DENT = 223;
    // The ground turns to rubber (G5): pos, a = strength.
    public static final int LUFFY_RUBBER = 224;
    // How he looks after a move: id, a = 1 tiny (G3's cost) / 2 tired (G4's), b = ticks.
    public static final int LUFFY_AFTER = 225;
    public static final int LUFFY_LAST = 239;

    // Todo's Black Flash Garantido. TODO_FLAME (stream): id = Todo, a = 0..1 how strong, b = 1 while he dashes (the flames
    // stretch back; the orange fire behind him only on the wind-up). BF_SPARKS (shot): pos = contact, dir = the blow, a = count:
    // short zigzags of black lightning with a red edge flying out.
    public static final int TODO_FLAME = 169;
    public static final int BF_SPARKS = 170;
    // Inumaki's megaphone in his hand (stream): id = Inumaki, a = 1 while held, c = 1 put away.
    public static final int MEGAPHONE = 171;
    // Nobara, remade (forja/nobara-kit, souls_ki kinds 19-22). The Hairpin's burst: ONE sphere that bursts into spikes of
    // black ink with a blue rim (kind 21): a = radius, b = life in seconds, c = 1 the red ink of the ult.
    public static final int NB_BURST = 172;
    // Resonance's barbed thorns tearing out of a body at pos (kind 20): dir = away from her, a = power, id = seed.
    public static final int THORNS = 173;
    // The ult's red thread (stream): id = the one tied, pos = her palm, dir = to the nail in them, a = hits come back so far.
    public static final int NB_LINK = 174;
    // The hammer in her right hand (stream): id = Nobara, a = 1 once it swings through.
    public static final int HAMMER = 175;
    // The nail in her own palm during the ult (stream): id = Nobara, pos = the palm.
    public static final int PALM_NAIL = 176;
    // Miwa, remade (forja/miwa-kit, Realista+Anime, souls_ki kinds 23-27). The draw's arc: ONE thin crescent round pos
    // (kind 23): dir = the middle of the sweep, a = radius, b = arc (radians), c = width (sign = which way it sweeps),
    // id = style (0 a draw, 1 the ult's full ring, 2 the ult's inner ring).
    public static final int MW_SLASH = 177;
    // The cut that appears on a body a moment later (kind 24): id = the one cut, dir = (angle, height, 1 front / -1 back),
    // a = how wide it opens, b = life in seconds, c = when it opens (seconds).
    public static final int MW_CUT = 178;
    // The ult's mark (stream): the faint line across someone inside Lua Partida, still uncut. id = the one marked.
    public static final int MW_MARK = 179;
    // The Line Cut's trail from pos to pos + dir (kind 27): a = half height, b = life in seconds.
    public static final int MW_STREAK = 180;
    // The Line Cut's slit in the ground from pos to pos + dir (kind 24 lying down): a = life in seconds.
    public static final int MW_GASH = 181;
    // The scabbard's click (a four-point glint at pos): a = size.
    public static final int MW_GLINT = 182;

    // ---- Bleach remake (client/fx/BleachFx: the approved pages forja/bleach, timelines run on the client) ----
    public static final int BL_FIRST = 280;
    // Rukia's Tsukishiro: the circle at pos (ground), a = radius; the column rises 0.4 s later.
    public static final int BL_TSUKI = 280;
    // Rukia's Hakuren: pos = her feet, dir = where she faces.
    public static final int BL_HAKUREN = 281;
    // Rukia's Shirafune: id = Rukia, pos = the blade's base, dir = the lance's way, a = where it stops, b = 0 pierced
    // someone there, 1 stuck in the ground there (the stake, 8 s), 2 nothing in reach.
    public static final int BL_SHIRAFUNE = 282;
    // Rukia's Rikujokoro on id (pos = its feet).
    public static final int BL_RIKU = 283;
    // Rukia's Soren Sokatsui: id = Rukia; c = 0 the chant starts, 1 fired at pos (the target), -1 called off.
    public static final int BL_SOREN = 284;
    // Rukia's Bankai: id = Rukia, pos = her feet; c = 0 starts, 1 ends (she thaws).
    public static final int BL_BANKAI = 285;
    // Ice over a body (id): c = 0 freezes from pos (dir.x = how much of the body per second, a = height for a body 2 tall,
    // b = mode (0 feet up, 1 out from pos) + 2 * style), 1 shatters (b = style), 2 thaws.
    public static final int BL_ICE = 286;
    // The Bankai's ice flower on id (pos = its feet): frozen, the flower opens, all shatters 2.15 s later.
    public static final int BL_FLOWER = 287;
    // Byakuya's Senbonzakura: id = Byakuya; c = 0 the river starts from his hand (it follows his aim), 1 let go (it comes back).
    public static final int BL_SENBON = 290;
    // A petal's cut: a short pink spark at pos.
    public static final int BL_CUT = 291;
    // Gokei: id = its own, pos = the sphere's center; c = 0 closing (held), 1 let go (it crushes).
    public static final int BL_GOKEI = 292;
    // Senka: id = Byakuya, pos = where he was (the afterimage), a = the target's id.
    public static final int BL_SENKA = 293;
    // Byakurai: id = Byakuya, pos = where the bolt lands.
    public static final int BL_BYAKURAI = 294;
    // Danku: id = Byakuya; c = 0 the wall rises (pos = his feet, dir = his facing), 1 something hit it at pos, 2 gone.
    public static final int BL_DANKU = 295;
    // Bankai Senbonzakura Kageyoshi: id = Byakuya, pos = his feet; c = 0 starts, 1 ends.
    public static final int BL_KAGEYOSHI = 296;
    // Senkei: id = Byakuya, pos = the target's feet, a = its id.
    public static final int BL_SENKEI = 297;
    // Shukei Hakuteiken: id = Byakuya, a = the target's id.
    public static final int BL_HAKUTEI = 298;
    public static final int BL_LAST = 319;
    public static final int ICE_TSUKI = 0, ICE_HAKUREN = 1, ICE_SHIRA = 2, ICE_TRAP = 3, ICE_FLOWER = 4;

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
