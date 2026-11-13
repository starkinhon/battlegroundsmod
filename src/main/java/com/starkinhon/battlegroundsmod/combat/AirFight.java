package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.AirCombat;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;
import com.starkinhon.battlegroundsmod.skill.DashAtaqueSpec;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;
import com.starkinhon.battlegroundsmod.universe.Flight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The combat flight (forja db-voo-mobs/combate.html, prévia v2 approved by Stark 2026-11-13: "insanamente perfeito"),
// server side. Attack mode (K) on while flying with the Dragon Ball flight: the player stands up in a fighting stance,
// locked on whoever fights them (the client picks it, client/AirFightClient, and tells the server here), and the base
// attacks turn into their air versions:
// - Dash Attack: a long homing ki dash (62 b/s) at the locked target; the client flies it (FlightClient moves the
//   player), the server lands the hit when they meet. Two dashes into each other (the soul's Investida too): the
//   punch exchange across the sky (Goku x Vegeta training);
// - Forte: gone, and back behind the target with a kick; with the target stunned in the middle of a combo (2+ hits),
//   the Martelo instead: over it, both fists down, slammed into the ground from any height (crater);
// - Barragem: homing ki balls from both hands while the key is held;
// - the combo's chase is a lunge in the air.
// Guard (held F) against a soul's Investida or Martelo leaves the soul open; ki balls on the guard fly up, and a guard
// raised just in time (the parry window) sends them back homing on the thrower. Ctrl dodges (iframes, see dodge).
public final class AirFight {
    private static final double DT = 0.05;
    // the client tells who it locks on (AirLock); kept while valid
    private static final Map<UUID, Integer> LOCK = new HashMap<>();
    // hits of the air combo in a row on the locked target, and when the last one landed
    private static final Map<UUID, int[]> COMBO = new HashMap<>();
    // dashing right now (the soul's Investida reads it for the exchange)
    private static final Map<UUID, Long> DASHING = new HashMap<>();
    // who is in a punch exchange right now (until the game time)
    private static final Map<LivingEntity, Long> EXCHANGE = new HashMap<>();

    public static final int MOVE_DASH = 0, MOVE_STOP = 1, MOVE_EXCHANGE = 2;
    // another player already flies that soul's page (multiplayer): the client leaves it to them for a while
    public static final int MOVE_DENY = 3;

    private AirFight() {
    }

    // ------------------------------------------------------------------ the lock

    public static void lock(ServerPlayer player, int id) {
        if (id < 0) {
            LOCK.remove(player.getUUID());
        } else {
            LOCK.put(player.getUUID(), id);
            // Locking on a soul from the sky calls it out: it takes the player as its target even past its follow
            // range (a Vegeta on the ground 48 blocks under a flying player never noticed him, so never took off).
            if (Flight.flying(player) && player.level().getEntity(id) instanceof SoulBoss boss && boss.air() != null
                    && boss.isAlive() && boss.getTarget() == null && Combat.isTarget(player, boss)) {
                boss.setTarget(player);
            }
        }
    }

    // Whoever this one fights in the air right now: a player's lock (flying, attack mode on), a flying soul's target.
    public static LivingEntity target(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            Integer id = LOCK.get(player.getUUID());
            if (id == null || !Flight.flying(player) || !CombatMode.in(player)) {
                return null;
            }
            if (player.level().getEntity(id) instanceof LivingEntity t && Combat.isTarget(t, player) && t.distanceToSqr(player) < 80 * 80) {
                return t;
            }
            return null;
        }
        if (entity instanceof SoulBoss boss && AirCombat.flying(boss)) {
            return boss.getTarget();
        }
        return null;
    }

    public static boolean active(LivingEntity entity) {
        return entity instanceof ServerPlayer && target(entity) != null;
    }

    public static boolean dashing(LivingEntity entity) {
        Long until = DASHING.get(entity.getUUID());
        return until != null && until >= entity.level().getGameTime();
    }

    public static boolean exchanging(LivingEntity entity) {
        Long until = EXCHANGE.get(entity);
        return until != null && until >= entity.level().getGameTime();
    }

    // Moves a flying player for the server: the client flies them (FlightClient), so it is told how.
    private static void move(ServerPlayer player, int kind, int target, int ticks) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.AirMove(kind, target, ticks));
    }

    private static Vec3 center(LivingEntity e) {
        return Combat.center(e);
    }

    // ------------------------------------------------------------------ the page's fight on the client

    // Stark 2026-11-13, after the server's copy still felt "muito inferior à prévia": the client runs the approved page
    // itself (client/PreviewFight: its AI, blows, dash, exchange, balls, beam, effects and camera, 60 times a second, no
    // lag), flying the soul it fights; here the soul goes where the page has it and the blows land.
    private static LivingEntity simTarget(ServerPlayer player, int id) {
        Integer locked = LOCK.get(player.getUUID());
        if (locked == null || locked != id || !Flight.flying(player)) {
            return null;
        }
        if (player.level().getEntity(id) instanceof LivingEntity t && t.isAlive() && t.distanceToSqr(player) < 64 * 64) {
            return t;
        }
        return null;
    }

    public static void sim(ServerPlayer player, int id, Vec3 pos, float yaw, int flags) {
        if (!(simTarget(player, id) instanceof SoulBoss boss) || boss.air() == null || pos.distanceToSqr(player.position()) > 64 * 64
                || !Double.isFinite(pos.x + pos.y + pos.z)) {
            return;
        }
        if (!boss.air().pilot(player, pos, yaw, flags)) {
            move(player, MOVE_DENY, id, 0);
        }
    }

    // kinds: the player's blows 0 punch, 1 heavy (the 4th punch, the dash, the kick from behind), 2 the Martelo,
    // 3 a ki ball, 4 a ball sent back; the soul's on the player 10 blow, 11 heavy, 12 Martelo, 13 ki ball, 14 beam,
    // 15 a blow on the guard, 16 the beam on the guard
    public static void simHit(ServerPlayer player, int kind, int id) {
        LivingEntity t = simTarget(player, id);
        if (t == null) {
            return;
        }
        if (kind < 0 || kind > 16) {
            return;
        }
        if (kind <= 4) {
            // The client says when its blows land: never faster than the page can (punches, clashes and balls come
            // 3 ticks apart at the quickest), so a changed client can't rain blows (multiplayer).
            PowerData data = player.getData(ModAttachments.POWER);
            long now = player.level().getGameTime();
            if (now - data.airSimHitAt < 2) {
                return;
            }
            data.airSimHitAt = now;
        }
        float punch = (float) ComboSpec.get().golpe(0).dano() * Melee.multiplier(player);
        switch (kind) {
            case 0 -> Combat.hurtBasic(player, t, punch);
            case 1 -> Combat.hurtBasic(player, t, Math.max(punch * 2.2f, 25f * Melee.multiplier(player) * 0.9f));
            case 2 -> Combat.hurtBasic(player, t, 25f * Melee.multiplier(player) * 1.1f);
            case 3 -> Combat.hurtMagic(player, t, 3.2f * Melee.multiplier(player));
            case 4 -> Combat.hurtMagic(player, t, 3.2f * Melee.multiplier(player) * 2.2f);
            case 10 -> Combat.hurtBasic(t, player, 9);
            case 11 -> Combat.hurtBasic(t, player, 20);
            case 12 -> Combat.hurtBasic(t, player, 26);
            case 13 -> Combat.hurtMagic(t, player, 7);
            case 14 -> Combat.hurtMagic(t, player, 24);
            case 15 -> Combat.hurtBasic(t, player, 1.4f);
            case 16 -> Combat.hurtMagic(t, player, 3.6f);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Ctrl: the dodge

    // The client already threw the player aside (0.22 s at 38 b/s); here the 0.34 s with nothing landing on them (the
    // soul's beam passes through too, see AirCombat).
    public static void dodge(ServerPlayer player) {
        if (!Flight.flying(player) || Stun.is(player) || Freeze.isFrozen(player)) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        long now = player.level().getGameTime();
        if (now - data.airDodgeAt < 10) {
            return;
        }
        data.airDodgeAt = now;
        Intangible.shield(player, 7);
        Fx.sound(Combat.level(player), player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.8f);
    }

    public static boolean dodging(LivingEntity entity) {
        return entity instanceof Player && entity.level().getGameTime() - entity.getData(ModAttachments.POWER).airDodgeAt <= 7;
    }

    // ------------------------------------------------------------------ the Dash Attack: the ki dash

    public static boolean dash(LivingEntity attacker, ServerLevel level) {
        LivingEntity target = target(attacker);
        if (!(attacker instanceof ServerPlayer player) || target == null) {
            return false;
        }
        PowerData data = attacker.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        int ticks = 36;
        data.busyTicks = ticks + 1;
        DASHING.put(player.getUUID(), level.getGameTime() + ticks);
        move(player, MOVE_DASH, target.getId(), ticks);
        SubCommon.pose(player, "voo.arrancada");
        Fx.sound(level, player.position(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.6f);
        DbCommon.screen(level, center(player), 4);
        boolean[] dodged = {false};
        // The soul answers a third of the dashes with its own: they meet halfway, the exchange.
        if (target instanceof SoulBoss boss && boss.air() != null && level.random.nextDouble() < 0.3) {
            boss.air().answerDash(player);
        }
        Tasks.add(age -> {
            if (!player.isAlive() || data.interrupts != interrupts || !Stun.canAttack(player) || !target.isAlive() || exchanging(player)) {
                end(player, data);
                return true;
            }
            double dist = center(player).distanceTo(center(target));
            PunchFx.follow(level, PunchFx.TRAIL, player, center(target).subtract(center(player)).normalize(), Melee.fistColor(player), 0);
            // Both on the boost into each other: the exchange.
            if (dist < 10 && target instanceof SoulBoss boss && boss.air() != null && boss.air().rushingAt(player)) {
                end(player, data);
                exchange(level, player, target);
                return true;
            }
            if (!dodged[0] && dist < 5 && target instanceof SoulBoss boss && boss.air() != null) {
                dodged[0] = true;
                if (level.random.nextDouble() < 0.15 && boss.air().zanzoken(player)) {
                    end(player, data);
                    return true;
                }
            }
            if (dist < 2.4) {
                end(player, data);
                dashHit(level, player, target);
                return true;
            }
            if (age >= ticks) {
                end(player, data);
                return true;
            }
            return false;
        });
        return true;
    }

    private static void end(ServerPlayer player, PowerData data) {
        data.busyTicks = 0;
        DASHING.remove(player.getUUID());
        move(player, MOVE_STOP, -1, 0);
    }

    private static void dashHit(ServerLevel level, ServerPlayer player, LivingEntity target) {
        if (guardPunish(level, target, player, "investida")) {
            return;
        }
        DashAtaqueSpec spec = DashAtaqueSpec.get();
        ComboSpec combo = ComboSpec.get();
        float damage = (float) (combo.golpe(Math.floorMod(spec.golpeDoCombo(), combo.golpes().size())).dano() * spec.golpe().multiplicadorDano())
                * Melee.multiplier(player);
        SubCommon.pose(player, "voo.soco1");
        if (!Combat.hurtBasic(player, target, damage)) {
            return;
        }
        Vec3 dir = center(target).subtract(center(player)).normalize();
        Melee.hitFx(player, target, "final");
        Fx.sound(level, center(target), SoundEvents.PLAYER_ATTACK_STRONG, 1.3f, 0.8f);
        throwAway(target, dir.scale(6), 6);
        Stun.apply(target, 18);
        combo(player, target, 1);
        Stamina.gain(player, StaminaSpec.get().ganhoAcertoPct());
    }

    // ------------------------------------------------------------------ Forte: behind, or the Martelo

    public static boolean strong(LivingEntity attacker, ServerLevel level) {
        LivingEntity target = target(attacker);
        if (!(attacker instanceof ServerPlayer player) || target == null || player.distanceTo(target) > 12) {
            return false;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        boolean hammer = Stun.is(target) && comboCount(player, target) >= 2;
        data.busyTicks = hammer ? 14 : 10;
        Vec3 from = player.position();
        SubNet.send(level, SubNet.VANISH, 0, from.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
        Fx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.8f);
        player.setInvisible(true);
        // A moment hold so the client does not fly off meanwhile.
        AirCombat.knock(player, Vec3.ZERO, hammer ? 10 : 6, false);
        Tasks.later(3, () -> {
            player.setInvisible(false);
            if (!player.isAlive() || data.interrupts != interrupts || !target.isAlive()) {
                return;
            }
            Vec3 fd = target.position().subtract(from);
            fd = fd.lengthSqr() < 1.0E-4 ? Combat.flatLook(player) : fd.normalize();
            Vec3 spot = hammer ? target.position().add(0, 1.7, 0) : target.position().add(fd.scale(1.5));
            if (!level.noCollision(player, player.getBoundingBox().move(spot.subtract(player.position())))) {
                spot = target.position().add(fd.scale(-1.6));
            }
            Vec3 look = center(target).subtract(spot.add(0, player.getEyeHeight(), 0));
            float yRot = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
            float xRot = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
            player.connection.teleport(spot.x, spot.y, spot.z, yRot, xRot);
            SubNet.send(level, SubNet.VANISH, 0, spot.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
            SubCommon.pose(player, hammer ? "voo.martelo" : "voo.chute_atras");
            Tasks.later(hammer ? 5 : 2, () -> {
                if (!player.isAlive() || data.interrupts != interrupts || !target.isAlive() || player.distanceTo(target) > 4.5) {
                    return;
                }
                if (guardPunish(level, target, player, hammer ? "martelo" : "forte")) {
                    return;
                }
                Defense.breakGuard(target, player);
                float damage = 25f * Melee.multiplier(player) * (hammer ? 1.1f : 0.9f);
                Combat.hurtBasic(player, target, damage);
                Melee.hitFx(player, target, "final", PunchFx.FORTE, hammer ? 1 : 0.5f);
                Vec3 c = center(target);
                DbCommon.screen(level, c, 1);
                Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
                Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.5f);
                if (hammer) {
                    slam(level, player, target);
                } else {
                    Vec3 away = target.position().subtract(player.position());
                    throwAway(target, (away.lengthSqr() < 1.0E-4 ? fdOf(player) : away.normalize()).scale(22), 12);
                    Stun.apply(target, 16);
                    combo(player, target, 1);
                }
                Stamina.gain(player, StaminaSpec.get().ganhoAcertoPct());
            });
        });
        return true;
    }

    private static Vec3 fdOf(LivingEntity e) {
        return Combat.flatLook(e);
    }

    // A grab that throws to the ground, used in the air: true when the caster flies and the target is off the ground.
    public static boolean slamsDown(LivingEntity caster, LivingEntity target) {
        boolean flies = caster instanceof ServerPlayer p ? Flight.flying(p) : caster instanceof SoulBoss b && AirCombat.flying(b);
        if (!flies || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        return target.getY() - DbCommon.groundY(level, target.position()) > 2.5;
    }

    // Straight down into the ground from any height (the Martelo, the grabs that throw to the ground): it lands in a
    // crater, takes a second blow and a player stops flying.
    public static void slam(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        Stun.apply(target, 60);
        if (target instanceof SoulBoss boss && boss.air() != null && AirCombat.flying(boss)) {
            boss.air().slammed();
        } else {
            throwAway(target, new Vec3(0, -42, 0), 40, true);
        }
        int[] age = {0};
        Tasks.add(a -> {
            age[0]++;
            if (!target.isAlive() || target.level() != level || age[0] > 100) {
                return true;
            }
            double above = target.getY() - DbCommon.groundY(level, target.position());
            if (age[0] < 2 || !(target.onGround() || above < 0.6)) {
                if (!(target instanceof Player)) {
                    target.setDeltaMovement(target.getDeltaMovement().x * 0.5, -2.1, target.getDeltaMovement().z * 0.5);
                    target.hurtMarked = true;
                }
                return false;
            }
            Vec3 feet = target.position();
            if (target instanceof ServerPlayer player && player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            DbCommon.hit(level, feet.add(0, 0.3, 0), 1.3f, Db.WHITE);
            DbCommon.screen(level, feet, 4);
            DbCommon.bigCrater(level, feet.add(0, -0.5, 0), 3.5, 2, 4);
            SubCommon.sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 1.3f, 0.8f);
            Combat.hurtBasic(attacker, target, 14);
            Stun.apply(target, 25);
            return true;
        });
    }

    // ------------------------------------------------------------------ Barragem: homing ki balls

    public static boolean barrage(LivingEntity attacker, ServerLevel level) {
        LivingEntity target = target(attacker);
        if (!(attacker instanceof ServerPlayer player) || target == null) {
            return false;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        int color = Melee.fistColor(player);
        float damage = 3.2f * Melee.multiplier(player);
        data.busyTicks = 14;
        int[] shots = {0};
        Tasks.add(age -> {
            if (!player.isAlive() || data.interrupts != interrupts || Stun.real(player) || !target.isAlive()) {
                SubCommon.pose(player, "");
                return true;
            }
            boolean holding = age < 12 || data.attackHeld && age < 70;
            if (!holding) {
                data.busyTicks = 2;
                return true;
            }
            data.busyTicks = 3;
            // a ball every 0.13 s, the hands taking turns (2 and 3 ticks)
            if (age >= shots[0] * 2.6) {
                boolean right = shots[0] % 2 == 0;
                SubCommon.pose(player, right ? "voo.ki_d" : "voo.ki_e");
                Vec3 look = center(target).subtract(player.getEyePosition()).normalize();
                Vec3 side = look.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(right ? 0.38 : -0.38);
                Vec3 from = player.getEyePosition().add(0, -0.45, 0).add(side).add(look.scale(0.6));
                // a small spread and a quick turn (at 0.35 / 4.5 rad/s they scattered wide, Stark's first play)
                Vec3 jitter = new Vec3(level.random.nextGaussian(), level.random.nextGaussian() * 0.6, level.random.nextGaussian()).scale(0.07);
                kiBall(level, player, from, center(target).subtract(from).normalize().add(jitter).normalize(), target, color, 46, 9, damage, false);
                shots[0]++;
            }
            return false;
        });
        return true;
    }

    // One homing ki ball, flown by the server tick by tick (drawn as the trailed KI_BALL stream). On a guard it flies
    // up; on a guard raised in time (the parry) it turns round, homing on its owner, faster and harder.
    public static void kiBall(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, LivingEntity target, int color, double speed,
            double turn, float damage, boolean reflected) {
        int id = SubNet.newId();
        Vec3[] pos = {from};
        Vec3[] vel = {dir.normalize().scale(speed)};
        boolean[] deflected = {false};
        SubCommon.sound(level, from, SoundEvents.FIRECHARGE_USE, 0.5f, 1.8f);
        Tasks.add(age -> {
            if (!owner.isAlive() && !reflected) {
                SubNet.send(level, SubNet.KI_BALL, id, pos[0], Vec3.ZERO, 0, color, 1);
                return true;
            }
            if (!deflected[0] && target.isAlive() && age > 1) {
                Vec3 want = center(target).subtract(pos[0]);
                double l = want.length();
                if (l > 1.0E-3) {
                    Vec3 vd = vel[0].normalize();
                    Vec3 td = want.scale(1 / l);
                    double ang = Math.acos(Mth.clamp(vd.dot(td), -1, 1));
                    double step = turn * DT;
                    if (ang > 1.0E-4) {
                        vd = vd.lerp(td, Math.min(1, step / ang)).normalize();
                    }
                    vel[0] = vd.scale(speed);
                }
            }
            Vec3 a = pos[0];
            Vec3 b = a.add(vel[0].scale(DT));
            var block = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : b;
            pos[0] = end;
            if (age > 60 || wall) {
                SubNet.send(level, SubNet.KI_BALL, id, end, Vec3.ZERO, 0.22f, color + 16 * 2, 1);
                DbCommon.hit(level, end, 0.35f, color);
                return true;
            }
            if (!deflected[0]) {
                for (LivingEntity e : Combat.alongLine(level, a, end, 0.55, owner)) {
                    Defense.Result guard = Defense.check(e, owner);
                    if (guard == Defense.Result.PARRY && !reflected) {
                        // back to the thrower, homing
                        SubNet.send(level, SubNet.KI_BALL, id, end, Vec3.ZERO, 0, color, 1);
                        DbCommon.hit(level, end, 0.6f, Db.WHITE);
                        SubCommon.sound(level, end, SoundEvents.ANVIL_LAND, 0.6f, 1.9f);
                        if (e instanceof ServerPlayer p) {
                            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.battlegroundsmod.air_reflect"), true);
                        }
                        kiBall(level, e, end, owner.position().add(0, 1, 0).subtract(end), owner, Melee.lighter(color), 54, 10, damage * 2.2f, true);
                        return true;
                    }
                    if (guard == Defense.Result.BLOCK) {
                        deflected[0] = true;
                        vel[0] = new Vec3(vel[0].x * 0.25, speed * 0.8, vel[0].z * 0.25);
                        DbCommon.hit(level, end, 0.4f, color);
                        SubCommon.sound(level, end, SoundEvents.SHIELD_BLOCK, 0.8f, 1.4f);
                        break;
                    }
                    if (Combat.hurtMagic(owner, e, damage)) {
                        Vec3 push = vel[0].normalize();
                        throwAway(e, push.scale(reflected ? 8 : 2.5), reflected ? 6 : 2);
                        if (reflected) {
                            Stun.apply(e, 12);
                        }
                    }
                    SubNet.send(level, SubNet.KI_BALL, id, end, Vec3.ZERO, 0.22f, color + 16 * 2, 1);
                    DbCommon.hit(level, end, reflected ? 0.8f : 0.4f, color);
                    SubCommon.sound(level, end, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6f, 1.6f);
                    return true;
                }
            }
            SubNet.send(level, SubNet.KI_BALL, id, end, Vec3.ZERO, reflected ? 0.3f : 0.22f, color + 16 * 2, 0);
            return false;
        });
    }

    // ------------------------------------------------------------------ the combo in the air

    // A combo punch at someone in the air a bit too far: the lunge (34 b/s) instead of the walk.
    public static boolean lunge(LivingEntity attacker, LivingEntity target) {
        if (!(attacker instanceof ServerPlayer player) || !active(player) || target == null) {
            return false;
        }
        double d = player.distanceTo(target);
        if (d <= 2.2 || d > 9) {
            return false;
        }
        Vec3 dir = center(target).subtract(center(player)).normalize();
        int ticks = (int) Mth.clamp(Math.round((d - 1.8) / (34 * DT)), 1, 6);
        AirCombat.knock(player, dir.scale(34), ticks, false);
        return true;
    }

    // A combo hit landed in the air: counts for the Martelo (the Forte on a stunned target after 2+ hits).
    public static void onMeleeHit(LivingEntity attacker, LivingEntity target) {
        if (attacker instanceof ServerPlayer player && active(player)) {
            combo(player, target, -1);
        }
    }

    private static void combo(ServerPlayer player, LivingEntity target, int set) {
        long now = player.level().getGameTime();
        int[] c = COMBO.computeIfAbsent(player.getUUID(), k -> new int[3]);
        if (c[2] != target.getId() || now - c[1] > 30) {
            c[0] = 0;
        }
        c[0] = set >= 0 ? set : c[0] + 1;
        c[1] = (int) now;
        c[2] = target.getId();
        if (target instanceof SoulBoss boss && boss.air() != null) {
            boss.air().comboTaken(c[0]);
        }
    }

    private static int comboCount(ServerPlayer player, LivingEntity target) {
        int[] c = COMBO.get(player.getUUID());
        if (c == null || c[2] != target.getId() || player.level().getGameTime() - c[1] > 30) {
            return 0;
        }
        return c[0];
    }

    // ------------------------------------------------------------------ the guard against the soul's big blows

    // A blow (Investida, Martelo) landing on a raised guard: chip damage only and the attacker is left open (stunned,
    // pushed back). True when it was guarded.
    public static boolean guardPunish(ServerLevel level, LivingEntity target, LivingEntity attacker, String what) {
        if (Defense.check(target, attacker) == Defense.Result.NONE) {
            return false;
        }
        Vec3 away = attacker.position().subtract(target.position());
        away = away.lengthSqr() < 1.0E-4 ? Combat.flatLook(target).scale(-1) : away.normalize();
        Vec3 c = center(target).add(away.scale(0.6));
        DbCommon.hit(level, c, 0.9f, Db.WHITE);
        DbCommon.screen(level, c, 4);
        SubCommon.sound(level, c, SoundEvents.SHIELD_BLOCK, 1.2f, 0.7f);
        SubCommon.sound(level, c, SoundEvents.ANVIL_LAND, 0.5f, 1.6f);
        Stun.apply(attacker, 24);
        throwAway(attacker, away.scale(7), 8);
        throwAway(target, away.scale(-3), 3);
        if (target instanceof ServerPlayer p) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.battlegroundsmod.air_guard_" + what), true);
        }
        return true;
    }

    // ------------------------------------------------------------------ the punch exchange

    // Both gone, and 6-7 clashes at points across the sky around where they met, then the last big one throws both back.
    public static void exchange(ServerLevel level, LivingEntity a, LivingEntity b) {
        if (exchanging(a) || exchanging(b)) {
            return;
        }
        int hits = 6 + level.random.nextInt(2);
        int every = 6;
        int total = hits * every + 6;
        long until = level.getGameTime() + total;
        EXCHANGE.put(a, until);
        EXCHANGE.put(b, until);
        Vec3 mid = center(a).add(center(b)).scale(0.5);
        double ground = DbCommon.groundY(level, mid);
        Vec3 c0 = new Vec3(mid.x, Math.max(mid.y, ground + 6), mid.z);
        hold(a, total);
        hold(b, total);
        // the players in it see it from the page's exchange camera (low, from the side)
        if (a instanceof ServerPlayer pa) {
            move(pa, MOVE_EXCHANGE, b.getId(), total);
        }
        if (b instanceof ServerPlayer pb) {
            move(pb, MOVE_EXCHANGE, a.getId(), total);
        }
        int ca = colorOf(a), cb = colorOf(b);
        for (int i = 0; i <= hits; i++) {
            int k = i;
            Tasks.later(i * every + 1, () -> {
                if (!a.isAlive() || !b.isAlive()) {
                    return;
                }
                boolean last = k == hits;
                Vec3 p;
                if (last) {
                    p = c0;
                } else {
                    double ang = level.random.nextDouble() * Math.PI * 2;
                    double r = 3 + level.random.nextDouble() * 6;
                    p = c0.add(Math.cos(ang) * r, (level.random.nextDouble() * 2 - 1) * 3.5, Math.sin(ang) * r);
                    p = new Vec3(p.x, Math.max(p.y, ground + 3), p.z);
                }
                double ang2 = level.random.nextDouble() * Math.PI * 2;
                Vec3 d = new Vec3(Math.cos(ang2), 0, Math.sin(ang2));
                place(level, a, p.subtract(d.scale(0.75)), p);
                place(level, b, p.add(d.scale(0.75)), p);
                a.setInvisible(false);
                b.setInvisible(false);
                SubCommon.pose(a, k % 2 == 0 ? "voo.soco1" : "voo.soco2");
                SubCommon.pose(b, k % 2 == 0 ? "voo.soco2" : "voo.soco1");
                Vec3 hit = p.add(0, 1.0, 0);
                DbCommon.hit(level, hit, last ? 2.2f : 1.3f, Db.WHITE);
                DbCommon.hit(level, hit, last ? 1.3f : 0.7f, k % 2 == 0 ? ca : cb);
                DbCommon.screen(level, hit, last ? 1 : 4);
                SubCommon.sound(level, hit, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.6f + level.random.nextFloat() * 0.3f);
                SubCommon.sound(level, hit, SoundEvents.GENERIC_EXPLODE.value(), last ? 1.3f : 0.6f, last ? 1.1f : 1.7f);
                if (last) {
                    EXCHANGE.remove(a);
                    EXCHANGE.remove(b);
                    throwAway(a, d.scale(-17).add(0, 2, 0), 8);
                    throwAway(b, d.scale(17).add(0, 2, 0), 8);
                    return;
                }
                Tasks.later(3, () -> {
                    // gone between the clashes: only a flicker where they were
                    SubNet.send(level, SubNet.VANISH, 0, a.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                    SubNet.send(level, SubNet.VANISH, 0, b.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                    a.setInvisible(true);
                    b.setInvisible(true);
                });
            });
        }
        Tasks.later(total + 2, () -> {
            a.setInvisible(false);
            b.setInvisible(false);
        });
    }

    private static void hold(LivingEntity e, int ticks) {
        if (e instanceof SoulBoss boss && boss.air() != null) {
            boss.air().hold(ticks);
        } else if (e instanceof ServerPlayer p && Flight.flying(p)) {
            AirCombat.knock(p, Vec3.ZERO, ticks, false);
        }
    }

    private static void place(ServerLevel level, LivingEntity e, Vec3 at, Vec3 face) {
        Vec3 look = face.subtract(at);
        float yRot = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        if (!level.noCollision(e, e.getBoundingBox().move(at.subtract(e.position())))) {
            return;
        }
        if (e instanceof ServerPlayer p) {
            p.connection.teleport(at.x, at.y, at.z, yRot, 0);
        } else {
            e.teleportTo(at.x, at.y, at.z);
            e.setYRot(yRot);
            e.setYHeadRot(yRot);
            e.yBodyRot = yRot;
        }
    }

    private static int colorOf(LivingEntity e) {
        if (e instanceof SoulBoss boss && boss.air() != null) {
            return boss.air().kiColor();
        }
        return Db.WHITE;
    }

    // ------------------------------------------------------------------ throwing

    public static void throwAway(LivingEntity target, Vec3 velocity, int ticks) {
        throwAway(target, velocity, ticks, false);
    }

    // Throws anyone at `velocity` (blocks per second): a flying player through their client, a flying soul through its
    // AirCombat, anyone else pushed.
    public static void throwAway(LivingEntity target, Vec3 velocity, int ticks, boolean slam) {
        if (target instanceof SoulBoss boss && boss.air() != null && AirCombat.flying(boss)) {
            boss.air().thrown(velocity, ticks);
            return;
        }
        AirCombat.knock(target, velocity, ticks, slam);
    }

    public static void clear(ServerPlayer player) {
        LOCK.remove(player.getUUID());
        COMBO.remove(player.getUUID());
        DASHING.remove(player.getUUID());
    }

    public static void prune() {
        EXCHANGE.keySet().removeIf(e -> e.isRemoved() || !exchanging(e));
    }
}
