package com.starkinhon.battlegroundsmod.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.boss.BossSpec;
import com.starkinhon.battlegroundsmod.challenge.ArenaTemplate;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// What the Livro dos Desafios shows of each challenger, gathered once from the mod's own data: the universe pages of the
// book (souls_skills/padrao.desafios.json), the boss spec (tier, power), the power (name, color, skills) and the arena
// (its place's name).
public final class BookData {
    public record Skill(int key, String name, String desc) {
    }

    public record Ch(String id, String nome, String alma, boolean boss, int tier, String foco, List<ItemStack> itens, int cor, String lugar,
                     String skin, ItemStack soul, List<Skill> skills, Uni uni) {
    }

    public static final class Uni {
        public final String id, nome;
        public final int cor;
        public final List<Ch> desafios = new ArrayList<>();

        Uni(String id, String nome, int cor) {
            this.id = id;
            this.nome = nome;
            this.cor = cor;
        }
    }

    private static List<Uni> unis;

    private BookData() {
    }

    private static String unisLang = "";

    public static List<Uni> unis() {
        String lang = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        if (unis == null || !unisLang.equals(lang)) {
            unisLang = lang;
            List<Uni> out = new ArrayList<>();
            for (ChallengeSpec.Universe u : ChallengeSpec.get().universos()) {
                if (u.desafios().isEmpty()) {
                    continue;
                }
                Uni uni = new Uni(u.id(), u.nome(), u.color());
                for (ChallengeSpec.Challenge c : u.desafios()) {
                    uni.desafios.add(make(c, uni));
                }
                out.add(uni);
            }
            unis = out;
        }
        return unis;
    }

    private static Ch make(ChallengeSpec.Challenge c, Uni uni) {
        String nome = c.id();
        int tier = Tiers.HUMANO, cor = uni.cor;
        String powerId = null;
        List<Skill> skills = new ArrayList<>();
        try {
            BossSpec spec = BossSpec.get(c.id());
            powerId = spec.poder();
            tier = spec.tier() != null ? spec.tier() : Tiers.HUMANO;
            Power power = Powers.get(powerId);
            if (power != null) {
                nome = power.name().getString();
                cor = power.color();
                for (int slot : power.slots()) {
                    int key = power.slotKey(slot);
                    if (key > 4) {
                        continue;
                    }
                    int index = key == 4 ? 4 : slot;
                    String base = "ability." + BattlegroundsMod.MODID + "." + power.id() + "." + index;
                    String desc = I18n.exists(base + ".desc") ? I18n.get(base + ".desc") : "";
                    skills.add(new Skill(key, power.abilityName(slot).getString(), desc));
                }
            }
        } catch (RuntimeException ignored) {
        }
        List<ItemStack> itens = new ArrayList<>();
        if (c.itens() != null) {
            for (String entry : c.itens()) {
                String[] parts = entry.trim().split("\\s+");
                ResourceLocation key = ResourceLocation.tryParse(parts[0]);
                if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                    continue;
                }
                int count = 1;
                try {
                    count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                } catch (NumberFormatException ignored) {
                }
                Item item = BuiltInRegistries.ITEM.get(key);
                itens.add(new ItemStack(item, count));
            }
        }
        ArenaTemplate.Meta meta = ArenaTemplate.meta(c.id());
        String lugar = meta != null && meta.nome() != null && !meta.nome().equals(c.id()) ? meta.nome() : "";
        // the arena's name and the challenger's line in the game's language (the data files are in Portuguese; Stark
        // 2026-11-13: the book mixed both languages with the game in English)
        if (!lugar.isEmpty()) {
            lugar = Component.translatableWithFallback("arena.battlegroundsmod." + c.id(), lugar).getString();
        }
        String foco = c.foco() == null ? "" : Component.translatableWithFallback("challenge.battlegroundsmod." + c.id() + ".foco", c.foco()).getString();
        ItemStack soul = ItemStack.EMPTY;
        if (powerId != null) {
            var holder = ModItems.SOULS.get(powerId);
            if (holder != null) {
                soul = new ItemStack(holder.get());
            }
        }
        boolean boss = ChallengeArenas.isBoss(c.id());
        return new Ch(c.id(), nome, nome, boss, tier, foco, itens, cor, lugar, c.id(), soul, skills, uni);
    }

    public static String tierName(int tier) {
        return Tiers.name(tier).getString();
    }

    public static boolean won(String id) {
        return ChallengeArenas.clientWins.contains(id);
    }

    public static int subsBeaten(Uni u) {
        int n = 0;
        for (Ch c : u.desafios) {
            if (!c.boss() && won(c.id())) {
                n++;
            }
        }
        return n;
    }

    public static int subsNeeded(Uni u) {
        int subs = 0;
        for (Ch c : u.desafios) {
            if (!c.boss()) {
                subs++;
            }
        }
        return Math.min(ChallengeSpec.get().subAlmasParaChefes(), subs);
    }

    // Locked: a boss whose universe has not enough SubAlmas beaten (free with progression off).
    public static boolean locked(Ch c) {
        return !com.starkinhon.battlegroundsmod.power.Progression.clientOn ? false : c.boss() && subsBeaten(c.uni()) < subsNeeded(c.uni());
    }

    // The player's record against a challenger: {wins, best time in seconds} or null.
    public static int[] record(String id) {
        Map<String, int[]> recs = ChallengeArenas.clientRecords;
        return recs == null ? null : recs.get(id);
    }

    public static Component lang(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
