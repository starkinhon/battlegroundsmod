package com.starkinhon.battlegroundsmod.universe;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;
import com.starkinhon.battlegroundsmod.power.SoulSync;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Senhor Kaioh's training (Stark 2026-11-19: he teaches only Goku's soul, in Goku's arena, the Kaioh planet, no new
// structure). Win Goku's arena wearing Goku's soul and Senhor Kaioh comes out; talk to him: catch Bubbles, his monkey,
// in 60 s under the planet's heavy gravity (the manga's first test). Hit him or grab him (right click) and he teaches the
// Kaioken x3/x10/x20 (B) and the Mini Genki Dama (Shift+V): SoulSync.learnTaught, kept on death. Missed: talk again.
// The arena's return waits while he is there (ChallengeArenas.holdReturn).
public final class KaiohTraining {
    private static final int TALK_WAIT = 1200;
    private static final int CHASE_TICKS = 1200;
    private static final ResourceLocation GRAVITY_ID = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "kaioh_gravity");
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "kaioh_speed");
    private static final ResourceLocation JUMP_ID = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "kaioh_jump");

    private static final class Chase {
        final UUID player;
        final UniverseNpc bubbles;
        final long until;

        Chase(UUID player, UniverseNpc bubbles, long until) {
            this.player = player;
            this.bubbles = bubbles;
            this.until = until;
        }
    }

    private static final Map<UUID, Chase> CHASES = new HashMap<>();

    private KaiohTraining() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(KaiohTraining::onTick);
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && caught(player, e.getTarget())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                stop(player);
            }
        });
    }

    // ChallengeArenas.win in Goku's arena: Senhor Kaioh comes out if a winner wears Goku's soul and has not learned yet.
    // Returns how long the return should wait (0: nobody to train).
    public static int arenaWon(ServerLevel arena, BlockPos spot, List<ServerPlayer> winners) {
        boolean anyone = false;
        for (ServerPlayer player : winners) {
            if (wearsGoku(player) && !SoulSync.knowsTaught(player)) {
                anyone = true;
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.kaioh.chegou").withStyle(s -> s.withColor(0x6FC3FF)), false);
            }
        }
        if (!anyone) {
            return 0;
        }
        BlockPos at = stand(arena, spot);
        UniverseNpc kaioh = ModEntities.NPC_KAIOH.get().spawn(arena, at, MobSpawnType.EVENT);
        if (kaioh == null) {
            return 0;
        }
        kaioh.restrictTo(at, 4);
        kaioh.setCustomName(kaioh.getType().getDescription());
        kaioh.setCustomNameVisible(true);
        Fx.burst(arena, ParticleTypes.CLOUD, Vec3.atBottomCenterOf(at).add(0, 1, 0), 14, 0.5, 0.03);
        Fx.sound(arena, Vec3.atCenterOf(at), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        return TALK_WAIT;
    }

    private static boolean wearsGoku(ServerPlayer player) {
        return "goku".equals(player.getData(ModAttachments.POWER).power);
    }

    // Right click on Senhor Kaioh (Npcs.talk).
    public static void talk(ServerPlayer player, UniverseNpc npc, MasterTraining.Talker say) {
        if (SoulSync.knowsTaught(player)) {
            say.line(Component.translatable("npc.battlegroundsmod.kaioh.after." + player.getRandom().nextInt(3)));
            return;
        }
        if (!wearsGoku(player)) {
            say.line(Component.translatable("npc.battlegroundsmod.kaioh.so_goku"));
            return;
        }
        if (CHASES.containsKey(player.getUUID())) {
            say.line(Component.translatable("npc.battlegroundsmod.kaioh.vai"));
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos at = stand(level, npc.blockPosition().offset(3, 0, -3));
        UniverseNpc bubbles = ModEntities.NPC_BUBBLES.get().spawn(level, at, MobSpawnType.EVENT);
        if (bubbles == null) {
            return;
        }
        bubbles.setCustomName(bubbles.getType().getDescription());
        bubbles.setCustomNameVisible(true);
        CHASES.put(player.getUUID(), new Chase(player.getUUID(), bubbles, level.getGameTime() + CHASE_TICKS));
        gravity(player, true);
        ChallengeArenas.holdReturn(player, CHASE_TICKS + 400, false);
        say.line(Component.translatable("npc.battlegroundsmod.kaioh.teste"));
        MasterTraining.title(player, "message.battlegroundsmod.kaioh.pegue", "message.battlegroundsmod.kaioh.gravidade");
        Fx.sound(level, bubbles.position(), SoundEvents.FOX_SCREECH, 1f, 1.6f);
    }

    // Right click on Bubbles (Npcs.talk).
    public static void touch(ServerPlayer player, UniverseNpc npc) {
        caught(player, npc);
    }

    // Hit or grabbed: the test is passed.
    private static boolean caught(ServerPlayer player, Entity target) {
        Chase chase = CHASES.get(player.getUUID());
        if (chase == null || chase.bubbles != target) {
            return false;
        }
        stop(player);
        SoulSync.learnTaught(player);
        ServerLevel level = player.serverLevel();
        Fx.burst(level, ParticleTypes.CLOUD, target.position().add(0, 0.6, 0), 12, 0.4, 0.04);
        Fx.sound(level, player.position(), SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
        MasterTraining.title(player, "message.battlegroundsmod.kaioh.aprendeu", "message.battlegroundsmod.kaioh.ensinou");
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.kaioh.teclas").withStyle(s -> s.withColor(0xFFD84A)), false);
        ChallengeArenas.holdReturn(player, 200, true);
        return true;
    }

    private static void stop(ServerPlayer player) {
        Chase chase = CHASES.remove(player.getUUID());
        if (chase != null) {
            chase.bubbles.discard();
        }
        gravity(player, false);
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (CHASES.isEmpty()) {
            return;
        }
        Iterator<Chase> it = CHASES.values().iterator();
        while (it.hasNext()) {
            Chase chase = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(chase.player);
            UniverseNpc b = chase.bubbles;
            if (player == null || !b.isAlive() || player.level() != b.level() || !wearsGoku(player)) {
                it.remove();
                b.discard();
                if (player != null) {
                    gravity(player, false);
                }
                continue;
            }
            long now = b.level().getGameTime();
            if (now >= chase.until) {
                it.remove();
                b.discard();
                gravity(player, false);
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.kaioh.falhou").withStyle(s -> s.withColor(0xFF8A6A)), false);
                continue;
            }
            int left = (int) ((chase.until - now) / 20);
            if (now % 10 == 0) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.kaioh.tempo", left), true);
            }
            // Bubbles runs from whoever chases him, zigzagging, with a hop now and then; close, he dashes off to a side.
            double d = b.distanceTo(player);
            if (d < 9 && (now % 6 == 0 || b.getNavigation().isDone())) {
                Vec3 away = b.position().subtract(player.position());
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() < 1.0E-4) {
                    away = new Vec3(1, 0, 0);
                }
                double zig = Math.sin(now * 0.35 + b.getId()) * (d < 3.5 ? 1.4 : 0.7);
                Vec3 side = new Vec3(-away.z, 0, away.x).normalize();
                Vec3 to = b.position().add(away.normalize().scale(6)).add(side.scale(zig * 4));
                b.getNavigation().moveTo(to.x, to.y, to.z, d < 3.5 ? 1.7 : 1.35);
                if (b.onGround() && b.getRandom().nextInt(d < 3.5 ? 4 : 10) == 0) {
                    b.getJumpControl().jump();
                }
            }
        }
    }

    // The Kaioh planet's gravity while chasing (the manga's 10x, made playable): heavier falls, a slower step, low jumps.
    private static void gravity(ServerPlayer player, boolean on) {
        modify(player, Attributes.GRAVITY, GRAVITY_ID, 1.5, on);
        modify(player, Attributes.MOVEMENT_SPEED, SPEED_ID, -0.2, on);
        modify(player, Attributes.JUMP_STRENGTH, JUMP_ID, -0.25, on);
    }

    private static void modify(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id,
            double amount, boolean on) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (on) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (instance.hasModifier(id)) {
            instance.removeModifier(id);
        }
    }

    // A free spot to stand near pos: up or down to the first floor with room for a body.
    private static BlockPos stand(ServerLevel level, BlockPos pos) {
        for (int dy = 0; dy <= 12; dy++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos p = pos.above(dy * sign);
                if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                    return p;
                }
            }
        }
        return pos;
    }
}
