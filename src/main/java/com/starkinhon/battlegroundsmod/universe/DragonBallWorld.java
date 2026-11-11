package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Progression;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// What fills the Dragon Ball universe besides its characters (Stark 2026-11-10: "items with a real advantage, fill the
// world, be creative"):
// - its common enemies drop its items now and then: Soldado do Freeza -> Scouter, Saibaman -> Senzu, Soldado da Red
//   Ribbon -> Cápsula Hoi-Poi;
// - any enemy beaten there may drop an Esfera do Dragão the player is still missing (SubAlmas much more often, three
//   times as often with the Radar in the inventory);
// - with the seven, Shenlong grants one wish (clickable in the chat): a free trip (Cristais Multiversais), Senzus, or
//   the rest of the current tier's bar at once.
public final class DragonBallWorld {
    public static final String UNIVERSE = "dragonball";
    private static final String WISH_TAG = "souls_desejo_ate";
    private static final double BALL_CHANCE = 0.03;
    private static final double BALL_CHANCE_SUB = 0.3;

    private DragonBallWorld() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DragonBallWorld::onDeath);
    }

    private static boolean here(Entity entity) {
        return UNIVERSE.equals(Universes.universeOf(entity.level().dimension()));
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !here(victim) || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(victim instanceof Enemy) && !(victim instanceof SoulBoss)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath();
        switch (id) {
            case "soldado_freeza" -> chance(level, victim, ModItems.SCOUTER.get(), 0.06);
            case "saibaman" -> chance(level, victim, ModItems.SENZU.get(), 0.06);
            default -> {
            }
        }
        boolean sub = victim instanceof SoulBoss boss && Powers.isSub(boss.powerData().power);
        double ball = (sub ? BALL_CHANCE_SUB : BALL_CHANCE) * (has(player, ModItems.DRAGON_RADAR.get()) ? 3 : 1);
        if (level.getRandom().nextDouble() < ball) {
            List<Item> missing = missing(player);
            if (!missing.isEmpty()) {
                Item item = missing.get(level.getRandom().nextInt(missing.size()));
                drop(level, victim, item);
                Fx.burst(level, ParticleTypes.END_ROD, victim.position().add(0, 1, 0), 30, 0.4, 0.1);
                Fx.sound(level, victim.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
            }
        }
    }

    private static void chance(ServerLevel level, LivingEntity victim, Item item, double chance) {
        if (level.getRandom().nextDouble() < chance) {
            drop(level, victim, item);
        }
    }

    private static void drop(ServerLevel level, LivingEntity victim, Item item) {
        ItemEntity entity = new ItemEntity(level, victim.getX(), victim.getY() + 0.5, victim.getZ(), new ItemStack(item));
        entity.setDefaultPickUpDelay();
        entity.setGlowingTag(true);
        level.addFreshEntity(entity);
    }

    private static boolean has(ServerPlayer player, Item item) {
        return player.getInventory().contains(new ItemStack(item));
    }

    // The Esferas the player doesn't carry yet.
    public static List<Item> missing(ServerPlayer player) {
        List<Item> missing = new ArrayList<>();
        for (var ball : ModItems.DRAGON_BALLS) {
            if (!has(player, ball.get())) {
                missing.add(ball.get());
            }
        }
        return missing;
    }

    // Radar: which Esferas are missing, and where the nearest enemy is (an Esfera may be with it).
    public static void radar(ServerPlayer player) {
        List<Item> missing = missing(player);
        if (missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.radar_all"), false);
            return;
        }
        StringBuilder stars = new StringBuilder();
        for (Item item : missing) {
            if (item instanceof com.starkinhon.battlegroundsmod.item.DragonBallItem ball) {
                stars.append(stars.length() == 0 ? "" : ", ").append(ball.stars());
            }
        }
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.radar_missing", stars.toString()), false);
        LivingEntity nearest = null;
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(96),
                e -> e instanceof SoulBoss && e.isAlive())) {
            if (nearest == null || entity.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = entity;
            }
        }
        if (nearest != null) {
            Vec3 to = nearest.position().subtract(player.position());
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.radar_target", nearest.getName(),
                    Math.round(to.horizontalDistance()), direction(to)), false);
        }
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, 1.8f);
    }

    private static Component direction(Vec3 to) {
        double angle = Math.toDegrees(Math.atan2(-to.x, to.z));
        String[] names = {"sul", "sudoeste", "oeste", "noroeste", "norte", "nordeste", "leste", "sudeste"};
        int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return Component.translatable("direction.battlegroundsmod." + names[index]);
    }

    // Whether the player carries at least one Dragon Ball (DbQuests' first ball).
    public static boolean hasAnyBall(ServerPlayer player) {
        for (var ball : ModItems.DRAGON_BALLS) {
            if (player.getInventory().findSlotMatchingItem(new ItemStack(ball.get())) >= 0) {
                return true;
            }
        }
        return false;
    }

    // With all seven: they fly off and Shenlong appears for one wish (60 s to choose, in the chat).
    public static void summon(ServerPlayer player) {
        List<Item> missing = missing(player);
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.esferas_faltam", 7 - missing.size()), true);
            return;
        }
        for (var ball : ModItems.DRAGON_BALLS) {
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(ball.get()));
            if (slot >= 0) {
                player.getInventory().removeItem(slot, 1);
            }
        }
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 3; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(player.getX() + (i - 1) * 4, player.getY(), player.getZ() + 4);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
        Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, player.position().add(0, 6, 0), 200, 3, 0.2);
        Fx.sound(level, player.position(), SoundEvents.ENDER_DRAGON_GROWL, 1f, 0.7f);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SHENLONG").withStyle(s -> s.withColor(0x3FD46A).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.shenlong_sub")));
        player.getPersistentData().putLong(WISH_TAG, level.getGameTime() + 1200);
        player.sendSystemMessage(Component.translatable("message.battlegroundsmod.shenlong_ask").withStyle(s -> s.withColor(0x3FD46A)));
        for (String wish : new String[] {"poder", "alma", "rival", "imortal", "senzu", "viagem"}) {
            Component line = Component.literal("  » ").append(Component.translatable("message.battlegroundsmod.wish." + wish))
                    .withStyle(s -> s.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/soul desejo " + wish))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("message.battlegroundsmod.wish." + wish + ".desc"))));
            player.sendSystemMessage(line);
        }
    }

    private static int wish(ServerPlayer player, String wish) {
        long until = player.getPersistentData().getLong(WISH_TAG);
        if (until < player.serverLevel().getGameTime()) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.wish_none"), true);
            return 0;
        }
        switch (wish) {
            case "viagem" -> give(player, new ItemStack(ModItems.MULTIVERSAL_CRYSTAL.get(), 2));
            case "senzu" -> give(player, new ItemStack(ModItems.SENZU.get(), 5));
            case "poder" -> {
                PowerData data = player.getData(ModAttachments.POWER);
                double rest = Progression.cost(data.tier) - data.poder;
                if (rest <= 0 || !Progression.on(player)) {
                    give(player, new ItemStack(ModItems.SENZU.get(), 5));
                } else {
                    Progression.add(player, rest);
                }
            }
            case "alma" -> give(player, wishedSoul(player));
            case "rival" -> {
                if (!rival(player)) {
                    give(player, new ItemStack(ModItems.SENZU.get(), 5));
                }
            }
            case "imortal" -> {
                player.getPersistentData().putLong(IMMORTAL_TAG, player.serverLevel().getGameTime() + IMMORTAL_TICKS);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, IMMORTAL_TICKS, 1, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, IMMORTAL_TICKS, 0, false, true));
            }
            default -> {
                return 0;
            }
        }
        player.getPersistentData().remove(WISH_TAG);
        player.sendSystemMessage(Component.translatable("message.battlegroundsmod.shenlong_done").withStyle(s -> s.withColor(0x3FD46A)));
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.6f);
        Fx.burst(player.serverLevel(), ParticleTypes.END_ROD, player.position().add(0, 2, 0), 70, 1.5, 0.3);
        return 1;
    }

    // The more wishes (Stark 2026-11-10: "mais pedido pro Shenlong"): a soul, a rival, eternal life for a while.
    public static final String IMMORTAL_TAG = "souls_imortal_ate";
    private static final int IMMORTAL_TICKS = 20 * 60 * 10;

    // Whether the "imortal" wish still holds: dying then loses no Poder (Progression).
    public static boolean immortal(ServerPlayer player) {
        return player.getPersistentData().getLong(IMMORTAL_TAG) > player.serverLevel().getGameTime();
    }

    // "Alma": the soul of a Dragon Ball character the player already beat in its arena (Kuririn's if none yet).
    private static ItemStack wishedSoul(ServerPlayer player) {
        var spec = com.starkinhon.battlegroundsmod.skill.ChallengeSpec.get();
        var universe = spec.universe("dragonball");
        List<String> beaten = new java.util.ArrayList<>();
        if (universe != null) {
            for (var c : universe.desafios()) {
                if (com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.wins(player).contains(c.id())) {
                    beaten.add(c.id());
                }
            }
        }
        String id = beaten.isEmpty() ? "kuririn" : beaten.get(player.getRandom().nextInt(beaten.size()));
        try {
            var soul = ModItems.SOULS.get(com.starkinhon.battlegroundsmod.boss.BossSpec.get(id).poder());
            if (soul != null) {
                return new ItemStack(soul.get());
            }
        } catch (RuntimeException ignored) {
        }
        return new ItemStack(ModItems.SENZU.get(), 5);
    }

    // "Rival": a Dragon Ball character comes down to fight, out in the world (the next one the player has not beaten
    // in the book, else any of them).
    private static boolean rival(ServerPlayer player) {
        var spec = com.starkinhon.battlegroundsmod.skill.ChallengeSpec.get();
        var universe = spec.universe("dragonball");
        if (universe == null || universe.desafios().isEmpty()) {
            return false;
        }
        String pick = null;
        for (var c : universe.desafios()) {
            if (!com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.wins(player).contains(c.id())) {
                pick = c.id();
                break;
            }
        }
        if (pick == null) {
            pick = universe.desafios().get(player.getRandom().nextInt(universe.desafios().size())).id();
        }
        var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, pick);
        if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
            return false;
        }
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(key);
        var at = player.blockPosition().relative(player.getDirection(), 8);
        at = player.serverLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        var entity = type.spawn(player.serverLevel(), at, net.minecraft.world.entity.MobSpawnType.EVENT);
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setTarget(player);
            mob.setPersistenceRequired();
        }
        return entity != null;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // /soul desejo <poder|alma|rival|imortal|senzu|viagem>: the wish, clicked in the chat after calling Shenlong.
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("desejo")
                .then(Commands.argument("desejo", StringArgumentType.word())
                        .executes(context -> wish(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "desejo"))));
    }
}
