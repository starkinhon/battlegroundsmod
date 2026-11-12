package com.starkinhon.battlegroundsmod.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.world.entity.Entity;

// Server-side scheduler for moves that play out over several ticks (wind-ups, barrages, projectiles).
public final class Tasks {

    @FunctionalInterface
    public interface Task {
        // Called once per server tick with the task's age (0 on the first tick). Return true when finished.
        boolean tick(int age);
    }

    // seq: order of creation, so the Izanami can cancel what was started after a point. system: bookkeeping (freezes,
    // intangibility) that must always run to its end, never cancelled.
    private static final class Entry {
        final Task task;
        final long seq;
        final boolean system;
        // Whose move started it (null: unknown). Tasks a task starts belong to the same one.
        final Entity owner;
        // The attack in flight this task moves (Destructible): while it is held in the air the task waits.
        Destructible.Attack link;
        int age;
        boolean cancelled;
        // Run once if the task is dropped by cancelSince/cancelOwned before it finishes (null: nothing to undo).
        Runnable onCancel;

        Entry(Task task, boolean system) {
            this.task = task;
            this.system = system;
            this.seq = nextSeq++;
            this.owner = current;
            if (!system && linking != null && (current == null || current == linking.owner())) {
                this.link = linking;
            }
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final List<Entry> PENDING = new ArrayList<>();
    private static long nextSeq;
    // The entity whose code is running now (a player's key press, a boss's AI tick, a task of theirs), for Entry.owner.
    private static Entity current;
    // The entry running now, and the attack just tracked by the code running now: the tasks it starts next move it.
    private static Entry running;
    private static Destructible.Attack linking;

    private Tasks() {
    }

    public static void add(Task task) {
        PENDING.add(new Entry(task, false));
    }

    // Like add, and onCancel runs if the task is cut off before it finishes (the Izanami, VazioRelampago): a move that
    // keeps state outside its task (a busy flag, a buff it must take back) frees it there, so being cut never leaves it
    // stuck for good.
    public static void add(Task task, Runnable onCancel) {
        Entry entry = new Entry(task, false);
        entry.onCancel = onCancel;
        PENDING.add(entry);
    }

    // A task cancelSince never stops.
    public static void addSystem(Task task) {
        PENDING.add(new Entry(task, true));
    }

    // Called by Destructible.track: ties the attack to the task running now (if it has none yet) and to the tasks the same
    // code starts after it, so holding the attack in the air pauses them.
    static void link(Destructible.Attack attack) {
        linking = attack;
        if (running != null && running.link == null && !running.system) {
            running.link = attack;
        }
    }

    // Marks this point: cancelSince(mark) later drops every (non system) task started after it.
    public static long mark() {
        return nextSeq;
    }

    // Whose code is running now (null: unknown): the owner of the sounds and effects it plays (SkillStop).
    public static Entity owner() {
        return current;
    }

    // Runs body with owner as the one every task it starts belongs to.
    public static void as(Entity owner, Runnable body) {
        Entity before = current;
        current = owner;
        try {
            body.run();
        } finally {
            current = before;
            linking = null;
        }
    }

    // Can be called from inside a task: the entries are only flagged here and dropped by tick(). Only tasks whose owner
    // passes affected (or with no known owner) are dropped, so a player far away keeps their moves going.
    public static void cancelSince(long mark, Predicate<Entity> affected) {
        for (List<Entry> list : List.of(ACTIVE, PENDING)) {
            for (Entry entry : list) {
                if (!entry.system && entry.seq >= mark && (entry.owner == null || affected.test(entry.owner))) {
                    entry.cancelled = true;
                }
            }
        }
    }

    // Drops only the (non system) tasks that belong to owner, from any time. Unlike cancelSince(0, ...), the tasks with
    // no known owner (other fighters' timers, the world's) are left alone.
    public static void cancelOwned(Entity owner) {
        for (List<Entry> list : List.of(ACTIVE, PENDING)) {
            for (Entry entry : list) {
                if (!entry.system && entry.owner == owner) {
                    entry.cancelled = true;
                }
            }
        }
    }

    // Runs body every tick for the given number of ticks.
    public static void repeat(int ticks, IntConsumer body) {
        add(age -> {
            body.accept(age);
            return age >= ticks - 1;
        });
    }

    public static void later(int delay, Runnable action) {
        add(age -> {
            if (age < delay) {
                return false;
            }
            action.run();
            return true;
        });
    }

    public static void tick() {
        linking = null;
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.cancelled) {
                it.remove();
                if (entry.link != null) {
                    // The attack it was moving is over too: it no longer counts as flying (bots dodged a ghost of it, the
                    // hakaizinho could "erase" it) until Destructible dropped it two minutes later.
                    entry.link.end();
                }
                if (entry.onCancel != null) {
                    Entity before = current;
                    current = entry.owner;
                    try {
                        entry.onCancel.run();
                    } catch (Exception e) {
                        BattlegroundsMod.LOGGER.error("Battlegrounds task cancel failed", e);
                    } finally {
                        current = before;
                    }
                }
                continue;
            }
            if (entry.link != null && entry.link.held()) {
                continue;
            }
            boolean done;
            Entity before = current;
            current = entry.owner;
            running = entry;
            try {
                done = entry.task.tick(entry.age++);
            } catch (Exception e) {
                BattlegroundsMod.LOGGER.error("Battlegrounds task failed", e);
                done = true;
            } finally {
                current = before;
                running = null;
                linking = null;
            }
            if (done) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
        current = null;
        running = null;
        linking = null;
    }
}
