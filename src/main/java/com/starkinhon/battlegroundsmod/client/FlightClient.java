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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dragon Ball flight on the client (universe/Flight), the approved page's physics (forja voo/voo.html) line by
// line: it moves the local player itself every tick (Minecraft's flying push is zero, see Flight), so the flight has
// its own feel: a cruise that answers the keys at once in every direction (see the steering in tick), floating up and
// down a little when no key is held, and the boost.
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
        if (minecraft.level == null || minecraft.player == null || entityId == minecraft.player.getId()
                || !(minecraft.level.getEntity(entityId) instanceof LivingEntity other)) {
            return;
        }
        float[] color = FlightFx.colorOf(other);
        // Someone else lighting the boost: everyone around sees the ring and hears the boom (the local player's own ones
        // go off the moment the key is pressed, see tick).
        if ((state & Flight.BOOST) != 0 && (old & Flight.BOOST) == 0) {
            Vec3 dir = other.getDeltaMovement().lengthSqr() > 1.0E-4 ? other.getDeltaMovement().normalize() : other.getLookAngle();
            FlightFx.burst(other.position().add(0, 1, 0).add(dir.scale(0.6)), dir, 1, color);
            boom(other.position(), true);
        }
        // A flying soul (boss/AirCombat) taking off and landing: the players' own take-off is in tick.
        if (!(other instanceof Player)) {
            if ((state & Flight.FLYING) != 0 && (old & Flight.FLYING) == 0) {
                FlightFx.burst(other.position().add(0, 0.15, 0), new Vec3(0, 1, 0), 0.9f, color);
                boom(other.position(), false);
            } else if ((state & Flight.FLYING) == 0 && (old & Flight.FLYING) != 0 && other.isAlive()) {
                FlightFx.burst(other.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 1.3f, color);
                minecraft.level.playLocalSound(other.getX(), other.getY(), other.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
                        0.45f, 1.5f, false);
            }
        }
    }

    // Who the server says flies (players and the flying souls).
    public static java.util.Set<Integer> ids() {
        return STATES.keySet();
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
        AirFightClient.clear();
        dodgeT = 0;
        active = false;
        knockTicks = 0;
        slam = false;
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

    static float cruise(LivingEntity entity) {
        if (!(entity instanceof Player)) {
            // the flying souls (boss/AirCombat): their cruise and the investida's boost
            return 16;
        }
        return 11 + (Tiers.HUMANO - Tiers.of(entity)) * 1.4f;
    }

    static float boostSpeed(LivingEntity entity) {
        return entity instanceof Player ? cruise(entity) * 2.3f : 46;
    }

    public static float boostSpeedOf(LivingEntity entity) {
        return boostSpeed(entity);
    }

    // Thrown by a flying soul's blow (boss/AirCombat): off at `velocity` without control for `ticks`, the speed dying
    // out (the page's 2.2) unless it is the Martelo's slam, which goes straight into the ground and lands there.
    private static int knockTicks;
    private static boolean slam;

    public static void knock(Vec3 velocity, int ticks, boolean slammed) {
        if (!active) {
            return;
        }
        vx = velocity.x;
        vy = velocity.y;
        vz = velocity.z;
        knockTicks = Math.max(knockTicks, ticks);
        slam = slammed;
        if (boost) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                setBoost(minecraft.player, false, boostSpeed(minecraft.player));
            }
        }
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
        AirFightClient.tick(player, now);
        if (!now) {
            if (active) {
                land();
            }
            active = false;
            knockTicks = 0;
            slam = false;
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
        if (knockTicks > 0) {
            knockTicks--;
            if (!slam) {
                float drag = damp(2.2f);
                vx -= vx * drag;
                vy -= vy * drag;
                vz -= vz * drag;
            } else if (knockTicks == 0) {
                slam = false;
            }
            if (!Double.isFinite(vx + vy + vz)) {
                vx = vy = vz = 0;
            }
            // thrown against a wall: the speed into it is gone
            if (player.horizontalCollision) {
                vx *= 0.2;
                vz *= 0.2;
            }
            player.setDeltaMovement(vx * DT, vy * DT, vz * DT);
            player.fallDistance = 0;
            speed01 = (float) Mth.clamp(Math.sqrt(vx * vx + vy * vy + vz * vz) / boostSpeed(player), 0, 1);
            ScreenFx.sustainFov(speed01 * 18);
            wind(player);
            return false;
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
        LivingEntity lock = tired ? null : AirFightClient.target();
        boolean has;
        if (lock != null) {
            // The combat flight (AirFightClient): around the target, no boost; Ctrl is the dodge.
            if (boost) {
                setBoost(player, false, boostV);
            }
            used = combat(player, lock, sprintTap, forward, right, up, cruise);
            has = true;
        } else {
        dodgeT = 0;
        if (sprintTap) {
            used = true;
            setBoost(player, !boost && !tired, boostV);
        }
        // Letting go of W ends the boost (like sprinting); so does being tired, or the server saying no (no stamina).
        if (boost && (forward <= 0 || tired || player.tickCount - boostAskedAt > 6 && (st & Flight.BOOST) == 0)) {
            setBoost(player, false, boostV);
        }

        Vec3 look = player.getLookAngle();
        // Right from the yaw alone, so A/D still strafe at full speed while looking straight up or down.
        double yaw = Math.toRadians(player.getYRot());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double wx = look.x * forward + rx * right;
        double wy = look.y * forward + up * 0.8;
        double wz = look.z * forward + rz * right;
        double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
        has = wl > 0.1;
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
        // Responsive steering (Stark 2026-11-12: "trocar de direção toda hora certinho e para todos os lados"): the
        // speed is split into the part along where the keys and camera point and the part across it. The part across
        // dies fast (no drifting sideways out of a turn, strafes and diagonals go exactly where pressed), the part along
        // picks up fast, and pressing the opposite way brakes hard before speeding back up. The boost turns a bit wider
        // so it still feels heavy, and only slows down gently from above the top speed (the kick and its end don't jerk).
        // Letting go of every key stops in about half a second, with a short glide.
        if (has) {
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            double ex = tx / tl, ey = ty / tl, ez = tz / tl;
            double along = vx * ex + vy * ey + vz * ez;
            double px = vx - ex * along, py = vy - ey * along, pz = vz - ez * along;
            float across = damp(boost ? 7f : 12f);
            px -= px * across;
            py -= py * across;
            pz -= pz * across;
            float ka = along < 0 ? 12f : along < tl ? (boost ? 6f : 8f) : 3f;
            along += (tl - along) * damp(ka);
            vx = ex * along + px;
            vy = ey * along + py;
            vz = ez * along + pz;
        } else {
            float stop = damp(6f);
            vx -= vx * stop;
            vy -= vy * stop;
            vz -= vz * stop;
        }
        if (!has) {
            // floating: rising and sinking a little
            vy += Math.sin(player.tickCount * DT * 2.1) * 0.4 * DT;
        }
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

    // ---------- the combat flight (AirFightClient; the approved page's combatMove, doDash, doDodge) ----------

    private static int dodgeT, dodgeCd, dodgeSide = 1;
    private static Vec3 dodgeVel = Vec3.ZERO;
    private static double lockD = -1;

    // The server started the ki dash (AirFight.dash): the kick and the ring now, the homing in combat.
    static void dashStart() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !active) {
            return;
        }
        Vec3 dir = player.getLookAngle();
        FlightFx.burst(player.position().add(0, 1, 0).add(dir.scale(0.6)), dir, 1);
        boom(player.position(), true);
        ScreenFx.fovKick(14, 12);
        ScreenFx.radialBlur(player.getEyePosition().add(dir.scale(30)), 0.6f, 10);
    }

    static void dashEnd() {
        vx *= 0.3;
        vy *= 0.3;
        vz *= 0.3;
    }

    public static boolean dodging() {
        return dodgeT > 0;
    }

    // Sets the speed for this tick around the locked target. Returns whether the Ctrl tap was used (the dodge).
    private static boolean combat(LocalPlayer player, LivingEntity target, boolean sprintTap, float forward, float right, float up, float cruise) {
        Vec3 me = player.position().add(0, 0.9, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0).subtract(me);
        double dist = Math.max(1.0E-3, to.length());
        Vec3 fwd = to.scale(1 / dist);
        Vec3 flat = new Vec3(fwd.x, 0, fwd.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        if (dodgeCd > 0) {
            dodgeCd--;
        }
        boolean used = false;
        LivingEntity dash = AirFightClient.dashing();
        if (sprintTap && dodgeCd <= 0 && dash == null) {
            // The dodge: 0.22 s at 38 b/s to the side pressed (back with S, alternating sides with nothing), 0.34 s untouchable.
            used = true;
            Vec3 dir;
            if (right != 0) {
                dir = side.scale(Math.signum(right));
            } else if (forward < 0) {
                dir = fwd.scale(-1);
            } else if (forward > 0) {
                dir = side.scale(dodgeSide).add(fwd.scale(0.6)).normalize();
                dodgeSide = -dodgeSide;
            } else {
                dir = side.scale(dodgeSide);
                dodgeSide = -dodgeSide;
            }
            dir = dir.add(0, up * 0.5, 0).normalize();
            dodgeVel = dir.scale(38);
            dodgeT = 5;
            dodgeCd = 10;
            PacketDistributor.sendToServer(new ModPayloads.AirDodge(true));
            FlightFx.burst(player.position().add(0, 1, 0), dir.scale(-1), 0.55f);
            Minecraft.getInstance().level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.8f, 1.7f, false);
            ScreenFx.fovKick(6, 6);
        }
        if (dodgeT > 0) {
            dodgeT--;
            vx = dodgeVel.x;
            vy = dodgeVel.y;
            vz = dodgeVel.z;
            strafe = strafe + ((float) Math.signum(dodgeVel.dot(side)) - strafe) * damp(10);
            return used;
        }
        if (dash != null) {
            // The ki dash: 62 b/s, homing on the target, ends on it.
            Vec3 tc = dash.position().add(0, dash.getBbHeight() / 2, 0).subtract(me);
            double l = tc.length();
            if (l < 2.0) {
                vx *= 0.3;
                vy *= 0.3;
                vz *= 0.3;
            } else {
                Vec3 want = tc.scale(62 / l);
                float k = damp(12);
                vx += (want.x - vx) * k;
                vy += (want.y - vy) * k;
                vz += (want.z - vz) * k;
            }
            return used;
        }
        double top = Math.max(16, cruise * 1.1);
        if (SkillAnimations.guarding(player)) {
            top *= 0.35;
        }
        Vec3 wish = side.scale(right).add(0, up, 0);
        if (forward > 0 && dist > 2.4 || forward < 0) {
            wish = wish.add(fwd.scale(forward));
        }
        if (right != 0) {
            // circling: the distance it had when the circling started is kept
            if (lockD < 0) {
                lockD = dist;
            }
            if (forward != 0) {
                lockD = dist;
            }
            wish = wish.add(fwd.scale(Mth.clamp((dist - lockD) * 0.25, -0.6, 0.6)));
        } else {
            lockD = -1;
        }
        double wl = wish.length();
        if (wl > 1) {
            wish = wish.scale(1 / wl);
        }
        float k = damp(wl > 0.1 ? 8 : 6);
        vx += (wish.x * top - vx) * k;
        vy += (wish.y * top - vy) * k;
        vz += (wish.z * top - vz) * k;
        if (wl < 0.1) {
            vy += Math.sin(player.tickCount * DT * 2.1) * 0.4 * DT;
        }
        strafe = strafe + (right - strafe) * damp(4);
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
