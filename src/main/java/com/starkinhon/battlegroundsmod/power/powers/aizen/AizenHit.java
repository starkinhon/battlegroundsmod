// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Stun;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// What the remade kit's blows share (forja/aizen-kit, approved 2026-11-22): damage falling off from the middle of a blast
// out to its edge, the ground under a point, and a crater the size of a divine soul (escala-destruicao-niveis: common
// skills ~10 blocks, the specials 20-30). The blocks only change where BlockRules.canBreak allows (Ruin checks it); the
// flying blocks, the dust ring and the shaking are drawn by client/fx/epic (RuinNet).
final class AizenHit {
    static final int DUST = 0x8A8494;
    static final int DUST_LIGHT = 0xB8B0C4;

    private AizenHit() {
    }

    // Everyone within R of center (but the caster): center damage in the middle down to edge damage at R, thrown away
    // from the middle (push blocks a tick, up), stunned stun ticks (0 = none). Returns who was hit.
    static List<LivingEntity> blast(LivingEntity caster, ServerLevel level, Vec3 center, double R, float center0, float edge, double push, double up,
            int stun) {
        List<LivingEntity> hit = Combat.inRadius(level, center, R, caster);
        for (LivingEntity target : hit) {
            double k = Mth.clamp(Combat.center(target).distanceTo(center) / Math.max(0.1, R), 0, 1);
            Combat.hurtMagic(caster, target, (float) Mth.lerp(k, center0, edge));
            if (push > 0) {
                Combat.pushFrom(target, center, push * (1 - 0.5 * k), up);
            }
            if (stun > 0) {
                Stun.apply(target, stun);
            }
        }
        return hit;
    }

    // The ground's top at x, z (the point's own height when the chunk isn't there).
    static Vec3 ground(ServerLevel level, Vec3 at) {
        int x = Mth.floor(at.x), z = Mth.floor(at.z);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return at;
        }
        int g = Ruin.ground(level, x, z);
        // Far below (a cliff, the void): keep the point.
        return Math.abs(g + 1 - at.y) > 24 ? at : new Vec3(at.x, g + 1, at.z);
    }

    // A crater of radius R and depth d opening from the middle in dur ticks, with its blocks flying, the dust ring and
    // the shaking (impact = one manga frame for those near, tint its color).
    static void crater(ServerLevel level, Vec3 at, double R, double d, int dur, double scorch, boolean impact, int tint) {
        Ruin.bowl(level, at.x, at.z, R, d, dur, 0, Ruin.opts().rim(0.2).scorch(scorch).seed((int) (at.x * 7 + at.z * 3)));
        RuinNet.debris(level, at, R * 0.7, 14 + R, 0, (int) Math.min(220, 20 + R * 7), 0.9 + R * 0.03);
        RuinNet.ring(level, at, R * 3.2, Math.max(2, R * 0.12), false, (int) (24 + R), DUST_LIGHT);
        RuinNet.dust(level, at, R * 0.9, R * 0.5, (int) (60 + R * 2), 10, DUST);
        RuinNet.tremor(level, at, Math.min(1.4, 0.3 + R * 0.03), 30, R * 2.5);
        RuinNet.quake(level, at, Math.min(2.4, 0.5 + R * 0.06), impact, tint);
    }

    // A furrow from a to b (width w, depth d) opened from a in dur ticks, blocks flying off it.
    static void furrow(ServerLevel level, Vec3 a, Vec3 b, double w, double d, int dur) {
        Ruin.trench(level, a, b, w, d, dur, 0, Ruin.opts().lip(1.2).seed((int) (a.x * 5 + b.z)));
        RuinNet.debrisLine(level, a, b, (int) Math.min(90, 10 + a.distanceTo(b) * 2), 1 + w * 0.2);
    }
}
