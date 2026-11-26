// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.SoulSync;
import com.starkinhon.battlegroundsmod.world.NarutoSites;
import com.starkinhon.battlegroundsmod.world.NarutoStructGen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The masters that teach (Stark 2026-11-19, "cada universo só tem uma técnica para ser ensinada"): per universe one
// technique of your own (Dragon Ball the Kamehameha, Naruto the Rasengan, Jujutsu a cutting blade) and one ability
// (the flight, the Controle de Energia, the Reversão). This class has Naruto's two:
// - Jiraiya, at the Sages' Spring (Myoboku), teaches the Rasengan in the manga's three steps with the training balloon:
//   1. the water balloon: hold right click until it bursts (3 s); 2. the rubber balloon: the same, harder (5 s);
//   3. the air balloon: fill it and let go between 70% and 90%, without bursting it. Then he teaches it: the Rasengan
//   becomes the player's own technique (worn at once without a soul; kept in the Livro das Almas otherwise).
// - Kakashi, at Training Ground 3 (Folha), teaches the Controle de Energia (universe/EnergyControl) to whoever passed
//   his bell test there (universe/NarutoActivities).
// In the normal world they stand at their structures (the nearest built one), like the Dragon Ball NPCs (DbActivities).
public final class MasterTraining {
    static final String JIRAIYA_STEP = "souls_jiraiya_etapa";
    static final String RASENGAN_DONE = "souls_rasengan_feito";
    public static final String BELLS_DONE = "souls_guizo_feito";
    private static final int WATER_TICKS = 60, RUBBER_TICKS = 100, AIR_TICKS = 80;
    private static final double AIR_LOW = 0.7, AIR_HIGH = 0.9;

    private record Home(Supplier<? extends EntityType<UniverseNpc>> type, String kind, int dx, int dz) {
    }

    private static final List<Home> HOMES = List.of(
            new Home(ModEntities.NPC_JIRAIYA, NarutoStructGen.MYOBOKU, 5, 7),
            new Home(ModEntities.NPC_KAKASHI, NarutoStructGen.FOLHA, 7, 9));

    private MasterTraining() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MasterTraining::onTick);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 37 && Progression.on(player)
                && player.level().dimension() == Level.OVERWORLD) {
            homes(player);
        }
    }

    private static void homes(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Home home : HOMES) {
            NarutoSites.Site site = NarutoSites.near(level, home.kind(), player.blockPosition(), 120);
            if (site == null) {
                continue;
            }
            int hx = site.x() + home.dx(), hz = site.z() + home.dz();
            EntityType<UniverseNpc> type = home.type().get();
            if (!com.starkinhon.battlegroundsmod.npc.NpcGuard.settled(level, hx, hz, 32)) {
                // his chunks' entities are not in yet: "nobody here" would make another one
                continue;
            }
            AABB area = new AABB(hx - 64, level.getMinBuildHeight(), hz - 64, hx + 64, level.getMaxBuildHeight(), hz + 64);
            if (!level.getEntities(type, area, Entity::isAlive).isEmpty()) {
                continue;
            }
            BlockPos at = DbActivities.dryGround(level, hx, hz, 12);
            if (at == null) {
                continue;
            }
            UniverseNpc npc = com.starkinhon.battlegroundsmod.npc.NpcGuard.spawn(level, type, at,
                    EntityType.getKey(type).getPath() + "@" + hx + "," + hz);
            if (npc != null) {
                npc.restrictTo(at, 8);
                npc.setCustomName(npc.getType().getDescription());
                npc.setCustomNameVisible(true);
            }
        }
    }

    // ---------------------------------------------------------------- Jiraiya: the Rasengan

    public static void jiraiya(ServerPlayer player, UniverseNpc npc, Talker say) {
        var data = player.getPersistentData();
        if (data.getBoolean(RASENGAN_DONE)) {
            say.line(Component.translatable("npc.battlegroundsmod.jiraiya.after." + player.getRandom().nextInt(3)));
            return;
        }
        int step = data.getInt(JIRAIYA_STEP);
        if (step >= 4) {
            teachRasengan(player);
            say.line(Component.translatable("npc.battlegroundsmod.jiraiya.teach"));
            return;
        }
        if (step == 0) {
            step = 1;
            data.putInt(JIRAIYA_STEP, step);
        }
        giveBalloon(player);
        say.line(Component.translatable("npc.battlegroundsmod.jiraiya.step" + step));
    }

    private static void giveBalloon(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.TRAINING_BALLOON.get())) {
                return;
            }
        }
        ItemStack balloon = new ItemStack(ModItems.TRAINING_BALLOON.get());
        if (!player.getInventory().add(balloon)) {
            player.drop(balloon, false);
        }
    }

    private static void removeBalloons(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.TRAINING_BALLOON.get())) {
                s.setCount(0);
            }
        }
        if (player.getOffhandItem().is(ModItems.TRAINING_BALLOON.get())) {
            player.getOffhandItem().setCount(0);
        }
    }

    // Held for `ticks`: shows the bar; true when the balloon burst (it stops being held).
    public static boolean balloonHeld(ServerPlayer player, int ticks) {
        int step = player.getPersistentData().getInt(JIRAIYA_STEP);
        if (step < 1 || step > 3) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.balao.jiraiya"), true);
            return true;
        }
        int goal = step == 1 ? WATER_TICKS : step == 2 ? RUBBER_TICKS : AIR_TICKS;
        double k = Math.min(1, ticks / (double) goal);
        Charge.report(player, k);
        ServerLevel level = player.serverLevel();
        if (ticks % 6 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.15f + 0.25f * (float) k, 1.4f + (float) k);
        }
        if (step == 3) {
            int pct = (int) Math.round(k * 100);
            boolean green = k >= AIR_LOW && k <= AIR_HIGH;
            player.displayClientMessage(Component.translatable(green ? "message.battlegroundsmod.balao.solte" : "message.battlegroundsmod.balao.ar", pct)
                    .withStyle(s -> s.withColor(green ? 0x6FE38A : 0xE6E0EE)), true);
        } else {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.balao.enche" + step, (int) Math.round(k * 100)), true);
        }
        if (ticks < goal) {
            return false;
        }
        burst(player, step == 1 ? 0x5AB8FF : step == 2 ? 0xE04848 : 0xDDEEFF);
        if (step == 3) {
            // the air balloon was meant to hold: burst = start the step again
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.balao.estourou_ar"), true);
            return true;
        }
        player.getPersistentData().putInt(JIRAIYA_STEP, step + 1);
        title(player, "message.battlegroundsmod.balao.passou" + step, "message.battlegroundsmod.balao.proxima" + step);
        return true;
    }

    public static void balloonReleased(ServerPlayer player, int ticks) {
        int step = player.getPersistentData().getInt(JIRAIYA_STEP);
        if (step < 1 || step > 3) {
            return;
        }
        int goal = step == 1 ? WATER_TICKS : step == 2 ? RUBBER_TICKS : AIR_TICKS;
        double k = ticks / (double) goal;
        if (step < 3) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.balao.fraco"), true);
            return;
        }
        if (k < AIR_LOW) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.balao.cedo"), true);
            return;
        }
        // let go in the green: the energy held in a ball, without bursting
        player.getPersistentData().putInt(JIRAIYA_STEP, 4);
        removeBalloons(player);
        ServerLevel level = player.serverLevel();
        Fx.burst(level, Fx.dust(0x9FD8FF, 1.4f), player.position().add(0, 1.2, 0), 40, 0.3, 0.04);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.5f);
        title(player, "message.battlegroundsmod.balao.passou3", "message.battlegroundsmod.balao.proxima3");
    }

    private static void burst(ServerPlayer player, int color) {
        ServerLevel level = player.serverLevel();
        Fx.burst(level, Fx.dust(color, 1.2f), player.position().add(0, 1.2, 0).add(player.getLookAngle().scale(0.5)), 30, 0.35, 0.12);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35f, 2f);
    }

    private static void teachRasengan(ServerPlayer player) {
        if (!SoulSync.learnInnate(player, Powers.RASENGAN)) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.rasengan_livro", Component.keybind("key.battlegroundsmod.skills_menu")), false);
        }
        player.getPersistentData().putBoolean(RASENGAN_DONE, true);
        removeBalloons(player);
        title(player, "message.battlegroundsmod.rasengan_aprendeu", "message.battlegroundsmod.rasengan_ensinou");
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    // ---------------------------------------------------------------- Kakashi: the Controle de Energia

    public static void kakashi(ServerPlayer player, UniverseNpc npc, Talker say) {
        if (EnergyControl.knows(player)) {
            say.line(Component.translatable("npc.battlegroundsmod.kakashi.after." + player.getRandom().nextInt(3)));
            return;
        }
        if (!player.getPersistentData().getBoolean(BELLS_DONE)) {
            say.line(Component.translatable("npc.battlegroundsmod.kakashi.weak"));
            return;
        }
        List<String> techniques = new ArrayList<>(player.getData(ModAttachments.TECHNIQUES));
        techniques.add(EnergyControl.TECHNIQUE);
        player.setData(ModAttachments.TECHNIQUES, List.copyOf(techniques));
        EnergyControl.sync(player);
        say.line(Component.translatable("npc.battlegroundsmod.kakashi.teach"));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.technique_learned")
                .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("technique.battlegroundsmod.controle_energia")));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    // ----------------------------------------------------------------

    static void title(ServerPlayer player, String title, String sub) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 50, 14));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(s -> s.withColor(0x6FC3FF).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub)));
    }

    // How an NPC's line reaches the player (Npcs.say: the talk scene or the chat).
    public interface Talker {
        void line(Component line);
    }
}
