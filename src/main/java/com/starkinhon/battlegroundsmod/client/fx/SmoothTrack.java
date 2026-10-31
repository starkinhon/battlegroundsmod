package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;

import net.minecraft.world.phys.Vec3;

// Smooth motion for an effect whose position the server sends once per tick (the Hakai ball, the Destruction Energy
// balls, the Planetary Destruction sphere).
// Sliding between "the packet before" and "the last packet" with the frame's partial tick stutters: packets don't land
// exactly on the client's tick, so some ticks get two at once and the next none, and the ball jumps then stops. Here each
// packet is put on its own timeline, one tick apart (the server sends one per tick), and the ball is drawn a little
// behind the newest one, on a clock that runs with the frames. So it always has two points to slide between, and moves
// at a steady speed even when packets arrive bunched up.
final class SmoothTrack {
    private record Snap(double t, Vec3 pos, float radius) {
    }

    // How far behind the newest packet the ball is drawn, in ticks. Enough to cover a packet arriving a tick late.
    private static final double DELAY = 1.25;
    // Past this many ticks out of step (a lag spike, the game paused) it jumps instead of catching up.
    private static final double RESYNC = 4;
    private static final int KEEP = 8;

    // The time of the last frame drawn (ticks + partial), so a packet handled between ticks gets a time between ticks.
    private static double frameNow;

    private final ArrayDeque<Snap> snaps = new ArrayDeque<>();
    private double clock = Double.NaN;
    private double lastFrame = Double.NaN;

    private Vec3 pos = Vec3.ZERO;
    private float radius;
    private Vec3 velocity = Vec3.ZERO;

    // FxWorld calls it once per frame, before drawing.
    static void frame(double now) {
        frameNow = now;
    }

    SmoothTrack(Vec3 pos, float radius) {
        push(pos, radius);
        this.pos = pos;
        this.radius = radius;
    }

    void push(Vec3 p, float r) {
        double arrival = Math.max(ScreenFx.now(), frameNow);
        double t = arrival;
        if (!snaps.isEmpty()) {
            double next = snaps.peekLast().t() + 1;
            // Packets bunched up keep one tick apart; if the server runs slow the timeline follows the arrivals.
            t = Math.max(next, arrival - 2);
            if (t - arrival > RESYNC) {
                t = arrival;
                snaps.clear();
            }
        }
        snaps.addLast(new Snap(t, p, r));
        while (snaps.size() > KEEP) {
            snaps.removeFirst();
        }
    }

    // Moves the clock to this frame and works out where the ball is. Safe to call more than once per frame.
    void sample(double now) {
        Snap last = snaps.peekLast();
        if (last == null) {
            return;
        }
        double target = last.t() - DELAY;
        if (Double.isNaN(clock) || Double.isNaN(lastFrame)) {
            clock = target;
        } else {
            double dt = Math.max(0, Math.min(now - lastFrame, 3));
            clock += dt;
            double error = target - clock;
            if (Math.abs(error) > RESYNC) {
                clock = target;
            } else {
                // Gently pulled back to the right distance behind, so it never drifts.
                clock += error * Math.min(1, dt * 0.25);
            }
        }
        lastFrame = now;

        Snap a = null;
        Snap b = null;
        for (Snap s : snaps) {
            if (s.t() <= clock) {
                a = s;
            } else {
                b = s;
                break;
            }
        }
        if (a == null) {
            // Not there yet (just born): hold at the first point.
            Snap first = snaps.peekFirst();
            pos = first.pos();
            radius = first.radius();
            velocity = Vec3.ZERO;
            if (snaps.size() > 1) {
                Snap second = second();
                velocity = second.pos().subtract(first.pos()).scale(1 / (second.t() - first.t()));
            }
            return;
        }
        if (b == null) {
            // Past the newest packet (one is late): keep going the same way for up to a tick, then wait.
            Snap before = beforeLast();
            velocity = before == null ? Vec3.ZERO : last.pos().subtract(before.pos()).scale(1 / (last.t() - before.t()));
            double over = Math.min(clock - last.t(), 1);
            pos = last.pos().add(velocity.scale(over));
            radius = last.radius();
            return;
        }
        double k = (clock - a.t()) / (b.t() - a.t());
        pos = a.pos().lerp(b.pos(), k);
        radius = (float) (a.radius() + (b.radius() - a.radius()) * k);
        velocity = b.pos().subtract(a.pos()).scale(1 / (b.t() - a.t()));
    }

    private Snap second() {
        var it = snaps.iterator();
        it.next();
        return it.next();
    }

    private Snap beforeLast() {
        if (snaps.size() < 2) {
            return null;
        }
        var it = snaps.descendingIterator();
        it.next();
        return it.next();
    }

    Vec3 pos() {
        return pos;
    }

    float radius() {
        return radius;
    }

    // How far it moves in one tick, right now.
    Vec3 velocity() {
        return velocity;
    }
}
