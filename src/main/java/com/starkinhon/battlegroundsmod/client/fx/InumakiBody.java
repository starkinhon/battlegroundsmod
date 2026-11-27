// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// What Inumaki's words do to a body (the approved preview forja/inumaki-kit/inumaki-vivo.js, Stark 2026-11-27):
// TREMBLE the body shakes with the voice; PAPER "Get crushed" flattens it into a sheet on the ground that sways and only
// springs back when the stun ends; DROWSY "Sleep" leaves it staggering side to side; SWELL "Explode" puffs it up and
// shakes it before it bursts; GONE hides a body the word finished (it became a pool, blew up, or flew away).
public final class InumakiBody {
    public static final int TREMBLE = 1, PAPER = 2, DROWSY = 3, SWELL = 4, GONE = 5;

    // entity id -> {mode, start (ticks), length (ticks)}
    private static final Map<Integer, double[]> STATE = new HashMap<>();
    private static final Map<Integer, double[]> TREMBLING = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();

    private InumakiBody() {
    }

    // From the server (SubNet.INU_BODY): mode 0 clears.
    static void set(int id, int mode, float ticks) {
        double now = ScreenFx.now();
        if (mode == TREMBLE) {
            TREMBLING.put(id, new double[] {mode, now, Math.max(1, ticks)});
        } else if (mode <= 0) {
            STATE.remove(id);
        } else {
            STATE.put(id, new double[] {mode, now, Math.max(1, ticks)});
        }
    }

    public static void clear() {
        STATE.clear();
        TREMBLING.clear();
        PUSHED.clear();
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() || (STATE.isEmpty() && TREMBLING.isEmpty())) {
            return;
        }
        LivingEntity e = event.getEntity();
        int id = e.getId();
        double now = ScreenFx.now() + event.getPartialTick();
        double[] s = STATE.get(id);
        if (s != null && now - s[1] > s[2] + 2) {
            STATE.remove(id);
            s = null;
        }
        double[] tr = TREMBLING.get(id);
        if (tr != null && now - tr[1] > tr[2]) {
            TREMBLING.remove(id);
            tr = null;
        }
        if (s == null && tr == null) {
            return;
        }
        if (s != null && (int) s[0] == GONE) {
            event.setCanceled(true);
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(id);
        if (tr != null) {
            // the ground and whoever is in the voice's way shake with it
            float t = (float) (now - tr[1]), k = 1 - t / (float) tr[2];
            pose.translate(Mth.sin(t * 4.15f) * 0.05f * k, 0, Mth.cos(t * 3.55f + 1) * 0.05f * k);
        }
        if (s == null) {
            return;
        }
        float t = (float) (now - s[1]) / 20f, len = (float) s[2] / 20f;
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        switch ((int) s[0]) {
            case PAPER -> {
                // a sheet of paper in the hole: flat in 0.08 s, the edge sways, springs back in the last 0.35 s
                float k = smooth(t / 0.08f);
                if (t > len - 0.35f) {
                    k *= 1 - smooth((t - (len - 0.35f)) / 0.35f);
                }
                if (t > 0.2f && k > 0.5f) {
                    pose.mulPose(Axis.of(new org.joml.Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw))).rotation(Mth.sin(t * 7) * 0.04f));
                }
                pose.translate(0, 0.02f * k, 0);
                pose.scale(1 + 0.35f * k, Math.max(0.035f, 1 - 0.965f * k), 1 + 0.35f * k);
            }
            case DROWSY -> {
                // staggering from one side to the other, head heavy, feet dragging
                float k = t < len ? smooth(t / 0.25f) : 1 - smooth((t - len) / 0.4f);
                float half = e.getBbHeight() * 0.5f;
                pose.translate(0, half * 0.1f, 0);
                pose.mulPose(Axis.of(new org.joml.Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw))).rotation(Mth.sin(t * 3.4f) * 0.14f * k));
                pose.translate(0, -half * 0.1f, 0);
            }
            case SWELL -> {
                // swells from inside and shakes before it bursts
                float k = smooth(t / Math.max(0.05f, len));
                pose.scale(1 + 0.35f * k + Mth.sin(t * 60) * 0.03f * k, 1 + 0.12f * k, 1 + 0.35f * k + Mth.cos(t * 55) * 0.03f * k);
            }
            default -> {
            }
        }
    }

    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }
}
