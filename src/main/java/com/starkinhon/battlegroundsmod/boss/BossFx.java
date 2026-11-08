package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.fx.Smoke;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Looks and sounds of the boss itself (warnings, entrance, phase changes, dashes). The skills bring their own.
final class BossFx {
    private static final double TITLE_RANGE = 48;

    private BossFx() {
    }

    // Warning before a move: a ring closing in at the feet and sparks gathering where the move comes from.
    // progress goes 0 -> 1 over the warning.
    static void tell(ServerLevel level, LivingEntity boss, String tipo, int color, double progress) {
        Vec3 feet = boss.position().add(0, 0.1, 0);
        Vec3 focus = switch (tipo == null ? "" : tipo) {
            case "golpe", "barragem" -> Combat.handPos(boss);
            case "ult" -> boss.position().add(0, boss.getBbHeight() + 1.2, 0);
            default -> boss.getEyePosition().add(boss.getLookAngle().scale(0.3));
        };
        Smoke.tell(level, feet, focus, color, progress);
    }

    static void tellStart(ServerLevel level, LivingEntity boss, String sound) {
        SoundEvent event = sound(sound);
        if (event != null) {
            Fx.sound(level, boss.position(), event, 1.4f, 0.8f);
        }
    }

    // Entrance and phase changes: a dark column around the boss while it stands still.
    static void aura(ServerLevel level, LivingEntity boss, int color, int age) {
        Vec3 base = boss.position();
        Smoke.aura(level, base, color, age);
        if (age % 10 == 0) {
            Fx.sound(level, base, SoundEvents.WITHER_AMBIENT, 0.8f, 0.5f);
        }
    }

    // The burst that ends the entrance or a phase change.
    static void shockwave(ServerLevel level, LivingEntity boss, int color, double radius) {
        Vec3 base = boss.position().add(0, 0.2, 0);
        Smoke.shockwave(level, boss.position(), color, radius);
        Fx.sound(level, base, SoundEvents.WITHER_SPAWN, 1.2f, 0.7f);
        Fx.sound(level, base, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
    }

    // Afterimage along a dash.
    static void dash(ServerLevel level, LivingEntity boss, int color) {
        Vec3 center = Combat.center(boss);
        Vec3 motion = boss.getDeltaMovement();
        Smoke.dash(level, boss.position(), motion.horizontalDistanceSqr() > 1.0E-4 ? motion : Combat.flatLook(boss), color);
        Fx.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.4f);
    }

    // Shadow step: a dark afterimage trail from where it was to where it appears, and a sharp whoosh.
    static void shadowStep(ServerLevel level, Vec3 from, Vec3 to, int color) {
        Vec3 a = from.add(0, 1, 0);
        Vec3 b = to.add(0, 1, 0);
        Smoke.shadowStep(level, from, to, color);
        Fx.sound(level, a, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2f, 1.4f);
        Fx.sound(level, b, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.8f);
    }

    static void teleport(ServerLevel level, Vec3 from, Vec3 to, int color) {
        Smoke.vanish(level, from, color);
        Smoke.appear(level, to, color);
        for (Vec3 pos : new Vec3[] {from, to}) {
            Fx.sound(level, pos, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
        }
    }

    static void title(ServerLevel level, LivingEntity boss, String title, String subtitle, int color) {
        // Titles on screen are part of the boss fight only (/gamerule soulbossfight).
        if (title == null || title.isEmpty() || !BossRules.bossfight(level)) {
            return;
        }
        Component big = Component.literal(title).withStyle(s -> s.withColor(color).withBold(true));
        Component small = Component.literal(subtitle == null ? "" : subtitle).withStyle(s -> s.withColor(0xAAAAAA));
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(boss) <= TITLE_RANGE) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
                player.connection.send(new ClientboundSetTitleTextPacket(big));
                player.connection.send(new ClientboundSetSubtitleTextPacket(small));
            }
        }
    }

    // "#ff7a10" -> 0xFF7A10. Falls back to red.
    static int color(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (RuntimeException e) {
            return 0xD12F2F;
        }
    }

    static SoundEvent sound(String id) {
        ResourceLocation location = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return location == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(null);
    }
}
