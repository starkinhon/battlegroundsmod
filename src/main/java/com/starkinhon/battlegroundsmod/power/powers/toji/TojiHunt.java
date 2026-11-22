// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.toji;

import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.boss.EndBossLoot;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.command.SoulCommand;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Toji hunting in Shibuya (Stark 2026-11-22), the way Freeza's ship lands on the Dragon Ball land (FzShip): a player of
// tier Universal or above who walks Shibuya is found now and then, at most once a day. The title warns, and he comes
// down 40-60 blocks away out of nowhere (no cursed energy: nothing saw him coming), a crater where he lands. Beaten
// there he drops the rare loot (EndBossLoot) and the Alvos advancement. With no player near for 3 minutes he leaves.
// One Toji at a time in the world.
public final class TojiHunt {
    private static final int TIER = 2;
    private static final int CHECK = 1200, CHANCE = 6;
    private static final int STAY_EMPTY = 20 * 60 * 3;
    private static final String DAY_TAG = "souls_toji_dia";

    private static UUID current;
    private static ServerLevel currentLevel;

    private TojiHunt() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TojiHunt::onPlayerTick);
        // /soul toji: Toji now, near the player (testing; ops only)
        SoulCommand.addSubcommand(() -> Commands.literal("toji").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            if (alive()) {
                c.getSource().sendFailure(Component.translatable("message.battlegroundsmod.toji.ja"));
                return 0;
            }
            return arrive(player) ? 1 : 0;
        }));
    }

    public static void resetStatics() {
        current = null;
        currentLevel = null;
    }

    private static boolean alive() {
        if (current == null || currentLevel == null) {
            return false;
        }
        Entity e = currentLevel.getEntity(current);
        return e != null && e.isAlive();
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK != CHECK / 3 || alive()
                || !Progression.on(player) || player.isSpectator() || player.isCreative() || Tiers.of(player) > TIER) {
            return;
        }
        if (player.level().dimension() != Level.OVERWORLD || !player.serverLevel().getBiome(player.blockPosition()).is(ModWorldgen.SHIBUYA)
                || player.getPersistentData().getLong(DAY_TAG) == day(player.level()) + 1
                || player.getRandom().nextInt(CHANCE) != 0) {
            return;
        }
        if (arrive(player)) {
            player.getPersistentData().putLong(DAY_TAG, day(player.level()) + 1);
        }
    }

    private static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    // A dry spot on the street 40-60 blocks from the player (lowest of a few tries: the street, not a roof).
    private static BlockPos spot(ServerLevel level, ServerPlayer player) {
        BlockPos best = null;
        for (int i = 0; i < 16; i++) {
            double a = level.random.nextDouble() * Mth.TWO_PI, d = 40 + level.random.nextDouble() * 20;
            int x = Mth.floor(player.getX() + Math.cos(a) * d), z = Mth.floor(player.getZ() + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int g = Ruin.ground(level, x, z);
            BlockPos top = new BlockPos(x, g + 1, z);
            if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty() || Math.abs(g - player.getY()) > 20) {
                continue;
            }
            if (best == null || top.getY() < best.getY()) {
                best = top;
            }
        }
        return best;
    }

    private static boolean arrive(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = spot(level, player);
        if (at == null) {
            return false;
        }
        Entity e = ModEntities.TOJI.get().spawn(level, at, MobSpawnType.EVENT);
        if (e == null) {
            return false;
        }
        Vec3 foot = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        e.setYRot((float) (Mth.atan2(player.getZ() - foot.z, player.getX() - foot.x) * Mth.RAD_TO_DEG) - 90);
        e.getPersistentData().putBoolean(EndBossLoot.WORLD_TAG, true);
        if (e instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setTarget(player);
        }
        current = e.getUUID();
        currentLevel = level;
        // he lands out of nowhere: the crater, the dust, the hit
        Ruin.bowl(level, foot.x, foot.z, 6, 3, 5, 0, Ruin.opts().rim(0.2));
        RuinNet.ring(level, foot, 36, 3, false, 24, Toji.DUST_LIGHT);
        RuinNet.dust(level, foot, 10, 6, 60, 10, Toji.DUST);
        RuinNet.quake(level, foot, 1.2, true, 0xFFFFFF);
        level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3f, 0.6f);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(foot) < 200 * 200) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.toji.titulo")
                        .withStyle(s -> s.withColor(0xE8E6E2))));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.toji.sub")));
                p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1f, 0.6f);
            }
        }
        UUID id = e.getUUID();
        Tasks.addSystem(age -> {
            if (age % 20 != 0) {
                return false;
            }
            Entity toji = level.getEntity(id);
            if (toji == null || !toji.isAlive()) {
                return true;
            }
            boolean empty = level.getNearestPlayer(toji.getX(), toji.getY(), toji.getZ(), 160, false) == null;
            if (empty && age > STAY_EMPTY) {
                toji.discard();
                return true;
            }
            return false;
        });
        return true;
    }
}
