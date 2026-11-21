// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.CutsceneCamera;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.combat.clash.Clash;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Clash de poderes (combat/clash/Clash, SubNet kinds 540-559), drawn as the approved preview draws it
// (forja/clash-poderes v2, Stark 2026-11-21): the point of contact is ONE sphere with the two colors fighting inside it
// and a white seam where they crush each other (souls_ki 72), its halo of rays (73) and ONE flattened crown tearing into
// tongues that leak out sideways (74); fractal lightning in both colors jumping out of it (some falling to the ground)
// and sparks of every size flying off and braking in the air. The beams and the balls themselves are drawn by their own
// renderers (the server keeps sending them, held at the point); a ball the clients fly on their own (the meteor, the
// giant katon, the Purple, the Fuga) is stopped by AttackClock and moved with the point (heldAt / shift / over).
// The two who clash get the cinematic camera for the whole clash: 7 shots with their own movement in a drawn order,
// a white flash on each cut, a cut to the shoulder of whoever throws the Shift shove, then following the point to the
// loser and the wide shot of the blast. Their screen also gets the bar (who is winning, the time, the energies).
public final class ClashFx {
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final int SHOTS = 7;
    private static final double SHOT_LEN = 46;
    // the camera keeps the blast a while after it, then gives the player their view back
    private static final double END_HOLD = 34;

    private static final class Bolt {
        final Vec3[] pts;
        final float w;
        final float[] col;
        final double born;
        final float life;

        Bolt(Vec3[] pts, float w, float[] col, double born, float life) {
            this.pts = pts;
            this.w = w;
            this.col = col;
            this.born = born;
            this.life = life;
        }
    }

    private static final class Spark {
        Vec3 pos;
        Vec3 vel;
        final float size;
        final float[] col;
        final double born;
        final float life;

        Spark(Vec3 pos, Vec3 vel, float size, float[] col, double born, float life) {
            this.pos = pos;
            this.vel = vel;
            this.size = size;
            this.col = col;
            this.born = born;
            this.life = life;
        }
    }

    private static final class Duel {
        final int id;
        final double born;
        final Random rnd;
        final float seed;
        Vec3 point;
        Vec3 prev;
        Vec3 lock;
        Vec3 axis = new Vec3(0, 0, 1);
        float radius = 1.4f;
        float lean;
        int state;
        double updated;
        double endAt = -1;
        Vec3 aOrigin;
        Vec3 bOrigin;
        int rgbA = 0x62C6FF;
        int rgbB = 0xB05CFF;
        float[] colA = FxDraw.rgb(0x62C6FF);
        float[] colB = FxDraw.rgb(0xB05CFF);
        float frozen;
        int aId = -1;
        int bId = -1;
        float pulseA;
        float pulseB;
        float surgeA;
        float surgeB;
        int winner;
        Vec3 boomAt;
        float spin;
        final List<Bolt> bolts = new ArrayList<>();
        final List<Spark> sparks = new ArrayList<>();
        double simAt = -1;
        double boltIn;
        double sparkIn;
        double groundY = Double.NaN;
        // camera
        boolean camera;
        int shot = -1;
        double shotAt;
        boolean camInit;
        Vec3 camPos;
        Vec3 camLook;
        double camAt = -1;
        boolean endCut;

        Duel(int id, Vec3 point, double now) {
            this.id = id;
            this.point = point;
            this.prev = point;
            this.born = now;
            this.updated = now;
            this.rnd = new Random(id * 7919L + 13);
            this.seed = rnd.nextFloat() * 10;
        }

        boolean over() {
            return endAt >= 0;
        }

        Vec3 at(float partial) {
            return prev.lerp(point, partial);
        }
    }

    private static final Map<Integer, Duel> DUELS = new HashMap<>();
    // The bar on the screen of the two (Clash.HUD): lean (+1 me winning), seconds left, energies, shown.
    private static int hudId = -1;
    private static float hudLean;
    private static float hudShown;
    private static float hudSecs;
    private static float hudMine;
    private static float hudOther;
    private static int hudSide;
    private static double hudAt = -1;

    private ClashFx() {
    }

    static boolean handles(int kind) {
        return kind >= Clash.FIRST && kind <= Clash.LAST;
    }

    static boolean active() {
        return !DUELS.isEmpty();
    }

    public static void clear() {
        for (Duel d : DUELS.values()) {
            endCamera(d);
        }
        DUELS.clear();
        hudId = -1;
        hudShown = 0;
    }

    private static double now() {
        return ScreenFx.now();
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = now();
        if (kind == Clash.HUD) {
            hudId = id;
            hudLean = (float) pos.x;
            hudSecs = (float) pos.y;
            hudMine = (float) pos.z;
            hudOther = (float) dir.x;
            hudSide = (int) Math.signum(dir.y);
            hudShown = a;
            hudAt = now;
            return;
        }
        Duel d = DUELS.get(id);
        if (d == null) {
            if (kind == Clash.BOOM) {
                return;
            }
            d = new Duel(id, pos, now);
            DUELS.put(id, d);
            if (kind == Clash.POINT) {
                d.lock = pos;
            }
        }
        switch (kind) {
            case Clash.POINT -> {
                if (d.lock == null) {
                    d.lock = pos;
                }
                if ((int) c == 2) {
                    return;
                }
                d.prev = d.point;
                d.point = pos;
                if (dir.lengthSqr() > 1.0E-6) {
                    d.axis = dir.normalize();
                }
                d.radius = a;
                d.lean = b;
                d.state = (int) c;
                d.updated = now;
            }
            case Clash.INFO -> {
                d.aOrigin = pos;
                d.bOrigin = dir;
                d.rgbA = (int) a;
                d.rgbB = (int) b;
                d.colA = FxDraw.rgb(d.rgbA);
                d.colB = FxDraw.rgb(d.rgbB);
                d.frozen = c;
            }
            case Clash.IDS -> {
                d.aId = (int) a;
                d.bId = (int) b;
            }
            case Clash.PRESS -> {
                boolean sideA = a > 0;
                if (sideA) {
                    d.pulseA = 1;
                } else {
                    d.pulseB = 1;
                }
                if (b >= 1) {
                    if (sideA) {
                        d.surgeA = 1;
                    } else {
                        d.surgeB = 1;
                    }
                    if (mine(d)) {
                        ScreenShake.start(0.8f, 10);
                    }
                }
            }
            case Clash.BOOM -> {
                d.endAt = now;
                d.boomAt = pos;
                d.winner = (int) c;
                d.updated = now;
                if (mine(d)) {
                    ScreenFx.flash(0xFFFFFF, 0.8f, 10);
                    ScreenShake.start(1.6f, 24);
                } else {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.position().distanceTo(pos) < 40) {
                        ScreenShake.start(0.9f, 16);
                    }
                }
            }
            default -> {
            }
        }
    }

    // One of the two clashing is the player at this screen.
    private static boolean mine(Duel d) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (d.aId == mc.player.getId() || d.bId == mc.player.getId());
    }

    // 1: the player is side A, -1: side B, 0: watching.
    private static int side(Duel d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        return d.aId == mc.player.getId() ? 1 : d.bId == mc.player.getId() ? -1 : 0;
    }

    // ---------- the balls the clients fly on their own (AttackClock) ----------

    // A clash holding a ball the clients fly is going on.
    static boolean holding() {
        for (Duel d : DUELS.values()) {
            if (!d.over() && d.frozen > 0) {
                return true;
            }
        }
        return false;
    }

    // The clash holding what is at p (a ball the clients fly), or -1.
    static int heldAt(Vec3 p) {
        for (Duel d : DUELS.values()) {
            if (!d.over() && d.frozen > 0 && d.lock != null && p.distanceTo(d.lock) < d.frozen + 2.5) {
                return d.id;
            }
        }
        return -1;
    }

    // How far the point moved since it locked: a held ball moves with it.
    static Vec3 shift(int id, float partial) {
        Duel d = DUELS.get(id);
        if (d == null || d.lock == null) {
            return Vec3.ZERO;
        }
        Vec3 at = d.over() && d.boomAt != null ? d.point : d.at(partial);
        return at.subtract(d.lock);
    }

    // The clash is over (it made the blast and the server erased the ball): the held ball goes.
    static boolean over(int id) {
        Duel d = DUELS.get(id);
        return d == null || d.over();
    }

    // ---------- every client tick ----------

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!DUELS.isEmpty()) {
                clear();
            }
            return;
        }
        double now = now();
        Iterator<Duel> it = DUELS.values().iterator();
        while (it.hasNext()) {
            Duel d = it.next();
            if (!d.over() && now - d.updated > 30) {
                // the server stopped talking about it (out of range, say)
                d.endAt = now;
            }
            if (d.over() && now - d.endAt > END_HOLD) {
                endCamera(d);
                it.remove();
                continue;
            }
            d.pulseA = Math.max(0, d.pulseA - 0.2f);
            d.pulseB = Math.max(0, d.pulseB - 0.2f);
            d.surgeA = Math.max(0, d.surgeA - 1 / 14f);
            d.surgeB = Math.max(0, d.surgeB - 1 / 14f);
            if (!d.over()) {
                // the crown turns faster on every press
                d.spin += 0.125f + 0.1f * (d.pulseA + d.pulseB);
                if (mine(d) && (d.pulseA > 0.75f || d.pulseB > 0.75f) && d.rnd.nextFloat() < 0.35f) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_BLAST, 0.9f + 0.3f * d.rnd.nextFloat(), 0.35f));
                }
            }
            d.groundY = groundUnder(mc, d.point);
            boolean cam = mine(d) && (!d.over() || now - d.endAt < END_HOLD - 6);
            if (cam && !d.camera) {
                d.camera = true;
                d.camInit = false;
                CutsceneCamera.begin("clash");
            }
            if (cam) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else {
                endCamera(d);
            }
        }
        if (hudShown > 0 && now - hudAt > 10) {
            hudShown = 0;
        }
    }

    private static void endCamera(Duel d) {
        if (d.camera) {
            d.camera = false;
            CutsceneCamera.end("clash");
        }
    }

    private static double groundUnder(Minecraft mc, Vec3 p) {
        var hit = mc.level.clip(new ClipContext(p, p.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? p.y - 24 : hit.getLocation().y;
    }

    // ---------- drawing (DbFx's pass, both the frame and the glow) ----------

    static void draw(double now, float partial) {
        if (DUELS.isEmpty()) {
            return;
        }
        for (Duel d : DUELS.values()) {
            if (DbFx.pass) {
                simulate(d, now + partial);
            }
            if (!d.over()) {
                sphere(d, now, partial);
            }
            bolts(d, now + partial);
            sparks(d, now + partial);
            // read once into a local: the JIT inlines this into DbFx.draw, where a re-read field once lost its null check
            Vec3 boomAt = d.boomAt;
            if (d.over() && boomAt != null) {
                boom(d, boomAt, (float) (now - d.endAt) + partial);
            }
        }
    }

    // The point: the bicolor sphere, its halo, the crown (queued once a frame, in the glow pass like KaiohFx).
    private static void sphere(Duel d, double now, float partial) {
        if (!DbFx.pass || !KiMesh.ready()) {
            return;
        }
        float time = (float) ((now - d.born) + partial) / 20f;
        float pulse = Math.max(d.pulseA, d.pulseB);
        float R = d.radius * (1 + 0.08f * (d.pulseA + d.pulseB)) + 0.05f * Mth.sin(time * 23);
        if (d.state == 1) {
            R = Math.max(0.7f, R);
        }
        Vector3f c = rel(d.at(partial));
        // local +z towards side A (the page's "lado do Goku"), so the seam sits across the axis
        Vector3f toA = new Vector3f((float) -d.axis.x, (float) -d.axis.y, (float) -d.axis.z);
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), toA);
        float bias = Mth.clamp(d.lean * 0.7f + (d.state == 1 ? Math.signum(d.lean) * 0.8f : 0), -1.4f, 1.4f);

        KiMesh.P core = KiMesh.p(KiMesh.CLASH_CORE);
        core.time = time;
        core.pulse = pulse;
        core.phase = bias;
        core.color = d.colA;
        core.color2 = d.colB;
        core.solid = true;
        KiMesh.queue(KiMesh.Mesh.BALL, new Matrix4f().translation(c).rotate(q).scale(R), core, KiMesh.FRONT, 11);

        KiMesh.P halo = KiMesh.p(KiMesh.CLASH_HALO);
        halo.time = time;
        halo.phase = bias;
        halo.color = d.colA;
        halo.color2 = d.colB;
        halo.alpha = 1;
        KiMesh.queue(KiMesh.Mesh.HALO, new Matrix4f().translation(c).rotate(q).scale(R * 1.75f), halo, KiMesh.BACK, 12);

        KiMesh.P crown = KiMesh.p(KiMesh.CLASH_CROWN);
        crown.time = time;
        crown.life = 0.62f;
        crown.seed = d.seed;
        crown.phase = bias;
        crown.color = d.colA;
        crown.color2 = d.colB;
        KiMesh.queue(KiMesh.Mesh.BLAST, new Matrix4f().translation(c).rotate(q).rotateZ(d.spin).scale(R * 1.9f, R * 1.9f, R * 0.45f), crown,
                KiMesh.BOTH, 15);
    }

    // New lightning and sparks (the page's timers: a bolt every 0.9-2.6 ticks, more on a press or a shove), and the
    // sparks' flight. Only once a frame.
    private static void simulate(Duel d, double t) {
        if (d.simAt < 0) {
            d.simAt = t;
            return;
        }
        double dt = Math.min(3, t - d.simAt);
        d.simAt = t;
        if (dt <= 0) {
            return;
        }
        for (Spark s : d.sparks) {
            double drag = Math.exp(-dt * 0.09);
            s.vel = new Vec3(s.vel.x * drag, s.vel.y * drag - 0.004 * dt, s.vel.z * drag);
            s.pos = s.pos.add(s.vel.scale(dt));
        }
        d.sparks.removeIf(s -> t - s.born > s.life);
        d.bolts.removeIf(b -> t - b.born > b.life);
        if (d.over()) {
            return;
        }
        Random r = d.rnd;
        float R = d.radius;
        boolean surge = d.surgeA > 0 || d.surgeB > 0;
        double heavy = (surge ? 2.2 : 1) * (1 + 0.6 * (d.pulseA + d.pulseB));
        Vec3 c = d.point;
        Vec3 toA = d.axis.scale(-1);
        Vec3 side = toA.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize();
        Vec3 up = side.cross(toA).normalize();
        d.boltIn -= dt * heavy;
        int guard = 0;
        while (d.boltIn <= 0 && guard++ < 6) {
            d.boltIn += 0.36 + 0.7 * r.nextDouble();
            // mostly out to the sides, a little along the axis (the page: z squeezed to 0.7)
            Vec3 dir = side.scale(r.nextDouble() - 0.5).add(up.scale(r.nextDouble() * 0.9 - 0.25)).add(toA.scale((r.nextDouble() - 0.5) * 0.7));
            if (dir.lengthSqr() < 1.0E-4) {
                continue;
            }
            dir = dir.normalize();
            Vec3 start = c.add(dir.scale(R * 0.9));
            boolean sideA = dir.dot(toA) + d.lean * 0.5 > 0;
            float[] col = sideA ? d.colA : d.colB;
            boolean ground = r.nextFloat() < 0.22f && !Double.isNaN(d.groundY) && c.y - d.groundY < 9;
            Vec3 end = start.add(dir.scale(R * (1.4 + 2.6 * r.nextDouble())));
            if (ground) {
                Vec3 flat = new Vec3(dir.x, 0, dir.z);
                end = new Vec3(c.x, d.groundY + 0.05, c.z).add(flat.scale(2 + 4 * r.nextDouble())).add(toA.scale((r.nextDouble() - 0.5) * 3));
            }
            Vec3[] pts = path(start, end, 0.34, r);
            float w = (float) (0.045 + 0.06 * r.nextDouble() * (surge ? 2.2 : 1));
            float life = (float) (1.6 + 3.6 * r.nextDouble());
            d.bolts.add(new Bolt(pts, w, col, t, life));
            if (r.nextFloat() < 0.55f) {
                Vec3 s2 = pts[8 + r.nextInt(14)];
                Vec3 d2 = dir.add(new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5)).normalize();
                d.bolts.add(new Bolt(path(s2, s2.add(d2.scale(0.8 + 1.6 * r.nextDouble())), 0.4, r), (float) (0.025 + 0.025 * r.nextDouble()), col, t,
                        (float) (1.2 + 2 * r.nextDouble())));
            }
            if (ground) {
                Vec3 g0 = pts[pts.length - 1];
                for (int i = 0; i < 6; i++) {
                    Vec3 v = new Vec3(r.nextDouble() - 0.5, 0.6 + r.nextDouble(), r.nextDouble() - 0.5).normalize().scale((4 + 5 * r.nextDouble()) / 20);
                    d.sparks.add(new Spark(g0.add(0, 0.1, 0), v, 0.1f + 0.12f * r.nextFloat(), col, t, 8 + 10 * r.nextFloat()));
                }
            }
        }
        // sparks: one every 0.12 ticks on the page; a third of that here keeps it light
        d.sparkIn -= dt * heavy;
        guard = 0;
        while (d.sparkIn <= 0 && guard++ < 24 && d.sparks.size() < 260) {
            d.sparkIn += 0.36;
            Vec3 dir = side.scale(r.nextDouble() - 0.5).add(up.scale(r.nextDouble() - 0.4)).add(toA.scale((r.nextDouble() - 0.5) * 0.5));
            if (dir.lengthSqr() < 1.0E-4) {
                continue;
            }
            dir = dir.normalize();
            float[] col = r.nextFloat() < 0.5f ? (dir.dot(toA) + d.lean * 0.5 > 0 ? d.colA : d.colB) : WHITE;
            double speed = (5 + 12 * r.nextDouble()) / 20;
            d.sparks.add(new Spark(c.add(dir.scale(R * 0.95)), dir.scale(speed), 0.06f + 0.14f * r.nextFloat() * r.nextFloat(), col, t,
                    8 + 14 * r.nextFloat()));
        }
    }

    // The fractal way from a to b (the page's path): each level splits the stretches and pushes the middle aside, less
    // at each level. 33 points.
    private static Vec3[] path(Vec3 a, Vec3 b, double rough, Random r) {
        List<Vec3> p = new ArrayList<>(List.of(a, b));
        double len = a.distanceTo(b);
        for (int lvl = 0; lvl < 5; lvl++) {
            List<Vec3> q = new ArrayList<>();
            q.add(p.get(0));
            double amp = len * rough * Math.pow(0.55, lvl);
            for (int i = 0; i < p.size() - 1; i++) {
                Vec3 mid = p.get(i).add(p.get(i + 1)).scale(0.5)
                        .add((r.nextDouble() - 0.5) * amp, (r.nextDouble() - 0.5) * amp, (r.nextDouble() - 0.5) * amp);
                q.add(mid);
                q.add(p.get(i + 1));
            }
            p = q;
        }
        return p.toArray(new Vec3[0]);
    }

    // Each bolt: a white-hot core thinning to the tip, its color around it, flickering out over its short life.
    private static void bolts(Duel d, double t) {
        for (Bolt b : d.bolts) {
            float k = (float) ((t - b.born) / b.life);
            if (k >= 1) {
                continue;
            }
            float flick = d.rnd.nextFloat() < 0.3f ? 0.65f : 1f;
            float op = (float) Math.pow(1 - k, 0.6) * flick;
            float[] hot = FxDraw.mix(b.col, WHITE, 0.55f);
            int n = b.pts.length;
            Vector3f prev = rel(b.pts[0]);
            for (int i = 1; i < n; i++) {
                Vector3f cur = rel(b.pts[i]);
                float u = i / (float) (n - 1);
                float tip = DbFx.smooth(u / 0.08f) * (1 - DbFx.smooth((u - 0.75f) / 0.25f) * 0.7f);
                float w = b.w * (1 - 0.75f * u);
                DbFx.rib(prev, cur, w * 1.6f, FxDraw.BEAM, 0.85f, hot, op * tip, true);
                DbFx.rib(prev, cur, w * 6, FxDraw.BEAM, 0.05f, b.col, op * tip * 0.3f, true);
                DbFx.rib(prev, cur, w * 1.2f, FxDraw.BEAM, 0.85f, hot, op * tip, false);
                prev = cur;
            }
        }
    }

    // Each spark: a short streak along its flight, its own size, speed and life.
    private static void sparks(Duel d, double t) {
        for (Spark s : d.sparks) {
            float k = (float) ((t - s.born) / s.life);
            if (k >= 1) {
                continue;
            }
            float op = (1 - k) * (1 - k);
            Vector3f p = rel(s.pos);
            Vector3f tail = rel(s.pos.subtract(s.vel.scale(2.2)));
            DbFx.rib(tail, p, s.size, FxDraw.STREAK, 0.9f, s.col, op, true);
            DbFx.bb(p, s.size * 3, 0, FxDraw.SOFT, 0.5f, FxDraw.mix(s.col, WHITE, 0.5f), op * 0.6f, true);
        }
    }

    // On top of the blast itself (the server's KI_BOOM, the approved one): the flash where the two energies let go and
    // the shock ring running out over the ground.
    private static void boom(Duel d, Vec3 boomAt, float age) {
        if (!DbFx.pass || !KiMesh.ready()) {
            return;
        }
        float size = 5;
        Vector3f c = rel(boomAt);
        float tp = age / 20f;
        if (tp < 0.3f) {
            KiMesh.P fl = KiMesh.p(KiMesh.FLASH);
            fl.color = d.winner > 0 ? FxDraw.mix(d.colA, WHITE, 0.6f) : d.winner < 0 ? FxDraw.mix(d.colB, WHITE, 0.6f) : WHITE;
            fl.alpha = 1 - tp / 0.3f;
            KiMesh.queue(KiMesh.Mesh.FLASH, KiMesh.sphere(c, (0.5f + 3 * Mth.sqrt(tp / 0.3f)) * size * 0.6f), fl, KiMesh.FRONT, 16);
        }
        if (tp < 0.9f && !Double.isNaN(d.groundY)) {
            float k = tp / 0.9f;
            KiMesh.P rg = KiMesh.p(28);
            rg.time = tp;
            rg.ringR = (0.6f + 13 * DbFx.easeOut(k)) * 1.2f;
            rg.w = 0.7f;
            rg.alpha = 1 - DbFx.smooth((k - 0.35f) / 0.65f);
            rg.smoke = 0;
            rg.glow = true;
            Vector3f g = new Vector3f(c.x, (float) (d.groundY - DbFx.cam.y) + 0.04f, c.z);
            KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(g, rg.ringR + rg.w * 1.6f + 1), rg, KiMesh.BOTH, 6);
        }
    }

    // ---------- the cinematic camera (only for the two) ----------

    public static boolean cutscene() {
        for (Duel d : DUELS.values()) {
            if (d.camera) {
                return true;
            }
        }
        return false;
    }

    public static FaceCutscene.Shot shot(float partial) {
        Duel cam = null;
        for (Duel d : DUELS.values()) {
            if (d.camera) {
                cam = d;
                break;
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (cam == null || mc.level == null || cam.aOrigin == null || cam.bOrigin == null) {
            return null;
        }
        Duel d = cam;
        double now = now() + partial;
        int me = side(d);
        // the page's frame, seen from the player's own side: "mine" stands where Goku stood (+z)
        Vec3 mine = me >= 0 ? d.aOrigin : d.bOrigin;
        Vec3 other = me >= 0 ? d.bOrigin : d.aOrigin;
        Vec3 toMine = mine.subtract(other);
        Vec3 flatTo = new Vec3(toMine.x, 0, toMine.z);
        flatTo = flatTo.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flatTo.normalize();
        Vec3 right = new Vec3(-flatTo.z, 0, flatTo.x);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 C = d.over() && d.boomAt != null ? d.boomAt : d.at(partial);
        double floor = (mine.y + other.y) / 2 - 1.42;
        Vec3 Cg = new Vec3(C.x, floor, C.z);
        float surgeMine = me >= 0 ? d.surgeA : d.surgeB;
        float surgeOther = me >= 0 ? d.surgeB : d.surgeA;
        Vec3 pos;
        Vec3 look;
        if (d.over()) {
            // the wide shot of the blast
            int win = d.winner * (me >= 0 ? 1 : -1);
            pos = Cg.add(right.scale(17)).add(up.scale(6)).add(flatTo.scale(win >= 0 ? 10 : -10));
            look = new Vec3(C.x, floor + 1.8, C.z);
            if (!d.endCut) {
                d.endCut = true;
                d.camInit = false;
            }
        } else if (d.state == 1) {
            // running with the sphere to the loser
            int win = d.lean > 0 ? 1 : -1;
            win *= me >= 0 ? 1 : -1;
            pos = Cg.add(right.scale(6)).add(up.scale(2)).add(flatTo.scale(win > 0 ? 5 : -5));
            look = C;
            if (d.shot != -2) {
                d.shot = -2;
                d.camInit = false;
                cut();
            }
        } else {
            if (surgeMine > 0.9f && d.shot != 2) {
                cutTo(d, 2, now);
            } else if (surgeOther > 0.9f && d.shot != 4) {
                cutTo(d, 4, now);
            } else if (d.shot < 0 || now - d.shotAt > SHOT_LEN) {
                int n;
                do {
                    n = d.rnd.nextInt(SHOTS);
                } while (n == d.shot);
                cutTo(d, n, now);
            }
            float u = (float) Mth.clamp((now - d.shotAt) / SHOT_LEN, 0, 1);
            float sway = Mth.sin((float) now * 0.03f);
            switch (d.shot) {
                case 0 -> pos = Cg.add(right.scale(14 - 3 * u)).add(up.scale(2.6)).add(flatTo.scale(4 - 8 * u));
                case 1 -> pos = Cg.add(right.scale(5.2)).add(up.scale(0.55)).add(flatTo.scale(4 - 2.5 * u));
                case 2 -> pos = mine.add(flatTo.scale(5.6 - 2.2 * u)).add(right.scale(2.8 - 0.9 * u)).add(up.scale(0.7));
                case 3 -> {
                    double a = 0.5 + 1.7 * u;
                    pos = Cg.add(right.scale(Math.sin(a) * 9)).add(up.scale(3.4)).add(flatTo.scale(Math.cos(a) * 9));
                }
                case 4 -> pos = other.subtract(flatTo.scale(5.6 - 2.2 * u)).add(right.scale(-2.8 + 0.9 * u)).add(up.scale(0.8));
                case 5 -> pos = Cg.add(right.scale(2.5)).add(up.scale(15 - 5 * u)).add(flatTo.scale(1.5));
                default -> pos = mine.add(flatTo.scale(2.7 - 0.5 * u)).add(right.scale(1.5 + 0.3 * sway)).add(up.scale(0.53));
            }
            look = d.shot == 1 ? C.add(0, 0.9, 0) : d.shot == 6 ? mine.add(0, 0.38, 0).lerp(C, 0.35 + 0.4 * u) : C;
        }
        // never inside a wall: pulled in to the first block between the point and the shot
        var hit = mc.level.clip(new ClipContext(C, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = pos.subtract(C);
            double l = back.length();
            if (l > 1.0E-3) {
                pos = hit.getLocation().subtract(back.scale(0.4 / l));
            }
        }
        double frame = d.camAt < 0 ? 0 : Math.min(3, now - d.camAt);
        d.camAt = Math.max(d.camAt, now);
        if (!d.camInit || d.camPos == null) {
            d.camPos = pos;
            d.camLook = look;
            d.camInit = true;
        } else {
            double k = 1 - Math.exp(-frame / 2.4);
            d.camPos = d.camPos.lerp(pos, k);
            d.camLook = d.camLook.lerp(look, k);
        }
        Vec3 dir = d.camLook.subtract(d.camPos);
        if (dir.lengthSqr() < 1.0E-6) {
            dir = flatTo.scale(-1);
        }
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(d.camPos, yaw, pitch);
    }

    private static void cutTo(Duel d, int n, double now) {
        d.shot = n;
        d.shotAt = now;
        d.camInit = false;
        cut();
    }

    // The white flash of a cut (the page's cutWhite: 0.55 fading in 0.14 s).
    private static void cut() {
        ScreenFx.flash(0xFFFFFF, 0.55f, 3);
    }

    // ---------- the bar (only on the screen of the two) ----------

    public static void renderGui(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (hudShown <= 0 || mc.player == null || mc.options.hideGui) {
            return;
        }
        Duel d = DUELS.get(hudId);
        if (d == null || d.over()) {
            return;
        }
        Font font = mc.font;
        int sw = g.guiWidth();
        int w = Math.min(280, (int) (sw * 0.6f));
        int x0 = (sw - w) / 2;
        int y0 = 12;
        boolean meA = hudSide >= 0;
        int colMine = 0xFF000000 | (meA ? d.rgbA : d.rgbB);
        int colOther = 0xFF000000 | (meA ? d.rgbB : d.rgbA);
        // lean from the player's side: +1 = they are winning (their color fills from the left)
        float lean = hudLean * (meA ? 1 : -1);
        float k = Mth.clamp((1 + lean) / 2, 0, 1);
        Entity eMine = mc.level.getEntity(meA ? d.aId : d.bId);
        Entity eOther = mc.level.getEntity(meA ? d.bId : d.aId);
        Component nMine = eMine != null ? eMine.getDisplayName() : Component.literal("?");
        Component nOther = eOther != null ? eOther.getDisplayName() : Component.literal("?");
        g.drawString(font, nMine, x0, y0, 0xFFFFFFFF, true);
        g.drawString(font, nOther, x0 + w - font.width(nOther), y0, 0xFFFFFFFF, true);
        String secs = String.format(java.util.Locale.ROOT, "%.1f s", Math.max(0, hudSecs));
        g.drawString(font, secs, x0 + (w - font.width(secs)) / 2, y0, 0xFFE8E8E8, true);
        int by = y0 + 11;
        int bh = 8;
        // the frame, the two colors meeting at the white mark
        g.fill(x0 - 2, by - 2, x0 + w + 2, by + bh + 2, 0xD8FFFFFF);
        g.fill(x0, by, x0 + w, by + bh, colOther);
        int split = x0 + Math.round(w * k);
        g.fill(x0, by, split, by + bh, colMine);
        g.fill(split - 2, by - 3, split + 2, by + bh + 3, 0xFFFFFFFF);
        // the energies (Shift: the all or nothing, back in 9 s)
        int ey = by + bh + 5;
        int ew = (int) (w * 0.3f);
        g.fill(x0, ey, x0 + ew, ey + 3, 0x40FFFFFF);
        g.fill(x0, ey, x0 + Math.round(ew * Mth.clamp(hudMine, 0, 1)), ey + 3, colMine);
        g.fill(x0 + w - ew, ey, x0 + w, ey + 3, 0x40FFFFFF);
        int oe = Math.round(ew * Mth.clamp(hudOther, 0, 1));
        g.fill(x0 + w - oe, ey, x0 + w, ey + 3, colOther);
        if (hudMine >= 0.5f) {
            Component shift = Component.translatable("clash.battlegroundsmod.shift");
            g.drawString(font, shift, x0, ey + 6, 0xFFFFFFFF, true);
        }
        // APERTE! pulsing
        float blink = 0.55f + 0.45f * Math.abs(Mth.sin((float) (now() + delta.getGameTimeDeltaPartialTick(true)) * 0.55f));
        Component press = Component.translatable("clash.battlegroundsmod.press");
        int alpha = Mth.clamp(Math.round(blink * 255), 0, 255);
        g.pose().pushPose();
        float s = 2f;
        g.pose().translate(sw / 2f, ey + 16, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, press, -font.width(press) / 2, 0, (alpha << 24) | 0xFFFFFF, true);
        g.pose().popPose();
    }
}
