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
        int age;
        boolean cancelled;

        Entry(Task task, boolean system) {
            this.task = task;
            this.system = system;
            this.seq = nextSeq++;
            this.owner = current;
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final List<Entry> PENDING = new ArrayList<>();
    private static long nextSeq;
    // The entity whose code is running now (a player's key press, a boss's AI tick, a task of theirs), for Entry.owner.
    private static Entity current;

    private Tasks() {
    }

    public static void add(Task task) {
        PENDING.add(new Entry(task, false));
    }

    // A task cancelSince never stops.
    public static void addSystem(Task task) {
        PENDING.add(new Entry(task, true));
    }

    // Marks this point: cancelSince(mark) later drops every (non system) task started after it.
    public static long mark() {
        return nextSeq;
    }

    // Runs body with owner as the one every task it starts belongs to.
    public static void as(Entity owner, Runnable body) {
        Entity before = current;
        current = owner;
        try {
            body.run();
        } finally {
            current = before;
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
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.cancelled) {
                it.remove();
                continue;
            }
            boolean done;
            Entity before = current;
            current = entry.owner;
            try {
                done = entry.task.tick(entry.age++);
            } catch (Exception e) {
                BattlegroundsMod.LOGGER.error("Battlegrounds task failed", e);
                done = true;
            } finally {
                current = before;
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
    }
}
