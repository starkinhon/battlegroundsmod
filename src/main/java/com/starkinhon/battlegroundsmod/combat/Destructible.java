package com.starkinhon.battlegroundsmod.combat;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Attacks in flight that something can destroy (Bills' hakaizinho erases them): each move tracks where its body is and
// how big it is, checks gone() every tick to stop itself, and calls end() when it is over on its own. onErase cleans up
// what the clients draw (the meteor, the fireball).
public final class Destructible {
    // A forgotten entry (a task cancelled by the Izanami never calls end) is dropped after this long.
    private static final int MAX_AGE = 20 * 120;

    public static final class Attack {
        private final ServerLevel level;
        private final Entity owner;
        private final long born;
        private final Runnable onErase;
        private Vec3 pos;
        private double radius;
        private boolean gone;
        private boolean ended;

        private Attack(ServerLevel level, Entity owner, Vec3 pos, double radius, Runnable onErase) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.radius = radius;
            this.onErase = onErase;
            this.born = level.getGameTime();
        }

        public void move(Vec3 pos) {
            this.pos = pos;
        }

        public void resize(double radius) {
            this.radius = radius;
        }

        public Vec3 pos() {
            return pos;
        }

        public double radius() {
            return radius;
        }

        public Entity owner() {
            return owner;
        }

        // Erased: the move must stop now, without its impact.
        public boolean gone() {
            return gone;
        }

        public void end() {
            ended = true;
        }
    }

    private static final List<Attack> LIVE = new ArrayList<>();

    private Destructible() {
    }

    public static Attack track(ServerLevel level, Entity owner, Vec3 pos, double radius, Runnable onErase) {
        prune();
        Attack attack = new Attack(level, owner, pos, radius, onErase);
        LIVE.add(attack);
        return attack;
    }

    // Erases every attack whose body touches the segment from a to b widened by radius, except the ones of spare (null
    // for none). Returns them, so the caller can show where each one was pulverized.
    public static List<Attack> eraseAlong(ServerLevel level, Vec3 a, Vec3 b, double radius, Entity spare) {
        prune();
        List<Attack> erased = new ArrayList<>();
        for (Attack attack : List.copyOf(LIVE)) {
            if (attack.level != level || attack.gone || attack.ended || attack.owner == spare && spare != null) {
                continue;
            }
            if (distanceToSegment(attack.pos, a, b) <= attack.radius + radius) {
                attack.gone = true;
                erased.add(attack);
                attack.onErase.run();
            }
        }
        LIVE.removeAll(erased);
        return erased;
    }

    // Tells the clients near pos to stop drawing what skill had there (a meteor, a slash, a falling sphere).
    public static void sendErase(ServerLevel level, String skill, Vec3 pos, double radius) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < 256 * 256) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.EraseAttack(skill, pos, (float) radius));
            }
        }
    }

    public static void clear() {
        LIVE.clear();
    }

    private static void prune() {
        LIVE.removeIf(attack -> attack.gone || attack.ended || attack.level.getGameTime() - attack.born > MAX_AGE);
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double length = ab.lengthSqr();
        double t = length < 1.0E-8 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / length));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}
