package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// B Ritual dos Nomes (forja/makima-ritual, approved 2026-11-11). B opens a sheet of paper (client/MakimaSheet): she
// writes a name per puppet she has out (ditto marks repeat the line above), with autocomplete of only those she has seen
// (with her eyes, through the rats, by hitting them). Then she joins her hands and says the name; the world goes gray;
// her puppets behind her repeat it one after the other and each one is crushed flat, and the one named with it, from up
// to 60 blocks away and through walls: one blow kneels them (25% of their life), two puts them on all fours (45%),
// three crushes them dead (a boss takes a heavy blow). Without puppets: signed with her own blood, 20% of her life, one
// blow. A name she doesn't know is struck out, warned about, and nothing is spent.
public final class Ritual {
    private static final double RANGE = 60;
    private static final int MAX_KNOWN = 64;
    private static final int T_SAY = 15;
    private static final int T_FIRST = 31;
    private static final int GAP = 19;
    private static final int DELAY = 6;
    private static final float[] SHARE = {0, 0.25f, 0.45f, 1f};
    private static final float[] BOSS_DAMAGE = {0, 60, 90, 160};

    record Known(UUID id, String name, String origin, long time) {
    }

    private static final Map<UUID, LinkedHashMap<UUID, Known>> KNOWN = new HashMap<>();
    private static final Map<UUID, Long> OPEN = new HashMap<>();

    private Ritual() {
    }

    // ---------- who she knows ----------

    static String nameOf(LivingEntity e) {
        if (e instanceof Player p) {
            return p.getGameProfile().getName();
        }
        return e.getName().getString();
    }

    public static void see(LivingEntity caster, LivingEntity e, String origin) {
        if (e == null || e == caster || !Makima.is(caster) || Makima.ours(caster, e)) {
            return;
        }
        LinkedHashMap<UUID, Known> map = KNOWN.computeIfAbsent(caster.getUUID(), k -> new LinkedHashMap<>());
        Known before = map.remove(e.getUUID());
        // "Through the rats" stays the origin once it was (it is what makes the sheet special).
        String o = before != null && "ratos".equals(before.origin()) && !"ratos".equals(origin) ? before.origin() : origin;
        map.put(e.getUUID(), new Known(e.getUUID(), nameOf(e), o, caster.level().getGameTime()));
        while (map.size() > MAX_KNOWN) {
            map.remove(map.keySet().iterator().next());
        }
    }

    static void mark(LivingEntity caster, LivingEntity target) {
        see(caster, target, "ordem");
    }

    // ---------- B ----------

    public static boolean use(LivingEntity caster, ServerLevel level) {
        int puppets = Math.min(3, Puppets.count(caster, Puppets.MOB));
        if (!(caster instanceof ServerPlayer player)) {
            // A boss names whoever it fights.
            LivingEntity target = caster instanceof Mob mob ? mob.getTarget() : null;
            if (target == null || target.distanceTo(caster) > RANGE) {
                return false;
            }
            List<LivingEntity> lines = new ArrayList<>();
            for (int i = 0; i < Math.max(1, puppets); i++) {
                lines.add(target);
            }
            return run(caster, level, lines);
        }
        List<String> entries = new ArrayList<>();
        LinkedHashMap<UUID, Known> map = KNOWN.get(caster.getUUID());
        if (map != null) {
            List<Known> list = new ArrayList<>(map.values());
            java.util.Collections.reverse(list);
            for (Known k : list) {
                Entity e = level.getEntity(k.id());
                if (e instanceof LivingEntity le && le.isAlive()) {
                    String type = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).getPath();
                    // Other names that find it too (the boss's own id, the kind of mob), so "Madara" finds the Uchiha
                    // Ghost on the sheet as it does in the ritual.
                    String alias = type.replace('_', ' ') + "," + le.getType().getDescription().getString()
                            + (le instanceof SoulBoss boss ? "," + boss.bossId() : "");
                    entries.add(k.name() + "|" + k.origin() + "|" + type + "|" + alias.replace("|", ""));
                }
            }
        }
        OPEN.put(caster.getUUID(), level.getGameTime());
        SubCommon.pose(caster, "makima.escreve");
        PacketDistributor.sendToPlayer(player, new ModPayloads.MkOpen(1, puppets, entries));
        // The cost and the cooldown come when the names are written.
        return true;
    }

    // The names written on the sheet (from the client): one per line.
    public static void written(ServerPlayer player, List<String> names) {
        Long opened = OPEN.remove(player.getUUID());
        if (opened == null || !Makima.is(player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (names.isEmpty() || names.stream().allMatch(n -> n == null || n.isBlank())) {
            // The sheet put away unwritten.
            SubCommon.pose(player, "makima.parado");
            return;
        }
        if (level.getGameTime() - opened > 20 * 120) {
            return;
        }
        int puppets = Math.min(3, Puppets.count(player, Puppets.MOB));
        int lines = Math.max(1, puppets);
        List<LivingEntity> targets = new ArrayList<>();
        LivingEntity last = null;
        for (int i = 0; i < Math.min(lines, names.size()); i++) {
            String raw = names.get(i) == null ? "" : names.get(i).trim();
            LivingEntity t;
            if ((raw.isEmpty() || raw.equals("\"") || raw.equals("''") || raw.equals("〃")) && last != null) {
                t = last;
            } else {
                t = resolve(player, level, raw);
            }
            if (t == null) {
                SubCommon.warn(player, "makima.ritual_unknown", raw);
                SubCommon.pose(player, "makima.parado");
                return;
            }
            targets.add(t);
            last = t;
        }
        if (targets.isEmpty()) {
            return;
        }
        run(player, level, targets);
    }

    private static LivingEntity resolve(LivingEntity caster, ServerLevel level, String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        LinkedHashMap<UUID, Known> map = KNOWN.get(caster.getUUID());
        if (map == null) {
            return null;
        }
        String want = raw.toLowerCase(Locale.ROOT);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Known k : map.values()) {
            Entity e = level.getEntity(k.id());
            if (!(e instanceof LivingEntity le) || !le.isAlive()) {
                continue;
            }
            boolean match = k.name().toLowerCase(Locale.ROOT).equals(want)
                    || nameOf(le).toLowerCase(Locale.ROOT).equals(want)
                    || BuiltInRegistries.ENTITY_TYPE.getKey(le.getType()).getPath().replace('_', ' ').equals(want)
                    || le.getType().getDescription().getString().toLowerCase(Locale.ROOT).equals(want)
                    || le instanceof SoulBoss boss && boss.bossId().equalsIgnoreCase(want);
            if (!match) {
                continue;
            }
            double d = le.distanceToSqr(caster);
            if (d < bd) {
                bd = d;
                best = le;
            }
        }
        return best;
    }

    // The ritual itself: lines[i] is the one named on line i (puppet i, or her blood when she has none).
    private static boolean run(LivingEntity caster, ServerLevel level, List<LivingEntity> lines) {
        List<LivingEntity> puppets = Puppets.of(caster, Puppets.MOB);
        int n = Math.min(Math.min(3, puppets.size()), lines.size());
        boolean blood = n == 0;
        for (LivingEntity t : lines) {
            if (t.level() != level || t.distanceTo(caster) > RANGE) {
                SubCommon.warn(caster, "makima.ritual_far", nameOf(t));
                return false;
            }
        }
        LivingEntity first = lines.get(0);
        SubCommon.pose(caster, blood ? "makima.ritual_sangue" : "makima.ritual");
        Makima.eyes(caster, T_FIRST + Math.max(1, n) * GAP + 40);
        SubCommon.busy(caster, T_SAY + 4);
        DbCommon.cooldown(caster, PowerData.SLOT_EXTRA, 600);
        SubCommon.Guard guard = SubCommon.guard(caster);
        if (blood) {
            // Signed with her own blood: a fifth of her life, never the last of it.
            MkNet.send(level, MkNet.DRIP, caster.getId(), caster.position(), 60, 0, 0);
            caster.setHealth(Math.max(1, caster.getHealth() - caster.getMaxHealth() * 0.2f));
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.7f);
        }
        // Each target's blows: how many lines name it, in order (1: one at level 2; 2: levels 1, 2; 3: levels 1, 2, 3).
        Map<LivingEntity, Integer> count = new HashMap<>();
        for (int i = 0; i < Math.max(1, n); i++) {
            count.merge(lines.get(Math.min(i, lines.size() - 1)), 1, Integer::sum);
        }
        Map<LivingEntity, Integer> done = new HashMap<>();
        Map<LivingEntity, Runnable> holds = new HashMap<>();
        int end = blood ? T_FIRST + 5 : T_FIRST + (n - 1) * GAP + DELAY;
        Tasks.add(age -> {
            if (guard.broken() && age < T_SAY) {
                return true;
            }
            if (age == T_SAY) {
                Makima.sayText(caster, nameOf(first), Makima.VOICE);
                Makima.gray(level, caster.position(), end - T_SAY + 30, 0.82f);
                for (LivingEntity t : count.keySet()) {
                    MkNet.send(level, MkNet.MARK, t.getId(), t.position(), end - T_SAY + 30, 1, 0);
                }
                SubCommon.sound(level, caster.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 1.6f);
            }
            if (age == T_FIRST - 4) {
                for (LivingEntity t : count.keySet()) {
                    holds.put(t, Freeze.hold(t, end - age + 30));
                }
            }
            for (int i = 0; i < Math.max(1, n); i++) {
                LivingEntity t = lines.get(Math.min(i, lines.size() - 1));
                int say = T_FIRST + i * GAP;
                int hit = blood ? T_FIRST + 5 : say + DELAY;
                if (!blood && age == say) {
                    LivingEntity p = puppets.get(i);
                    if (p.isAlive()) {
                        Makima.sayText(p, nameOf(t), Makima.PUPPET_VOICE);
                        MkNet.send(level, MkNet.PULSE, p.getId(), p.position(), caster.getId(), 1, 0);
                    }
                }
                if (age == hit) {
                    if (!blood) {
                        crushPuppet(level, puppets.get(i), i);
                    }
                    int k = done.merge(t, 1, Integer::sum);
                    int total = count.get(t);
                    int lvl = blood ? 1 : total == 1 ? 2 : k;
                    crushTarget(caster, level, t, lvl);
                }
            }
            if (age >= end + 30) {
                holds.values().forEach(Runnable::run);
                return true;
            }
            return false;
        });
        return true;
    }

    private static void crushPuppet(ServerLevel level, LivingEntity p, int i) {
        Puppets.Entry e = Puppets.entry(p);
        if (e != null) {
            // Spent: it does not go back to her stock.
            Puppets.forgetPuppet(p);
        }
        if (!p.isAlive()) {
            return;
        }
        Vec3 at = SubCommon.ground(level, p.position());
        MkNet.send(level, MkNet.COLUMN, p.getId(), at, 0.7f, 14, 0);
        MkNet.send(level, MkNet.BITS, p.getId(), at.add(0, 0.3, 0), Vec3.ZERO, 30, 0.8f, 2);
        MkNet.send(level, MkNet.MIST, p.getId(), at.add(0, 0.3, 0), Vec3.ZERO, 60, 0, 0);
        MkNet.send(level, MkNet.POOL, p.getId(), at, 2.4f, 300, 1);
        MkNet.send(level, MkNet.CRACK, p.getId(), at, 2.6f, 2, 20 + i * 5);
        SubCommon.sound(level, at, SoundEvents.SLIME_SQUISH, 1.4f, 0.5f);
        SubCommon.sound(level, at, SoundEvents.ANVIL_LAND, 0.7f, 0.5f);
        p.discard();
    }

    private static void crushTarget(LivingEntity caster, ServerLevel level, LivingEntity t, int lvl) {
        if (!t.isAlive()) {
            return;
        }
        Vec3 at = SubCommon.ground(level, t.position());
        MkNet.send(level, MkNet.COLUMN, t.getId(), at, 0.9f + lvl * 0.35f, 18, 0);
        MkNet.send(level, MkNet.CRACK, t.getId(), at, 2.4f + lvl * 1.6f, 2, t.getId() * 1.3f + lvl);
        MkNet.send(level, MkNet.DUST, t.getId(), at, 2 + lvl, 0, 0);
        MkNet.send(level, MkNet.CRUSH, t.getId(), t.position(), lvl, 0, 0);
        SubCommon.sound(level, at, SoundEvents.ANVIL_LAND, 1.0f, 0.4f + lvl * 0.05f);
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5f + lvl * 0.2f, 0.5f);
        boolean boss = Makima.boss(t);
        Stun.apply(t, 40);
        if (lvl == 1) {
            MkNet.send(level, MkNet.MIST, t.getId(), t.getEyePosition(), new Vec3(0, -0.2, 1), 10, 0, 0);
            SubCommon.pose(t, "makima.ajoelhado");
        } else if (lvl == 2) {
            MkNet.send(level, MkNet.MIST, t.getId(), Combat.center(t), Vec3.ZERO, 40, 0, 0);
            MkNet.send(level, MkNet.POOL, t.getId(), at, 2.2f, 300, 1);
            SubCommon.pose(t, "makima.quatro");
        } else {
            MkNet.send(level, MkNet.IMPACT, caster.getId(), Combat.center(t), 2, 1, 0);
            MkNet.send(level, MkNet.SHELL, t.getId(), at.add(0, 0.3, 0), 2.2f, 0, t.getId() * 0.9f);
            MkNet.send(level, MkNet.POOL, t.getId(), at, 4.2f, 400, 1);
            MkNet.send(level, MkNet.CRACK, t.getId(), at, 7.5f, 2, t.getId() * 2.1f + 3);
        }
        Ritual.see(caster, t, "ritual");
        float damage = boss ? BOSS_DAMAGE[lvl] : t.getMaxHealth() * SHARE[lvl];
        if (lvl == 3 && !boss) {
            MkNet.send(level, MkNet.BITS, t.getId(), at.add(0, 0.25, 0), Vec3.ZERO, 46, 1, 2);
            MkNet.send(level, MkNet.MIST, t.getId(), at.add(0, 0.3, 0), Vec3.ZERO, 130, 0, 0);
            Corrente.kill(caster, t, Math.max(400, damage));
        } else {
            // Pressure, not a blow: straight to the body (it ignores the distance and the walls).
            Combat.hurt(caster, t, damage);
        }
    }

    public static void forget(LivingEntity caster) {
        KNOWN.remove(caster.getUUID());
        OPEN.remove(caster.getUUID());
    }

    public static void clear() {
        KNOWN.clear();
        OPEN.clear();
    }
}
