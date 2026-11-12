package com.starkinhon.battlegroundsmod.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

// Sincronia: how in tune the player is with each soul (decided by Stark 2026-11-12, threads "Menu & Hud" and
// "Refazer o sistema de conquistas").
// - A soul comes with only its Z. The Poder the player earns while wearing a soul also fills that soul's Sincronia.
// - Each Sincronia level gives 1 point, and the player CHOOSES which skill to awaken with it in the Livro das Almas.
// - The ult (V, and Shift+V when there is one) only opens after every other skill is awake: the kit closes at the end.
// - Everything is saved per soul on the player: wearing Kuririn, swapping to Goku and back keeps Kuririn's Sincronia
//   and skills, so a fight can mix souls (Kuririn's stun, back to Goku).
// Only with progression on (Progression.on): in a flat world or with /gamerule soulsProgressao false every skill is
// free, like before. /soul skillcompleta wakes the whole kit for testing.
public final class SoulSync {
    // A level costs this many enemies of the player's own tier (Progression.value), a bit more each level.
    public static final double KILLS_PER_LEVEL = 4;
    public static final double GROWTH = 0.35;

    public record Entry(int level, float xp, int points, List<Integer> awake) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("level", 0).forGetter(Entry::level),
                Codec.FLOAT.optionalFieldOf("xp", 0f).forGetter(Entry::xp),
                Codec.INT.optionalFieldOf("points", 0).forGetter(Entry::points),
                Codec.INT.listOf().optionalFieldOf("awake", List.of(0)).forGetter(Entry::awake)
        ).apply(i, Entry::new));

        public static final Entry NEW = new Entry(0, 0, 0, List.of(0));

        public boolean has(int slot) {
            return awake.contains(slot);
        }
    }

    public static final Codec<Map<String, Entry>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC);

    // Client: whether skills are gated at all (synced with the map).
    public static boolean clientOn = true;

    private SoulSync() {
    }

    public static Entry get(Player player, String power) {
        Entry entry = player.getData(ModAttachments.SOUL_SYNC).get(power);
        return entry != null ? entry : Entry.NEW;
    }

    // The soul was worn at least once (or its crystal absorbed): it can be swapped back to from the book.
    public static boolean known(Player player, String power) {
        return player.getData(ModAttachments.SOUL_SYNC).containsKey(power);
    }

    private static void put(Player player, String power, Entry entry) {
        Map<String, Entry> map = new HashMap<>(player.getData(ModAttachments.SOUL_SYNC));
        map.put(power, entry);
        player.setData(ModAttachments.SOUL_SYNC, Map.copyOf(map));
    }

    // Every skill of the power, in awakening order: Z X C B, the Shift variants, then the ult(s) last.
    public static List<Integer> skills(Power power) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        List<Integer> ults = new ArrayList<>();
        for (int slot : power.slots()) {
            if (PowerData.isUlt(slot)) {
                ults.add(slot);
            } else {
                out.add(slot);
            }
        }
        for (int key = 0; key <= 4; key++) {
            int slot = power.shiftSlot(key);
            if (slot < PowerData.SLOT_SHIFT || (key == 4 && power.abilities().size() <= 4)) {
                continue;
            }
            if (PowerData.isUlt(slot)) {
                ults.add(slot);
            } else {
                out.add(slot);
            }
        }
        out.addAll(ults);
        return List.copyOf(out);
    }

    // Gating on? Server: progression; client: the synced flag.
    private static boolean gated(Player player) {
        if (player instanceof ServerPlayer sp) {
            return Progression.on(sp);
        }
        return clientOn;
    }

    // Can this skill be used? Base attacks and anything outside progression always can.
    public static boolean awake(Player player, Power power, int slot) {
        if (power == null || PowerLogic.isBaseAttack(slot) || !gated(player)) {
            return true;
        }
        if (!skills(power).contains(slot)) {
            return true;
        }
        return slot == 0 || get(player, power.id()).has(slot);
    }

    // Could this skill be awakened now (a point to spend, and the ult only after all the others)?
    public static boolean canAwaken(Player player, Power power, int slot) {
        Entry e = get(player, power.id());
        List<Integer> all = skills(power);
        if (!all.contains(slot) || e.has(slot) || e.points() <= 0) {
            return false;
        }
        if (PowerData.isUlt(slot)) {
            for (int s : all) {
                if (!PowerData.isUlt(s) && !e.has(s)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Sincronia needed for the next level, at the player's tier.
    public static double need(int tier, int level) {
        return Math.max(1, Progression.value(tier) * KILLS_PER_LEVEL * (1 + GROWTH * level));
    }

    // Levels left before the kit is complete: past that the bar stops (no points to spend).
    private static boolean complete(Power power, Entry e) {
        return e.awake().size() + e.points() >= skills(power).size();
    }

    // The soul is worn: make sure it has an entry (comes with its Z).
    public static void touch(ServerPlayer player, String power) {
        if (!known(player, power)) {
            put(player, power, Entry.NEW);
            sync(player);
        }
    }

    // Poder earned while wearing a soul fills its Sincronia (called from Progression.add).
    public static void gain(ServerPlayer player, double amount) {
        PowerData data = player.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (power == null || amount <= 0 || !Progression.on(player)) {
            return;
        }
        Entry e = get(player, power.id());
        if (complete(power, e)) {
            return;
        }
        int level = e.level();
        int points = e.points();
        double xp = e.xp() * need(data.tier, level) + amount;
        int gained = 0;
        while (xp >= need(data.tier, level) && !complete(power, new Entry(level, 0, points, e.awake()))) {
            xp -= need(data.tier, level);
            level++;
            points++;
            gained++;
        }
        Entry next = new Entry(level, complete(power, new Entry(level, 0, points, e.awake())) ? 1f : (float) (xp / need(data.tier, level)),
                points, e.awake());
        put(player, power.id(), next);
        if (gained > 0) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.sync_level", power.name().copy()
                    .withStyle(s -> s.withColor(power.color())), level), false);
            com.starkinhon.battlegroundsmod.combat.Fx.sound(player.serverLevel(), player.position(), SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
        }
        sync(player);
    }

    // The book asked to awaken a skill (ModPayloads.AwakenSkill).
    public static void awaken(ServerPlayer player, String powerId, int slot) {
        Power power = Powers.get(powerId);
        if (power == null || !canAwaken(player, power, slot)) {
            return;
        }
        Entry e = get(player, powerId);
        List<Integer> awake = new ArrayList<>(e.awake());
        awake.add(slot);
        put(player, powerId, new Entry(e.level(), e.xp(), e.points() - 1, List.copyOf(awake)));
        com.starkinhon.battlegroundsmod.combat.Fx.sound(player.serverLevel(), player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.sync_awake", power.abilityName(slot)), true);
        sync(player);
    }

    // /soul skillcompleta: the whole kit awake (testing, recording).
    public static void completeAll(ServerPlayer player, Power power) {
        Entry e = get(player, power.id());
        put(player, power.id(), new Entry(Math.max(e.level(), skills(power).size() - 1), 1f, 0, skills(power)));
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        Tag tag = MAP_CODEC.encodeStart(NbtOps.INSTANCE, player.getData(ModAttachments.SOUL_SYNC)).result().orElse(new CompoundTag());
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncSoulSync(tag instanceof CompoundTag c ? c : new CompoundTag(),
                Progression.on(player), player.getData(ModAttachments.POWER).tier));
    }

    // Client: apply the synced map.
    public static void apply(Player player, CompoundTag tag, boolean on) {
        clientOn = on;
        player.setData(ModAttachments.SOUL_SYNC, MAP_CODEC.parse(NbtOps.INSTANCE, tag).result().map(Map::copyOf).orElse(Map.of()));
    }

    // /soul skillcompleta [alma|todas] [jogador]
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("skillcompleta").requires(s -> s.hasPermission(2))
                .executes(c -> complete(c.getSource(), c.getSource().getPlayerOrException(), null))
                .then(Commands.argument("alma", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.stream.Stream.concat(java.util.stream.Stream.of("todas"), Powers.all().stream().map(Power::id)), b))
                        .executes(c -> complete(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "alma")))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(c -> complete(c.getSource(), EntityArgument.getPlayer(c, "jogador"), StringArgumentType.getString(c, "alma")))));
    }

    private static int complete(CommandSourceStack source, ServerPlayer player, String which) {
        List<Power> targets = new ArrayList<>();
        if ("todas".equals(which)) {
            targets.addAll(Powers.all());
        } else {
            Power power = Powers.get(which != null ? which : player.getData(ModAttachments.POWER).power);
            if (power == null) {
                source.sendFailure(Component.translatable("message.battlegroundsmod.sync_no_soul"));
                return 0;
            }
            targets.add(power);
        }
        for (Power power : targets) {
            completeAll(player, power);
        }
        source.sendSuccess(() -> Component.translatable("message.battlegroundsmod.sync_complete",
                targets.size() == 1 ? targets.get(0).name() : Component.literal(String.valueOf(targets.size())), player.getDisplayName()), true);
        return targets.size();
    }
}
