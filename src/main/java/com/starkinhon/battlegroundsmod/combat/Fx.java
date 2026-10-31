package com.starkinhon.battlegroundsmod.combat;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Particle, sound and effect helpers. Big looks go through effects(), which the client draws with its own shaders.
public final class Fx {
    // Particles are sent with the long-distance flag so far-away cameras still see big moves.
    private static final double VIEW_DISTANCE_SQR = 128 * 128;
    private static final double EFFECTS_DISTANCE_SQR = 448 * 448;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private Fx() {
    }

    public static DustParticleOptions dust(int rgb, float scale) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        return new DustParticleOptions(new Vector3f(r, g, b), Math.min(scale, 4f));
    }

    public static void burst(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
        send(level, particle, pos, count, spread, spread, spread, speed);
    }

    // Spawns one particle moving with the given velocity.
    public static void moving(ServerLevel level, ParticleOptions particle, Vec3 pos, Vec3 velocity) {
        send(level, particle, pos, 0, velocity.x, velocity.y, velocity.z, 1);
    }

    public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step) {
        Vec3 delta = to.subtract(from);
        int points = Math.max(1, (int) (delta.length() / step));
        for (int i = 0; i <= points; i++) {
            burst(level, particle, from.add(delta.scale(i / (double) points)), 1, 0, 0);
        }
    }

    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double offset) {
        for (int i = 0; i < points; i++) {
            double angle = offset + i * Math.PI * 2 / points;
            burst(level, particle, center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 1, 0, 0);
        }
    }

    public static void sphere(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = GOLDEN_ANGLE * i;
            burst(level, particle, center.add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius), 1, 0, 0);
        }
    }

    // Outline of a box: used for the black coffin.
    public static void box(ServerLevel level, ParticleOptions particle, AABB box, double step) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double y : ys) {
            for (double z : zs) {
                line(level, particle, new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z), step);
            }
            for (double x : xs) {
                line(level, particle, new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ), step);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                line(level, particle, new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z), step);
            }
        }
    }

    // Plays the skill file's "vfx" for this moment on every client close enough to see it (screen effects
    // like impact frames and bending, and the shader-drawn world effects).
    public static void effects(ServerLevel level, String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir) {
        broadcast(level, pos, new ModPayloads.PlayEffects(skill, awakened, momento, pos, dir, -1, 0));
    }

    // Same, lasting ticks for the effects that last as long as the moment (duraMomento).
    public static void effects(ServerLevel level, String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int ticks) {
        broadcast(level, pos, new ModPayloads.PlayEffects(skill, awakened, momento, pos, dir, -1, ticks));
    }

    // Same, with effects that can stick to entity (seguir in the file) for ticks (duraMomento).
    public static void effects(ServerLevel level, String skill, boolean awakened, String momento, Entity entity, int ticks) {
        Vec3 pos = entity.position().add(0, entity.getBbHeight() / 2, 0);
        broadcast(level, pos, new ModPayloads.PlayEffects(skill, awakened, momento, pos, entity.getLookAngle(), entity.getId(), ticks));
    }

    // Only for one player's screen (the one being burned, say).
    public static void effectsTo(ServerPlayer player, String skill, boolean awakened, String momento, int ticks) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.PlayEffects(skill, awakened, momento, player.getEyePosition(), player.getLookAngle(),
                player.getId(), ticks));
    }

    private static void broadcast(ServerLevel level, Vec3 pos, ModPayloads.PlayEffects payload) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < EFFECTS_DISTANCE_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void send(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE_SQR) {
                level.sendParticles(player, particle, true, pos.x, pos.y, pos.z, count, dx, dy, dz, speed);
            }
        }
    }
}
