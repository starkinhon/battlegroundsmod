package com.starkinhon.soulsjjk.jjc;

import java.util.List;

import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.compat.Att;
import com.starkinhon.soulsjjk.power.Power;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.PowerLogic;
import com.starkinhon.soulsjjk.power.Powers;
import com.starkinhon.soulsjjk.power.Tiers;

import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.TechniqueDecideProcedure;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

// Gojo Souls and Sukuna Souls as JujutsuCraft cursed techniques (Stark, 2026-11-10): chosen in JujutsuCraft's technique
// book (JjcBook), their moves are entries of JujutsuCraft's technique selector (cycled with its key, Shift goes back)
// and are fired with its start key, paid in JujutsuCraft's cursed energy. JujutsuCraft's own entries stay: the three
// attacks (0 to 2) and "cancel domain" (21). Our entries take 3 to 19 and 20 (JujutsuCraft's domain slot).
//
// Behind each entry is the Souls mod's skill slot, run by PowerLogic exactly as in the Souls mod (its own cooldowns,
// charge by holding the key, second presses that command the move, the Expansões and their duel).
public final class JjcTechniques {
    public static final int GOJO = 51;
    public static final int SUKUNA = 52;

    // select: JujutsuCraft's selector number. slot: the Souls mod's skill slot. cost: cursed energy.
    record Entry(int select, int slot, double cost) {
    }

    private static final List<Entry> GOJO_ENTRIES = List.of(
            new Entry(3, 0, 80),                       // Azul (Z)
            new Entry(4, 1, 120),                      // Vermelho (X)
            new Entry(5, 2, 400),                      // Roxo (C)
            new Entry(6, PowerData.SLOT_SHIFT, 150),   // Azul + Black Flash (Shift + Z)
            new Entry(7, PowerData.SLOT_SHIFT + 1, 150), // Vermelho a queima-roupa (Shift + X)
            new Entry(8, PowerData.SLOT_SHIFT_ULT, 600), // Expansao de 0,2 s (Shift + V)
            new Entry(20, PowerData.SLOT_ULT, 1250));  // Expansao de Dominio: Vazio Infinito (V)

    private static final List<Entry> SUKUNA_ENTRIES = List.of(
            new Entry(3, 0, 40),                       // Desmantelar (Z)
            new Entry(4, PowerData.SLOT_SHIFT, 150),   // Desmantelar: cortes no oponente (Shift + Z)
            new Entry(5, 1, 120),                      // Clivar (X)
            new Entry(6, 2, 500),                      // Fuga (C)
            new Entry(7, PowerData.SLOT_EXTRA, 200),   // Cravada (B)
            new Entry(20, PowerData.SLOT_ULT, 1250));  // Santuario Malevolente (V)

    private JjcTechniques() {
    }

    public static boolean ours(double ct) {
        return ct == GOJO || ct == SUKUNA;
    }

    static List<Entry> entries(int ct) {
        return ct == GOJO ? GOJO_ENTRIES : ct == SUKUNA ? SUKUNA_ENTRIES : List.of();
    }

    static Entry entry(int ct, int select) {
        for (Entry e : entries(ct)) {
            if (e.select() == select) {
                return e;
            }
        }
        return null;
    }

    public static String powerOf(double ct) {
        return ct == GOJO ? Powers.STRONGEST.id() : ct == SUKUNA ? Powers.SUKUNA.id() : null;
    }

    public static JujutsucraftModVariables.PlayerVariables vars(Entity entity) {
        if (JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY == null) {
            return null;
        }
        return entity.getCapability(JujutsucraftModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(null);
    }

    // The technique in use (the second one when JujutsuCraft switched to it).
    public static int technique(JujutsucraftModVariables.PlayerVariables v) {
        return (int) Math.round(v.SecondTechnique ? v.PlayerCurseTechnique2 : v.PlayerCurseTechnique);
    }

    // ---------- selector ----------

    // JujutsuCraft's selector key (KeyChangeTechniqueOnKeyPressedProcedure). Returns true when the technique is ours and
    // it was handled here. With noChangeTechnique set JujutsuCraft only wants the entry refreshed (name and cost).
    public static boolean change(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        JujutsucraftModVariables.PlayerVariables v = vars(player);
        if (v == null || !ours(technique(v))) {
            return false;
        }
        int ct = technique(v);
        int select = (int) Math.round(v.PlayerSelectCurseTechnique);
        if (!v.noChangeTechnique) {
            int step = player.isShiftKeyDown() ? -1 : 1;
            for (int i = 0; i < 23; i++) {
                select += step;
                if (select < 0) {
                    select = 21;
                } else if (select > 21) {
                    select = 0;
                }
                if (valid(ct, select)) {
                    break;
                }
            }
        } else if (!valid(ct, select)) {
            select = 0;
        }
        decide(player, ct, select);
        return true;
    }

    private static boolean valid(int ct, int select) {
        return select <= 2 || select == 21 || entry(ct, select) != null;
    }

    private static void decide(Player player, int ct, int select) {
        boolean physical = false;
        boolean passive = false;
        double cost = 0;
        String name;
        Entry e = entry(ct, select);
        if (e != null) {
            Power power = Powers.get(powerOf(ct));
            name = power.abilityName(e.slot()).getString();
            cost = e.cost();
        } else if (select == 21) {
            name = Component.translatable("jujutsu.technique.cancel_domain").getString();
            physical = true;
            passive = true;
        } else {
            name = Component.translatable("jujutsu.technique.attack" + (select + 1)).getString();
            physical = true;
        }
        TechniqueDecideProcedure.execute(player, passive, physical, cost, ct, select, name);
    }

    // ---------- start ----------

    // JujutsuCraft's start key (StartCursedTechniqueProcedure). Returns true when the selected entry is one of ours and
    // it was handled here; JujutsuCraft's own entries (attacks, cancel domain) go on through JujutsuCraft.
    public static boolean start(Entity entity) {
        if (!(entity instanceof Player any)) {
            return false;
        }
        if (!(entity instanceof ServerPlayer player)) {
            // JujutsuCraft runs the key on the client too: our entries only run on the server.
            JujutsucraftModVariables.PlayerVariables cv = vars(any);
            return cv != null && ours(technique(cv)) && (any.getMainHandItem().getTag() == null || !any.getMainHandItem().getTag().getBoolean("used_item"))
                    && entry(technique(cv), (int) Math.round(cv.PlayerSelectCurseTechnique)) != null;
        }
        JujutsucraftModVariables.PlayerVariables v = vars(player);
        if (v == null || !ours(technique(v)) || holdingTechniqueItem(player)) {
            return false;
        }
        int ct = technique(v);
        Entry e = entry(ct, (int) Math.round(v.PlayerSelectCurseTechnique));
        if (e == null) {
            return false;
        }
        syncPower(player, v);
        PowerData data = Att.get(player, ModAttachments.POWER);
        // Held from the first tick, so a charged move knows the key is down (JujutsuCraft just set PRESS_Z).
        data.abilityHeld = true;
        boolean creative = player.getAbilities().instabuild;
        boolean free = creative || data.noCooldown;
        // A second press of a move that is out (closing the domain, commanding the Blue...) is free: PowerLogic sorts
        // that out before any cast, so only a press that starts a move is checked and charged.
        if (!free && !secondPress(player, e.slot())) {
            if (data.cooldowns[e.slot()] > 0) {
                player.displayClientMessage(Component.translatable("message.soulsjjk.jjc_cooldown",
                        String.format(java.util.Locale.ROOT, "%.1f", data.cooldowns[e.slot()] / 20.0)), true);
                return true;
            }
            if (blocked(player, e.select())) {
                player.displayClientMessage(Component.translatable("jujutsu.message.dont_use"), true);
                return true;
            }
            if (v.PlayerCursePower < e.cost()) {
                player.displayClientMessage(Component.translatable("jujutsu.message.dont_use"), true);
                return true;
            }
        }
        PowerLogic.tryUse(player, e.slot());
        if (PowerLogic.castOk && !creative) {
            v.PlayerCursePower = Math.max(0, v.PlayerCursePower - e.cost());
            v.syncPlayerVariables(player);
        }
        return true;
    }

    // A press that only talks to a move already out (PowerLogic.tryUse runs these before casting).
    private static boolean secondPress(ServerPlayer player, int slot) {
        if (PowerData.isUlt(slot)) {
            return com.starkinhon.soulsjjk.power.powers.duel.DomainDuel.fighting(player)
                    || com.starkinhon.soulsjjk.power.powers.gojo.InfiniteVoid.open(player)
                    || com.starkinhon.soulsjjk.power.powers.sukuna.ShrineDomain.open(player);
        }
        return slot == 0 && com.starkinhon.soulsjjk.power.powers.gojo.Azul.out(player);
    }

    // JujutsuCraft's own blocks on starting a technique (LogicStartProcedure): one of its techniques running, its
    // technique cooldown, and the burnout after a domain (which also blocks domains).
    private static boolean blocked(LivingEntity entity, int select) {
        if (has(entity, "cursed_technique") || has(entity, "unstable")) {
            return true;
        }
        return select != 20 && (has(entity, "cooldown_time") || has(entity, "falling_blossom_emotion") || has(entity, "domain_amplification"));
    }

    private static boolean has(LivingEntity entity, String id) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("jujutsucraft", id));
        return effect != null && entity.hasEffect(effect);
    }

    // A cursed tool or item with its own technique (JujutsuCraft's "used_item") fires through JujutsuCraft.
    private static boolean holdingTechniqueItem(ServerPlayer player) {
        var tag = player.getMainHandItem().getTag();
        return tag != null && tag.getBoolean("used_item");
    }

    // ---------- every tick ----------

    // The Souls power follows the JujutsuCraft technique; the key stays "held" while JujutsuCraft's start key is
    // down (PRESS_Z); the tier (which scales the moves' damage) follows the JujutsuCraft grade.
    public static void tick(ServerPlayer player) {
        JujutsucraftModVariables.PlayerVariables v = vars(player);
        if (v == null) {
            return;
        }
        syncPower(player, v);
        PowerData data = Att.get(player, ModAttachments.POWER);
        if (!data.hasPower()) {
            return;
        }
        data.abilityHeld = player.getPersistentData().getBoolean("PRESS_Z");
        if (player.tickCount % 20 == 0) {
            int tier = gradeTier(player);
            if (tier != data.tier) {
                data.tier = tier;
                PowerLogic.sync(player);
            }
        }
    }

    public static void syncPower(ServerPlayer player, JujutsucraftModVariables.PlayerVariables v) {
        PowerData data = Att.get(player, ModAttachments.POWER);
        String want = powerOf(technique(v));
        if (want == null) {
            if (data.hasPower()) {
                PowerLogic.clear(player);
            }
            return;
        }
        if (!want.equals(data.power)) {
            data.power = want;
            data.ult = 0;
            data.outfit = true;
            data.resetCombat();
            PowerLogic.sync(player);
        }
    }

    // JujutsuCraft's grade, as a Souls tier: Grau Especial = Tier 0 (the moves' full damage) down to no grade.
    private static final String[][] GRADES = {
            {"sorcerer_grade_special", "0"}, {"sorcerer_grade_1", "2"}, {"sorcerer_grade_1_semi", "3"}, {"sorcerer_grade_2", "4"},
            {"sorcerer_grade_2_semi", "5"}, {"sorcerer_grade_3", "6"}, {"sorcerer_grade_4", "7"}};
    private static final int NO_GRADE = 8;

    private static int gradeTier(ServerPlayer player) {
        var advancements = player.server.getAdvancements();
        for (String[] grade : GRADES) {
            Advancement adv = advancements.getAdvancement(new ResourceLocation("jujutsucraft", grade[0]));
            if (adv != null && player.getAdvancements().getOrStartProgress(adv).isDone()) {
                return Tiers.clamp(Integer.parseInt(grade[1]));
            }
        }
        return NO_GRADE;
    }
}
