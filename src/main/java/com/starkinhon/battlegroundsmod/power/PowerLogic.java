package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.combat.DashAttack;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Server-side rules: using abilities, ult charge, picking a power.
public final class PowerLogic {
    private static final float ULT_PASSIVE_PER_TICK = 0.03f;
    private static final float ULT_PER_DAMAGE = 2.0f;
    private static final int SYNC_INTERVAL = 4;
    private static final double ULT_TITLE_RANGE = 48;
    // Powers whose ult draws its own title, so the vanilla one is not sent.
    private static final java.util.Set<String> OWN_ULT_TITLE = java.util.Set.of("strongest");

    private PowerLogic() {
    }

    public static Ability ability(Power power, int slot) {
        if (slot >= PowerData.SLOT_SHIFT) {
            int index = slot - PowerData.SLOT_SHIFT;
            return index < power.shiftAbilities().size() ? power.shiftAbilities().get(index) : null;
        }
        return switch (slot) {
            case PowerData.SLOT_STRONG -> Melee.STRONG;
            case PowerData.SLOT_BARRAGE -> Melee.BARRAGE;
            case PowerData.SLOT_DASH_ATTACK -> DashAttack.ability();
            case PowerData.SLOT_EXTRA -> power.abilities().size() > 4 ? power.abilities().get(4) : null;
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
        if (PowerData.isUlt(slot) && com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.cancel(player)) {
            // V with his domain open: Gojo closes it.
            return;
        }
        if (slot < 0 || slot >= PowerData.SLOT_COUNT || !canCast(player, data, slot)) {
            return;
        }
        Ability ability = ability(power, slot);
        boolean ultimate = PowerData.isUlt(slot);
        // Creative mode skips the costs so moves can be recorded freely.
        boolean free = player.isCreative() || data.noCooldown;
        if (!free && ultimate && data.ult < PowerData.MAX_ULT) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.ult_not_ready"), true);
            return;
        }
        if (!cast(player, slot)) {
            return;
        }
        if (!free && ultimate) {
            data.ult = 0;
        }
        if (!data.noCooldown) {
            data.cooldowns[slot] = ability.cooldown();
        }
        sync(player);
    }

    // Being hit (stun, Cooldown effect) blocks every move but a counter.
    private static boolean canCast(LivingEntity caster, PowerData data, int slot) {
        Power power = Powers.get(data.power);
        Ability ability = power == null ? null : ability(power, slot);
        if (ability == null) {
            return false;
        }
        boolean counter = ability.counter();
        return !caster.isSpectator() && caster.isAlive() && data.cooldowns[slot] <= 0 && data.busyTicks <= 0 && !Freeze.isFrozen(caster)
                && (counter || Stun.canAttack(caster)) && !Defense.isGuarding(caster) && !sealed(caster)
                && !com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.blocks(caster);
    }

    public static boolean sealed(LivingEntity caster) {
        return caster.getData(ModAttachments.POWER).sealedUntil > caster.level().getGameTime();
    }

    // No skills for ticks: every slot shows the lock as its cooldown, and whatever the fighter was doing stops.
    public static void seal(LivingEntity target, int ticks) {
        PowerData data = target.getData(ModAttachments.POWER);
        data.sealedUntil = Math.max(data.sealedUntil, target.level().getGameTime() + ticks);
        for (int i = 0; i < data.cooldowns.length; i++) {
            data.cooldowns[i] = Math.max(data.cooldowns[i], ticks);
        }
        interrupt(target);
        if (target instanceof ServerPlayer player) {
            sync(player);
        }
    }

    // Cuts off the moves the fighter has going: wind-ups, fire still pouring out, a meteor not yet thrown, a counter
    // waiting, a queued punch, and the body animation that went with them.
    public static void interrupt(LivingEntity target) {
        PowerData data = target.getData(ModAttachments.POWER);
        data.interrupts++;
        data.busyTicks = 0;
        data.punchDelay = 0;
        data.punchQueued = false;
        Susanoo.cancel(target);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.PlaySkillAnimation(target.getId(), "", false));
    }

    // Runs a move of the caster's power, with no costs and no cooldown set: players go through tryUse, bosses set
    // their own cooldowns. Returns false when the move did not happen.
    public static boolean cast(LivingEntity caster, int slot) {
        PowerData data = caster.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (power == null || slot < 0 || slot >= PowerData.SLOT_COUNT || !canCast(caster, data, slot)) {
            return false;
        }
        ServerLevel level = Combat.level(caster);
        if (!ability(power, slot).action().use(caster, level)) {
            return false;
        }
        data.lastCastSlot = slot;
        data.lastCastTime = level.getGameTime();
        CombatMode.enter(caster);
        if (PowerData.isUlt(slot)) {
            announceUlt(caster, power, slot);
        }
        return true;
    }

    // Big title on the screen of everyone nearby. Makes ults read well on video.
    private static void announceUlt(LivingEntity player, Power power, int slot) {
        // Gojo's Domain Expansion writes its own words on the screen ("Expansão de Domínio:" then "Vazio Infinito."),
        // and its long name in the big vanilla title runs off the screen.
        if (OWN_ULT_TITLE.contains(power.id())) {
            return;
        }
        Component title = power.abilityName(slot).copy().withStyle(s -> s.withColor(power.color()).withBold(true));
        Component subtitle = player.getDisplayName().copy().withStyle(ChatFormatting.GRAY);
        for (ServerPlayer other : Combat.level(player).players()) {
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
        CombatMode.leave(player);
        sync(player);
    }

    public static void addUltCharge(ServerPlayer player, float damageDealt) {
        PowerData data = player.getData(ModAttachments.POWER);
        if (data.hasPower()) {
            data.ult = Math.min(PowerData.MAX_ULT, data.ult + damageDealt * ULT_PER_DAMAGE);
        }
    }

    public static void tick(LivingEntity player) {
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
        if (data.punchQueued && data.punchDelay <= 0) {
            Melee.punch(player, null);
        }
        if (data.busyTicks > 0) {
            data.busyTicks--;
        }
        if (data.comboResetTicks > 0 && --data.comboResetTicks == 0) {
            data.comboStep = 0;
        }
        CombatMode.tick(player);
        // Bosses get their tier's damage only; their health and armor come from their own file.
        if (player instanceof Player) {
            Tiers.apply(player);
        }
        if (data.hasPower()) {
            data.ult = Math.min(PowerData.MAX_ULT, data.ult + ULT_PASSIVE_PER_TICK);
        }
        if (player instanceof ServerPlayer serverPlayer && player.tickCount % SYNC_INTERVAL == 0) {
            sync(serverPlayer);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            String aura = auraOf(serverPlayer);
            if (!aura.equals(data.auraSent)) {
                data.auraSent = aura;
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new ModPayloads.AuraState(serverPlayer.getId(), aura));
            }
        }
    }

    // The aura other players should see on this one: their power while in combat mode, else none.
    public static String auraOf(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        return data.hasPower() && CombatMode.in(player) && player.isAlive() ? data.power : "";
    }

    // Someone just started seeing this player: tell them about the aura.
    public static void sendAura(ServerPlayer player, ServerPlayer watcher) {
        PacketDistributor.sendToPlayer(watcher, new ModPayloads.AuraState(player.getId(), auraOf(player)));
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ModPayloads.SyncPower.of(player.getData(ModAttachments.POWER)));
    }
}
