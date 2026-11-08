package com.starkinhon.battlegroundsmod.client.outfit;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.client.fx.FxRenderTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// Kokushibo's sword stretching when the H key ("Alongar a Espada", power/powers/MoonDemon.stretch) puts it into the
// awakened kit, and shrinking back when it is pressed again: the "carne viva" scene Stark approved
// (forja/kokushibo-espada/espada-carne-viva.html, scene "Alongar a espada"), ported line by line. The blade comes alive,
// swells and is pulled out to the long sword's length, then its three blades grow out of it one by one (by the guard, by
// the tip, on top), and the flesh calms down. Going back plays the same scene backwards.
// Drawn in place of the held sword by KokushiboSwordRenderer; the change comes from the server (SwordFx MOON_STRETCH).
public final class SwordStretch {
    // The scene's length in ticks (5.5 s, as approved).
    public static final float LENGTH = 110;
    // The branches grow in this order: {branch, start tick, ticks}.
    private static final float[][] GROW = {{1, 46, 30}, {2, 54, 30}, {0, 62, 24}};
    private static final float BLADE_BASE = KokushiboSwordRenderer.BLADE_BASE;

    // A change of the sword: stretched (on) or back to normal, at that entity's tick.
    private record Change(boolean on, float at) {
    }

    private static final Map<Integer, Change> CHANGES = new HashMap<>();
    // The entity whose held item is being drawn right now (ItemInHandRendererMixin), so the item renderer knows whose
    // sword it is.
    private static LivingEntity holder;

    private static Meta meta;
    private static boolean broken;

    private SwordStretch() {
    }

    public static void holder(LivingEntity entity) {
        holder = entity;
    }

    public static LivingEntity holder() {
        return holder;
    }

    // From the server: the entity's sword starts stretching (on) or going back. Turned around midway, it goes back from
    // where it was instead of jumping.
    public static void set(int entityId, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (entity == null) {
            CHANGES.remove(entityId);
            return;
        }
        float now = entity.tickCount;
        Change old = CHANGES.get(entityId);
        float from = old == null ? (on ? 0 : LENGTH) : scene(old, now);
        if (from < 0) {
            from = on ? 0 : LENGTH;
        }
        CHANGES.put(entityId, new Change(on, on ? now - from : now - (LENGTH - from)));
    }

    // Where in the scene the entity's sword is: -1 the normal sword, LENGTH the long one at rest, anything between
    // mid-change.
    public static float tick(LivingEntity entity, float partial) {
        Change change = CHANGES.get(entity.getId());
        Minecraft mc = Minecraft.getInstance();
        boolean self = entity == mc.player;
        if (change == null) {
            // his own sword: the synced state (rejoining with it stretched, say)
            return self && entity.getData(ModAttachments.POWER).moonSword ? LENGTH : -1;
        }
        float t = scene(change, entity.tickCount + partial);
        if (t < 0 || self && change.on && t >= LENGTH && !entity.getData(ModAttachments.POWER).moonSword) {
            CHANGES.remove(entity.getId());
            return -1;
        }
        return t;
    }

    private static float scene(Change change, float now) {
        float elapsed = Math.max(0, now - change.at);
        if (change.on) {
            return Math.min(LENGTH, elapsed);
        }
        return elapsed >= LENGTH ? -1 : LENGTH - elapsed;
    }

    public static void clear() {
        CHANGES.clear();
    }

    // Each branch's vertices, where along it each one is (0 at the blade, 1 at its tip), its centre line and each
    // vertex's offset from it (to grow it out along itself).
    private static final class Branch {
        int from, n;
        float[] s;
        float[][] centre;
        float[] off;

        float[] at(float u) {
            int bins = centre.length - 1;
            u = Math.max(0, Math.min(1, u)) * bins;
            int j = Math.min(bins - 1, (int) Math.floor(u));
            float k = u - j;
            float[] a = centre[j], b = centre[j + 1];
            return new float[] {a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
        }
    }

    // The long sword's data the scene needs (models/mesh/kokushibo_katana_long_meta.bin, taken from the approved page):
    // how many vertices are the blade, guard and handle (the same as the normal sword's, only stretched), their height
    // on the normal sword, the blade's stretch K, and each branch's vertex count and s.
    private static final class Meta {
        int main;
        float k;
        float[] origY;
        Branch[] branches;
        float[] pos, nor;
    }

    private static Meta meta() {
        if (meta != null || broken) {
            return meta;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "models/mesh/kokushibo_katana_long_meta.bin");
        try (InputStream raw = Minecraft.getInstance().getResourceManager().open(id); DataInputStream in = new DataInputStream(raw)) {
            Meta m = new Meta();
            m.main = in.readInt();
            int count = in.readInt();
            m.k = in.readFloat();
            int[] sizes = new int[count];
            for (int b = 0; b < count; b++) {
                sizes[b] = in.readInt();
            }
            m.origY = new float[m.main];
            for (int i = 0; i < m.main; i++) {
                m.origY[i] = in.readFloat();
            }
            float[] d = KokushiboSwordRenderer.katanaLong().data();
            int from = m.main;
            m.branches = new Branch[count];
            for (int b = 0; b < count; b++) {
                Branch br = new Branch();
                br.from = from;
                br.n = sizes[b];
                br.s = new float[br.n];
                for (int i = 0; i < br.n; i++) {
                    br.s[i] = in.readFloat();
                }
                // the centre line: the vertices averaged in 24 steps along s
                int bins = 24;
                float[][] acc = new float[bins + 1][4];
                for (int i = 0; i < br.n; i++) {
                    int v = from + i, j = Math.round(br.s[i] * bins);
                    acc[j][0] += d[v * 8];
                    acc[j][1] += d[v * 8 + 1];
                    acc[j][2] += d[v * 8 + 2];
                    acc[j][3]++;
                }
                br.centre = new float[bins + 1][];
                for (int j = 0; j <= bins; j++) {
                    if (acc[j][3] > 0) {
                        br.centre[j] = new float[] {acc[j][0] / acc[j][3], acc[j][1] / acc[j][3], acc[j][2] / acc[j][3]};
                    }
                }
                float[] first = null;
                for (float[] c : br.centre) {
                    if (c != null) {
                        first = c;
                        break;
                    }
                }
                for (int j = 0; j <= bins; j++) {
                    if (br.centre[j] == null) {
                        br.centre[j] = j > 0 ? br.centre[j - 1].clone() : first.clone();
                    }
                }
                br.off = new float[br.n * 3];
                for (int i = 0; i < br.n; i++) {
                    int v = from + i;
                    float[] c = br.at(br.s[i]);
                    br.off[i * 3] = d[v * 8] - c[0];
                    br.off[i * 3 + 1] = d[v * 8 + 1] - c[1];
                    br.off[i * 3 + 2] = d[v * 8 + 2] - c[2];
                }
                m.branches[b] = br;
                from += br.n;
            }
            int vertices = d.length / 8;
            if (from != vertices) {
                throw new IllegalStateException("Long sword data covers " + from + " of " + vertices + " vertices");
            }
            m.pos = new float[vertices * 3];
            m.nor = new float[vertices * 3];
            for (int v = 0; v < vertices; v++) {
                m.nor[v * 3] = d[v * 8 + 5];
                m.nor[v * 3 + 1] = d[v * 8 + 6];
                m.nor[v * 3 + 2] = d[v * 8 + 7];
            }
            meta = m;
        } catch (IOException | RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Kokushibo's sword stretching can't be drawn; the long sword shows up at once", e);
        }
        return meta;
    }

    // Draws the sword at that tick of the scene, in sword space through pose. False when it can't (then the caller draws
    // the plain long sword).
    static boolean render(float tick, float clock, PoseStack.Pose pose, MultiBufferSource buffer, int light) {
        Meta m = meta();
        if (m == null) {
            return false;
        }
        float[] d = KokushiboSwordRenderer.katanaLong().data();
        float[] p = m.pos;
        // how alive it is
        float flesh = tick < 14 ? easeOut(tick / 14) : tick < 88 ? 1 : 1 - easeInOut(clamp01((tick - 88) / 20));
        // the blade stretching
        float st = easeInOut(clamp01((tick - 14) / 30));
        float sc = (1 + (m.k - 1) * st) / m.k;
        for (int i = 0; i < m.main; i++) {
            float x = d[i * 8], yf = d[i * 8 + 1], z = d[i * 8 + 2];
            float y0 = m.origY[i];
            float y = y0 + (yf - y0) * st;
            float blade = y > BLADE_BASE ? 1 : 0.1f;
            // while it stretches, the flesh swells more where it is being pulled out
            float pull = blade * flesh * (0.35f + 1.2f * (float) Math.sin(Math.PI * st) * clamp01((y - 8) / 20));
            float b = boil(y, clock);
            p[i * 3] = x * (1 + 0.15f * pull * b);
            p[i * 3 + 1] = y;
            p[i * 3 + 2] = z * (1 + pull * (0.6f + 1.4f * b));
        }
        float[] g = new float[m.branches.length];
        for (float[] q : GROW) {
            int b = (int) q[0];
            if (b < g.length) {
                g[b] = easeOut(clamp01((tick - q[1]) / q[2]));
            }
        }
        for (int b = 0; b < m.branches.length; b++) {
            Branch br = m.branches[b];
            float gb = g[b];
            for (int i = 0; i < br.n; i++) {
                float s = br.s[i];
                int v = br.from + i;
                float px, py, pz;
                if (gb <= 0.001f) {
                    float[] c = br.at(0);
                    px = c[0];
                    py = c[1];
                    pz = c[2];
                } else {
                    float[] c = br.at(s * gb);
                    // thin at the growing end, fat and wobbly while it grows, the final width once grown
                    float tipRound = 1 - 0.6f * (float) Math.pow(s, 6) * (1 - gb);
                    float wob = 1 + (1 - gb) * 0.5f * (float) Math.sin(s * 18 - clock * 10);
                    float w = clamp01(gb * 1.6f) * tipRound;
                    px = c[0] + br.off[i * 3] * w * wob;
                    py = c[1] + br.off[i * 3 + 1] * w;
                    pz = c[2] + br.off[i * 3 + 2] * w * (1 + flesh * (1.5f - gb) * (0.6f + boil(s * 20 + b * 5, clock)));
                }
                p[v * 3] = px;
                p[v * 3 + 1] = BLADE_BASE + (py - BLADE_BASE) * sc;
                p[v * 3 + 2] = pz;
            }
        }
        boolean shader = FxRenderTypes.fleshReady();
        RenderType type = shader ? FxRenderTypes.flesh(KokushiboSwordRenderer.TEXTURE) : RenderType.entityCutoutNoCull(KokushiboSwordRenderer.TEXTURE);
        new FleshSheathe.Out(buffer.getBuffer(type), pose, light, shader).bent(d, p, m.nor, flesh, flesh);
        return true;
    }

    private static float boil(float y, float clock) {
        return Math.max(0, (float) Math.sin(y * 1.6f - clock * 6)) * (0.5f + 0.5f * (float) Math.sin(y * 0.4f + clock * 2));
    }

    private static float clamp01(float x) {
        return Math.max(0, Math.min(1, x));
    }

    private static float easeInOut(float u) {
        return u < 0.5f ? 4 * u * u * u : 1 - (float) Math.pow(-2 * u + 2, 3) / 2;
    }

    private static float easeOut(float u) {
        return 1 - (float) Math.pow(1 - u, 3);
    }
}
