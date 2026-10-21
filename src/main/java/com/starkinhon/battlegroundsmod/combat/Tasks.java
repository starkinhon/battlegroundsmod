package com.starkinhon.battlegroundsmod.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// Server-side scheduler for moves that play out over several ticks (wind-ups, barrages, projectiles).
public final class Tasks {

    @FunctionalInterface
    public interface Task {
        // Called once per server tick with the task's age (0 on the first tick). Return true when finished.
        boolean tick(int age);
    }

    private static final class Entry {
        final Task task;
        int age;

        Entry(Task task) {
            this.task = task;
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final List<Entry> PENDING = new ArrayList<>();

    private Tasks() {
    }

    public static void add(Task task) {
        PENDING.add(new Entry(task));
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
            boolean done;
            try {
                done = entry.task.tick(entry.age++);
            } catch (Exception e) {
                BattlegroundsMod.LOGGER.error("Battlegrounds task failed", e);
                done = true;
            }
            if (done) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }
}
