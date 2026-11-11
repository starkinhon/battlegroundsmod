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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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

    static final class Entry {
        final LivingEntity puppet;
        final LivingEntity owner;
        final int kind;
        final String type;
        final long risen;
        int life;

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

    // Gone without a trace (owner gone, the V's time over, "Morram" done): it sinks into a pool.
    static void sink(Entry e) {
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
        if (e.kind == SOUL && e.owner.isAlive()) {
            Almas.back(e.owner, e.type);
        } else if (e.kind == MOB && e.owner.isAlive()) {
            Fantoche.back(e.owner, e.type, p);
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
                drive(level, owner, mob, now);
            }
        }
        for (Entry e : sinking) {
            sink(e);
        }
    }

    private static void drive(ServerLevel level, LivingEntity owner, Mob mob, long now) {
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
                if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
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
            // Too far behind: it sinks and comes up again by her.
            Vec3 at = SubCommon.ground(level, owner.position().add(owner.getLookAngle().scale(-2.5)));
            mob.teleportTo(at.x, at.y, at.z);
            MkNet.send(level, MkNet.POOL, mob.getId(), at, 1.3f, 40, 0);
            MkNet.send(level, MkNet.SINK, mob.getId(), at, 1, 16, 0);
        } else if (d > 6 && mob.getTarget() == null) {
            mob.getNavigation().moveTo(owner, 1.2);
        }
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
