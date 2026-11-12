package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

// Makima's puppets: the mobs she brought back (C Fantoche) and the souls she raised (V). Each one hangs from her back by
// a chain (MkNet.LEASH), has her rings in its eyes and fights for her: whoever she fights, whoever hurts her, or the
// monsters near her; with nobody to fight it walks after her. Never against her or her other puppets (MakimaEvents).
public final class Puppets {
    public static final int MOB = 0;
    public static final int SOUL = 1;
    // The tag a puppet carries in its saved data: a puppet left over from a session that ended (its owner's registry is
    // gone) is taken away when it loads.
    static final String TAG = "makima_puppet";
    // What she told it on the wheel (client/MakimaWheel): fight freely, go after her target, keep by her, hold its spot.
    static final int FREE = 0;
    static final int ATTACK = 1;
    static final int FOLLOW = 2;
    static final int STAY = 3;

    static final class Entry {
        final LivingEntity puppet;
        final LivingEntity owner;
        final int kind;
        final String type;
        final long risen;
        int life;
        int order = FREE;
        LivingEntity mark;
        Vec3 post;

        Entry(LivingEntity puppet, LivingEntity owner, int kind, String type, long risen, int life) {
            this.puppet = puppet;
            this.owner = owner;
            this.kind = kind;
            this.type = type;
            this.risen = risen;
            this.life = life;
        }
    }

    private static final Map<UUID, Entry> PUPPETS = new HashMap<>();
    // Who each owner fights now (the last one she hit, or who hit her), and since when.
    private static final Map<UUID, LivingEntity> FOE = new HashMap<>();
    private static final Map<UUID, Long> FOE_TIME = new HashMap<>();

    private Puppets() {
    }

    // life: ticks until it sinks back by itself (-1 stays).
    static void add(LivingEntity owner, LivingEntity puppet, int kind, String type, int life) {
        PUPPETS.put(puppet.getUUID(), new Entry(puppet, owner, kind, type, owner.level().getGameTime(), life));
        puppet.getPersistentData().putBoolean(TAG, true);
        if (puppet instanceof SoulBoss boss) {
            boss.puppetOf(owner);
        }
        if (puppet instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setTarget(null);
        }
    }

    public static boolean isPuppet(LivingEntity e) {
        return e != null && PUPPETS.containsKey(e.getUUID());
    }

    public static LivingEntity ownerOf(LivingEntity e) {
        Entry entry = e == null ? null : PUPPETS.get(e.getUUID());
        return entry == null ? null : entry.owner;
    }

    static Entry entry(LivingEntity e) {
        return e == null ? null : PUPPETS.get(e.getUUID());
    }

    // Her puppets of a kind (-1 all), nearest first.
    static List<LivingEntity> of(LivingEntity owner, int kind) {
        List<LivingEntity> list = new ArrayList<>();
        for (Entry e : PUPPETS.values()) {
            if (e.owner == owner && (kind < 0 || e.kind == kind) && e.puppet.isAlive()) {
                list.add(e.puppet);
            }
        }
        list.sort(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(owner)));
        return list;
    }

    static int count(LivingEntity owner, int kind) {
        return of(owner, kind).size();
    }

    // Someone she fights: the puppets go after them.
    static void foe(LivingEntity owner, LivingEntity foe) {
        if (foe == null || foe == owner || Makima.ours(owner, foe) || !foe.isAlive()) {
            return;
        }
        FOE.put(owner.getUUID(), foe);
        FOE_TIME.put(owner.getUUID(), owner.level().getGameTime());
    }

    // Gone without a trace (owner gone, the V's time over, "Morram" done, called back on the wheel): it sinks into a pool
    // and goes back to her stock.
    static void sink(Entry e) {
        sink(e, true);
    }

    // keep false: let go on the wheel, it sinks and is gone for good.
    static void sink(Entry e, boolean keep) {
        LivingEntity p = e.puppet;
        PUPPETS.remove(p.getUUID());
        if (!(p.level() instanceof ServerLevel level) || !p.isAlive()) {
            return;
        }
        Vec3 at = SubCommon.ground(level, p.position());
        MkNet.send(level, MkNet.POOL, p.getId(), at, Math.max(1.1f, p.getBbWidth() * 1.6f), 50, 0);
        MkNet.send(level, MkNet.SINK, p.getId(), p.position(), 0, 20, 0);
        SubCommon.sound(level, at, SoundEvents.SCULK_BLOCK_SPREAD, 1.2f, 0.6f);
        com.starkinhon.battlegroundsmod.combat.Freeze.hold(p, 22);
        com.starkinhon.battlegroundsmod.combat.Tasks.later(20, () -> {
            if (!p.isRemoved()) {
                p.discard();
            }
        });
        if (!keep) {
            return;
        }
        if (e.kind == SOUL && e.owner.isAlive()) {
            Almas.back(e.owner, e.type);
        } else if (e.kind == MOB && e.owner.isAlive()) {
            Fantoche.back(e.owner, e.type, p);
        }
    }

    // ---------- the wheel's orders (ModPayloads.MkOrder) ----------

    static final int A_BACK = 0;
    static final int A_ATTACK = 1;
    static final int A_FOLLOW = 2;
    static final int A_STAY = 3;
    static final int A_FREE = 4;
    static final int A_LET_GO = 5;
    static final int A_ALL_BACK = 6;
    static final int A_ALL_ATTACK = 7;

    // Her puppets out, for the wheel: "@id|kind|order|type|name".
    static List<String> wheel(LivingEntity owner) {
        List<String> out = new ArrayList<>();
        for (Entry e : PUPPETS.values()) {
            if (e.owner == owner && e.puppet.isAlive()) {
                out.add("@" + e.puppet.getId() + "|" + e.kind + "|" + e.order + "|" + e.type + "|" + e.puppet.getName().getString());
            }
        }
        return out;
    }

    public static void order(ServerPlayer owner, int action, int id) {
        if (!Makima.is(owner) || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        if (action == A_ALL_BACK || action == A_ALL_ATTACK) {
            List<Entry> mine = new ArrayList<>();
            for (Entry e : PUPPETS.values()) {
                if (e.owner == owner && e.puppet.isAlive()) {
                    mine.add(e);
                }
            }
            if (mine.isEmpty()) {
                return;
            }
            if (action == A_ALL_BACK) {
                for (Entry e : mine) {
                    sink(e);
                }
                Makima.eyes(owner, 30);
                return;
            }
            LivingEntity target = target(owner);
            if (target == null) {
                SubCommon.warn(owner, "makima.ordem_sem_alvo");
                return;
            }
            for (Entry e : mine) {
                attack(level, e, target);
            }
            obeyed(level, owner, target);
            return;
        }
        Entity found = level.getEntity(id);
        Entry e = found instanceof LivingEntity le ? PUPPETS.get(le.getUUID()) : null;
        if (e == null || e.owner != owner || !e.puppet.isAlive()) {
            return;
        }
        LivingEntity p = e.puppet;
        switch (action) {
            case A_BACK -> sink(e);
            case A_LET_GO -> {
                // Only a mob she brought back: a soul is hers for good.
                if (e.kind == MOB) {
                    sink(e, false);
                }
            }
            case A_ATTACK -> {
                LivingEntity target = target(owner);
                if (target == null) {
                    SubCommon.warn(owner, "makima.ordem_sem_alvo");
                    return;
                }
                attack(level, e, target);
                obeyed(level, owner, target);
            }
            case A_FOLLOW, A_STAY, A_FREE -> {
                e.order = action == A_FOLLOW ? FOLLOW : action == A_STAY ? STAY : FREE;
                e.mark = null;
                e.post = p.position();
                if (action != A_FREE && p instanceof Mob mob) {
                    calm(mob);
                    mob.getNavigation().stop();
                }
                Makima.obeyEyes(p, 30);
                SubCommon.sound(level, p.position(), SoundEvents.CHAIN_STEP, 1.0f, 0.7f);
            }
            default -> {
            }
        }
    }

    // Whom she means: the one in her aim, or else the one she fights.
    private static LivingEntity target(LivingEntity owner) {
        LivingEntity aimed = SubCommon.aimed(owner, 48);
        if (aimed != null && !Makima.ours(owner, aimed) && aimed.isAlive()) {
            return aimed;
        }
        LivingEntity foe = FOE.get(owner.getUUID());
        if (foe != null && foe.isAlive() && !foe.isRemoved() && foe.level() == owner.level() && foe.distanceTo(owner) < 48
                && !Makima.ours(owner, foe)) {
            return foe;
        }
        return null;
    }

    private static void attack(ServerLevel level, Entry e, LivingEntity target) {
        e.order = ATTACK;
        e.mark = target;
        if (e.puppet instanceof Mob mob) {
            aim(mob, target);
        }
        Makima.obeyEyes(e.puppet, 40);
        SubCommon.sound(level, e.puppet.position(), SoundEvents.CHAIN_HIT, 0.9f, 0.7f);
    }

    private static void obeyed(ServerLevel level, LivingEntity owner, LivingEntity target) {
        Makima.eyes(owner, 40);
        foe(owner, target);
        Ritual.mark(owner, target);
    }

    private static void aim(Mob mob, LivingEntity target) {
        if (mob.getTarget() != target) {
            mob.setTarget(target);
            if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            }
        }
    }

    // Stops fighting.
    private static void calm(Mob mob) {
        mob.setTarget(null);
        if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    public static void tick(ServerLevel level) {
        if (PUPPETS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Entry> sinking = new ArrayList<>();
        Iterator<Entry> it = PUPPETS.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.puppet.level() != level) {
                continue;
            }
            LivingEntity p = e.puppet;
            if (!p.isAlive() || p.isRemoved()) {
                it.remove();
                if (e.kind == SOUL && e.owner.isAlive()) {
                    // A soul that falls goes back to her stock (it was already dead once).
                    Almas.back(e.owner, e.type);
                }
                continue;
            }
            LivingEntity owner = e.owner;
            if (!owner.isAlive() || owner.isRemoved() || owner.level() != level || !Makima.is(owner)) {
                sinking.add(e);
                continue;
            }
            int age = (int) (now - e.risen);
            if (e.life > 0 && age >= e.life) {
                sinking.add(e);
                continue;
            }
            if (age % 10 == 0) {
                MkNet.send(level, MkNet.LEASH, p.getId(), p.position(), owner.getId(), e.kind, age);
            }
            if (age < 26 || !(p instanceof Mob mob)) {
                continue;
            }
            if ((age + p.getId()) % 5 == 0) {
                drive(level, owner, mob, now, e);
            }
        }
        for (Entry e : sinking) {
            sink(e);
        }
    }

    private static void drive(ServerLevel level, LivingEntity owner, Mob mob, long now, Entry entry) {
        if (entry.order == ATTACK) {
            LivingEntity m = entry.mark;
            if (m != null && m.isAlive() && !m.isRemoved() && m.level() == level && m.distanceTo(owner) < 48 && !Makima.ours(owner, m)) {
                aim(mob, m);
                return;
            }
            // Its target is gone: back to fighting freely.
            entry.order = FREE;
            entry.mark = null;
        }
        if (entry.order == FOLLOW) {
            // By her side, hands off everyone.
            calm(mob);
            double d = mob.distanceTo(owner);
            if (d > 40) {
                catchUp(level, owner, mob);
            } else if (d > 3.5) {
                mob.getNavigation().moveTo(owner, 1.3);
            }
            return;
        }
        if (entry.order == STAY && entry.post != null) {
            // Holds its spot: fights only what comes within 7 blocks of it.
            Vec3 post = entry.post;
            LivingEntity t = mob.getTarget();
            if (t != null && (t == owner || Makima.ours(owner, t) || t.distanceToSqr(post) > 49)) {
                calm(mob);
                t = null;
            }
            if (t == null) {
                LivingEntity near = null;
                double best = 49;
                for (LivingEntity e : Combat.inRadius(level, post, 7, mob)) {
                    boolean hostile = e == FOE.get(owner.getUUID())
                            || (e instanceof Mob m && e instanceof Enemy && (m.getTarget() == owner || m.getTarget() == mob));
                    if (hostile && !Makima.ours(owner, e) && e.distanceToSqr(post) < best) {
                        best = e.distanceToSqr(post);
                        near = e;
                    }
                }
                if (near != null) {
                    aim(mob, near);
                } else if (mob.position().distanceToSqr(post) > 2.25) {
                    mob.getNavigation().moveTo(post.x, post.y, post.z, 1.1);
                }
            }
            return;
        }
        LivingEntity foe = FOE.get(owner.getUUID());
        Long since = FOE_TIME.get(owner.getUUID());
        if (foe != null && (!foe.isAlive() || foe.isRemoved() || foe.level() != level || since == null || now - since > 600
                || foe.distanceTo(owner) > 40 || Makima.ours(owner, foe))) {
            FOE.remove(owner.getUUID());
            foe = null;
        }
        if (owner instanceof Mob boss && boss.getTarget() != null && !Makima.ours(owner, boss.getTarget())) {
            foe = boss.getTarget();
        }
        if (foe == null) {
            // Monsters near her that are after her.
            double best = Double.MAX_VALUE;
            for (LivingEntity e : Combat.inRadius(level, owner.position(), 16, owner)) {
                if (e instanceof Mob m && e instanceof Enemy && m.getTarget() == owner && !Makima.ours(owner, e)) {
                    double d = e.distanceToSqr(owner);
                    if (d < best) {
                        best = d;
                        foe = e;
                    }
                }
            }
        }
        LivingEntity current = mob.getTarget();
        if (current != null && (current == owner || Makima.ours(owner, current))) {
            mob.setTarget(null);
            current = null;
        }
        if (foe != null) {
            if (current != foe) {
                mob.setTarget(foe);
                if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
                    mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, foe);
                }
            }
            return;
        }
        if (current != null && current.distanceTo(owner) > 24) {
            mob.setTarget(null);
        }
        double d = mob.distanceTo(owner);
        if (d > 40) {
            catchUp(level, owner, mob);
        } else if (d > 6 && mob.getTarget() == null) {
            mob.getNavigation().moveTo(owner, 1.2);
        }
    }

    // Too far behind: it sinks and comes up again by her.
    private static void catchUp(ServerLevel level, LivingEntity owner, Mob mob) {
        Vec3 at = SubCommon.ground(level, owner.position().add(owner.getLookAngle().scale(-2.5)));
        mob.teleportTo(at.x, at.y, at.z);
        MkNet.send(level, MkNet.POOL, mob.getId(), at, 1.3f, 40, 0);
        MkNet.send(level, MkNet.SINK, mob.getId(), at, 1, 16, 0);
    }

    // Spent (crushed by the Ritual): out of the registry, never back to her stock.
    static void forgetPuppet(LivingEntity puppet) {
        PUPPETS.remove(puppet.getUUID());
    }

    public static void forget(LivingEntity owner) {
        FOE.remove(owner.getUUID());
        FOE_TIME.remove(owner.getUUID());
    }

    public static void clear() {
        PUPPETS.clear();
        FOE.clear();
        FOE_TIME.clear();
    }
}
