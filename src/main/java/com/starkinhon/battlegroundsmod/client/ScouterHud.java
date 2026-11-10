package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

// The Scouter's lens (item/ScouterItem): with it on the head, a green glass over the right side of the screen reads
// whoever is under the crosshair up to 64 blocks: name, tier and a Dragon Ball style power level.
// The tier is read from the target's max health (each tier adds the same health), so it is a scouter's guess.
public final class ScouterHud {
    private static final double RANGE = 64;
    private static LivingEntity last;
    private static int seen;

    private ScouterHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SCOUTER.get())) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int x1 = width - 150;
        int y1 = 14;
        // The lens.
        graphics.fill(x1 - 10, y1 - 8, width - 6, y1 + 56, 0x2A20FF60);
        graphics.fill(x1 - 10, y1 - 8, width - 6, y1 - 7, 0x9040FF80);
        LivingEntity target = target(mc, player);
        if (target != last) {
            last = target;
            seen = 0;
        }
        if (target == null) {
            graphics.drawString(mc.font, Component.translatable("hud.battlegroundsmod.scouter.idle"), x1, y1, 0xFF60FF90, false);
            return;
        }
        seen++;
        int tier = tierOf(target);
        long level = powerLevel(tier);
        // The number counts up for a moment, like the real thing.
        long shown = seen >= 20 ? level : level * seen / 20;
        int color = tier < Tiers.of(player) ? 0xFFFF5050 : tier == Tiers.of(player) ? 0xFFFFD050 : 0xFF60FF90;
        graphics.drawString(mc.font, target.getDisplayName(), x1, y1, 0xFF60FF90, false);
        graphics.drawString(mc.font, Component.literal("Tier " + tier + " · ").append(Tiers.name(tier)), x1, y1 + 12, color, false);
        graphics.drawString(mc.font, Component.translatable("hud.battlegroundsmod.scouter.level", String.format("%,d", shown)), x1, y1 + 24, color, false);
        int hp = Math.round(target.getHealth());
        graphics.drawString(mc.font, Component.translatable("hud.battlegroundsmod.scouter.health", hp, Math.round(target.getMaxHealth())), x1, y1 + 36, 0xFFB0FFC0, false);
        if (tier < Tiers.of(player) - 2 && (seen / 6) % 2 == 0) {
            graphics.drawString(mc.font, Component.translatable("hud.battlegroundsmod.scouter.danger"), x1, y1 + 48, 0xFFFF3030, false);
        }
    }

    private static LivingEntity target(Minecraft mc, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1),
                e -> e instanceof LivingEntity && e != player && !e.isSpectator(), RANGE * RANGE);
        Entity entity = hit == null ? null : hit.getEntity();
        return entity instanceof LivingEntity living && player.hasLineOfSight(living) ? living : null;
    }

    private static int tierOf(LivingEntity target) {
        if (target instanceof Player other) {
            return Tiers.of(other);
        }
        return Mth.clamp(Tiers.HUMANO - (int) Math.floor((target.getMaxHealth() - 15) / 50.0), Tiers.DEUS, Tiers.HUMANO);
    }

    // 5 for a human, about 2.5 times more each tier (Tier 0 is close to fifty thousand).
    private static long powerLevel(int tier) {
        return Math.round(5 * Math.pow(2.5, Tiers.HUMANO - tier));
    }
}
