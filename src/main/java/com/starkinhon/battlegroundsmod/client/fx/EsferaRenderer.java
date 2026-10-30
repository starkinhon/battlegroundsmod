package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Bills' sphere of destruction, a small sun drawn only with shader light (no textures): a white-hot core, a boiling
// orange-yellow body with darker spots, a bright rim and a cross of light through it. The same look in space
// (SpaceScene) and when it falls on the target, following its head on the path the server lands it with.
public final class EsferaRenderer {
    private record Fall(int target, Vec3 start, Vec3 lastHead, int ticks, float radius, long born) {
    }

    private static final List<Fall> FALLS = new ArrayList<>();
    private static final float[] CORE = FxDraw.rgb(0xFFFBEA);
    private static final float[] HOT = FxDraw.rgb(0xFFE65A);
    private static final float[] BODY = FxDraw.rgb(0xFF9A1E);
    private static final float[] EMBER = FxDraw.rgb(0xD8420E);
    private static final float[] SPOT = FxDraw.rgb(0x5A1606);

    private EsferaRenderer() {
    }

    public static void fall(int targetId, Vec3 start, Vec3 lastHead, boolean awakened) {
        EsferaSpec spec = EsferaSpec.get(awakened);
        FALLS.add(new Fall(targetId, start, lastHead, Math.max(1, spec.tempo().quedaTicks()), (float) spec.esfera().raioQueda(), ScreenFx.now()));
    }

    public static boolean active() {
        return !FALLS.isEmpty();
    }

    static void render(Vec3 camera, double now, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            FALLS.clear();
            return;
        }
        FALLS.removeIf(f -> now - f.born() > f.ticks() + 1);
        for (Fall f : FALLS) {
            Entity target = level.getEntity(f.target());
            Vec3 head = target != null && target.isAlive() ? target.getPosition(partial).add(0, target.getBbHeight(), 0) : f.lastHead();
            double k = (now - f.born()) / f.ticks();
            Vec3 center = EsferaSpec.fallAt(f.start(), head, k);
            Vec3 ahead = EsferaSpec.fallAt(f.start(), head, Math.min(1, k + 0.02));
            Vec3 dir = ahead.subtract(center);
            drawSun(center.subtract(camera).toVector3f(), dir.lengthSqr() < 1.0E-6 ? new Vector3f(0, -1, 0) : dir.normalize().toVector3f(), f.radius(),
                    (float) (now - f.born()), f.target() * 131 + 7);
        }
    }

    // dir: where it flies (fire and light left behind it), or null when it stays still.
    static void drawSun(Vector3f at, Vector3f dir, float r, float age, int seed) {
        // Corona, wide and soft, then the body.
        FxDraw.billboard(at, r * 4.2f, 0, FxDraw.SOFT, 0.05f, EMBER, 0.4f, true);
        FxDraw.billboard(at, r * 3.0f, 0, FxDraw.SOFT, 0.2f, BODY, 0.55f, true);
        int count = Mth.clamp((int) (40 + r * 10), 40, 110);
        Vector3f axis = new Vector3f(0.2f, 1, 0.3f).normalize();
        for (int k = 0; k < count; k++) {
            float layer = FxWorld.hash(seed, k, 1);
            Vector3f d = FxWorld.sphere(seed, k);
            d.rotateAxis(age * (0.05f - 0.03f * layer) + FxWorld.hash(seed, k, 2) * 6, axis.x, axis.y, axis.z);
            float boil = 1 + 0.12f * Mth.sin(age * 0.3f + k * 1.3f);
            Vector3f pos = new Vector3f(d).mul(r * (0.3f + 0.55f * layer)).add(at);
            float[] color = layer < 0.45f ? FxDraw.mix(HOT, BODY, layer / 0.45f) : FxDraw.mix(BODY, EMBER, (layer - 0.45f) / 0.55f * 0.6f);
            FxDraw.billboard(pos, r * (0.5f + 0.4f * layer) * boil, FxWorld.hash(seed, k, 3) * 6 + age * 0.03f, FxDraw.FIRE, 0.1f + 0.35f * layer,
                    color, 0.95f, true);
        }
        // Dark spots drifting over the surface, like the burnt patches of the reference.
        for (int k = 0; k < 14; k++) {
            Vector3f d = FxWorld.sphere(seed + 3, k);
            d.rotateAxis(age * 0.02f, axis.x, axis.y, axis.z);
            Vector3f pos = new Vector3f(d).mul(r * 0.9f).add(at);
            FxDraw.billboard(pos, r * (0.12f + 0.12f * FxWorld.hash(seed, k, 9)), k, FxDraw.SMOKE, 0.3f, SPOT, 0.55f, false);
        }
        // Bright rim and the white core.
        FxDraw.billboard(at, r * 2.05f, 0, FxDraw.RING, 0.08f, HOT, 0.9f, true);
        FxDraw.billboard(at, r * 1.3f, 0, FxDraw.SOFT, 0.85f, CORE, 0.95f, true);
        // A cross of light through the middle, slowly turning.
        for (int k = 0; k < 2; k++) {
            float a = age * 0.01f + k * 1.9f;
            Vector3f across = new Vector3f(Mth.cos(a), Mth.sin(a) * 0.6f, Mth.sin(a) * 0.8f).normalize().mul(r * 2.6f);
            FxDraw.ribbon(new Vector3f(at).sub(across), new Vector3f(at).add(across), r * 0.16f, FxDraw.STREAK, 0.9f, CORE, 0.8f, true);
        }
        if (dir != null) {
            // Fire and light trailing behind it.
            for (int j = 1; j <= 12; j++) {
                float f = j / 12f;
                Vector3f pos = new Vector3f(dir).mul(-r * (0.5f + f * 3.2f)).add(at)
                        .add((FxWorld.hash(seed, j, 5) - 0.5f) * r * 0.6f, (FxWorld.hash(seed, j, 6) - 0.5f) * r * 0.6f, (FxWorld.hash(seed, j, 7) - 0.5f) * r * 0.6f);
                FxDraw.billboard(pos, r * (1.3f - f * 0.7f), age * 0.1f + j, FxDraw.FIRE, 0.25f + f * 0.6f, FxDraw.mix(BODY, EMBER, f), 1 - f * 0.8f,
                        true);
            }
            FxDraw.ribbon(at, new Vector3f(dir).mul(-r * 5).add(at), r * 1.2f, FxDraw.BEAM, 0.3f, HOT, 0.5f, true);
        }
    }
}
