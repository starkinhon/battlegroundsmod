package com.starkinhon.battlegroundsmod.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The bosses' lines: short phrases from the anime they say in certain moments of the fight, floating over their head
// (client/FalaRender) for everyone near, instead of the chat. Only real bosses speak (not the player with the soul,
// not the SubAlma mobs). The text is a lang key (fala.battlegroundsmod.*) or plain text (a souls_bosses "fala").
// Lines come from the power the boss carries, so a future Aizen or Kokushibo boss speaks with no extra code.
public final class BossFalas {
    // Who hears it.
    private static final double RANGE = 32;
    // Gap between two lines of the same boss, so it never turns into spam; kills skip it.
    private static final int GAP = 200;
    // A target lost for this long ends the fight (the "once per fight" lines can come again).
    private static final int FIGHT_END = 300;
    // Bills gets hungry after this long in the same fight.
    private static final int HUNGRY = 1800;

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    // Last distance of each target the Azul pulls (by entity id), to see who is getting away from it.
    private static final Map<Integer, double[]> AZUL = new HashMap<>();

    private static final class State {
        long lastSaid = -100000;
        final Map<String, Long> said = new HashMap<>();
        long fightStart = -1;
        long lastTarget = -100000;
        boolean lowSaid;
        long formName = -100000;
    }

    private BossFalas() {
    }

    // The boss says this line if it may: true when it did.
    public static boolean say(LivingEntity speaker, String key, int cooldown) {
        return say(speaker, key, cooldown, false);
    }

    // Says it now, over the gap between lines (a scripted moment, like the Bills boss's Inevitável).
    public static boolean sayNow(LivingEntity speaker, String key) {
        return say(speaker, key, 0, true);
    }

    private static boolean say(LivingEntity speaker, String key, int cooldown, boolean force) {
        if (!(speaker instanceof SoulBoss boss) || !boss.isBoss() || !boss.isAlive() || !(boss.level() instanceof ServerLevel level)) {
            return false;
        }
        State state = STATES.computeIfAbsent(boss, b -> new State());
        long now = level.getGameTime();
        Long last = state.said.get(key);
        if (last != null && now - last < cooldown) {
            return false;
        }
        if (!force && now - state.lastSaid < GAP) {
            return false;
        }
        state.lastSaid = now;
        state.said.put(key, now);
        int[] colors = colors(boss.powerData().power);
        ModPayloads.Fala payload = new ModPayloads.Fala(boss.getId(), key, colors[0], colors[1]);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(boss) <= RANGE * RANGE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        return true;
    }

    // Anyone (players too) says a scripted line in a move, over their head (client/FalaRender), with these shades.
    public static void line(LivingEntity speaker, String key, int[] colors) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        ModPayloads.Fala payload = new ModPayloads.Fala(speaker.getId(), key, colors[0], colors[1]);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) <= RANGE * RANGE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // Once per fight: true the first time in this fight.
    private static boolean sayOncePerFight(LivingEntity speaker, String key) {
        if (!(speaker instanceof SoulBoss boss)) {
            return false;
        }
        State state = STATES.computeIfAbsent(boss, b -> new State());
        if (state.said.containsKey(key) && state.said.get(key) >= state.fightStart && state.fightStart >= 0) {
            return false;
        }
        return say(speaker, key, 0);
    }

    // The soul's dark and bright shades: the text runs from the dark one (left) to the bright one (right), the outline is
    // the dark one darkened and the glow the bright one (client/FalaRender).
    public static int[] colors(String power) {
        return switch (power == null ? "" : power) {
            case "sukuna" -> new int[] {0x7A0E14, 0xFF6A50};
            case "makima" -> new int[] {0xA3122A, 0xFFD6DC};
            case "rubber_boy" -> new int[] {0x9E1B12, 0xFFD36B};
            case "one_punch" -> new int[] {0xB07A10, 0xFFF3B0};
            case "shigaraki" -> new int[] {0x5A0E0E, 0xDADCE4};
            case "hashirama" -> new int[] {0x3E5A2C, 0xF2D49A};
            case "strongest" -> new int[] {0x1E5FD0, 0x8FE0FF};
            case "uchiha_ghost" -> new int[] {0x8A1A14, 0xFF5A3C};
            case "destruction_god" -> new int[] {0x6A2AC0, 0xE2B4FF};
            case "manipulator" -> new int[] {0x4C1CA8, 0xC8A2FF};
            case "moon_demon" -> new int[] {0x7A1A9E, 0xFF9AE8};
            default -> new int[] {0xFFFFFF, 0xFFFFFF};
        };
    }

    // Every server tick of a boss: the fight clock and the lines that come from how the fight is going.
    public static void tick(SoulBoss boss) {
        if (!boss.isBoss() || !boss.isAlive() || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        State state = STATES.computeIfAbsent(boss, b -> new State());
        long now = level.getGameTime();
        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            if (state.fightStart < 0 || now - state.lastTarget > FIGHT_END) {
                state.fightStart = now;
                // Kokushibo opens every fight with a line (Stark).
                if ("moon_demon".equals(boss.powerData().power)) {
                    sayNow(boss, "fala.battlegroundsmod.kokushibo.inicio");
                }
                // Sukuna too: against a Gojo the line from the start of their fight, against anyone else "know your place".
                if ("sukuna".equals(boss.powerData().power)) {
                    sayNow(boss, "strongest".equals(powerOf(target)) ? "fala.battlegroundsmod.sukuna.inicio_gojo"
                            : "fala.battlegroundsmod.sukuna.inicio");
                }
                // The four of 2026-11-12: each opens the fight with its line.
                String power = boss.powerData().power;
                String opening = switch (power == null ? "" : power) {
                    case "rubber_boy" -> "fala.battlegroundsmod.luffy.inicio";
                    case "one_punch" -> "fala.battlegroundsmod.saitama.inicio";
                    case "shigaraki" -> "fala.battlegroundsmod.shigaraki.inicio";
                    case "makima" -> "fala.battlegroundsmod.makima.inicio";
                    case "hashirama" -> "fala.battlegroundsmod.hashirama.inicio";
                    default -> null;
                };
                if (opening != null) {
                    sayNow(boss, opening);
                }
            }
            state.lastTarget = now;
        } else if (now - state.lastTarget > FIGHT_END) {
            state.fightStart = -1;
        }
        float life = boss.getHealth() / boss.getMaxHealth();
        if (life > 0.6f) {
            state.lowSaid = false;
        }
        // Sukuna: low on life, once until he heals back up.
        if ("sukuna".equals(boss.powerData().power) && !state.lowSaid && life <= 0.3f
                && say(boss, "fala.battlegroundsmod.sukuna.pouca_vida", 0)) {
            state.lowSaid = true;
        }
        if (!"destruction_god".equals(boss.powerData().power) || target == null || !target.isAlive() || BillsInevitavel.running(boss)) {
            return;
        }
        // Bills: the target stunned right in front of him.
        Vec3 to = target.position().subtract(boss.position());
        if (Stun.is(target) && to.length() < 6 && to.lengthSqr() > 1.0e-4
                && Combat.flatLook(boss).dot(new Vec3(to.x, 0, to.z).normalize()) > 0.5) {
            say(boss, "fala.battlegroundsmod.bills.atordoado", 600);
        }
        // Bills: the fight dragging on.
        if (state.fightStart >= 0 && now - state.fightStart >= HUNGRY) {
            sayOncePerFight(boss, "fala.battlegroundsmod.bills.fome");
        }
        // Bills: low on life, once until he heals back up.
        if (!state.lowSaid && life <= 0.3f && say(boss, "fala.battlegroundsmod.bills.pouca_vida", 0)) {
            state.lowSaid = true;
        }
    }

    // The Azul is pulling this target and it is still getting away (running, dashing): Gojo teases it.
    public static void azulPull(LivingEntity caster, LivingEntity target, double distance) {
        if (!(caster instanceof SoulBoss) || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        double[] last = AZUL.put(target.getId(), new double[] {distance, now});
        if (AZUL.size() > 256) {
            AZUL.clear();
        }
        if (last != null && now - (long) last[1] <= 2 && distance - last[0] > 0.2) {
            say(caster, "fala.battlegroundsmod.gojo.fugindo", 400);
        }
    }

    // Kokushibo: the first Moon form of a fight.
    public static void firstForm(LivingEntity caster) {
        sayOncePerFight(caster, "fala.battlegroundsmod.kokushibo.respiracao");
    }

    // Kokushibo says the name of the form he is using now and then (Stark, 2026-11-08: every 2 minutes; the 16th always).
    private static final int FORM_NAME_GAP = 2400;

    public static void formName(LivingEntity caster, int form) {
        if (!(caster instanceof KokushiboEntity boss) || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        State state = STATES.computeIfAbsent(boss, b -> new State());
        long now = level.getGameTime();
        String key = "fala.battlegroundsmod.kokushibo.forma." + form;
        boolean said = form == 16 ? sayNow(boss, key) : now - state.formName >= FORM_NAME_GAP && say(boss, key, 0);
        if (said) {
            state.formName = now;
        }
    }

    // A boss killed someone.
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof SoulBoss boss) || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // The Inevitável ends with its own line.
        if (BillsInevitavel.victim(victim)) {
            return;
        }
        String key = switch (boss.powerData().power) {
            // Gojo only says his when someone else is still fighting him.
            case "strongest" -> othersLeft(level, boss, victim) ? "fala.battlegroundsmod.gojo.menos_um" : null;
            case "uchiha_ghost" -> "fala.battlegroundsmod.madara.matou";
            case "destruction_god" -> "fala.battlegroundsmod.bills.matou";
            case "manipulator" -> "fala.battlegroundsmod.aizen.matou";
            case "moon_demon" -> "fala.battlegroundsmod.kokushibo.matou";
            // Sukuna: to Gojo his farewell; to anyone of his tier or above, "stand proud"; the weak get nothing.
            case "sukuna" -> "strongest".equals(powerOf(victim)) ? "fala.battlegroundsmod.sukuna.matou_gojo"
                    : tierOf(victim) <= boss.powerData().tier ? "fala.battlegroundsmod.sukuna.matou_forte" : null;
            // Saitama: someone who was a fight (a player or another soul), over again in no time.
            case "one_punch" -> victim instanceof Player || victim instanceof SoulBoss ? "fala.battlegroundsmod.saitama.matou" : null;
            default -> null;
        };
        if (key != null) {
            say(boss, key, 100, true);
        }
    }

    // The power someone fights with (a boss's soul, or a player's or mob's power), "" for none.
    private static String powerOf(LivingEntity e) {
        if (e instanceof SoulBoss other) {
            return other.spec().poder();
        }
        if (e.hasData(com.starkinhon.battlegroundsmod.ModAttachments.POWER)) {
            com.starkinhon.battlegroundsmod.power.PowerData data = e.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER);
            return data.hasPower() ? data.power : "";
        }
        return "";
    }

    // Someone's tier (lower is stronger); a plain mob or a player without a soul counts as human.
    private static int tierOf(LivingEntity e) {
        if (e instanceof SoulBoss other) {
            return other.powerData().tier;
        }
        if (e.hasData(com.starkinhon.battlegroundsmod.ModAttachments.POWER)) {
            com.starkinhon.battlegroundsmod.power.PowerData data = e.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER);
            if (data.hasPower()) {
                return data.tier;
            }
        }
        return com.starkinhon.battlegroundsmod.power.Tiers.HUMANO;
    }

    // Anyone else near still in the fight: players in survival, or anything that has the boss as its target.
    private static boolean othersLeft(ServerLevel level, SoulBoss boss, LivingEntity victim) {
        for (LivingEntity e : Combat.inRadius(level, Combat.center(boss), RANGE, boss)) {
            if (e == victim || !e.isAlive()) {
                continue;
            }
            if (e instanceof Player player ? !player.isSpectator() && !player.isCreative() : e instanceof Mob mob && mob.getTarget() == boss) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        STATES.clear();
        AZUL.clear();
        BillsInevitavel.clear();
    }
}
