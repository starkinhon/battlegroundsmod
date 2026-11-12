package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.client.fx.FlightFx;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.universe.Flight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dragon Ball flight on the client (universe/Flight), the approved page's physics (forja voo/voo.html) line by
// line: it moves the local player itself every tick (Minecraft's flying push is zero, see Flight), so the flight has
// its own feel: a cruise that keeps its momentum, turns that run in an arc instead of cutting through the middle,
// floating up and down a little when no key is held, and the boost.
// Keys: jump twice takes off (Minecraft's), WASD flies where the camera looks, Space up, Shift down (Shift near the ground
// and slow lands), Ctrl while flying turns the boost on (half of it at once, the ki ring and the boom), letting go of W
// or Ctrl again turns it off. Tired (low stamina, server side) the boost goes out and the player glides down.
// Speeds in blocks per second, by tier: cruise 11 (T10) to 25 (T0), boost 2.3x (25 to 58).
public final class FlightClient {
    private static final float DT = 0.05f;
    // how each player flies, from the server (Flight.FLYING / BOOST / TIRED)
    private static final Map<Integer, Integer> STATES = new HashMap<>();

    // the local player's flight
    private static double vx, vy, vz;
    private static boolean active;
    private static boolean boost;
    private static int boostAskedAt = -100;
    private static float boostAmt;
    private static float strafe;
    private static float speed01;
    private static Wind wind;

    private FlightClient() {
    }

    public static void state(int entityId, int state) {
        Minecraft minecraft = Minecraft.getInstance();
        int old = STATES.getOrDefault(entityId, 0);
        if (state == 0) {
            STATES.remove(entityId);
        } else {
            STATES.put(entityId, state);
        }
        // Someone else lighting the boost: everyone around sees the ring and hears the boom (the local player's own ones
        // go off the moment the key is pressed, see tick).
        if (minecraft.level != null && minecraft.player != null && entityId != minecraft.player.getId()
                && (state & Flight.BOOST) != 0 && (old & Flight.BOOST) == 0 && minecraft.level.getEntity(entityId) instanceof Player other) {
            Vec3 dir = other.getDeltaMovement().lengthSqr() > 1.0E-4 ? other.getDeltaMovement().normalize() : other.getLookAngle();
            FlightFx.burst(other.position().add(0, 1, 0).add(dir.scale(0.6)), dir, 1);
            boom(other.position(), true);
        }
    }

    public static int state(Entity entity) {
        return STATES.getOrDefault(entity.getId(), 0);
    }

    public static boolean flying(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.player) {
            return active;
        }
        return (state(entity) & Flight.FLYING) != 0;
    }

    public static void clear() {
        STATES.clear();
        active = false;
        boost = false;
    }

    // The local player's speed in blocks per second (for the pose), or null when not flying this way.
    public static Vec3 velocity() {
        return active ? new Vec3(vx, vy, vz) : null;
    }

    public static float strafe() {
        return strafe;
    }

    public static float boostAmount() {
        return boostAmt;
    }

    static float cruise(Player player) {
        return 11 + (Tiers.HUMANO - Tiers.of(player)) * 1.4f;
    }

    static float boostSpeed(Player player) {
        return cruise(player) * 2.3f;
    }

    public static float boostSpeedOf(Player player) {
        return boostSpeed(player);
    }

    private static float damp(float k) {
        return 1 - (float) Math.exp(-k * DT);
    }

    // Every client tick before the player moves. sprintTap: Ctrl was tapped this tick. Returns whether the tap was
    // used by the flight (then it does not count towards the teleport's double tap).
    public static boolean tick(boolean sprintTap) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            active = false;
            return false;
        }
        boolean now = player.getAbilities().flying && !player.isCreative() && !player.isSpectator() && !player.isPassenger();
        if (!now) {
            if (active) {
                land();
            }
            active = false;
            boostAmt = Math.max(0, boostAmt - 0.15f);
            stopWind();
            return false;
        }
        int st = state(player);
        boolean tired = (st & Flight.TIRED) != 0;
        if (!active) {
            // Take-off: the push up, a ring of dust under the feet and the boom.
            Vec3 dm = player.getDeltaMovement();
            vx = dm.x * 20;
            vy = Math.max(dm.y * 20, 0) + 9;
            vz = dm.z * 20;
            active = true;
            boost = false;
            FlightFx.burst(player.position().add(0, 0.2, 0), new Vec3(0, 1, 0), 0.8f);
            boom(player.position(), false);
        }
        Options options = minecraft.options;
        boolean keys = minecraft.screen == null;
        float forward = keys ? (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0) : 0;
        float right = keys ? (options.keyRight.isDown() ? 1 : 0) - (options.keyLeft.isDown() ? 1 : 0) : 0;
        float up = keys ? (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0) : 0;
        strafe = strafe + (right - strafe) * damp(4);
        float cruise = cruise(player);
        float boostV = boostSpeed(player);

        boolean used = false;
        if (sprintTap) {
            used = true;
            setBoost(player, !boost && !tired, boostV);
        }
        // Letting go of W ends the boost (like sprinting); so does being tired, or the server saying no (no stamina).
        if (boost && (forward <= 0 || tired || player.tickCount - boostAskedAt > 6 && (st & Flight.BOOST) == 0)) {
            setBoost(player, false, boostV);
        }

        Vec3 look = player.getLookAngle();
        double rx = -look.z, rz = look.x;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl > 1.0E-4) {
            rx /= rl;
            rz /= rl;
        }
        double wx = look.x * forward + rx * right;
        double wy = look.y * forward + up * 0.8;
        double wz = look.z * forward + rz * right;
        double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
        boolean has = wl > 0.1;
        if (has) {
            wx /= wl;
            wy /= wl;
            wz /= wl;
        }
        float top = boost ? boostV : tired ? cruise * 0.5f : cruise;
        double tx = wx * top, ty = wy * top, tz = wz * top;
        if (tired) {
            // Out of breath: no climbing, sinking down to land.
            ty = Math.min(ty, -4);
            has = true;
        }
        // Soft acceleration: picks up fast, slows down a bit slower (the flight glides).
        float k = has ? (boost ? 3.2f : 2.6f) : 1.6f;
        double cur = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (has && cur > 3 && top > 0) {
            // Turns in an arc: with momentum the direction swings round at a limited rate.
            double dx = vx / cur, dy = vy / cur, dz = vz / cur;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            double ex = tx / tl, ey = ty / tl, ez = tz / tl;
            double ang = Math.acos(Mth.clamp(dx * ex + dy * ey + dz * ez, -1, 1));
            if (ang < 2.5) {
                double turn = (boost ? 1.8 : 2.4) * DT;
                double f = ang > 1.0E-4 ? Math.min(1, turn / ang) : 1;
                dx += (ex - dx) * f;
                dy += (ey - dy) * f;
                dz += (ez - dz) * f;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl > 1.0E-3) {
                    dx /= dl;
                    dy /= dl;
                    dz /= dl;
                } else {
                    dx = ex;
                    dy = ey;
                    dz = ez;
                }
                double sp = cur + (tl - cur) * damp(k);
                vx = dx * sp;
                vy = dy * sp;
                vz = dz * sp;
            } else {
                // Turning right round: brakes and turns.
                float kk = damp(k * 0.8f);
                vx += (tx - vx) * kk;
                vy += (ty - vy) * kk;
                vz += (tz - vz) * kk;
            }
        } else {
            float kk = damp(k);
            vx += (tx - vx) * kk;
            vy += (ty - vy) * kk;
            vz += (tz - vz) * kk;
        }
        if (!has) {
            // floating: rising and sinking a little
            vy += Math.sin(player.tickCount * DT * 2.1) * 0.4 * DT;
        }
        boostAmt += ((boost ? 1 : 0) - boostAmt) * damp(boost ? 5 : 2.5f);
        if (!Double.isFinite(vx + vy + vz)) {
            vx = vy = vz = 0;
        }
        // Blocked by a wall or a ceiling: the speed into it is gone (no sliding along it at full speed).
        Vec3 moved = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo).scale(20);
        if (player.horizontalCollision) {
            vx = moved.x;
            vz = moved.z;
        }
        if (player.verticalCollision && vy > 0) {
            vy = Math.min(vy, Math.max(0, moved.y));
        }
        // Brushing the ground only lands when the player means it (Shift, slow, no boost) or is tired; otherwise it
        // skims over it.
        double horiz = Math.sqrt(vx * vx + vz * vz);
        boolean land = tired || up < 0 && horiz < cruise * 0.8 && !boost;
        if (!land && vy < 0 && !player.level().noCollision(player, player.getBoundingBox().move(0, vy * DT - 0.05, 0))) {
            vy = 0;
        }
        player.setDeltaMovement(vx * DT, vy * DT, vz * DT);
        player.fallDistance = 0;

        // The screen: wider view with speed, a punch of it at the boost, the blur towards where it flies.
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        speed01 = (float) Mth.clamp(speed / boostV, 0, 1);
        ScreenFx.sustainFov(speed01 * 18);
        float blur = Mth.clamp((speed01 - 0.45f) * 1.8f, 0, 1) * (0.25f + 0.75f * boostAmt);
        if (blur > 0.01f && speed > 0.1) {
            Vec3 dir = new Vec3(vx, vy, vz).scale(1 / speed);
            ScreenFx.sustainBlur(player.getEyePosition().add(dir.scale(50)), blur * 0.35f);
        }
        wind(player);
        return used;
    }

    private static void setBoost(LocalPlayer player, boolean on, float boostV) {
        if (on == boost) {
            return;
        }
        boost = on;
        PacketDistributor.sendToServer(new ModPayloads.FlightBoost(on));
        if (on) {
            boostAskedAt = player.tickCount;
            // The kick: half of the boost at once, the ki ring in front, the boom, the FOV punch.
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz);
            Vec3 dir = l > 1 ? new Vec3(vx / l, vy / l, vz / l) : player.getLookAngle();
            vx += dir.x * boostV * 0.45;
            vy += dir.y * boostV * 0.45;
            vz += dir.z * boostV * 0.45;
            FlightFx.burst(player.position().add(0, 1, 0).add(dir.scale(0.6)), dir, 1);
            boom(player.position(), true);
            ScreenFx.fovKick(14, 14);
            ScreenFx.radialBlur(player.getEyePosition().add(dir.scale(30)), 0.6f, 10);
        }
    }

    private static void land() {
        Minecraft minecraft = Minecraft.getInstance();
        if (boost) {
            boost = false;
            PacketDistributor.sendToServer(new ModPayloads.FlightBoost(false));
        }
        if (minecraft.player != null && minecraft.player.onGround()) {
            FlightFx.burst(minecraft.player.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5f);
        }
    }

    // The take-off thump and the boost's boom (Minecraft's own sounds, pitched like the page's).
    static void boom(Vec3 at, boolean big) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (big) {
            minecraft.level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2f, 0.55f, false);
            minecraft.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35f, 1.6f, false);
        } else {
            minecraft.level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9f, 0.7f, false);
        }
    }

    // The wind in the ears, louder and higher with speed.
    private static void wind(LocalPlayer player) {
        if (wind == null || wind.isStopped()) {
            wind = new Wind(player);
            Minecraft.getInstance().getSoundManager().play(wind);
        }
    }

    private static void stopWind() {
        if (wind != null) {
            wind.end();
            wind = null;
        }
    }

    private static final class Wind extends AbstractTickableSoundInstance {
        private final LocalPlayer player;
        private float level;

        Wind(LocalPlayer player) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !active) {
                stop();
                return;
            }
            level += (speed01 - level) * 0.2f;
            this.volume = Mth.clamp(level * level * 0.9f + boostAmt * 0.25f, 0, 1.1f);
            this.pitch = 0.75f + level * 0.6f;
        }
    }
}
