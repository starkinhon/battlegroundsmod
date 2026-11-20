// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.movement;

// Movement state for any fighter (players and bosses). Not saved. Times are level game times, so nothing has to
// count down each tick.
public class MovementData {
    public final long[] readyAt = new long[Movement.Kind.values().length];
    // The air jump was spent and comes back on landing.
    public boolean airJumpUsed = false;
    // When the last move started: landing checks wait a few ticks so the take-off tick doesn't count as landing.
    public long lastMoveAt = Long.MIN_VALUE / 2;
    // When the teleport to the target (double tap of the sprint key) is ready again.
    public long teleportReadyAt = 0;
    // When the "teleport locked" warning may show again (players before 5 challenges of the book).
    public long teleportLockedWarnAt = 0;
    // Flat direction of the last dash on the ground while its dust trail runs (server), else null.
    public net.minecraft.world.phys.Vec3 trailDir;
}
