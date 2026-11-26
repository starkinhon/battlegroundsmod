// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Where the fists really are: the arms of the player model as last drawn (skill poses included, mixin/PlayerModelMixin),
// turned into a world point the same way LivingEntityRenderer places the model. For effects that burn on a fist and must
// follow the punch instead of floating at a fixed spot in front of the chest.
public final class HandTrack {
    private static final class Arms {
        // per arm: pivot x y z, rot x y z, scale x y z (right 0..8, left 9..17)
        final float[] v = new float[18];
        long time;
    }

    private static final Map<Integer, Arms> ARMS = new HashMap<>();

    private HandTrack() {
    }

    public static void capture(LivingEntity e, PlayerModel<?> m) {
        if (e.level() == null) {
            return;
        }
        if (ARMS.size() > 256) {
            long now = e.level().getGameTime();
            ARMS.values().removeIf(a -> now - a.time > 100);
        }
        Arms a = ARMS.computeIfAbsent(e.getId(), k -> new Arms());
        put(a.v, 0, m.rightArm);
        put(a.v, 9, m.leftArm);
        a.time = e.level().getGameTime();
    }

    private static void put(float[] v, int o, ModelPart p) {
        v[o] = p.x;
        v[o + 1] = p.y;
        v[o + 2] = p.z;
        v[o + 3] = p.xRot;
        v[o + 4] = p.yRot;
        v[o + 5] = p.zRot;
        v[o + 6] = p.xScale;
        v[o + 7] = p.yScale;
        v[o + 8] = p.zScale;
    }

    // The fist (end of the arm) in world space, or null when the model was not drawn lately (first person, out of view).
    public static Vec3 fist(LivingEntity e, boolean right, float partial) {
        Arms a = ARMS.get(e.getId());
        if (a == null || e.level() == null || e.level().getGameTime() - a.time > 3) {
            return null;
        }
        float[] v = a.v;
        int o = right ? 0 : 9;
        // the arm box runs 12 px down from the shoulder pivot; the fist is its last couple of pixels
        Vector3f p = new Vector3f(right ? -1 : 1, 10, 0);
        p.mul(v[o + 6], v[o + 7], v[o + 8]);
        new Quaternionf().rotationZYX(v[o + 5], v[o + 4], v[o + 3]).transform(p);
        p.add(v[o], v[o + 1], v[o + 2]).div(16);
        p.y -= 1.501f;
        float s = (e instanceof Player ? 0.9375f : 1) * e.getScale();
        p.mul(-s, -s, s);
        float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        new Quaternionf().rotationY((180 - yaw) * Mth.DEG_TO_RAD).transform(p);
        Vec3 feet = e.getPosition(partial);
        if (e instanceof Player && e.isCrouching()) {
            feet = feet.add(0, -0.125 * e.getScale(), 0);
        }
        return feet.add(p.x, p.y, p.z);
    }

    public static void clear() {
        ARMS.clear();
    }
}
