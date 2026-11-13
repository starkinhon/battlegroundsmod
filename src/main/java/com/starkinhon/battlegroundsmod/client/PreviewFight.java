package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.client.fx.PreviewFx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.universe.Flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The combat flight IS the approved page (forja db-voo-mobs/combate.html, prévia v2: "insanamente perfeito"), run on the
// client frame by frame. Stark 2026-11-13, after two ports that copied the numbers onto the server's AI: "parece uma
// versão muito inferior à prévia... prefiro muito mais jogar a prévia" and "copia exatamente a prévia". The server moved
// the soul 20 times a second with lag, and the punches went there and back; the page runs everything at 60 frames with
// nothing in between. So here, while the player flies locked (K) on a flying soul, the page's own code runs: the player
// (playerCombat, combatMove, doDash, doStrong, doDodge, doPunch, the barrage, the guard), the soul's AI (mobUpdate,
// airTactics, the exchange, the clash), the ki balls, the beam, the hitstop, the shake, the camera (placeCam, FOV 70) and
// the page's effects (client/fx/PreviewFx). The server only puts the soul where the page has it (AirSim) and lands the
// damage (AirSimHit), see combat/AirFight.sim. The page's ground (y 0) is the ground under each one here.
// Keys like the page: 1 Arrancada, 2 Forte, 3 held Rajada (the hotbar's numbers do not change the slot meanwhile, Stark
// 2026-11-13), the click punches, Ctrl dodges, F guards (the page's E opens the inventory here), W/S/A/D around the target.
public final class PreviewFight {
    private static final Random R = new Random();

    private static double rnd() {
        return R.nextDouble();
    }

    private static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double damp(double k, double dt) {
        return 1 - Math.exp(-k * dt);
    }

    private static double angDiff(double a, double b) {
        return Math.atan2(Math.sin(b - a), Math.cos(b - a));
    }

    private static Vec3 v(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    private static Vec3 norm(Vec3 a) {
        double l = a.length();
        return l < 1.0E-9 ? Vec3.ZERO : a.scale(1 / l);
    }

    private static Vec3 clampLength(Vec3 a, double max) {
        double l = a.length();
        return l > max ? a.scale(max / l) : a;
    }

    private static double angle(Vec3 a, Vec3 b) {
        double d = Math.sqrt(a.lengthSqr() * b.lengthSqr());
        return d < 1.0E-12 ? Math.PI / 2 : Math.acos(clamp(a.dot(b) / d, -1, 1));
    }

    private static Vec3 flat(Vec3 a) {
        return new Vec3(a.x, 0, a.z);
    }

    // ------------------------------------------------------------------ the page's entities

    static final class E {
        LivingEntity ent;
        Vec3 pos = Vec3.ZERO, vel = Vec3.ZERO;
        double yaw, boostAmt, stun, tumble;
        Double faceYaw;
        boolean flying = true, boost, slam, combat, visible = true;
        float[] col, blast;
        String anim, name;
        double animT, animLen;
        Hit pending;
    }

    static final class Hit {
        double t, knock, reach = 2.5, stun, dmg;
        boolean big, slam, finisher, beam, melee, ball;
        String punish;
    }

    static final class PAct {
        String k;
        double t, dT, hitAt;
        boolean clash, dodge, dodged, gone, out, hit, g2;
        Vec3 dir = Vec3.ZERO;
    }

    static final class Blast {
        final PreviewFx.Ball ball = new PreviewFx.Ball();
        Vec3 vel;
        E owner, target;
        boolean homing, noHit, reflected;
        double speed, turn, t;
        float[] col;
    }

    static final class BeamS {
        final PreviewFx.Beam b = new PreviewFx.Beam();
        Vec3 from, dir;
        double len, r = 1.15, t;
        E owner;
        boolean hit, dusted, told;
    }

    static final class Ex {
        double t, next = 0.02, hideAt = 9;
        int n, total;
        Vec3 center, camDir;
    }

    private static final float[] AURA_P = {1, 0.78f, 0.45f};
    private static final float[] BLAST_P = {1, 0.6f, 0.25f};

    private static float[] aura(String name) {
        return switch (name) {
            case "goku" -> new float[] {0.86f, 0.94f, 1};
            case "vegeta" -> new float[] {0.7f, 0.85f, 1};
            case "piccolo" -> new float[] {0.9f, 1, 0.9f};
            case "kuririn" -> new float[] {1, 0.97f, 0.88f};
            case "freeza" -> new float[] {0.95f, 0.75f, 1};
            case "bills" -> new float[] {0.8f, 0.62f, 1};
            default -> new float[] {0.86f, 0.94f, 1};
        };
    }

    private static float[] blastCol(String name) {
        return switch (name) {
            case "goku" -> new float[] {0.55f, 0.8f, 1};
            case "vegeta" -> new float[] {1, 0.86f, 0.35f};
            case "piccolo", "kuririn" -> name.equals("piccolo") ? new float[] {1, 0.92f, 0.35f} : new float[] {1, 0.85f, 0.4f};
            case "freeza" -> new float[] {0.95f, 0.35f, 0.85f};
            case "bills" -> new float[] {0.75f, 0.45f, 1};
            default -> new float[] {1, 0.86f, 0.35f};
        };
    }

    private static E P, M;
    private static boolean running;
    private static double clock, hitstop, shake, exEnd = -9;
    private static Ex EX;
    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final List<BeamS> BEAMS = new ArrayList<>();
    private static long lastFrame;
    private static double sendAcc;
    // after the player came down (landed, slammed): no restart until it is up in the air again
    private static boolean parked;
    // a skill has the mob (Flight.HELD): the page follows it
    private static boolean held;
    // where the page put the player last frame: anything else that moves it (a skill's teleport from the server) wins
    private static Vec3 placed;
    // the server has the player (a real stun, a hold, a skill's teleport): until when (System.nanoTime)
    private static long pHeldUntil;
    // this frame: the server has the player (pHeldUntil, a skill driving them, a stun)
    private static boolean pHeld;
    // a cutscene (a boss's, a skill's, the soul ceremony) has the camera: the page lets go until it ends
    private static boolean scene;
    // another player already flies this soul's page (multiplayer): not this one until then (System.nanoTime)
    private static final java.util.Map<Integer, Long> DENIED = new java.util.HashMap<>();

    // the soul's AI (the page's AI object)
    private static String st = "ar", act, phase;
    private static double aiT, at, cd, airT, orbit, minD, d0, startT, lastHit;
    private static int hits, shots, combo;
    private static Vec3 aim;
    private static boolean hitDone;
    private static PreviewFx.Ball charge;

    // the player (the page's P fields)
    private static PAct pact;
    private static int pCombo, punchN, barN, dodgeSide = 1;
    private static double comboT, lungeT, iframe, dodgeT = -9, barT, guardStart, lockD = -1;
    private static boolean guard, guardWant, guardShown;
    private static Hit pPending;

    // input queued between frames
    private static boolean qPunch, qDash, qStrong, qDodge;

    // the camera
    private static Vec3 camPos, camLook;
    private static double fov = 70;

    private PreviewFight() {
    }

    public static boolean running() {
        return running;
    }

    // ------------------------------------------------------------------ hooks

    // The punch key (PunchInput): the page's doPunch.
    public static boolean punch() {
        if (!running) {
            return false;
        }
        qPunch = true;
        return true;
    }

    // The base attack key (BaseAttacks.use): Dash Attack, Forte (the Barragem is held, see barrageHeld).
    public static boolean baseAttack(int selected) {
        if (!running) {
            return false;
        }
        if (selected == 0) {
            qDash = true;
        } else if (selected == 1) {
            qStrong = true;
        }
        return true;
    }

    // Thrown by the server while the page runs (AirCombat.knock: a skill's push, a soul technique's blast): off this way
    // inside the page, a short stun like the page's own blows; the slam goes down and lands like the Martelo.
    static void knock(Vec3 velocity, int ticks, boolean slammed) {
        if (!running || P == null) {
            return;
        }
        if (pact != null) {
            endPAct();
        }
        P.vel = velocity;
        P.flying = true;
        if (slammed) {
            P.slam = true;
            P.stun = 2;
            P.tumble = 0.5;
        } else {
            P.stun = Math.max(P.stun, Math.min(0.8, ticks * 0.05));
            P.tumble = velocity.length() > 20 ? 0.4 : P.tumble;
        }
        play(P, "apanha", Math.min(0.5, P.stun + 0.1));
    }

    // One of the player's own skills is moving them (FlightClient.serverMotion: a dash, a launch): the page lets the
    // skill have the player and follows it, like after a teleport.
    static void driven() {
        if (running) {
            pHeldUntil = Math.max(pHeldUntil, System.nanoTime() + 250_000_000L);
        }
    }

    // Ctrl tapped in the flight (FlightClient): the page's doDodge.
    static void dodgeKey() {
        qDodge = true;
    }

    // The hotbar's numbers turn into the moves while it runs (Stark 2026-11-13): 1 dash, 2 strong, 3 held barrage.
    public static void onKey(InputEvent.Key event) {
        if (!running || event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        var slots = mc.options.keyHotbarSlots;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].matches(event.getKey(), event.getScanCode())) {
                while (slots[i].consumeClick()) {
                    // eaten: the slot stays
                }
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    if (i == 0) {
                        qDash = true;
                    } else if (i == 1) {
                        qStrong = true;
                    }
                }
            }
        }
    }

    // Every client tick, before Minecraft reads the hotbar keys: clicks that came some other way (a held key repeating, the
    // test scripts' keys) are eaten too.
    public static void tickPre(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        if (!running) {
            return;
        }
        var slots = Minecraft.getInstance().options.keyHotbarSlots;
        for (int i = 0; i < slots.length; i++) {
            while (slots[i].consumeClick()) {
                if (i == 0) {
                    qDash = true;
                } else if (i == 1) {
                    qStrong = true;
                }
            }
        }
    }

    private static boolean barrageHeld(Minecraft mc) {
        return mc.screen == null && (mc.options.keyHotbarSlots[2].isDown() || Keybinds.USE_ATTACK.isDown() && BaseAttacks.selectedIndex() == 2);
    }

    // The flight's state for the poses (FlightClient.state): the page's say for both while it runs, -1 otherwise.
    static int stateOf(net.minecraft.world.entity.Entity e) {
        if (!running) {
            return -1;
        }
        E x = P != null && e == P.ent ? P : M != null && e == M.ent ? M : null;
        if (x == null) {
            return -1;
        }
        return x.flying ? Flight.FLYING | (x.combat || x == M ? Flight.COMBAT : 0) | (x.boost ? Flight.BOOST : 0) : 0;
    }

    static Vec3 playerVelocity() {
        return running ? P.vel : null;
    }

    static float playerBoost() {
        return running ? (float) P.boostAmt : 0;
    }

    public static float fov() {
        return running ? (float) fov : 0;
    }

    // The page's camera for CameraMixin (through AirFightClient.shot), or null.
    static FaceCutscene.Shot shot() {
        if (!running || camPos == null || camLook == null) {
            return null;
        }
        Vec3 c = camPos;
        if (shake > 0) {
            double s = shake * shake * 0.35;
            c = c.add((rnd() - 0.5) * s, (rnd() - 0.5) * s, (rnd() - 0.5) * s);
        }
        Vec3 d = camLook.subtract(c);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        if (d.lengthSqr() < 1.0E-4) {
            return null;
        }
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(c, yaw, pitch);
    }

    // ------------------------------------------------------------------ start, stop

    private static double ground(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return -64;
        }
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
    }

    // Some ground under it (not the void of the End, a domain's empty space, below the world): the page needs a floor.
    private static boolean hasGround(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && ground(p) > mc.level.getMinBuildHeight() + 1;
    }

    // Whether any cutscene has the camera now (the same list as CameraMixin's, the HUD's hiding).
    private static boolean cutscene(Minecraft mc) {
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        return com.starkinhon.battlegroundsmod.client.fx.DuelScene.cutscene() || com.starkinhon.battlegroundsmod.client.fx.ShenlongScene.cutscene()
                || com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene.cutscene() || com.starkinhon.battlegroundsmod.client.cutscene.CutscenePlayer.active()
                || BossCutscene.active() || HakaiCutscene.active() || EsferaCutscene.active() || GrabCutscene.active() || BopCutscene.active()
                || ShoulderCutscene.active() || FaceCutscene.shot(partial) != null || CutsceneHud.hidden();
    }

    static boolean scene() {
        return scene;
    }

    // The server said another player flies this soul's page (AirFight.MOVE_DENY): leave it to them for a while.
    static void deny(int entityId) {
        DENIED.put(entityId, System.nanoTime() + 3_000_000_000L);
        if (running && M != null && M.ent.getId() == entityId) {
            stop();
        }
    }

    private static boolean denied(LivingEntity target) {
        Long until = DENIED.get(target.getId());
        if (until == null) {
            return false;
        }
        if (System.nanoTime() > until) {
            DENIED.remove(target.getId());
            return false;
        }
        return true;
    }

    private static double alt(E e) {
        return e.pos.y - ground(e.pos);
    }

    private static boolean airborne(E e) {
        return e.flying || alt(e) > 1.2;
    }

    private static E make(LivingEntity ent, boolean player) {
        E e = new E();
        e.ent = ent;
        e.pos = ent.position();
        e.yaw = Math.toRadians(-ent.getYRot());
        e.name = player ? "voce" : BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType()).getPath();
        e.col = player ? AURA_P : aura(e.name);
        e.blast = player ? BLAST_P : blastCol(e.name);
        return e;
    }

    private static void start(LocalPlayer player, LivingEntity mob) {
        P = make(player, true);
        Vec3 fv = FlightClient.velocity();
        P.vel = fv == null ? Vec3.ZERO : fv;
        P.combat = true;
        M = make(mob, false);
        M.vel = new Vec3(mob.getX() - mob.xo, mob.getY() - mob.yo, mob.getZ() - mob.zo).scale(20);
        M.combat = true;
        st = "ar";
        act = null;
        phase = null;
        aiT = 0;
        at = 0;
        cd = 1.0;
        airT = 0;
        orbit = rnd() * 6;
        combo = 0;
        lastHit = -9;
        hitDone = false;
        aim = null;
        charge = null;
        pact = null;
        pCombo = punchN = barN = 0;
        comboT = lungeT = iframe = barT = 0;
        dodgeT = -9;
        lockD = -1;
        guard = guardWant = false;
        pPending = null;
        EX = null;
        clock = 0;
        hitstop = 0;
        shake = 0;
        exEnd = -9;
        qPunch = qDash = qStrong = qDodge = false;
        camPos = null;
        fov = 70;
        running = true;
    }

    private static void stop() {
        if (!running) {
            return;
        }
        running = false;
        held = false;
        placed = null;
        pHeldUntil = 0;
        pHeld = false;
        if (P != null && P.ent != null) {
            P.ent.setInvisible(false);
            ClientHooks.guardPose(P.ent.getId(), false);
            FlightClient.carry(P.vel);
        }
        if (M != null && M.ent != null) {
            M.ent.setInvisible(false);
        }
        for (Blast b : BLASTS) {
            PreviewFx.BALLS.remove(b.ball);
        }
        BLASTS.clear();
        for (BeamS b : BEAMS) {
            PreviewFx.BEAMS.remove(b.b);
        }
        BEAMS.clear();
        if (charge != null) {
            PreviewFx.BALLS.remove(charge);
            charge = null;
        }
        PreviewFx.boostView = 0;
        EX = null;
        P = M = null;
    }

    public static void clear() {
        stop();
        parked = false;
        scene = false;
        DENIED.clear();
        PreviewFx.clear();
    }

    // ------------------------------------------------------------------ every frame (AirFightClient.onFrame)

    static void frame() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dtReal = lastFrame == 0 ? 0.016 : (now - lastFrame) / 1.0E9;
        lastFrame = now;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            stop();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        double dt = Math.min(dtReal, 0.05);
        // A cutscene has the camera (a boss's entrance or phase, Hakai, Bop, a domain, a skill's face shot, the soul
        // ceremony): the page lets go and the cutscene plays as it does on the ground; it takes the fight back after.
        scene = cutscene(mc);
        if (scene) {
            stop();
            PreviewFx.step(dt, dt);
            return;
        }
        LivingEntity target = AirFightClient.target();
        boolean flying = FlightClient.velocity() != null || running;
        boolean want = flying && target instanceof SoulBoss && target.isAlive() && !target.isRemoved() && !player.isSpectator() && player.isAlive()
                && !player.isCreative() && player.getAbilities().flying && !denied(target) && hasGround(player.position());
        if (parked && player.getY() - ground(player.position()) > 2.5) {
            parked = false;
        }
        if (running && (!want || target != M.ent)) {
            stop();
        }
        if (!running) {
            if (want && !parked && FlightClient.flying(target) && player.getY() - ground(player.position()) > 1) {
                start(player, target);
            } else {
                PreviewFx.step(dt, dt);
                return;
            }
        }
        double camDt = dt;
        if (hitstop > 0) {
            hitstop -= dt;
            dt *= 0.05;
        }
        clock += dt;
        if (placed != null && player.position().distanceToSqr(placed) > 0.09) {
            // a skill moved the player (Goku's teleport, the Kaioken rush, Freeza's, a grab's hold): the page goes on
            // from there, after the server is done with it
            pHeldUntil = now + 400_000_000L;
        }
        // The server has the player: stunned for real (a boss's skill, another player's), held, teleported by a skill.
        // Both stand still (no input, no blows from the soul) and the page follows the player where the server puts it.
        pHeld = now < pHeldUntil || FlightClient.driving() || player.hasEffect(com.starkinhon.battlegroundsmod.ModEffects.ATORDOADO)
                || player.hasEffect(com.starkinhon.battlegroundsmod.ModEffects.BRAIN_DAMAGE);
        if (pHeld) {
            P.pos = player.position();
            P.vel = Vec3.ZERO;
            P.boost = false;
            pact = null;
            setGuard(false);
            qPunch = qDash = qStrong = qDodge = false;
            if (EX != null) {
                EX = null;
                setVisible(P, true);
                setVisible(M, true);
                P.boost = M.boost = false;
            }
        } else {
            playerCombat(mc, dt);
        }
        if (!running) {
            PreviewFx.step(dt, camDt);
            return;
        }
        // One of the player's skills has it (Flight.HELD from the server: a real stun, a hold): the skill moves it
        // there, the page only follows and takes it back when it lets go.
        held = (FlightClient.serverState(M.ent) & Flight.HELD) != 0;
        if (pHeld && !held) {
            // the player is the server's right now: the soul waits where it is (no blows on someone who can't move)
            M.vel = Vec3.ZERO;
            M.boost = false;
            act = null;
        } else if (held) {
            M.pos = M.ent.getPosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
            M.vel = Vec3.ZERO;
            M.slam = false;
            M.flying = true;
            M.visible = true;
            M.boost = false;
            act = null;
            if (!"ar".equals(st)) {
                st = "ar";
            }
        } else {
            mobUpdate(dt);
        }
        physicsHits(dt);
        if (!running) {
            PreviewFx.step(dt, camDt);
            return;
        }
        blasts(dt);
        updateBeams(dt);
        if (EX != null) {
            exchangeStep(dt);
        }
        face(P, dt);
        face(M, dt);
        PreviewFx.step(dt, camDt);
        placeCam(Math.max(camDt, 1.0E-4));
        shake = Math.max(0, shake - camDt * 1.6);
        PreviewFx.boostView = P.boostAmt * clamp(P.vel.length() / 42, 0, 1) * 0.4;
        place(P, true);
        if (!held) {
            place(M, false);
        }
        sendAcc += camDt;
        if (sendAcc >= 0.05) {
            sendAcc = 0;
            int flags = (M.flying ? 1 : 0) | (M.boost ? 2 : 0) | (M.visible ? 0 : 4);
            PacketDistributor.sendToServer(new ModPayloads.AirSim(M.ent.getId(), M.pos.x, M.pos.y, M.pos.z, (float) -Math.toDegrees(M.yaw), flags));
        }
    }

    // the page's pose(): the heading (the body's own turn is FlightPose's)
    private static void face(E e, double dt) {
        double yawT = e.yaw;
        double h = Math.hypot(e.vel.x, e.vel.z);
        boolean melee = e.anim != null;
        boolean lean = !e.combat && e.flying && !melee && e.vel.length() > 10;
        if (e.faceYaw != null && (!lean || melee)) {
            yawT = e.faceYaw;
        } else if (h > 1.2) {
            yawT = Math.atan2(e.vel.x, e.vel.z);
        }
        e.yaw += angDiff(e.yaw, yawT) * damp(e.flying ? 7 : 12, dt);
        if (e.anim != null) {
            e.animT += dt;
            if (e.animT > e.animLen) {
                e.anim = null;
            }
        }
        e.boostAmt = lerp(e.boostAmt, e.boost ? 1 : 0, damp(e.boost ? 6 : 3, dt));
    }

    private static void place(E e, boolean player) {
        LivingEntity en = e.ent;
        if (player && pHeld) {
            // the server (or the player's own skill) moves the player: left where it is, with its speed (a dash keeps going)
            e.pos = en.position();
        } else {
            en.setPos(e.pos.x, e.pos.y, e.pos.z);
            en.xo = en.xOld = e.pos.x;
            en.yo = en.yOld = e.pos.y;
            en.zo = en.zOld = e.pos.z;
            en.setDeltaMovement(Vec3.ZERO);
        }
        float yaw = (float) -Math.toDegrees(e.yaw);
        en.setYRot(yaw);
        en.yRotO = yaw;
        en.setYHeadRot(yaw);
        en.yHeadRotO = yaw;
        en.yBodyRot = yaw;
        en.yBodyRotO = yaw;
        if (player) {
            placed = e.pos;
        }
        if (player && M != null) {
            // The view looks right at the target (the body keeps the page's heading): every skill aims through the
            // player's look (Kamehameha, Final Flash, the teleports), and in the first test of the page they all went
            // off to the side (Stark 2026-11-13: "a mira dos ataques está totalmente errada").
            Vec3 d = M.pos.add(0, M.ent.getBbHeight() * 0.55, 0).subtract(e.pos.add(0, en.getEyeHeight(), 0));
            float aimYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
            float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -89, 89);
            en.setYRot(aimYaw);
            en.yRotO = aimYaw;
            en.setYHeadRot(aimYaw);
            en.yHeadRotO = aimYaw;
            en.setXRot(pitch);
            en.xRotO = pitch;
        }
        boolean hide = !e.visible;
        if (en.isInvisible() != hide) {
            en.setInvisible(hide);
        }
        en.fallDistance = 0;
    }

    private static final java.util.Map<String, String> ANIM = java.util.Map.of("socoD", "voo.soco1", "socoE", "voo.soco2", "chute", "voo.soco4",
            "martelo", "voo.martelo", "tiroD", "voo.ki_d", "tiroE", "voo.ki_e", "feixe", "voo.feixe", "choque", "voo.arrancada", "atras", "voo.chute_atras",
            "solta", "voo.feixe_solta");

    private static void play(E e, String anim, double len) {
        e.anim = anim;
        e.animT = 0;
        e.animLen = len;
        ClientHooks.playSkillAnimation(e.ent.getId(), ANIM.getOrDefault(anim, ""), false);
    }

    private static void setVisible(E e, boolean v) {
        e.visible = v;
    }

    private static void ghostOf(E e) {
        PreviewFx.ghostOf(e.ent, e.col);
    }

    private static void send(int kind) {
        if (M != null) {
            PacketDistributor.sendToServer(new ModPayloads.AirSimHit(kind, M.ent.getId()));
        }
    }

    // ------------------------------------------------------------------ the page's physics

    private static void steer(E e, Vec3 wish, double top, double dt, double k) {
        Vec3 target = wish.scale(top);
        double cur = e.vel.length();
        if (wish.lengthSqr() > 0.01 && cur > 3) {
            Vec3 vd = e.vel.scale(1 / cur), td = norm(wish);
            double ang = angle(vd, td);
            if (ang < 2.4) {
                double turn = (e.boost ? 3 : 3.4) * dt;
                if (ang > 1.0E-4) {
                    vd = norm(vd.lerp(td, Math.min(1, turn / ang)));
                }
                e.vel = vd.scale(lerp(cur, top * wish.length(), damp(k, dt)));
                return;
            }
        }
        e.vel = e.vel.lerp(target, damp(k, dt));
    }

    private static void integrate(E e, double dt) {
        if (!e.flying) {
            e.vel = e.vel.add(0, -32 * dt, 0);
        }
        e.pos = e.pos.add(e.vel.scale(dt));
        double g = ground(e.pos);
        if (e.pos.y <= g) {
            e.pos = new Vec3(e.pos.x, g, e.pos.z);
            if (e.vel.y < 0) {
                e.vel = new Vec3(e.vel.x, 0, e.vel.z);
            }
            if (!e.flying) {
                double k = 1 - damp(10, dt);
                e.vel = new Vec3(e.vel.x * k, e.vel.y, e.vel.z * k);
            }
        }
    }

    // ------------------------------------------------------------------ the soul (mobUpdate, airTactics)

    private static void startAct(String a) {
        act = a;
        at = 0;
        hits = 0;
        shots = 0;
        phase = null;
        startT = clock;
        aim = null;
    }

    private static void mobUpdate(double dt) {
        E m = M, p = P;
        Vec3 toP = p.pos.subtract(m.pos);
        double dist = toP.length();
        airT = airborne(p) && !p.slam ? airT + dt : 0;
        aiT += dt;
        m.combat = m.flying;
        if (EX != null) {
            return;
        }
        if (m.stun > 0) {
            m.stun -= dt;
            m.tumble = Math.max(0, m.tumble - dt);
            integrate(m, dt);
            if (m.slam) {
                if (alt(m) <= 0.05) {
                    double g = ground(m.pos);
                    m.slam = false;
                    m.flying = false;
                    m.combat = false;
                    m.vel = Vec3.ZERO;
                    m.stun = 1.0;
                    m.tumble = 0;
                    PreviewFx.craterAt(m.pos, g);
                    PreviewFx.dustAt(m.pos, g, 11, 1.4);
                    PreviewFx.ringAt(m.pos.add(0, 0.1, 0), v(0, 1, 0), 1.8, PreviewFx.WHITE, 0.6);
                    PreviewFx.shockAt(m.pos, 1.3);
                    shake = 0.9;
                    hitstop = 0.1;
                    st = "chao";
                    act = null;
                    airT = 0;
                }
            } else {
                m.vel = m.vel.scale(1 - damp(2.5, dt));
            }
            return;
        }
        m.faceYaw = Math.atan2(toP.x, toP.z);
        if ("chao".equals(st)) {
            m.flying = false;
            m.boost = false;
            if (airT > 0.35) {
                st = "decola";
                aiT = 0;
            }
        } else if ("decola".equals(st)) {
            m.vel = m.vel.scale(0.8);
            if (aiT > 0.3) {
                st = "ar";
                m.flying = true;
                m.vel = v(0, 20, 0).add(norm(flat(toP)).scale(6));
                double g = ground(m.pos);
                PreviewFx.ringAt(m.pos.add(0, 0.15, 0), v(0, 1, 0), 0.9, m.col);
                PreviewFx.dustAt(m.pos, g, 3.5, 0.9);
                PreviewFx.shockAt(m.pos.add(0, 0.5, 0), 0.6);
                cd = 0.9;
            }
        } else {
            airTactics(dt, toP, dist);
        }
        if (m.pending != null) {
            m.pending.t -= dt;
            if (m.pending.t <= 0) {
                Hit h = m.pending;
                m.pending = null;
                if (m.pos.add(0, 0.9, 0).distanceTo(p.pos.add(0, 0.9, 0)) < h.reach) {
                    hurt(p, m, h);
                }
            }
        }
        integrate(m, dt);
    }

    private static Hit hit(double t, boolean big, double knock, double reach) {
        Hit h = new Hit();
        h.t = t;
        h.big = big;
        h.knock = knock;
        h.reach = reach;
        return h;
    }

    private static boolean clashWanted(double dist) {
        return pact != null && "arrancada".equals(pact.k) && pact.clash && dist < 18 && clock - exEnd > 5;
    }

    private static void airTactics(double dt, Vec3 toP, double dist) {
        E m = M, p = P;
        at += dt;
        Vec3 pc = p.pos.add(0, 0.9, 0), mc = m.pos.add(0, 0.9, 0);
        if (act == null) {
            m.boost = false;
            if (clashWanted(dist)) {
                startAct("choque");
            } else if (dist > 26) {
                startAct("persegue");
            } else {
                orbit += dt * 0.55;
                double rad = 9 + Math.sin(aiT * 0.7) * 3.5;
                Vec3 off = v(Math.cos(orbit) * rad, 1.5 + Math.sin(aiT * 0.9) * 2.5, Math.sin(orbit) * rad);
                Vec3 want = p.pos.add(off);
                steer(m, clampLength(want.subtract(m.pos).scale(0.35), 1), 16, dt, 3);
                cd -= dt;
                if (cd <= 0) {
                    double r = rnd();
                    startAct(r < 0.3 ? "investida" : r < 0.5 ? "rajada" : r < 0.64 ? "sumico" : "recua");
                }
            }
        }
        if (act == null) {
            return;
        }
        if (("investida".equals(act) || "persegue".equals(act)) && !"combo".equals(phase) && clashWanted(dist)) {
            startAct("choque");
        }
        String a = act;
        if ("investida".equals(a) || "persegue".equals(a) || "choque".equals(a)) {
            if (!"combo".equals(phase)) {
                Vec3 lead = pc.add(p.vel.scale(clamp(dist / 50, 0, 0.5)));
                m.boost = true;
                m.faceYaw = null;
                steer(m, norm(lead.subtract(mc)), "persegue".equals(a) ? 62 : 46, dt, 7);
                if (at < dt * 1.5) {
                    PreviewFx.ringAt(mc, norm(toP), 0.8, m.col);
                }
                if ("choque".equals(a) && dist < 9) {
                    m.vel = norm(pc.subtract(mc)).scale(46);
                }
                if ("persegue".equals(a) && dist < 9) {
                    startAct("investida");
                    return;
                }
                if ("choque".equals(a) && dist < 2.6 + m.vel.subtract(p.vel).length() * dt * 1.5) {
                    clash();
                    return;
                }
                if ("investida".equals(a) && dist < 1.8 && guard) {
                    guardPunish();
                    return;
                }
                if ("investida".equals(a) && dist < 1.8) {
                    phase = "combo";
                    at = 0;
                    hits = 0;
                    m.boost = false;
                }
                if (at < dt * 1.5) {
                    minD = d0 = dist;
                } else {
                    minD = Math.min(minD, dist);
                }
                if (at > 0.3 && minD < d0 - 2 && dist > minD + 3) {
                    m.vel = m.vel.scale(0.35);
                    PreviewFx.ringAt(mc, norm(m.vel), 0.5, m.col);
                    endAct(0.6);
                    return;
                }
                if (alt(m) < 3 && m.vel.y < 0) {
                    m.vel = new Vec3(m.vel.x, m.vel.y * 0.5, m.vel.z);
                }
                if (at > ("persegue".equals(a) ? 3.5 : 1.4)) {
                    endAct(0.8);
                }
            } else {
                m.vel = m.vel.lerp(p.vel, damp(10, dt));
                Vec3 hold = pc.add(norm(toP).scale(-1.3));
                m.pos = m.pos.lerp(hold.subtract(0, 0.9, 0), damp(8, dt));
                double[] T = {0, 0.2, 0.42};
                String[] A = {"socoD", "socoE", "chute"};
                if (hits < 3 && at >= T[hits]) {
                    play(m, A[hits], 0.26);
                    Hit h = hit(0.08, hits == 2, hits == 2 ? 26 : 3, 2.8);
                    h.finisher = hits == 2;
                    m.pending = h;
                    hits++;
                }
                if (at > 0.75) {
                    if (alt(p) > 5 && rnd() < 0.7 && p.stun > 0) {
                        startAct("martelo");
                        phase = "some";
                    } else {
                        endAct(1.3);
                    }
                }
            }
        } else if ("martelo".equals(a)) {
            Vec3 pf = norm(flat(p.vel));
            if ("some".equals(phase)) {
                if (at < 0.02 && m.visible) {
                    ghostOf(m);
                    setVisible(m, false);
                }
                if (at > 0.2) {
                    Vec3 aimAt = pc.add(p.vel.scale(0.28));
                    m.pos = aimAt.add(0, 1.4, 0).add(pf.scale(0.9)).subtract(0, 0.9, 0);
                    m.vel = p.vel;
                    setVisible(m, true);
                    ghostOf(m);
                    phase = "bate";
                    at = 0;
                    m.faceYaw = Math.atan2(-p.vel.x, -p.vel.z);
                    play(m, "martelo", 0.5);
                }
            } else {
                m.vel = p.vel;
                Vec3 over = pc.add(0, 1.3, 0).add(pf.scale(0.8)).subtract(0, 0.9, 0);
                m.pos = m.pos.lerp(over, damp(18, dt));
                if (at > 0.27 && !hitDone) {
                    hitDone = true;
                    Hit h = hit(0, true, 0, 3.4);
                    h.slam = true;
                    h.punish = "martelo";
                    m.pending = h;
                }
                if (at > 0.6) {
                    hitDone = false;
                    endAct(1.0);
                }
            }
        } else if ("rajada".equals(a)) {
            m.boost = false;
            orbit += dt * 1.25;
            Vec3 off = v(Math.cos(orbit) * 10, 2.5, Math.sin(orbit) * 10);
            steer(m, clampLength(p.pos.add(off).subtract(m.pos).scale(0.4), 1), 20, dt, 4);
            if (at > 0.25 + shots * 0.2 && shots < 5) {
                boolean right = shots % 2 == 0;
                play(m, right ? "tiroD" : "tiroE", 0.18);
                Vec3 side = v(Math.cos(m.yaw), 0, -Math.sin(m.yaw)).scale(right ? -0.4 : 0.4);
                Vec3 from = mc.add(0, 0.45, 0).add(side).add(norm(toP).scale(0.7));
                Vec3 aimAt = pc.add(p.vel.scale(dist / 42));
                fireBlast(from, aimAt, m.blast, m, null, false, 42, 0.32, 5);
                shots++;
            }
            if (at > 1.6) {
                endAct(1.1);
            }
        } else if ("recua".equals(a)) {
            if (phase == null) {
                phase = "foge";
            }
            if ("foge".equals(phase)) {
                Vec3 away = flat(m.pos.subtract(p.pos));
                away = away.lengthSqr() < 0.01 ? v(1, 0, 0) : away.normalize();
                Vec3 want = p.pos.add(away.scale(34));
                want = new Vec3(want.x, Math.max(p.pos.y + 8, ground(want) + 9), want.z);
                steer(m, norm(want.subtract(m.pos)), 40, dt, 6);
                if (at < dt * 1.5) {
                    PreviewFx.ringAt(mc, away, 0.8, m.col);
                    ghostOf(m);
                }
                if (dist > 28 || at > 1.9) {
                    phase = "carga";
                    at = 0;
                    play(m, "feixe", 1.9);
                    charge = new PreviewFx.Ball();
                    charge.col = m.blast;
                    charge.tailed = false;
                    PreviewFx.BALLS.add(charge);
                }
            } else if ("carga".equals(phase)) {
                m.vel = m.vel.scale(1 - damp(6, dt));
                double k = clamp(at / 1.0, 0, 1);
                Vec3 fwd = norm(toP);
                Vec3 atp = mc.add(0, 0.35, 0).add(fwd.scale(0.9));
                if (charge != null) {
                    charge.pos = atp;
                    charge.size = (float) (0.12 + 1.1 * k * k + Math.sin(clock * 50) * 0.03);
                }
                if (at > 0.7 && aim == null) {
                    aim = pc;
                    PreviewFx.starAt(atp, 1.8, PreviewFx.WHITE, 0.25);
                    PreviewFx.ringAt(atp, fwd, 0.7, m.col, 0.3);
                }
                if (at > 1.05) {
                    if (charge != null) {
                        PreviewFx.BALLS.remove(charge);
                        charge = null;
                    }
                    play(m, "solta", 0.9);
                    fireBeam(atp, norm(aim.subtract(atp)), m.blast, m);
                    m.vel = m.vel.add(fwd.scale(-6));
                    phase = "solta";
                    at = 0;
                }
            } else {
                m.vel = m.vel.scale(1 - damp(5, dt));
                if (at > 0.9) {
                    endAct(1.4);
                }
            }
        } else if ("sumico".equals(a)) {
            Vec3 back = v(Math.sin(p.yaw), 0, Math.cos(p.yaw));
            if (!"chuta".equals(phase)) {
                if (at < 0.02 && m.visible) {
                    ghostOf(m);
                    setVisible(m, false);
                }
                if (at > 0.22) {
                    m.pos = p.pos.add(back.scale(-1.5));
                    m.vel = p.vel;
                    setVisible(m, true);
                    ghostOf(m);
                    phase = "chuta";
                    at = 0;
                    play(m, "chute", 0.32);
                    m.pending = hit(0.12, true, 20, 2.8);
                }
            } else {
                m.vel = p.vel;
                if (at < 0.14) {
                    m.pos = m.pos.lerp(p.pos.add(back.scale(-1.4)), damp(16, dt));
                }
                if (at > 0.5) {
                    endAct(1.2);
                }
            }
        }
    }

    private static void endAct(double c) {
        act = null;
        phase = null;
        cd = c + rnd() * 0.8;
        if (M != null) {
            M.boost = false;
            setVisible(M, true);
        }
        if (charge != null) {
            PreviewFx.BALLS.remove(charge);
            charge = null;
        }
    }

    private static void guardPunish() {
        E m = M;
        Vec3 away = norm(m.pos.subtract(P.pos));
        Vec3 c = P.pos.add(0, 1, 0).add(away.scale(0.8));
        endAct(0.9);
        m.pending = null;
        m.boost = false;
        m.stun = 1.2;
        m.vel = away.scale(7);
        play(m, "apanha", 1.0);
        PreviewFx.starAt(c, 1.6, PreviewFx.WHITE, 0.3);
        PreviewFx.ringAt(c, away, 0.9, P.col, 0.4);
        PreviewFx.shockAt(c, 0.8);
        hitstop = 0.09;
        shake = 0.4;
        PreviewFx.impactFrame(2, 0);
    }

    // ------------------------------------------------------------------ the exchange and the clash

    private static void startExchange() {
        Vec3 mid = M.pos.add(P.pos).scale(0.5).add(0, 0.9, 0);
        mid = new Vec3(mid.x, Math.max(mid.y, ground(mid) + 9), mid.z);
        Vec3 side = flat(P.pos.subtract(M.pos));
        side = side.lengthSqr() < 0.01 ? v(1, 0, 0) : side.normalize();
        Vec3 camDir = v(-side.z, 0, side.x);
        if (camPos != null && camPos.subtract(mid).dot(camDir) < 0) {
            camDir = camDir.scale(-1);
        }
        if (pact != null) {
            endPAct();
        }
        endAct(0);
        pPending = null;
        M.pending = null;
        lungeT = 0;
        EX = new Ex();
        EX.center = mid;
        EX.camDir = camDir;
        EX.total = 6 + (rnd() < 0.5 ? 1 : 0);
    }

    private static void exchangeStep(double dt) {
        Ex e = EX;
        e.t += dt;
        P.boost = M.boost = true;
        if (e.t >= e.hideAt) {
            e.hideAt = 9;
            ghostOf(P);
            ghostOf(M);
            setVisible(P, false);
            setVisible(M, false);
        }
        if (e.t < e.next) {
            return;
        }
        e.n++;
        if (e.n > e.total) {
            exEnd = clock;
            setVisible(P, true);
            setVisible(M, true);
            EX = null;
            P.boost = M.boost = false;
            clashFinal();
            return;
        }
        double a = rnd() * Math.PI * 2;
        // A guest on someone else's world (LAN, server) jumping more than 10 blocks in a tick is pulled back by the
        // server ("moved too quickly"): there the clashes stay a bit closer together (the host's world has no such check).
        boolean guest = !Minecraft.getInstance().hasSingleplayerServer();
        Vec3 pt;
        if (guest) {
            double rr = 1.5 + rnd() * 2.5;
            pt = e.center.add(Math.cos(a) * rr, (rnd() - 0.4) * 3.5, Math.sin(a) * rr);
        } else {
            pt = e.center.add(Math.cos(a) * (3 + rnd() * 6), (rnd() - 0.4) * 7, Math.sin(a) * (3 + rnd() * 6));
        }
        pt = new Vec3(pt.x, Math.max(pt.y, ground(pt) + 4), pt.z);
        double b = rnd() * Math.PI * 2;
        Vec3 dir = norm(v(Math.cos(b), (rnd() - 0.5) * 0.5, Math.sin(b)));
        setVisible(P, true);
        setVisible(M, true);
        P.pos = pt.add(dir.scale(0.8)).subtract(0, 0.9, 0);
        M.pos = pt.add(dir.scale(-0.8)).subtract(0, 0.9, 0);
        P.vel = dir.scale(-26);
        M.vel = dir.scale(26);
        P.faceYaw = P.yaw = Math.atan2(-dir.x, -dir.z);
        M.faceYaw = M.yaw = Math.atan2(dir.x, dir.z);
        String[] AN = {"choque", "socoD", "chute", "socoE", "choque", "chute", "socoD"};
        int k = e.n % AN.length;
        play(P, AN[k], 0.26);
        play(M, AN[(k + 3) % AN.length], 0.26);
        PreviewFx.starAt(pt, 1.3, PreviewFx.WHITE, 0.26);
        PreviewFx.ringAt(pt, camPos == null ? dir : norm(camPos.subtract(pt)), 0.55, e.n % 2 == 1 ? P.col : M.col, 0.4);
        PreviewFx.ringAt(pt, dir, 0.45, PreviewFx.WHITE, 0.3);
        PreviewFx.shockAt(pt, 0.9);
        hitstop = 0.05;
        shake = Math.max(shake, 0.35);
        PreviewFx.linesKick = 0.7;
        if (e.n % 2 == 1) {
            PreviewFx.impactFrame(1, 0);
        }
        e.next = e.t + 0.3;
        e.hideAt = e.t + 0.15;
    }

    private static void clashBurst() {
        E m = M, p = P;
        Vec3 mid = m.pos.add(p.pos).scale(0.5).add(0, 0.9, 0);
        play(m, "choque", 0.4);
        play(p, "choque", 0.4);
        PreviewFx.starAt(mid, 2.6, PreviewFx.WHITE, 0.4);
        PreviewFx.ringAt(mid, camPos == null ? v(0, 1, 0) : norm(camPos.subtract(mid)), 1.1, PreviewFx.WHITE, 0.6);
        PreviewFx.ringAt(mid, norm(p.pos.subtract(m.pos)), 1.6, m.col, 0.7);
        PreviewFx.shockAt(mid, 1.4);
        hitstop = 0.16;
        shake = 0.7;
        PreviewFx.linesKick = 1;
        PreviewFx.impactFrame(1, 1, 2, 0, 2, 1);
        Vec3 away = norm(p.pos.subtract(m.pos));
        p.vel = away.scale(17);
        m.vel = away.scale(-17);
        p.boost = false;
        m.boost = false;
        p.stun = 0.35;
    }

    private static void clashFinal() {
        clashBurst();
        M.stun = 0.35;
        endAct(0.9);
    }

    private static void clash() {
        if (clock - exEnd > 5) {
            startExchange();
            return;
        }
        clashBurst();
        endAct(0.9);
        if (pact != null) {
            endPAct();
        }
    }

    // ------------------------------------------------------------------ blows on the player (hurt)

    private static void hurt(E t, E from, Hit h) {
        if (t == P) {
            if (iframe > 0) {
                ghostOf(P);
                return;
            }
            if (guard) {
                Vec3 away = norm(P.pos.subtract(from.pos));
                Vec3 c = P.pos.add(0, 1, 0).add(away.scale(-0.7));
                PreviewFx.ringAt(c, away.scale(-1), h.big ? 0.6 : 0.3, P.col, 0.3);
                PreviewFx.starAt(c, h.big ? 1.1 : 0.6, PreviewFx.WHITE, 0.2);
                hitstop = Math.max(hitstop, 0.04);
                shake = Math.max(shake, h.beam ? 0.6 : 0.15);
                P.vel = P.vel.add(away.scale(h.beam ? 16 : h.slam ? 4 : (h.knock == 0 ? 3 : h.knock) * 0.25));
                send(h.beam ? 16 : 15);
                if (h.punish != null) {
                    guardPunish();
                }
                return;
            }
            if (pact != null) {
                endPAct();
            }
            send(h.slam ? 12 : h.beam ? 14 : h.ball ? 13 : h.big ? 11 : 10);
        }
        Vec3 c = t.pos.add(0, 0.95, 0);
        Vec3 dirAway = t.pos.subtract(from.pos);
        dirAway = dirAway.lengthSqr() < 0.01 ? v(0, 0, 1) : dirAway.normalize();
        hitFx(c.add(dirAway.scale(-0.4)), h.big, from.col, dirAway);
        if (h.slam) {
            t.vel = v(dirAway.x * 4, -40, dirAway.z * 4);
            t.slam = true;
            t.stun = 2;
            t.tumble = 0.5;
            PreviewFx.impactFrame(1, 2, 1, 0);
            play(t, "apanha", 0.6);
            return;
        }
        if (airborne(t)) {
            t.vel = dirAway.scale(h.knock).add(0, h.knock * 0.15, 0);
            t.stun = h.finisher || h.knock > 10 ? 0.6 : 0.18;
            if (h.knock > 10) {
                t.tumble = 0.4;
                PreviewFx.impactFrame(1, 0);
            }
        } else {
            t.vel = new Vec3(dirAway.x * h.knock * 0.6, h.knock > 8 ? 5 : 0, dirAway.z * h.knock * 0.6);
            t.stun = h.knock > 8 ? 0.5 : 0.15;
        }
        play(t, "apanha", Math.min(0.5, t.stun + 0.1));
    }

    private static void hitFx(Vec3 p, boolean big, float[] col, Vec3 dir) {
        PreviewFx.starAt(p, big ? 1.3 : 0.75, col);
        PreviewFx.ringAt(p, dir != null ? dir : camPos == null ? v(0, 1, 0) : norm(camPos.subtract(p)), big ? 0.42 : 0.2, col, big ? 0.4 : 0.26);
        hitstop = Math.max(hitstop, big ? 0.11 : 0.045);
        shake = Math.max(shake, big ? 0.5 : 0.18);
        if (big) {
            PreviewFx.shockAt(p, 0.7);
        }
    }

    private static void physicsHits(double dt) {
        if (P.slam && alt(P) <= 0.05) {
            double g = ground(P.pos);
            P.slam = false;
            PreviewFx.dustAt(P.pos, g, 9, 1.3);
            PreviewFx.ringAt(P.pos.add(0, 0.1, 0), v(0, 1, 0), 1.6, PreviewFx.WHITE, 0.6);
            PreviewFx.shockAt(P.pos, 1.2);
            shake = 0.8;
            hitstop = 0.08;
            // on the page the player lands there: here the flight lets go (FlightClient lands, slammed)
            place(P, true);
            Vec3 keep = P.vel;
            stop();
            parked = true;
            FlightClient.knock(new Vec3(keep.x * 0.1, -42, keep.z * 0.1), 3, true);
            return;
        }
        if (P.stun > 0) {
            P.stun -= dt;
            P.tumble = Math.max(0, P.tumble - dt);
            if (P.flying || P.slam) {
                P.vel = P.vel.scale(P.slam ? 1 : 1 - damp(2.2, dt));
            }
        }
    }

    // ------------------------------------------------------------------ the player (playerCombat)

    private static void endPAct() {
        pact = null;
        P.boost = false;
        setVisible(P, true);
    }

    private static boolean canAct() {
        return M != null && P.flying && pact == null && P.stun <= 0 && !P.slam;
    }

    private static void doDash() {
        if (!canAct()) {
            return;
        }
        double r = rnd();
        pact = new PAct();
        pact.k = "arrancada";
        pact.clash = r < 0.3;
        pact.dodge = r >= 0.3 && r < 0.45;
        P.boost = true;
        Vec3 d = norm(M.pos.subtract(P.pos));
        PreviewFx.ringAt(P.pos.add(0, 0.9, 0), d, 0.9, P.col);
        PreviewFx.shockAt(P.pos.add(0, 0.9, 0), 0.5);
        ghostOf(P);
        PreviewFx.linesKick = 0.8;
        play(P, "choque", 1.6);
    }

    private static void doStrong() {
        if (!canAct()) {
            return;
        }
        double dist = M.pos.distanceTo(P.pos);
        if (dist > 12) {
            return;
        }
        pact = new PAct();
        pact.k = M.stun > 0 && pCombo >= 2 ? "martelo" : "atras";
    }

    private static void doDodge(double sx, double fz) {
        if (M == null || !P.flying || pact != null || P.stun > 0 || P.slam) {
            return;
        }
        Vec3 d = norm(flat(M.pos.subtract(P.pos)));
        Vec3 right = v(-d.z, 0, d.x);
        Vec3 dir = right.scale(sx).add(d.scale(fz));
        if (dir.lengthSqr() < 0.01) {
            dodgeSide = -dodgeSide;
            dir = right.scale(dodgeSide);
        }
        pact = new PAct();
        pact.k = "esquiva";
        pact.dir = dir.normalize();
        iframe = 0.34;
        dodgeT = clock;
        ghostOf(P);
        PacketDistributor.sendToServer(new ModPayloads.AirDodge(true));
    }

    private static void doPunch() {
        if (M == null || pact != null || P.stun > 0 || P.slam) {
            return;
        }
        if (P.anim != null && (P.anim.equals("socoD") || P.anim.equals("socoE") || P.anim.equals("chute")) && P.animT < 0.2) {
            return;
        }
        int n = punchN = (comboT > 0 ? punchN : 0) + 1;
        boolean fin = n % 4 == 0;
        comboT = 0.9;
        play(P, fin ? "chute" : n % 2 == 1 ? "socoD" : "socoE", fin ? 0.34 : 0.26);
        double d = M.pos.distanceTo(P.pos);
        if (d > 2.2 && d < 9) {
            lungeT = 0.18;
        }
        Hit h = hit(fin ? 0.12 : 0.09, fin, fin ? 14 : 2.5, 2.9);
        h.stun = fin ? 0.7 : 0.38;
        pPending = h;
    }

    private static void combatMove(double dt, double f, double s, double up) {
        Vec3 d = M.pos.subtract(P.pos);
        double dist = d.length();
        Vec3 fd = flat(d);
        fd = fd.lengthSqr() < 0.01 ? v(0, 0, 1) : fd.normalize();
        Vec3 right = v(-fd.z, 0, fd.x), fwd3 = norm(d);
        if (f != 0 || lockD < 0) {
            lockD = dist;
        }
        Vec3 wish = Vec3.ZERO;
        if (f > 0 && dist > 2.4 || f < 0) {
            wish = wish.add(fwd3.scale(f));
        }
        wish = wish.add(right.scale(s)).add(0, up, 0);
        if (s != 0 && f == 0) {
            wish = wish.add(fwd3.scale(clamp((dist - lockD) * 0.25, -0.6, 0.6)));
        }
        if (wish.lengthSqr() > 1) {
            wish = wish.normalize();
        }
        P.vel = P.vel.lerp(wish.scale(18), damp(8, dt));
    }

    private static void actStep(double dt) {
        PAct a = pact;
        a.t += dt;
        Vec3 pc = P.pos.add(0, 0.9, 0), mc = M.pos.add(0, 0.9, 0), d = mc.subtract(pc);
        double dist = d.length();
        if ("arrancada".equals(a.k)) {
            P.boost = true;
            if (!a.dodged) {
                P.faceYaw = Math.atan2(d.x, d.z);
            }
            Vec3 want = a.dodged ? norm(P.vel) : norm(d);
            P.vel = P.vel.lerp(want.scale(62), damp(a.dodged ? 2 : 12, dt));
            if (a.dodge && !a.dodged && dist < 5.5 && M.stun <= 0 && act == null && M.visible) {
                a.dodged = true;
                a.dT = a.t;
                ghostOf(M);
                Vec3 r = norm(v(-d.z, 0, d.x)).scale(rnd() < 0.5 ? 4.5 : -4.5);
                M.pos = M.pos.add(r).add(0, 1.5, 0);
                cd = 0.25;
            }
            if (act != null && (act.equals("investida") || act.equals("persegue") || act.equals("choque")) && !"combo".equals(phase) && M.boost && dist < 6
                    && clock - exEnd > 5) {
                startExchange();
                return;
            }
            if (!a.dodged && dist < 2.3) {
                Hit h = hit(0, true, 6, 0);
                h.stun = 0.9;
                h.melee = true;
                if (hitM(h)) {
                    pCombo = 1;
                    comboT = 0.9;
                }
                P.vel = M.vel.scale(0.3);
                endPAct();
                P.anim = null;
                return;
            }
            if (a.t > 1.8 || a.dodged && a.t - a.dT > 0.4) {
                endPAct();
                P.vel = P.vel.scale(0.4);
                P.anim = null;
            }
        } else if ("atras".equals(a.k) || "martelo".equals(a.k)) {
            if (!a.gone) {
                a.gone = true;
                ghostOf(P);
                setVisible(P, false);
            }
            if (!a.out && a.t > 0.15) {
                a.out = true;
                setVisible(P, true);
                Vec3 fd = norm(flat(d));
                P.pos = "atras".equals(a.k) ? M.pos.add(fd.scale(1.5)) : M.pos.add(0, 1.7, 0).add(fd.scale(-0.6));
                P.vel = M.vel;
                ghostOf(P);
                Vec3 to = M.pos.subtract(P.pos);
                P.faceYaw = P.yaw = Math.atan2(to.x, to.z);
                play(P, "atras".equals(a.k) ? "atras" : "martelo", "atras".equals(a.k) ? 0.34 : 0.5);
                a.hitAt = a.t + ("atras".equals(a.k) ? 0.1 : 0.27);
            }
            if (a.out) {
                P.vel = M.vel;
                if ("martelo".equals(a.k) && !a.hit) {
                    P.pos = P.pos.lerp(M.pos.add(0, 1.6, 0), damp(18, dt));
                }
                if (!a.hit && a.t >= a.hitAt) {
                    a.hit = true;
                    Hit h;
                    if ("atras".equals(a.k)) {
                        h = hit(0, true, 22, 0);
                        h.stun = 0.8;
                    } else {
                        h = hit(0, true, 0, 0);
                        h.slam = true;
                    }
                    h.melee = true;
                    hitM(h);
                }
                if (a.t > a.hitAt + 0.3) {
                    endPAct();
                }
            }
        } else if ("esquiva".equals(a.k)) {
            if (a.t < 0.22) {
                P.vel = a.dir.scale(38);
            } else {
                P.vel = P.vel.scale(0.5);
                endPAct();
            }
            if (!a.g2 && a.t > 0.11) {
                a.g2 = true;
                ghostOf(P);
            }
        }
    }

    // the player's blow lands on the soul (the page's hitM)
    private static boolean hitM(Hit h) {
        E m = M;
        if (m == null || !m.visible) {
            return false;
        }
        Vec3 away = m.pos.subtract(P.pos);
        away = away.lengthSqr() < 0.01 ? v(0, 0, 1) : away.normalize();
        if (h.melee) {
            combo = clock - lastHit < 1.3 ? combo + 1 : 1;
            lastHit = clock;
            if (combo >= 8 && !h.slam) {
                combo = 0;
                ghostOf(m);
                m.pos = m.pos.add(away.scale(7)).add(0, 3, 0);
                ghostOf(m);
                m.stun = 0;
                m.vel = Vec3.ZERO;
                cd = 0.3;
                return false;
            }
        }
        hitFx(m.pos.add(0, 0.95, 0).add(away.scale(-0.3)), h.big, P.col, away.scale(-1));
        send(h.slam ? 2 : h.ball ? (h.big ? 4 : 3) : h.big ? 1 : 0);
        if (h.stun == 0 && !h.slam) {
            m.vel = m.vel.add(away.scale(h.knock));
            return true;
        }
        if (act != null) {
            endAct(0.9);
        }
        m.pending = null;
        m.boost = false;
        if (h.slam) {
            m.vel = v(away.x * 3, -42, away.z * 3);
            m.slam = true;
            m.stun = 3;
            m.tumble = 0.5;
            PreviewFx.impactFrame(1, 2, 1, 0);
            play(m, "apanha", 0.8);
            return true;
        }
        m.vel = away.scale(h.knock).add(0, h.knock * 0.1, 0);
        m.stun = Math.max(m.stun, h.stun);
        if (h.knock > 10) {
            m.tumble = 0.35;
            PreviewFx.impactFrame(1, 0);
        }
        play(m, "apanha", Math.min(0.5, m.stun + 0.1));
        return true;
    }

    private static void playerCombat(Minecraft mc, double dt) {
        P.combat = P.flying;
        if (iframe > 0) {
            iframe -= dt;
        }
        boolean wantGuard = mc.screen == null && Keybinds.GUARD.isDown();
        if (EX != null) {
            qPunch = qDash = qStrong = qDodge = false;
            setGuard(false);
            return;
        }
        if (comboT > 0) {
            comboT -= dt;
            if (comboT <= 0) {
                pCombo = 0;
                punchN = 0;
            }
        }
        Options o = mc.options;
        boolean keys = mc.screen == null;
        double f = keys ? (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0) : 0;
        double s = keys ? (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0) : 0;
        double up = keys ? (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0) : 0;
        boolean barrage = barrageHeld(mc);
        boolean punch = qPunch;
        qPunch = false;
        if (wantGuard && !guardWant) {
            guardStart = clock;
        }
        guardWant = wantGuard;
        setGuard(wantGuard && P.flying && pact == null && P.stun <= 0 && !P.slam);
        if (guard) {
            f *= 0.35;
            s *= 0.35;
            up *= 0.35;
            barrage = false;
            punch = false;
        }
        if (qDash) {
            doDash();
        }
        if (qStrong) {
            doStrong();
        }
        if (qDodge) {
            doDodge(s == 0 ? 0 : Math.signum(s), f == 0 ? 0 : Math.signum(f));
        }
        qDash = qStrong = qDodge = false;
        Vec3 d = M.pos.subtract(P.pos);
        if (P.stun <= 0 && !P.slam) {
            P.faceYaw = Math.atan2(d.x, d.z);
            if (pact != null) {
                actStep(dt);
                if (EX != null) {
                    return;
                }
            } else {
                combatMove(dt, f, s, up);
                // coming down onto the ground on purpose (Shift): the page lands; here the flight lets go
                if (alt(P) < 0.3 && up < 0) {
                    stop();
                    parked = true;
                    return;
                }
                if (barrage) {
                    barT -= dt;
                    if (barT <= 0) {
                        barT = 0.13;
                        boolean right = ++barN % 2 == 1;
                        play(P, right ? "tiroD" : "tiroE", 0.15);
                        Vec3 fd = norm(d);
                        Vec3 side = v(Math.cos(P.yaw), 0, -Math.sin(P.yaw)).scale(right ? -0.4 : 0.4);
                        Vec3 from = P.pos.add(0, 1.35, 0).add(side).add(fd.scale(0.7));
                        Vec3 to = from.add(fd.scale(10)).add((rnd() - 0.5) * 7, (rnd() - 0.3) * 6, (rnd() - 0.5) * 7);
                        fireBlast(from, to, P.blast, P, M, true, 44, 0.22, 4 + rnd() * 1.5);
                    }
                } else {
                    barT = 0;
                }
            }
            if (punch) {
                doPunch();
            }
            if (lungeT > 0) {
                lungeT -= dt;
                Vec3 to = M.pos.subtract(P.pos);
                if (to.length() > 1.7) {
                    P.vel = norm(to).scale(34);
                } else {
                    P.vel = M.vel;
                    lungeT = 0;
                }
            }
        } else {
            barT = 0;
        }
        if (pPending != null) {
            pPending.t -= dt;
            if (pPending.t <= 0) {
                Hit h = pPending;
                pPending = null;
                h.melee = true;
                if (P.pos.distanceTo(M.pos) < 2.9 && hitM(h)) {
                    pCombo++;
                    comboT = 0.9;
                    P.vel = M.vel;
                }
            }
        }
        integrate(P, dt);
    }

    private static void setGuard(boolean on) {
        guard = on;
        if (on != guardShown && P != null) {
            guardShown = on;
            ClientHooks.guardPose(P.ent.getId(), on);
        }
    }

    // ------------------------------------------------------------------ ki balls and the beam

    private static void fireBlast(Vec3 from, Vec3 to, float[] col, E owner, E target, boolean homing, double speed, double size, double turn) {
        Blast b = new Blast();
        b.ball.pos = from;
        b.ball.size = (float) size;
        b.ball.col = col;
        b.vel = norm(to.subtract(from)).scale(speed);
        b.owner = owner;
        b.target = target;
        b.homing = homing;
        b.speed = speed;
        b.turn = turn;
        b.col = col;
        BLASTS.add(b);
        PreviewFx.BALLS.add(b.ball);
    }

    private static void blasts(double dt) {
        for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext();) {
            Blast b = it.next();
            b.t += dt;
            Vec3 bp = b.ball.pos;
            if (b.homing && b.target != null && b.target.visible) {
                Vec3 want = norm(b.target.pos.add(0, 0.95, 0).subtract(bp));
                Vec3 cur = norm(b.vel);
                double ang = angle(cur, want);
                if (ang > 1.0E-4) {
                    cur = norm(cur.lerp(want, Math.min(1, b.turn * dt / ang)));
                }
                b.vel = cur.scale(b.speed);
            }
            bp = bp.add(b.vel.scale(dt));
            b.ball.pos = bp;
            b.ball.push();
            E tgt = b.noHit ? null : b.target != null ? b.target : P;
            boolean hitP = tgt != null && tgt.visible && !(tgt == P && iframe > 0) && bp.distanceTo(tgt.pos.add(0, 0.95, 0)) < 1.0;
            if (hitP && tgt == P && guard) {
                if (clock - guardStart < 0.22) {
                    b.target = b.owner;
                    b.owner = P;
                    b.homing = true;
                    b.speed = 54;
                    b.turn = 10;
                    b.reflected = true;
                    b.t = 0;
                    b.vel = b.vel.scale(-1);
                    PreviewFx.starAt(bp, 1.4, PreviewFx.WHITE, 0.25);
                    PreviewFx.ringAt(bp, norm(b.vel), 0.6, PreviewFx.WHITE, 0.3);
                    hitstop = 0.06;
                    play(P, "socoD", 0.2);
                } else {
                    b.noHit = true;
                    b.homing = false;
                    double sp = b.vel.length();
                    b.vel = v(-b.vel.x * 0.25, sp * 0.95, -b.vel.z * 0.25);
                    b.t = 0.6;
                    PreviewFx.starAt(bp, 0.8, b.col, 0.2);
                }
                continue;
            }
            double g = ground(bp);
            if (hitP || bp.y < g || b.t > 2.4) {
                if (hitP) {
                    if (tgt == P) {
                        Hit h = hit(0, false, 5, 0);
                        h.ball = true;
                        hurt(P, b.owner, h);
                    } else {
                        Hit h = b.reflected ? hit(0, true, 8, 0) : hit(0, false, 1.4, 0);
                        h.stun = b.reflected ? 0.6 : 0;
                        h.ball = true;
                        hitM(h);
                    }
                    PreviewFx.starAt(bp, 1.2, b.col, 0.3);
                } else {
                    PreviewFx.starAt(bp, 0.9, b.col, 0.28);
                    if (bp.y - g < 0.5) {
                        PreviewFx.dustAt(bp, g, 2.2, 0.6);
                    }
                }
                PreviewFx.BALLS.remove(b.ball);
                it.remove();
            }
        }
    }

    private static void fireBeam(Vec3 from, Vec3 dir, float[] col, E owner) {
        BeamS b = new BeamS();
        b.from = from;
        b.dir = dir;
        b.owner = owner;
        double len = 80;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            HitResult hr = mc.level.clip(new ClipContext(from, from.add(dir.scale(80)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner.ent));
            if (hr.getType() != HitResult.Type.MISS) {
                len = Math.min(len, hr.getLocation().distanceTo(from));
            }
        }
        b.len = len;
        b.b.from = from;
        b.b.dir = dir;
        b.b.col = col;
        b.b.r = b.r;
        BEAMS.add(b);
        PreviewFx.BEAMS.add(b.b);
        PreviewFx.shockAt(from, 0.9);
        shake = Math.max(shake, 0.45);
    }

    private static void updateBeams(double dt) {
        for (Iterator<BeamS> it = BEAMS.iterator(); it.hasNext();) {
            BeamS b = it.next();
            b.t += dt;
            double grow = Math.min(1, b.t / 0.22), L = Math.max(0.1, b.len * grow);
            double life = 1.05, fade = 1 - sstep(life - 0.3, life, b.t), w = (0.75 + 0.25 * Math.sin(b.t * 60)) * (0.35 + 0.65 * fade);
            b.b.len = L;
            b.b.w = w;
            b.b.fade = fade;
            if (grow >= 1 && b.len < 79 && !b.dusted) {
                b.dusted = true;
                Vec3 gp = b.from.add(b.dir.scale(b.len));
                PreviewFx.dustAt(gp, ground(gp), 9, 1.2);
                PreviewFx.shockAt(gp, 1);
                PreviewFx.starAt(gp, 3, b.b.col, 0.4);
            }
            Vec3 pc = P.pos.add(0, 0.95, 0);
            double k = clamp(pc.subtract(b.from).dot(b.dir), 0, L);
            Vec3 q = b.from.add(b.dir.scale(k));
            if (!b.hit && P.visible && iframe > 0 && q.distanceTo(pc) < b.r * 1.15 + 1.5) {
                b.hit = true;
                ghostOf(P);
            }
            if (!b.hit && P.visible && q.distanceTo(pc) < b.r * 1.15 + 0.5) {
                b.hit = true;
                boolean guarded = guard;
                Hit h = hit(0, true, 30, 0);
                h.beam = true;
                hurt(P, b.owner, h);
                if (!guarded) {
                    PreviewFx.impactFrame(1, 0, 2, 0);
                    PreviewFx.shockAt(pc, 1.2);
                }
            }
            if (b.t > life) {
                PreviewFx.BEAMS.remove(b.b);
                it.remove();
            }
        }
    }

    private static double sstep(double a, double b, double x) {
        double t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ the camera (placeCam, camKey 'jogador', combatOn)

    private static void placeCam(double dt) {
        Vec3 head = P.pos.add(0, 1.6, 0);
        Vec3 want, atp;
        if (EX != null) {
            Vec3 c = EX.center;
            want = c.add(EX.camDir.scale(24));
            want = new Vec3(want.x, Math.max(ground(want) + 2.5, c.y - 8), want.z);
            atp = c;
        } else {
            Vec3 mc = M.pos.add(0, 1.0, 0), dv = mc.subtract(head);
            double dl = dv.length(), hz = Math.hypot(dv.x, dv.z);
            Vec3 fl = hz > 0.8 ? v(dv.x / hz, 0, dv.z / hz) : v(Math.sin(P.yaw), 0, Math.cos(P.yaw));
            Vec3 right = v(-fl.z, 0, fl.x);
            double el = clamp(Math.atan2(dv.y, Math.max(hz, 0.01)), -0.5, 0.5);
            Vec3 back = fl.scale(Math.cos(el)).add(0, Math.sin(el), 0);
            double side = 1.3 + 1.6 * clamp((7 - dl) / 5, 0, 1);
            want = head.add(back.scale(-5.4)).add(0, 0.9, 0).add(right.scale(-side));
            atp = head.lerp(mc, clamp(1 - 2.5 / Math.max(dl, 0.1), 0.45, 0.8));
        }
        want = new Vec3(want.x, Math.max(want.y, ground(want) + 0.4), want.z);
        if (camPos == null) {
            camPos = want;
            camLook = atp;
        }
        camPos = camPos.lerp(want, damp(EX != null ? 4 : 12, dt));
        camLook = camLook.lerp(atp, damp(14, dt));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && EX == null) {
            HitResult hr = mc.level.clip(new ClipContext(head, camPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, P.ent));
            if (hr.getType() != HitResult.Type.MISS) {
                camPos = hr.getLocation().add(norm(head.subtract(hr.getLocation())).scale(0.3));
            }
        }
        double sp = P.vel.length();
        fov = lerp(fov, 70 + clamp(sp / 42, 0, 1) * 14, damp(5, dt));
    }
}
