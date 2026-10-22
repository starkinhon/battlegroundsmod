package com.starkinhon.battlegroundsmod.movement;

// Movement state for any fighter (players and bosses). Not saved. Times are level game times, so nothing has to
// count down each tick.
public class MovementData {
    public final long[] readyAt = new long[Movement.Kind.values().length];
    // The air jump was spent and comes back on landing.
    public boolean airJumpUsed = false;
    // When the last move started: landing checks wait a few ticks so the take-off tick doesn't count as landing.
    public long lastMoveAt = Long.MIN_VALUE / 2;
}
