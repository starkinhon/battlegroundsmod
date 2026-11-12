package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.FlightClient;
import com.starkinhon.battlegroundsmod.client.FlightPose;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
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

    private record Ring(Vec3 at, Vec3 dir, float scale, double born, float seed) {
    }

    private static final List<Ring> RINGS = new ArrayList<>();
    // per player: the feet over the last ticks (newest first)
    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();

    private FlightFx() {
    }

    private static double seconds(double ticks) {
        return ticks / 20.0;
    }

    // A ki ring at `at` facing `dir` (scale 1 = the boost's, 0.8 the take-off's).
    public static void burst(Vec3 at, Vec3 dir, float scale) {
        RINGS.add(new Ring(at, dir, scale, ScreenFx.now(), (float) (Math.random() * 50)));
    }

    public static boolean active() {
        return !RINGS.isEmpty() || !TRAILS.isEmpty() || anyFlying();
    }

    private static boolean anyFlying() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        for (Player p : minecraft.level.players()) {
            if (FlightClient.flying(p)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        RINGS.clear();
        TRAILS.clear();
    }

    // Every client tick: the feet go into each flier's trail.
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        for (Player p : minecraft.level.players()) {
            Vec3 up = FlightPose.lastUp(p);
            if (up == null) {
                continue;
            }
            ArrayDeque<Vec3> trail = TRAILS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            trail.addFirst(p.position().add(0, FlightPose.pivot(), 0).subtract(up.scale(0.95)));
            while (trail.size() > TRAIL) {
                trail.removeLast();
            }
        }
        TRAILS.keySet().removeIf(id -> !(minecraft.level.getEntity(id) instanceof Player p) || FlightPose.lastUp(p) == null);
    }

    static void render(Vec3 camera, double now, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double t = seconds(now);
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        for (Player p : minecraft.level.players()) {
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
        rings(camera, now);
    }

    private static void shell(Player p, Vec3 center, Vec3 up, Vec3 camera, double t, float partial) {
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

    private static void trail(Player p, Vec3 center, Vec3 up, Vec3 camera, float weight, float partial) {
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
            k.color = AURA;
            Vector3f n = r.dir().toVector3f();
            if (n.lengthSquared() < 1.0E-6f) {
                n.set(0, 1, 0);
            }
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), n.normalize());
            KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.disc(r.at().subtract(camera).toVector3f(), s, q), k, KiMesh.BOTH, 12);
        }
    }
}
