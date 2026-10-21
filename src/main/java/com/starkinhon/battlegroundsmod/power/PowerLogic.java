package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Server-side rules: using abilities, energy and ult charge, picking a power.
public final class PowerLogic {
    private static final float ENERGY_REGEN_PER_TICK = 0.4f;
    private static final float ULT_PASSIVE_PER_TICK = 0.03f;
    private static final float ULT_PER_DAMAGE = 2.0f;
    private static final int SYNC_INTERVAL = 4;
    private static final double ULT_TITLE_RANGE = 48;

    private PowerLogic() {
    }

    public static Ability ability(Power power, int slot) {
        return switch (slot) {
            case PowerData.SLOT_STRONG -> Melee.STRONG;
            case PowerData.SLOT_BARRAGE -> Melee.BARRAGE;
            default -> power.abilities().get(slot);
        };
    }

    public static void tryUse(ServerPlayer player, int slot) {
        PowerData data = player.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (power == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_power"), true);
            return;
        }
        if (slot < 0 || slot >= PowerData.SLOT_COUNT || player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (data.cooldowns[slot] > 0 || data.busyTicks > 0) {
            return;
        }
        Ability ability = ability(power, slot);
        boolean ultimate = slot == PowerData.SLOT_ULT;
        // Creative mode skips the costs so moves can be recorded freely.
        boolean free = player.isCreative();
        if (!free && ultimate && data.ult < PowerData.MAX_ULT) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.ult_not_ready"), true);
            return;
        }
        if (!free && data.energy < ability.energyCost()) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_energy"), true);
            return;
        }
        if (!ability.action().use(player, player.serverLevel())) {
            return;
        }
        if (!free) {
            if (ultimate) {
                data.ult = 0;
            } else {
                data.energy -= ability.energyCost();
            }
        }
        data.cooldowns[slot] = ability.cooldown();
        if (ultimate) {
            announceUlt(player, power);
        }
        sync(player);
    }

    // Big title on the screen of everyone nearby. Makes ults read well on video.
    private static void announceUlt(ServerPlayer player, Power power) {
        Component title = power.abilityName(PowerData.SLOT_ULT).copy().withStyle(s -> s.withColor(power.color()).withBold(true));
        Component subtitle = player.getDisplayName().copy().withStyle(ChatFormatting.GRAY);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other.distanceTo(player) <= ULT_TITLE_RANGE) {
                other.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
                other.connection.send(new ClientboundSetTitleTextPacket(title));
                other.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
        }
    }

    public static boolean hasBook(ServerPlayer player) {
        return player.isCreative() || player.getInventory().contains(new ItemStack(ModItems.POWER_BOOK.get()));
    }

    public static void selectFromBook(ServerPlayer player, String id) {
        if (!hasBook(player)) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.need_book"), true);
            return;
        }
        Power power = Powers.get(id);
        if (power != null) {
            select(player, power);
        }
    }

    public static void select(ServerPlayer player, Power power) {
        PowerData data = player.getData(ModAttachments.POWER);
        data.power = power.id();
        data.ult = 0;
        data.resetCombat();
        Vec3 pos = player.position().add(0, 1, 0);
        Fx.burst(player.serverLevel(), Fx.dust(power.color(), 2f), pos, 60, 0.6, 0);
        Fx.burst(player.serverLevel(), ParticleTypes.TOTEM_OF_UNDYING, pos, 40, 0.5, 0.4);
        Fx.sound(player.serverLevel(), pos, SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.power_selected",
                power.name().copy().withStyle(s -> s.withColor(power.color()))), false);
        sync(player);
    }

    public static void clear(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        data.power = "";
        data.ult = 0;
        data.resetCombat();
        sync(player);
    }

    public static void addUltCharge(ServerPlayer player, float damageDealt) {
        PowerData data = player.getData(ModAttachments.POWER);
        if (data.hasPower()) {
            data.ult = Math.min(PowerData.MAX_ULT, data.ult + damageDealt * ULT_PER_DAMAGE);
        }
    }

    public static void tick(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        for (int i = 0; i < data.cooldowns.length; i++) {
            if (data.cooldowns[i] > 0) {
                data.cooldowns[i]--;
            }
        }
        if (data.noFallTicks > 0) {
            data.noFallTicks--;
        }
        if (data.punchDelay > 0) {
            data.punchDelay--;
        }
        if (data.busyTicks > 0) {
            data.busyTicks--;
        }
        if (data.comboResetTicks > 0 && --data.comboResetTicks == 0) {
            data.comboStep = 0;
        }
        if (data.hasPower()) {
            data.energy = Math.min(PowerData.MAX_ENERGY, data.energy + ENERGY_REGEN_PER_TICK);
            data.ult = Math.min(PowerData.MAX_ULT, data.ult + ULT_PASSIVE_PER_TICK);
        }
        if (player.tickCount % SYNC_INTERVAL == 0) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ModPayloads.SyncPower.of(player.getData(ModAttachments.POWER)));
    }
}
