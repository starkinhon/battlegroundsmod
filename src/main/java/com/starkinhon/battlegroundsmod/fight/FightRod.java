package com.starkinhon.battlegroundsmod.fight;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The Fight Rod: right click a mob to pick it (it glows), right click a second one and the two fight each other until
// one dies. While paired they only have eyes for each other: nothing else (players, other mobs) can pull their target
// away. Bosses keep their own AI, so they fight with their skills; mobs that can't attack get a simple melee goal.
public final class FightRod {
    private static final int SELECT_GLOW_TICKS = 20 * 60;

    // Player -> the mob they picked first.
    private static final Map<UUID, Mob> SELECTED = new HashMap<>();
    // Each fighter -> its forced enemy (both directions are stored).
    private static final Map<Mob, Mob> ENEMY = new IdentityHashMap<>();

    private FightRod() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FightRod::onInteract);
        NeoForge.EVENT_BUS.addListener(FightRod::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(FightRod::onServerTick);
        NeoForge.EVENT_BUS.addListener(FightRod::onServerStopped);
    }

    // The forced enemy of this mob, or null when it is not in a Fight Rod duel.
    public static Mob enemyOf(Mob mob) {
        Mob enemy = ENEMY.get(mob);
        return enemy != null && fighting(enemy) ? enemy : null;
    }

    // Runs before the mob's own interaction, so villagers, horses etc. don't open menus or get mounted.
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getItemStack().is(ModItems.FIGHT_ROD.get()) || !(event.getTarget() instanceof Mob mob)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide()) {
            return;
        }
        select(event.getEntity(), mob);
    }

    // Sneak + right click in the air with the rod.
    public static void clearSelection(Player player) {
        Mob picked = SELECTED.remove(player.getUUID());
        if (picked != null) {
            picked.removeEffect(MobEffects.GLOWING);
        }
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.fight_rod.cleared")
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static void select(Player player, Mob mob) {
        ServerLevel level = (ServerLevel) mob.level();
        Mob first = SELECTED.get(player.getUUID());
        if (first != null && (!fighting(first) || first.level() != level)) {
            first = null;
        }
        if (first == mob) {
            SELECTED.remove(player.getUUID());
            mob.removeEffect(MobEffects.GLOWING);
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.fight_rod.deselected", mob.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (first == null) {
            SELECTED.put(player.getUUID(), mob);
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, SELECT_GLOW_TICKS, 0, false, false));
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY(0.5), mob.getZ(), 20,
                    mob.getBbWidth() * 0.5, mob.getBbHeight() * 0.4, mob.getBbWidth() * 0.5, 0.1);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1.4f);
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.fight_rod.selected", mob.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        SELECTED.remove(player.getUUID());
        first.removeEffect(MobEffects.GLOWING);
        startFight(level, first, mob);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.fight_rod.fight", first.getDisplayName(), mob.getDisplayName())
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    private static void startFight(ServerLevel level, Mob a, Mob b) {
        endFight(a);
        endFight(b);
        ENEMY.put(a, b);
        ENEMY.put(b, a);
        for (Mob mob : new Mob[] { a, b }) {
            if (!canFightOnItsOwn(mob) && mob instanceof PathfinderMob pathfinder) {
                FightGoal.addTo(pathfinder);
            }
            aim(mob, ENEMY.get(mob));
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY(1.0) + 0.3, mob.getZ(), 6,
                    mob.getBbWidth() * 0.4, 0.2, mob.getBbWidth() * 0.4, 0);
        }
        level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    // Monsters, neutral mobs (wolves, golems...) and the bosses already know how to attack whatever they target.
    private static boolean canFightOnItsOwn(Mob mob) {
        return mob instanceof Enemy || mob instanceof NeutralMob;
    }

    private static void endFight(Mob mob) {
        Mob enemy = ENEMY.remove(mob);
        if (enemy != null && ENEMY.get(enemy) == mob) {
            ENEMY.remove(enemy);
        }
    }

    // Both the classic target and the brain's attack memory (piglins, hoglins, wardens use the brain).
    private static void aim(Mob mob, Mob enemy) {
        if (mob.getTarget() != enemy) {
            mob.setTarget(enemy);
        }
        if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, (LivingEntity) enemy);
        }
    }

    // Nobody else steals a fighter's attention while its enemy lives.
    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        Mob enemy = enemyOf(mob);
        if (enemy != null && event.getNewAboutToBeSetTarget() != enemy) {
            event.setNewAboutToBeSetTarget(enemy);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        SELECTED.values().removeIf(mob -> !fighting(mob));
        if (ENEMY.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Mob, Mob>> it = ENEMY.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, Mob> entry = it.next();
            Mob mob = entry.getKey();
            Mob enemy = entry.getValue();
            if (!fighting(mob) || !fighting(enemy) || mob.level() != enemy.level()) {
                it.remove();
                continue;
            }
            if (mob.tickCount % 10 == 0) {
                aim(mob, enemy);
            }
        }
    }

    private static boolean fighting(Mob mob) {
        return mob.isAlive() && !mob.isRemoved();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SELECTED.clear();
        ENEMY.clear();
    }
}
