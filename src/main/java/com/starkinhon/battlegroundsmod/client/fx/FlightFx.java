package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.FlightClient;
import com.starkinhon.battlegroundsmod.client.FlightPose;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// The Dragon Ball flight's effects (forja voo/voo.html, approved by Stark 2026-11-10), for everyone who flies:
// the ki shell of the boost (souls_ki kind 17: a cone from 1 block ahead of the head opening back round the body,
// flames running back along it and a torn back edge), the ki ring of the boost and the take-off (kind 18: one spiky
// ring blowing out, facing the way it goes) and the trail from the feet (souls_vfx shape 35: strands of ki opening back,
// only with momentum, strong on the boost). In first person your own shell and trail are left out (they would wrap the
// camera); the rings still show.
public final class FlightFx {
    private static final float[] AURA = {0.85f, 0.95f, 1f};
    private static final double RING_LIFE = 0.42;
    private static final int TRAIL = 30;

    private record Ring(Vec3 at, Vec3 dir, float scale, double born, float seed, float[] color) {
    }

    private static final List<Ring> RINGS = new ArrayList<>();
    // per player: the feet over the last ticks (newest first)
    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();
    // per flier skimming low and fast: the ground under it and how strong the wake was there (newest first)
    private record Wake(Vec3 at, float strength, boolean water) {
    }

    private static final Map<Integer, ArrayDeque<Wake>> WAKES = new HashMap<>();
    private static final int WAKE = 34;

    private FlightFx() {
    }

    private static double seconds(double ticks) {
        return ticks / 20.0;
    }

    // A ki ring at `at` facing `dir` (scale 1 = the boost's, 0.8 the take-off's).
    public static void burst(Vec3 at, Vec3 dir, float scale) {
        burst(at, dir, scale, AURA);
    }

    public static void burst(Vec3 at, Vec3 dir, float scale, float[] color) {
        RINGS.add(new Ring(at, dir, scale, ScreenFx.now(), (float) (Math.random() * 50), color));
    }

    // The ki color of a flier's rings: the players' white-blue, each flying soul its own (boss/AirCombat).
    public static float[] colorOf(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            return AURA;
        }
        return switch (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath()) {
            case "goku" -> new float[] {0.55f, 0.8f, 1f};
            case "vegeta" -> new float[] {1f, 0.9f, 0.45f};
            case "piccolo" -> new float[] {0.9f, 1f, 0.75f};
            case "kuririn" -> new float[] {1f, 0.85f, 0.6f};
            case "freeza" -> new float[] {0.95f, 0.65f, 1f};
            default -> AURA;
        };
    }

    // Everyone flying, or fading out of it: the players and the flying souls.
    private static List<LivingEntity> fliers(Minecraft minecraft) {
        List<LivingEntity> out = new ArrayList<>(minecraft.level.players());
        Set<Integer> ids = new HashSet<>(FlightClient.ids());
        ids.addAll(FlightPose.ids());
        for (int id : ids) {
            if (minecraft.level.getEntity(id) instanceof LivingEntity e && !(e instanceof Player)) {
                out.add(e);
            }
        }
        return out;
    }

    public static boolean active() {
        return !RINGS.isEmpty() || !TRAILS.isEmpty() || !WAKES.isEmpty() || anyFlying();
    }

    private static boolean anyFlying() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        for (LivingEntity p : fliers(minecraft)) {
            if (FlightClient.flying(p)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        RINGS.clear();
        TRAILS.clear();
        WAKES.clear();
    }

    // Every client tick: the feet go into each flier's trail.
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        for (LivingEntity p : fliers(minecraft)) {
            Vec3 up = FlightPose.lastUp(p);
            if (up == null) {
                continue;
            }
            ArrayDeque<Vec3> trail = TRAILS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            trail.addFirst(p.position().add(0, FlightPose.pivot(), 0).subtract(up.scale(0.95)));
            while (trail.size() > TRAIL) {
                trail.removeLast();
            }
            wakeTick(minecraft, p);
        }
        WAKES.entrySet().removeIf(en -> {
            if (minecraft.level.getEntity(en.getKey()) instanceof LivingEntity p && FlightPose.lastUp(p) != null) {
                return false;
            }
            // the flier is gone or landed: what is on the ground fades by itself
            ArrayDeque<Wake> w = en.getValue();
            w.addFirst(w.isEmpty() ? new Wake(Vec3.ZERO, 0, false) : new Wake(w.peekFirst().at(), 0, w.peekFirst().water()));
            while (w.size() > WAKE) {
                w.removeLast();
            }
            return w.stream().allMatch(k -> k.strength() < 0.01f);
        });
        TRAILS.keySet().removeIf(id -> !(minecraft.level.getEntity(id) instanceof LivingEntity p) || FlightPose.lastUp(p) == null);
    }

    // The page's wake (Kuririn's rasante): flying under 8 blocks over the ground faster than 15 blocks a second, the
    // ground under the flier is torn into two bands of dust opening out behind it (stronger on the boost).
    private static void wakeTick(Minecraft minecraft, LivingEntity p) {
        double sp = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo).length() * 20;
        BlockPos below = p.blockPosition();
        int floor = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, below.getX(), below.getZ());
        double y = p.getY() - floor;
        float low = FlightClient.flying(p) && !p.isInvisible()
                ? Mth.clamp((float) (8 - y) / 6, 0, 1) * Mth.clamp((float) (sp - 15) / 20, 0, 1) : 0;
        ArrayDeque<Wake> w = WAKES.get(p.getId());
        if (w == null) {
            if (low < 0.01f) {
                return;
            }
            w = new ArrayDeque<>();
            WAKES.put(p.getId(), w);
        }
        // Over water (Stark 2026-11-14): the same rasante as foam on the surface (shape 42, param 1), a bit stronger.
        boolean water = minecraft.level.getFluidState(new BlockPos(below.getX(), floor - 1, below.getZ())).is(net.minecraft.tags.FluidTags.WATER);
        w.addFirst(new Wake(new Vec3(p.getX(), water ? floor - 0.06 : floor + 0.08, p.getZ()), water ? Math.min(1, low * 1.2f) : low, water));
        while (w.size() > WAKE) {
            w.removeLast();
        }
        if (w.stream().allMatch(k -> k.strength() < 0.01f)) {
            WAKES.remove(p.getId());
        }
    }

    private static void wakes(Vec3 camera, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        for (Map.Entry<Integer, ArrayDeque<Wake>> en : WAKES.entrySet()) {
            ArrayDeque<Wake> history = en.getValue();
            if (history.size() < 3) {
                continue;
            }
            float boost = minecraft.level.getEntity(en.getKey()) instanceof LivingEntity p ? FlightPose.boost(p, partial) : 0;
            int n = history.size();
            Vector3f[] pts = new Vector3f[n];
            float[] widths = new float[n];
            float[] us = new float[n];
            boolean[] wet = new boolean[n];
            float strongest = 0;
            boolean anyWet = false, anyDry = false;
            int i = 0;
            for (Wake k : history) {
                pts[i] = k.at().subtract(camera).toVector3f();
                us[i] = i / (float) (n - 1);
                // the page's side: 1.4 + 2.5 boost each way, opening as it ages; how strong it was there scales it
                widths[i] = 2 * (1.4f + 2.5f * boost) * (k.strength() < 0.01f ? 0 : 0.55f + 0.45f * k.strength());
                wet[i] = k.water();
                anyWet |= k.water();
                anyDry |= !k.water();
                strongest = Math.max(strongest, k.strength());
                i++;
            }
            float o = Math.min(1, strongest * 1.5f);
            // dust over the ground, foam over the water (a shore crossed: each part of the strip its own)
            if (anyDry) {
                FxDraw.stripFlat(pts, masked(widths, wet, false), us, 42, 0, AURA, o, false);
            }
            if (anyWet) {
                FxDraw.stripFlat(pts, masked(widths, wet, true), us, 42, 1, AURA, o, false);
            }
        }
    }

    private static float[] masked(float[] widths, boolean[] wet, boolean keepWet) {
        float[] out = new float[widths.length];
        for (int i = 0; i < widths.length; i++) {
            out[i] = wet[i] == keepWet ? widths[i] : 0;
        }
        return out;
    }

    static void render(Vec3 camera, double now, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double t = seconds(now);
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        for (LivingEntity p : fliers(minecraft)) {
            if (p.isInvisible()) {
                // a soul's Zanzoken: gone for a moment, shell and trail too
                continue;
            }
            Vec3 up = FlightPose.bodyUp(p, partial);
            if (up == null) {
                continue;
            }
            boolean self = p == minecraft.player && firstPerson;
            Vec3 center = p.getPosition(partial).add(0, FlightPose.pivot(), 0);
            float weight = FlightPose.weight(p, partial);
            if (!self) {
                shell(p, center, up, camera, t, partial);
                trail(p, center, up, camera, weight, partial);
            }
        }
        wakes(camera, partial);
        rings(camera, now);
    }

    private static void shell(LivingEntity p, Vec3 center, Vec3 up, Vec3 camera, double t, float partial) {
        float a = FlightPose.shell(p, partial);
        if (a <= 0.01f || !KiMesh.ready()) {
            return;
        }
        // the nose 1 block ahead of the head (Stark), the cone opening back round the whole body
        Vector3f nose = center.add(up.scale(2.05)).subtract(camera).toVector3f();
        KiMesh.P k = KiMesh.p(KiMesh.FLIGHT_SHELL);
        k.time = (float) t;
        k.alpha = Math.min(1, a);
        k.color = AURA;
        k.seed = (p.getId() % 97) * 0.37f;
        k.len = 5.2f;
        KiMesh.queue(KiMesh.Mesh.TUBE, KiMesh.along(nose, up.scale(-1).toVector3f()), k, KiMesh.BOTH, 10);
    }

    private static void trail(LivingEntity p, Vec3 center, Vec3 up, Vec3 camera, float weight, float partial) {
        ArrayDeque<Vec3> history = TRAILS.get(p.getId());
        if (history == null || history.size() < 2) {
            return;
        }
        Vec3 vel = FlightPose.velocity(p, partial);
        float boost = FlightPose.boost(p, partial);
        float sp = (float) (vel.length() / Math.max(1, FlightClient.boostSpeedOf(p)));
        float o = Mth.clamp(sp * 0.6f + boost * 0.8f, 0, 1) * weight;
        if (o < 0.02f) {
            return;
        }
        int n = history.size() + 1;
        Vector3f[] pts = new Vector3f[n];
        float[] widths = new float[n];
        float[] us = new float[n];
        pts[0] = center.subtract(up.scale(0.95)).subtract(camera).toVector3f();
        Iterator<Vec3> it = history.iterator();
        for (int i = 1; i < n; i++) {
            pts[i] = it.next().subtract(camera).toVector3f();
        }
        float w = 2 * (0.18f + 0.5f * boost);
        for (int i = 0; i < n; i++) {
            us[i] = i / (float) (n - 1);
            widths[i] = w * (1 + us[i] * 0.6f);
        }
        FxDraw.strip(pts, widths, us, 35, 0, AURA, o, true);
    }

    private static void rings(Vec3 camera, double now) {
        if (RINGS.isEmpty()) {
            return;
        }
        Iterator<Ring> it = RINGS.iterator();
        while (it.hasNext()) {
            Ring r = it.next();
            double life = seconds(now - r.born()) / RING_LIFE;
            if (life >= 1 || life < -0.5) {
                it.remove();
                continue;
            }
            if (!KiMesh.ready() || life < 0) {
                continue;
            }
            float L = (float) life;
            float s = (1.6f + 7 * (1 - (float) Math.pow(1 - L, 3))) * r.scale();
            KiMesh.P k = KiMesh.p(KiMesh.FLIGHT_RING);
            k.life = L;
            k.seed = r.seed();
            k.color = r.color();
            Vector3f n = r.dir().toVector3f();
            if (n.lengthSquared() < 1.0E-6f) {
                n.set(0, 1, 0);
            }
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), n.normalize());
            KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.disc(r.at().subtract(camera).toVector3f(), s, q), k, KiMesh.BOTH, 12);
        }
    }
}
