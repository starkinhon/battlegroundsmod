package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Realismo: the realistic volumes (tornado, fire, smoke, fireball, lava, wind). One screen pass (shaders/core/fx_realismo)
// rebuilds every pixel's place in the world from the depth buffer and marches the view ray through each volume, lit by
// the game's own sun and sky. The flying debris round tornadoes and wind are vanilla block particles of the real blocks
// under them, so they look like the world and any shader pack draws them as its own.
// Learned from Orbital Laser by Stink-O (MIT): see NOTICE.md and section 13 of the effects guide.
public final class Realismo {
    public static final int TORNADO = 0;
    public static final int FOGO = 1;
    public static final int FUMACA = 2;
    public static final int EXPLOSAO = 3;
    public static final int LAVA = 4;
    public static final int VENTO = 5;
    private static final int MAX = 8;

    // speed: how fast it travels along dir (blocks a second); a tornado also wanders round its path.
    // path: where it is each frame (given the partial tick), for a volume riding something that moves on its own (the
    // meteor's tail); null to stay at pos. owner: what it belongs to, so it can be taken away with it (remove).
    private record Vol(int type, Vec3 pos, Vec3 dir, float speed, float radius, float height, float intensity, float[] tint, float seed,
            long start, int life, int follow, String seguir, DoubleFunction<Vec3> path, Object owner) {
        double age(double now) {
            return (now - start) / 20.0;
        }

        Vec3 at(double now, float partial) {
            if (path != null) {
                Vec3 p = path.apply(partial);
                if (p != null) {
                    return p;
                }
            }
            Vec3 base = pos;
            if (follow >= 0) {
                Vec3 anchored = FxAnchor.point(follow, seguir, partial);
                if (anchored != null) {
                    base = anchored;
                }
            }
            double t = age(now);
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            if (flat.lengthSqr() < 1.0e-6) {
                flat = new Vec3(1, 0, 0);
            }
            flat = flat.normalize();
            Vec3 moved = base.add(flat.scale(speed * t));
            if (type == TORNADO) {
                // (it wanders: a slow weave across its path)
                Vec3 side = new Vec3(-flat.z, 0, flat.x);
                moved = moved.add(side.scale(Math.sin(t * 0.45 + seed) * radius * 0.8)).add(flat.scale(Math.sin(t * 0.31 + seed * 2) * radius * 0.4));
            }
            return moved;
        }
    }

    private static final List<Vol> VOLS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static int frame;

    private Realismo() {
    }

    // modo: tornado, fogo, fumaca, explosao, lava, vento (English names work too); -1 if unknown.
    public static int type(String modo) {
        if (modo == null) {
            return FOGO;
        }
        return switch (modo.toLowerCase(Locale.ROOT)) {
            case "tornado" -> TORNADO;
            case "fogo", "fire" -> FOGO;
            case "fumaca", "fumaça", "smoke" -> FUMACA;
            case "explosao", "explosão", "bola", "fireball", "explosion" -> EXPLOSAO;
            case "lava" -> LAVA;
            case "vento", "wind" -> VENTO;
            default -> -1;
        };
    }

    public static String name(int type) {
        return switch (type) {
            case TORNADO -> "tornado";
            case FOGO -> "fogo";
            case FUMACA -> "fumaca";
            case EXPLOSAO -> "explosao";
            case LAVA -> "lava";
            default -> "vento";
        };
    }

    // Sizes when a file or command leaves them out: radius, height, seconds.
    public static float[] defaults(int type) {
        return switch (type) {
            case TORNADO -> new float[] {9, 42, 20};
            case FOGO -> new float[] {2.5f, 7, 10};
            case FUMACA -> new float[] {3, 30, 18};
            case EXPLOSAO -> new float[] {10, 0, 7};
            case LAVA -> new float[] {12, 0, 20};
            default -> new float[] {24, 14, 14};
        };
    }

    // A volume at pos (the foot of a tornado, fire or smoke; the middle of a fireball; the middle of a lava pool or a
    // stretch of wind). dir is the wind, the lean or the way it travels. tint 0xFFFFFF keeps its natural colour.
    // follow: an entity to stay on (-1 none), seguir as in the skill files.
    public static void add(int type, Vec3 pos, Vec3 dir, float speed, float radius, float height, float intensity, int tint, int lifeTicks,
            int follow, String seguir) {
        add(type, pos, dir, speed, radius, height, intensity, tint, lifeTicks, follow, seguir, null, null, 0);
    }

    // A volume that rides path (see Vol) and belongs to owner. preAgeTicks: it starts as if it had already been going that
    // long (a smoke column already at its full height, no fading in); it still lasts lifeTicks from now.
    public static void ride(int type, DoubleFunction<Vec3> path, Vec3 dir, float radius, float height, float intensity, int tint, int lifeTicks,
            int preAgeTicks, Object owner) {
        Vec3 pos = path.apply(0);
        add(type, pos == null ? Vec3.ZERO : pos, dir, 0, radius, height, intensity, tint, lifeTicks, -1, null, path, owner, preAgeTicks);
    }

    // Takes away every volume that belongs to owner (the meteor pulverized in the air).
    public static void remove(Object owner) {
        if (owner != null) {
            VOLS.removeIf(v -> v.owner() == owner);
        }
    }

    private static void add(int type, Vec3 pos, Vec3 dir, float speed, float radius, float height, float intensity, int tint, int lifeTicks,
            int follow, String seguir, DoubleFunction<Vec3> path, Object owner, int preAgeTicks) {
        if (type < 0) {
            return;
        }
        int pre = Math.max(0, preAgeTicks);
        float[] d = defaults(type);
        if (VOLS.size() >= MAX) {
            VOLS.remove(0);
        }
        float seed = RANDOM.nextFloat() * 97f;
        VOLS.add(new Vol(type, pos, dir == null ? Vec3.ZERO : dir, speed, radius > 0 ? radius : d[0], height > 0 ? height : d[1],
                intensity > 0 ? intensity : 1, rgb(tint), seed, ScreenFx.now() - pre, Math.max(1, lifeTicks) + pre, follow, seguir, path, owner));
    }

    public static void clear() {
        VOLS.clear();
    }

    public static boolean active() {
        return !VOLS.isEmpty() && FxConfig.on(FxConfig.REALISMO);
    }

    public static void tick() {
        long now = ScreenFx.now();
        VOLS.removeIf(v -> now - v.start() > v.life());
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            VOLS.clear();
            return;
        }
        if (!FxConfig.on(FxConfig.REALISMO) || Minecraft.getInstance().isPaused()) {
            return;
        }
        for (Vol v : VOLS) {
            double t = v.age(now);
            double env = Math.min(1, t / 1.5) * Math.min(1, (v.life() / 20.0 - t) / 2.0);
            if (env <= 0) {
                continue;
            }
            if (v.type() == TORNADO) {
                debrisTornado(level, v, v.at(now, 1), t, env);
            } else if (v.type() == VENTO) {
                debrisWind(level, v, v.at(now, 1), env);
            }
        }
    }

    // Pieces of the real ground torn up and flung round the funnel's foot, climbing as they circle it.
    private static void debrisTornado(ClientLevel level, Vol v, Vec3 foot, double t, double env) {
        int n = (int) Math.round(6 * env * v.intensity());
        float reach = Math.max(3, v.radius() * 0.7f);
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI;
            double r = reach * (0.4 + RANDOM.nextDouble() * 0.9);
            double x = foot.x + Math.cos(a) * r;
            double z = foot.z + Math.sin(a) * r;
            BlockState state = groundAt(level, x, foot.y, z);
            if (state == null) {
                continue;
            }
            double y = foot.y + RANDOM.nextDouble() * 1.5;
            double spin = 0.9 + RANDOM.nextDouble() * 0.6;
            // (round it, fast, and up; some thrown out)
            double vx = -Math.sin(a) * spin - Math.cos(a) * 0.15;
            double vz = Math.cos(a) * spin - Math.sin(a) * 0.15;
            double vy = 0.4 + RANDOM.nextDouble() * 0.7;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, vx, vy, vz);
        }
        if (RANDOM.nextFloat() < env) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, foot.x + RANDOM.nextGaussian() * reach * 0.5, foot.y + 0.5,
                    foot.z + RANDOM.nextGaussian() * reach * 0.5, 0, 0.05, 0);
        }
    }

    // Bits of the ground carried along the wind.
    private static void debrisWind(ClientLevel level, Vol v, Vec3 middle, double env) {
        Vec3 flat = new Vec3(v.dir().x, 0, v.dir().z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : flat.normalize();
        int n = (int) Math.round(3 * env * v.intensity());
        for (int i = 0; i < n; i++) {
            double x = middle.x + (RANDOM.nextDouble() * 2 - 1) * v.radius() * 0.7;
            double z = middle.z + (RANDOM.nextDouble() * 2 - 1) * v.radius() * 0.7;
            BlockState state = groundAt(level, x, middle.y, z);
            if (state == null) {
                continue;
            }
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, middle.y + 0.2 + RANDOM.nextDouble() * 1.5, z,
                    flat.x * 0.9, 0.08 + RANDOM.nextDouble() * 0.12, flat.z * 0.9);
        }
    }

    // The block on top of the ground near (x, y, z), looking a few blocks up and down; null if there is only air.
    private static BlockState groundAt(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y) + 3, Mth.floor(z));
        for (int i = 0; i < 8; i++) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty()) {
                return s;
            }
            p.move(0, -1, 0);
        }
        return null;
    }

    // The pass: after the level (with depth) or, with a shader pack on, over the finished picture without depth.
    static void pass(Matrix4f view, Matrix4f proj, Vec3 camera, float partial) {
        var shader = FxShaders.realismo;
        if (shader == null || !active()) {
            return;
        }
        double now = ScreenFx.now() + partial;
        List<Vol> live = new ArrayList<>(VOLS);
        live.sort(Comparator.comparingDouble((Vol v) -> -v.at(now, partial).distanceToSqr(camera)));
        int count = 0;
        for (Vol v : live) {
            if (count >= MAX) {
                break;
            }
            double age = v.age(now);
            if (age < 0) {
                continue;
            }
            Vec3 at = v.at(now, partial).subtract(camera);
            Vec3 dir = v.dir().lengthSqr() < 1.0e-6 ? Vec3.ZERO : v.dir().normalize();
            if (v.type() == VENTO && dir.lengthSqr() < 1.0e-6) {
                dir = new Vec3(1, 0, 0);
            }
            // (a tornado leans the way it goes)
            Vec3 lean = v.type() == TORNADO ? dir.scale(Math.min(1, v.speed() / 6.0)) : dir;
            Matrix4f m = new Matrix4f(
                    (float) at.x, (float) at.y, (float) at.z, v.radius(),
                    v.height(), (float) age, v.life() / 20f, v.type(),
                    (float) lean.x, (float) lean.y, (float) lean.z, v.intensity(),
                    v.tint()[0], v.tint()[1], v.tint()[2], v.seed());
            shader.safeGetUniform("Vol" + count).set(m);
            count++;
        }
        if (count == 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float sunAngle = level.getSunAngle(partial);
        shader.safeGetUniform("SunDir").set(-Mth.sin(sunAngle), Mth.cos(sunAngle), 0f);
        Vec3 sky = level.getSkyColor(camera, partial);
        shader.safeGetUniform("SkyColor").set((float) sky.x, (float) sky.y, (float) sky.z);
        shader.safeGetUniform("Rain").set(level.getRainLevel(partial));
        shader.safeGetUniform("Count").set((float) count);
        shader.safeGetUniform("Quality").set((float) FxConfig.realismQuality());
        shader.safeGetUniform("Frame").set((float) (frame++ % 64));
        shader.safeGetUniform("InverseProj").set(new Matrix4f(proj).invert());
        shader.safeGetUniform("ViewToWorld").set(new Matrix4f(view).invert());
        shader.safeGetUniform("DepthOn").set(FxPost.sceneDepth() > 0 ? 1f : 0f);
        FxPost.pass(shader);
    }

    private static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }
}
