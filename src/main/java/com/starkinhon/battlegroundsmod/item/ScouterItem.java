// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.animal.jjmob.JjMob;
import com.starkinhon.battlegroundsmod.animal.jjmob.JjMobSpec;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Goku;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Scouter (Dragon Ball): worn on the head it reads whoever the player looks at, up to 64 blocks: name, tier and power
// level (client/ScouterHud). Know if that one is too strong before picking the fight.
// Stark 2026-11-12 (remake dos itens DB): an Equipable head item, not an ArmorItem, so the player's head shows the
// scouter's own cube model (models/item/scouter_3d.json, "head" display, drawn by vanilla CustomHeadLayer) instead of
// a leather helmet: housing on the left ear, arm along the temple, translucent green lens over the left eye.
// Stark 2026-11-21 (remake, preview https://claude.ai/artifact/2ec5F7ZjqGmNFUeJCxk2f7 approved): the server reads the
// real power (onServerTick: tier, a small per-fighter spread, live boosts like the Kaioken, a boss's phase) and sends
// it while worn (ModPayloads.ScouterReadings); client/ScouterHud draws the lens on the left eye, locks on, shows glyphs
// then the number, the scan (key Y) and the break when the target is far too strong.
public class ScouterItem extends Item implements Equipable {
    // Every 5 ticks: a Kaioken shows on the lens within a quarter of a second.
    private static final int READ_EVERY = 5;
    // How far the scan reaches (the lock itself is up to 64 blocks, client/ScouterHud).
    public static final double SCAN_RANGE = 96;
    private static final int MAX_READS = 40;

    // Client side only, set by the ScouterReadings payload: the wearer's own power and each fighter's by entity id.
    public static long clientMe;
    public static Map<Integer, Long> clientLevels = new HashMap<>();
    public static long clientAt;

    public ScouterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ScouterItem::onServerTick);
    }

    public static boolean worn(net.minecraft.world.entity.player.Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SCOUTER.get());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % READ_EVERY != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !worn(player)) {
                continue;
            }
            List<LivingEntity> near = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(SCAN_RANGE),
                    e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
                            && e.distanceToSqr(player) <= SCAN_RANGE * SCAN_RANGE);
            near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
            List<Integer> ids = new ArrayList<>();
            List<Long> levels = new ArrayList<>();
            for (int i = 0; i < near.size() && i < MAX_READS; i++) {
                ids.add(near.get(i).getId());
                levels.add(level(near.get(i)));
            }
            PacketDistributor.sendToPlayer(player, new ModPayloads.ScouterReadings(level(player), ids, levels));
        }
    }

    // The power level the Scouter reads. The anime's scale: a human is 5 and each tier is about 5.6 times the one below
    // (Tier 0 close to 150 million). Players read exact; everyone else has a small spread of their own (0.85 to 1.2,
    // fixed by the UUID, so the same mob always reads the same). Animals and the like read 1 to 4.
    public static long level(LivingEntity e) {
        Integer tier = tierOf(e);
        double k;
        if (tier == null) {
            tier = Tiers.HUMANO;
            k = 0.2 + 0.6 * spread(e);
        } else {
            k = e instanceof net.minecraft.world.entity.player.Player ? 1 : 0.85 + 0.35 * spread(e);
        }
        double v = 5 * Math.pow(5.6, Tiers.HUMANO - tier) * k * boost(e);
        return Math.max(1, Math.round(v));
    }

    private static Integer tierOf(LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.player.Player || e instanceof SoulBoss) {
            return Tiers.of(e);
        }
        // A summoned curse pays nothing (Progression) but still has its grade.
        if (e instanceof JjMob curse) {
            return Tiers.clamp(JjMobSpec.tier(curse.kind()));
        }
        return Progression.tierOf(e);
    }

    private static double spread(LivingEntity e) {
        return (e.getUUID().getLeastSignificantBits() >>> 11) / (double) (1L << 53);
    }

    // What changes the reading live: the Kaioken (x2, then x3 / x10 / x20 from the Senhor Kaioh), the Kame's Max Power
    // and a boss's later phases.
    private static double boost(LivingEntity e) {
        double b = 1;
        if (SubCommon.hasState(e, DbCommon.KAIOKEN)) {
            int lv = Goku.kaiokenLevel(e);
            b *= lv >= 3 ? 20 : lv == 2 ? 10 : lv == 1 ? 3 : 2;
        }
        if (SubCommon.hasState(e, DbCommon.MAX_POWER)) {
            b *= 1.5;
        }
        if (e instanceof SoulBoss boss && boss.phase() > 0) {
            b *= 1 + 0.5 * boss.phase();
        }
        return b;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.scouter.desc").withStyle(s -> s.withColor(0x8EE07A)));
    }
}
