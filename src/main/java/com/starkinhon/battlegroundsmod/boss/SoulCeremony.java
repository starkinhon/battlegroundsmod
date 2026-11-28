// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.CustomDeath;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakai;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Stealing a soul: a boss killed by a player does not just drop its soul item. The body falls, the soul leaves it slowly,
// takes the shape of the soul item's icon (client/fx/SoulCeremonyFx reads the icon's pixels, so any new icon works) and
// falls to the ground as the real item. Picking it up shows the soul's name and tells the server.
// Only kills by a player: a boss killed by another boss, a mob or the world drops its soul the normal way, and so does a
// death with its own look (ash from the Katon or the Meteoro, the Hakai's sparks, see combat/CustomDeath).
// The first time a player steals a given soul plays the whole ceremony; later ones the short version.
// Where: on the body, wherever the killing blow threw it (Stark 2026-11-23: after a Strong the soul fell at the killer's
// feet while the light played on the body far away). The light follows the body while it flies; once it lies still (or
// when the soul starts taking shape, at the latest) the spot is fixed on both sides (ModPayloads.SoulCeremonyAnchor) and
// the item is born right there. A body thrown into the void or into a wall gives its soul on the last safe ground.
public final class SoulCeremony {
    // Persistent-data key on the item born from the ceremony: the power id, read on pickup.
    public static final String ITEM_TAG = "souls_cerimonia";
    // And the tier of the boss that soul came from, shown on pickup.
    private static final String TIER_TAG = "souls_cerimonia_tier";
    // Where above the feet the soul takes the crystal's shape and the item is born.
    public static final double FORM_HEIGHT = 1.7;

    // Ticks from the death: the soul starts leaving the body (out), starts taking the icon's shape (morph), shines once
    // formed (shine), becomes the item and falls (drop); the body fades from fade to end and is gone at end.
    // Shared with client/fx/SoulCeremonyFx so both sides keep the same clock.
    public record Timing(int out, int morph, int shine, int drop, int fade, int end) {
        public static final Timing FULL = new Timing(24, 60, 84, 92, 104, 120);
        public static final Timing SHORT = new Timing(6, 22, 38, 44, 50, 64);

        public static Timing of(boolean full) {
            return full ? FULL : SHORT;
        }
    }

    // The soul's own color (the Forja preview's), else the power's.
    private static final Map<String, Integer> COLORS = Map.of(
            "strongest", 0x86DCFF,
            "uchiha_ghost", 0xFF3A2C,
            "moon_demon", 0xC7A2FF,
            "destruction_god", 0xA64DFF);

    private final ServerPlayer killer;
    private final DamageSource source;
    private final ItemStack stack;
    private final String power;
    private final Integer tier;
    private Timing timing = Timing.FULL;
    private int age;
    // The body's feet where the soul is born, fixed once it settles (null while it still flies).
    private Vec3 anchor;
    // The last spot the body stood on solid ground (starts where it died): the fallback for a body lost in the void.
    private Vec3 lastGround;

    SoulCeremony(ServerPlayer killer, DamageSource source, ItemStack stack, String power, Integer tier, Vec3 deathPos) {
        this.killer = killer;
        this.source = source;
        this.stack = stack;
        this.power = power;
        this.tier = tier;
        this.lastGround = deathPos;
    }

    // A kill that earns the ceremony: by a player (or what a player threw or shot).
    static ServerPlayer killer(DamageSource source) {
        Entity entity = source.getEntity();
        return entity instanceof ServerPlayer player && !player.isSpectator() ? player : null;
    }

    public static int color(String power) {
        Integer color = COLORS.get(power);
        if (color != null) {
            return color;
        }
        Power p = Powers.get(power);
        return p != null ? p.color() & 0xFFFFFF : 0xFFFFFF;
    }

    // One tick of the dead boss's ceremony. False hands the death back to vanilla (the soul already dropped normally).
    boolean tick(ServerLevel level, SoulBoss boss) {
        age++;
        if (boss.onGround() && boss.level() == level) {
            lastGround = boss.position();
        }
        if (age == 1) {
            // Decided a tick late: the fire kills mark their victim right after the killing hit.
            if (CustomDeath.marked(boss) || source.is(Hakai.DAMAGE)) {
                dropPlain(level, boss, stack, lastGround);
                return false;
            }
            boolean full = !killer.getData(ModAttachments.STOLEN_SOULS).contains(power);
            timing = Timing.of(full);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(boss,
                    new ModPayloads.SoulCeremony(boss.getId(), killer.getId(), power, color(power), full));
        }
        if (anchor == null && (settled(boss) || age >= timing.morph())) {
            // From here on the soul stays put, even if the body is still sliding a little.
            anchor = safeSpot(level, boss.position(), lastGround);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(boss,
                    new ModPayloads.SoulCeremonyAnchor(boss.getId(), anchor.x, anchor.y, anchor.z));
        }
        Vec3 form = (anchor != null ? anchor : boss.position()).add(0, FORM_HEIGHT, 0);
        if (age == timing.out()) {
            Fx.sound(level, form, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.5f);
        }
        if (age == timing.shine()) {
            Fx.sound(level, form, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.7f);
            Fx.sound(level, form, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.6f);
        }
        if (age == timing.drop()) {
            ItemEntity item = new ItemEntity(level, form.x, form.y, form.z, stack.copy(), 0, -0.05, 0);
            item.setPickUpDelay(12);
            item.setUnlimitedLifetime();
            item.getPersistentData().putString(ITEM_TAG, power);
            if (tier != null) {
                item.getPersistentData().putInt(TIER_TAG, tier);
            }
            if (level.addFreshEntity(item)) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(boss, new ModPayloads.SoulCeremonyDrop(boss.getId(), item.getId()));
            }
            Fx.sound(level, form, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
        }
        if (age >= timing.end() && !boss.isRemoved()) {
            // No vanilla poof: the body has already faded away on the clients.
            boss.remove(Entity.RemovalReason.KILLED);
        }
        return true;
    }

    // Lying still: on the ground (or floating in water or lava) and no longer sliding.
    private static boolean settled(SoulBoss boss) {
        Vec3 v = boss.getDeltaMovement();
        return (boss.onGround() || boss.isInLiquid()) && v.x * v.x + v.z * v.z < 0.0025 && Math.abs(v.y) < 0.2;
    }

    // A body that fell out of the world (SoulBoss.onBelowWorld): put back where its soul is born, or its last ground.
    Vec3 rescueSpot(ServerLevel level) {
        return anchor != null ? anchor : safeSpot(level, lastGround, lastGround);
    }

    // A soul with no ceremony (killed by the world, a mob, or a death with its own look): the item at the body, safe.
    static void dropPlain(ServerLevel level, SoulBoss boss, ItemStack stack, Vec3 fallback) {
        Vec3 at = safeSpot(level, boss.position(), fallback);
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.25, at.z, stack.copy());
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    // Where an item can be born for a body at feet: out of any block the body was pushed into, never over the void.
    // Off the world or with no floor under it at all, the fallback (the last ground the body stood on, or its death).
    static Vec3 safeSpot(Level level, Vec3 feet, Vec3 fallback) {
        Vec3 found = freeAbove(level, feet);
        if (found != null && hasFloor(level, found)) {
            return found;
        }
        if (fallback != null && fallback != feet) {
            Vec3 back = freeAbove(level, fallback);
            if (back != null) {
                return back;
            }
            return fallback;
        }
        return found != null ? found : feet;
    }

    // Up to a few blocks up out of whole solid blocks; null if buried deeper or out of the world.
    private static Vec3 freeAbove(Level level, Vec3 feet) {
        if (feet.y < level.getMinBuildHeight() || feet.y > level.getMaxBuildHeight() + 64) {
            return null;
        }
        BlockPos pos = BlockPos.containing(feet);
        for (int up = 0; up <= 6; up++) {
            BlockPos at = pos.above(up);
            if (at.getY() >= level.getMaxBuildHeight()) {
                return null;
            }
            // Only whole blocks bury it: on a slab, a path or a carpet the body is just standing.
            if (!level.getBlockState(at).isCollisionShapeFullBlock(level, at)) {
                return up == 0 ? feet : new Vec3(feet.x, at.getY(), feet.z);
            }
        }
        return null;
    }

    // Something solid (or a liquid) somewhere below, so the item does not fall out of the world.
    private static boolean hasFloor(Level level, Vec3 feet) {
        BlockPos.MutableBlockPos at = BlockPos.containing(feet).mutable();
        for (int y = at.getY(); y >= level.getMinBuildHeight(); y--) {
            at.setY(y);
            var state = level.getBlockState(at);
            if (!state.getCollisionShape(level, at).isEmpty() || !state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Picking up a ceremony's soul: its name on screen for whoever took it, a line in chat for everyone, and the soul
    // counts as stolen (the next one of it plays the short ceremony).
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !event.getItemEntity().getPersistentData().contains(ITEM_TAG)) {
            return;
        }
        var tag = event.getItemEntity().getPersistentData();
        String power = tag.getString(ITEM_TAG);
        Integer tier = tag.contains(TIER_TAG) ? tag.getInt(TIER_TAG) : null;
        // Only once per item, even if a full inventory left part of the stack on the ground.
        tag.remove(ITEM_TAG);
        Power p = Powers.get(power);
        Component name = p != null ? p.name() : Component.literal(power);
        int color = color(power);
        List<String> stolen = new ArrayList<>(player.getData(ModAttachments.STOLEN_SOULS));
        if (!stolen.contains(power)) {
            stolen.add(power);
            player.setData(ModAttachments.STOLEN_SOULS, List.copyOf(stolen));
        }
        Component big = Component.translatable("message.battlegroundsmod.alma_de", name.copy()).withStyle(s -> s.withColor(color).withBold(true));
        Component small = Component.literal(tier != null ? "Tier " + tier : "").withStyle(s -> s.withColor(0xCCCCCC));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 50, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(big));
        player.connection.send(new ClientboundSetSubtitleTextPacket(small));
        Component soul = name.copy().withStyle(s -> s.withColor(color).withBold(true));
        Component line = Component.empty().append(player.getDisplayName())
                .append(Component.translatable("message.battlegroundsmod.roubou_alma", soul).withStyle(s -> s.withColor(0xAAAAAA)));
        player.server.getPlayerList().broadcastSystemMessage(line, false);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.4f);
    }
}
