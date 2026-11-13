package com.starkinhon.battlegroundsmod.boss;

import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dragon Ball souls fight in the air (forja db-voo-mobs, approved by Stark 2026-11-12: "a melhor coisa que eu já vi
// num mod de anime"), the page's AI line by line. Goku, Vegeta, Piccolo, Kuririn and Freeza fight on the ground as
// always (SoulBossAI); once their target has been flying for 0.35 s they charge up and take off after it, and once it
// has been back on the ground for 0.45 s they dive after it and land in a burst of dust. In the air they face the target
// circling it slowly, then pick a move:
// - Investida: the boost straight at where the target is going to be, 3 blows stuck to it, the last one throws it far
//   (and, with the target high up and still stunned, often the Martelo next);
// - Martelo: a Zanzoken over the target, both fists down, it is slammed into the ground (crater, shake) and stops flying;
// - Rajada: circling sideways while firing 5 ki balls aimed ahead of the target;
// - Sumico: gone, and a kick from behind;
// - Perseguicao: too far (over 26 blocks), the boost after it, faster than anyone's boost;
// - Choque: the target comes at it on the boost: it goes the same way and the fists meet (a flash, an impact frame, both
//   thrown back, no damage).
// While it flies this owns the mob (SoulBossAI does not run), moves it itself every tick (a speed of its own, like the
// player's flight: Minecraft's friction and gravity don't apply) and tells the clients it flies (FlightState), so they
// draw the flight's pose, shell, trail and rings on it like on a player (client/FlightPose, FlightFx).
// It also flies the sky passers (universe/SkyPassers) while they pass: they only follow the speed it is given.
public final class AirCombat {
    public static final Set<String> FLIERS = Set.of("goku", "vegeta", "piccolo", "kuririn", "freeza");
    private static final double DT = 0.05;

    enum St { CHAO, DECOLA, AR, MERGULHO }

    enum Act { NONE, INVESTIDA, MARTELO, RAJADA, SUMICO, PERSEGUE, CHOQUE }

    private final SoulBoss boss;
    St st = St.CHAO;
    private Act act = Act.NONE;
    private int phase;
    private double t, at, cd = 1.2, airT, gndT, orbit;
    private int hits, shots;
    private boolean hitDone;
    // its own speed in blocks per second, and whether it is on the boost
    Vec3 vel = Vec3.ZERO;
    boolean boost;
    // the movement it set last tick (to tell a push from outside: a punch, an explosion)
    private Vec3 lastSet;
    // thrown by a hit: gliding without control
    private int knocked;
    // a blow on its way (lands after `pendT` ticks if the target is still in reach)
    private Hit pending;
    private int pendT;
    // the target's speed in blocks per second (from how it moved: the server doesn't move a flying player)
    private Vec3 tPrev;
    private Vec3 tVel = Vec3.ZERO;
    // whoever the Martelo slammed, until it hits the ground
    private LivingEntity slammed;
    private int slamT;
    // flown by SkyPassers (not fighting): extra pose flags for the clients
    boolean passer;
    int flags;
    private int sent;

    private record Hit(boolean big, boolean slam, double knock, double reach, boolean finisher, float damage) {
    }

    private AirCombat(SoulBoss boss) {
        this.boss = boss;
        this.orbit = boss.getRandom().nextDouble() * 6;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AirCombat::onStartTracking);
        com.starkinhon.battlegroundsmod.command.SoulCommand.addSubcommand(AirCombat::command);
    }

    // /soul aereo <golpe>: the nearest flying soul (64 blocks) does that move next (tests and videos).
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command() {
        return net.minecraft.commands.Commands.literal("aereo").requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("golpe", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (Act a : Act.values()) {
                                if (a != Act.NONE) {
                                    builder.suggest(a.name().toLowerCase(java.util.Locale.ROOT));
                                }
                            }
                            return builder.buildFuture();
                        }).executes(context -> {
                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "golpe").toUpperCase(java.util.Locale.ROOT);
                            Act act;
                            try {
                                act = Act.valueOf(name);
                            } catch (IllegalArgumentException e) {
                                act = Act.NONE;
                            }
                            if (act == Act.NONE) {
                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Golpes: investida, martelo, rajada, sumico, persegue, choque"));
                                return 0;
                            }
                            Vec3 at = context.getSource().getPosition();
                            SoulBoss best = null;
                            for (SoulBoss b : context.getSource().getLevel().getEntitiesOfClass(SoulBoss.class, new net.minecraft.world.phys.AABB(at, at).inflate(64))) {
                                if (b.air != null && b.air.st == St.AR && !b.air.passer && (best == null || b.distanceToSqr(at) < best.distanceToSqr(at))) {
                                    best = b;
                                }
                            }
                            if (best == null) {
                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Nenhum mob de Dragon Ball lutando no ar por perto."));
                                return 0;
                            }
                            best.air.act = Act.NONE;
                            best.air.phase = 0;
                            best.air.forced = act;
                            String shown = act.name().toLowerCase(java.util.Locale.ROOT);
                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Próximo golpe aéreo: " + shown), false);
                            return 1;
                        }));
    }

    public static boolean flier(SoulBoss boss) {
        return !boss.isBoss() && FLIERS.contains(boss.bossId());
    }

    // The mob's own (made on its first tick), or null for anyone else.
    public static AirCombat of(SoulBoss boss) {
        if (boss.air == null && flier(boss)) {
            boss.air = new AirCombat(boss);
        }
        return boss.air;
    }

    // While it flies (or takes off, or dives) this moves it, not SoulBossAI.
    public static boolean owns(SoulBoss boss) {
        AirCombat air = boss.air;
        return air != null && (air.st != St.CHAO || air.passer);
    }

    public static boolean flying(LivingEntity entity) {
        return entity instanceof SoulBoss boss && boss.air != null && boss.air.st != St.CHAO && boss.air.st != St.DECOLA;
    }

    private static float damp(double k) {
        return (float) (1 - Math.exp(-k * DT));
    }

    // ------------------------------------------------------------------ every tick, before the mob moves

    public static void tick(SoulBoss boss) {
        AirCombat air = of(boss);
        if (air != null && boss.level() instanceof ServerLevel level) {
            air.run(level);
        }
    }

    private boolean controlled;

    private void run(ServerLevel level) {
        step(level);
        boolean owned = st != St.CHAO || passer;
        if (owned != controlled) {
            controlled = owned;
            boss.airControl(owned);
        }
    }

    private void step(ServerLevel level) {
        if (!boss.isAlive()) {
            if (st != St.CHAO) {
                land(level, false);
            }
            send();
            return;
        }
        LivingEntity target = boss.getTarget();
        if (target != null && (!target.isAlive() || target.level() != level || target.distanceToSqr(boss) > 96 * 96)) {
            target = null;
        }
        trackTarget(target);
        watchSlam(level);
        boolean wasPushed = pushed();
        if (passer) {
            // SkyPassers set the speed; it only flies along it.
            st = St.AR;
            move();
            send();
            return;
        }
        if (target == null) {
            if (st != St.CHAO) {
                comeDown(level);
            }
            send();
            return;
        }
        boolean up = airborne(target);
        airT = up && slammed != target ? airT + DT : 0;
        gndT = !up ? gndT + DT : 0;
        t += DT;
        if (st == St.CHAO) {
            if (airT > 0.35 && !Stun.is(boss) && !boss.staged()) {
                takeOffStart();
            }
            send();
            return;
        }
        Vec3 toP = center(target).subtract(center(boss));
        double dist = toP.length();
        if (wasPushed || knocked > 0 || Stun.is(boss)) {
            // thrown or stunned: no control, the speed dies out (the page's 2.5)
            knocked = Math.max(0, knocked - 1);
            if (st == St.AR || st == St.MERGULHO) {
                vel = vel.scale(1 - damp(2.5));
                boost = false;
            }
            move();
            send();
            return;
        }
        boss.getLookControl().setLookAt(target, 30, 30);
        switch (st) {
            case DECOLA -> {
                vel = vel.scale(0.8);
                if (t > 0.3) {
                    takeOff(level, toP);
                }
            }
            case AR -> {
                if (gndT > 0.45 && act == Act.NONE) {
                    st = St.MERGULHO;
                    t = 0;
                    boost = true;
                } else {
                    tactics(level, target, toP, dist);
                }
            }
            case MERGULHO -> {
                Vec3 away = flat(boss.position().subtract(target.position()));
                Vec3 spot = target.position().add(away.scale(2.2));
                spot = new Vec3(spot.x, DbCommon.groundY(level, spot), spot.z);
                steer(spot.subtract(boss.position()).normalize(), 48, 8);
                double above = boss.getY() - DbCommon.groundY(level, boss.position());
                if (boss.onGround() || above < 0.4 || t > 2.5) {
                    land(level, true);
                } else if (up) {
                    st = St.AR;
                    boost = false;
                }
            }
            default -> {
            }
        }
        resolveHit(level, target);
        if (st != St.CHAO) {
            move();
        }
        send();
    }

    private void takeOffStart() {
        st = St.DECOLA;
        t = 0;
        boss.getNavigation().stop();
        if (boss.ai != null) {
            boss.ai.halt();
        }
        SubCommon.pose(boss, "soco.forte_carga");
        vel = boss.getDeltaMovement().scale(20);
    }

    private void takeOff(ServerLevel level, Vec3 toP) {
        st = St.AR;
        act = Act.NONE;
        Vec3 side = flat(toP);
        vel = new Vec3(0, 20, 0).add(side.scale(6));
        cd = 0.9;
        boss.setNoGravity(true);
        boss.setOnGround(false);
        SubCommon.pose(boss, "");
        DbCommon.screen(level, boss.position(), 4);
        SubCommon.sound(level, boss.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.7f);
    }

    private void land(ServerLevel level, boolean show) {
        st = St.CHAO;
        act = Act.NONE;
        boost = false;
        vel = Vec3.ZERO;
        cd = 0.5;
        boss.setNoGravity(false);
        boss.setDeltaMovement(0, -0.1, 0);
        lastSet = null;
        if (show) {
            DbCommon.screen(level, boss.position(), 4);
            SubCommon.sound(level, boss.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.5f);
        }
    }

    // No target anymore while in the air: back down to the ground.
    private void comeDown(ServerLevel level) {
        if (st == St.DECOLA) {
            land(level, false);
            return;
        }
        boost = false;
        steer(new Vec3(0, -1, 0), 14, 3);
        double above = boss.getY() - DbCommon.groundY(level, boss.position());
        if (boss.onGround() || above < 0.4) {
            land(level, true);
            return;
        }
        move();
    }

    // ------------------------------------------------------------------ the target

    private static Vec3 center(LivingEntity e) {
        return e.position().add(0, 0.9, 0);
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    // In the air for the fight: flying with the technique (players), another soul flying, or well off the ground.
    static boolean airborne(LivingEntity target) {
        if (target instanceof Player player) {
            return Flight.flying(player);
        }
        if (target instanceof SoulBoss other && other.air != null) {
            return flying(other);
        }
        return false;
    }

    private void trackTarget(LivingEntity target) {
        if (target == null) {
            tPrev = null;
            tVel = Vec3.ZERO;
            return;
        }
        Vec3 now = target.position();
        if (tPrev != null) {
            Vec3 seen = now.subtract(tPrev).scale(20);
            if (seen.lengthSqr() < 150 * 150) {
                tVel = tVel.lerp(seen, 0.6);
            }
        }
        tPrev = now;
    }

    private static boolean boosting(LivingEntity target) {
        return target instanceof Player player && Flight.boosting(player)
                || target instanceof SoulBoss other && other.air != null && other.air.boost;
    }

    // ------------------------------------------------------------------ moving

    // Something else moved it (a punch, an explosion): the speed it was given wins, and it glides on it for a moment.
    private boolean pushed() {
        if (lastSet == null || st == St.CHAO) {
            return false;
        }
        Vec3 dm = boss.getDeltaMovement();
        Vec3 expected = new Vec3(lastSet.x * 0.91, lastSet.y * 0.98, lastSet.z * 0.91);
        if (dm.subtract(expected).length() > 0.15 && dm.length() > expected.length() + 0.1) {
            vel = dm.scale(20);
            knocked = 10;
            act = Act.NONE;
            pending = null;
            boss.setInvisible(false);
            return true;
        }
        return false;
    }

    // The page's steer: arcs into the new direction, keeping the momentum; the speed eases to `top` at rate k.
    private void steer(Vec3 wish, double top, double k) {
        double cur = vel.length();
        double wl = wish.length();
        if (wl > 0.1 && cur > 3) {
            Vec3 vd = vel.scale(1 / cur);
            Vec3 td = wish.scale(1 / wl);
            double ang = Math.acos(Mth.clamp(vd.dot(td), -1, 1));
            if (ang < 2.4) {
                double turn = (boost ? 3 : 3.4) * DT;
                if (ang > 1.0E-4) {
                    vd = vd.lerp(td, Math.min(1, turn / ang)).normalize();
                }
                vel = vd.scale(Mth.lerp(damp(k), cur, top * Math.min(1, wl)));
                return;
            }
        }
        vel = vel.lerp(wish.scale(top), damp(k));
    }

    private void move() {
        boss.getNavigation().stop();
        boss.setZza(0);
        boss.setXxa(0);
        boss.setJumping(false);
        boss.setNoGravity(true);
        boss.fallDistance = 0;
        if (!Double.isFinite(vel.x + vel.y + vel.z)) {
            vel = Vec3.ZERO;
        }
        // Blocked by the ground while flying: no pushing into it.
        if (vel.y < 0 && boss.onGround() && st == St.AR) {
            vel = new Vec3(vel.x, 0, vel.z);
        }
        lastSet = vel.scale(DT);
        boss.setDeltaMovement(lastSet);
        double h = vel.horizontalDistance();
        if (h > 1.5) {
            float yaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
            boss.setYRot(yaw);
            boss.yBodyRot = yaw;
        }
    }

    // ------------------------------------------------------------------ the moves

    // The next move asked for by /soul aereo (tests and videos), or null.
    private Act forced;

    private void start(Act a) {
        act = a;
        at = 0;
        phase = 0;
        hits = 0;
        shots = 0;
        hitDone = false;
    }

    private void endAct(double cooldown) {
        act = Act.NONE;
        phase = 0;
        cd = cooldown + boss.getRandom().nextDouble() * 0.8;
        boost = false;
        boss.setInvisible(false);
    }

    private boolean comingAtMe(LivingEntity target, Vec3 toP, double dist) {
        return boosting(target) && tVel.length() > 15 && tVel.normalize().dot(toP.scale(-1 / Math.max(1.0E-4, dist))) > 0.5 && dist < 18;
    }

    private void tactics(ServerLevel level, LivingEntity target, Vec3 toP, double dist) {
        at += DT;
        Vec3 pc = center(target), mc = center(boss);
        if (act == Act.NONE) {
            boost = false;
            if (comingAtMe(target, toP, dist)) {
                start(Act.CHOQUE);
            } else if (dist > 26) {
                start(Act.PERSEGUE);
            } else {
                // facing it from a distance, circling slowly (leaning into the curve)
                orbit += DT * 0.35;
                Vec3 want = target.position().add(Math.cos(orbit) * 7.5, 1.2, Math.sin(orbit) * 7.5);
                Vec3 wish = want.subtract(boss.position()).scale(0.35);
                if (wish.length() > 1) {
                    wish = wish.normalize();
                }
                steer(wish, 16, 3);
                cd -= DT;
                if (forced != null) {
                    start(forced);
                    forced = null;
                } else if (cd <= 0) {
                    double r = boss.getRandom().nextDouble();
                    // high over the ground, now and then the Martelo straight away (in the first test it only came after
                    // a whole Investida with the target stunned, and never showed in a minute of fight)
                    double above = target.getY() - DbCommon.groundY(level, target.position());
                    if (above > 6 && r < 0.14) {
                        start(Act.MARTELO);
                    } else {
                        r = boss.getRandom().nextDouble();
                        start(r < 0.45 ? Act.INVESTIDA : r < 0.73 ? Act.RAJADA : Act.SUMICO);
                    }
                }
            }
        }
        if (act == Act.NONE) {
            return;
        }
        if ((act == Act.INVESTIDA || act == Act.PERSEGUE) && phase == 0 && comingAtMe(target, toP, dist)) {
            start(Act.CHOQUE);
        }
        switch (act) {
            case INVESTIDA, PERSEGUE, CHOQUE -> rush(level, target, toP, dist, pc, mc);
            case MARTELO -> hammer(level, target, pc);
            case RAJADA -> volley(level, target, toP, dist, pc, mc);
            case SUMICO -> vanishKick(level, target);
            default -> {
            }
        }
    }

    private void rush(ServerLevel level, LivingEntity target, Vec3 toP, double dist, Vec3 pc, Vec3 mc) {
        if (phase == 0) {
            // the boost, aimed where the target is going to be
            Vec3 lead = pc.add(tVel.scale(Mth.clamp(dist / 50, 0, 0.5)));
            boost = true;
            steer(lead.subtract(mc).normalize(), act == Act.PERSEGUE ? 62 : 46, 7);
            if (act == Act.CHOQUE && dist < 9) {
                // locks on at the end of the run
                vel = pc.subtract(mc).normalize().scale(46);
            }
            if (act == Act.PERSEGUE && dist < 9) {
                start(Act.INVESTIDA);
                return;
            }
            if (act == Act.CHOQUE && dist < 2.6 + vel.subtract(tVel).length() * DT * 1.5) {
                clash(level, target);
                return;
            }
            if (act == Act.INVESTIDA && dist < 1.8 + vel.subtract(tVel).length() * DT) {
                phase = 1;
                at = 0;
                hits = 0;
                boost = false;
            }
            if (at > (act == Act.PERSEGUE ? 3.5 : 1.4)) {
                endAct(0.8);
            }
            return;
        }
        // 3 blows stuck to it; the last one throws it far
        Vec3 hold = pc.subtract(toP.normalize().scale(1.3)).subtract(0, 0.9, 0);
        vel = tVel.add(hold.subtract(boss.position()).scale(damp(8) / DT));
        double[] times = {0, 0.2, 0.42};
        String[] anims = {"soco.combo1", "soco.combo2", "soco.combo4"};
        if (hits < 3 && at >= times[hits]) {
            SubCommon.pose(boss, anims[hits]);
            boolean last = hits == 2;
            hitLater(new Hit(last, false, last ? 26 : 3, 2.8, last, last ? 20 : 9), 2);
            hits++;
        }
        if (at > 0.75) {
            double above = target.getY() - DbCommon.groundY(level, target.position());
            if (above > 3 && boss.getRandom().nextDouble() < 0.8 && Stun.is(target)) {
                start(Act.MARTELO);
            } else {
                endAct(1.3);
            }
        }
    }

    // Zanzoken over where the target is going to be, the hammer with both fists down.
    private void hammer(ServerLevel level, LivingEntity target, Vec3 pc) {
        Vec3 ahead = flat(tVel);
        if (phase == 0) {
            if (at < DT * 1.5) {
                vanish(level, boss.position());
            }
            if (at > 0.2) {
                Vec3 spot = pc.add(tVel.scale(0.28)).add(0, 1.4, 0).add(ahead.scale(0.9)).subtract(0, 0.9, 0);
                appear(level, spot);
                vel = tVel;
                phase = 1;
                at = 0;
                SubCommon.pose(boss, "soco.forte_golpe");
            }
            return;
        }
        Vec3 over = pc.add(0, 1.3, 0).add(ahead.scale(0.8)).subtract(0, 0.9, 0);
        vel = tVel.add(over.subtract(boss.position()).scale(damp(18) / DT));
        if (at > 0.27 && !hitDone) {
            hitDone = true;
            hitLater(new Hit(true, true, 0, 3.4, true, 26), 0);
        }
        if (at > 0.6) {
            endAct(1.0);
        }
    }

    // Circling sideways, 5 ki balls aimed ahead of the target, hands taking turns.
    private void volley(ServerLevel level, LivingEntity target, Vec3 toP, double dist, Vec3 pc, Vec3 mc) {
        boost = false;
        orbit += DT * 1.25;
        Vec3 want = target.position().add(Math.cos(orbit) * 10, 2.5, Math.sin(orbit) * 10);
        Vec3 wish = want.subtract(boss.position()).scale(0.4);
        if (wish.length() > 1) {
            wish = wish.normalize();
        }
        steer(wish, 20, 4);
        if (at > 0.25 + shots * 0.2 && shots < 5) {
            boolean right = shots % 2 == 0;
            SubCommon.pose(boss, right ? "soco.combo1" : "db.palma_torta");
            Vec3 dir = toP.normalize();
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(right ? 0.4 : -0.4);
            Vec3 from = mc.add(0, 0.45, 0).add(side).add(dir.scale(0.7));
            Vec3 aim = pc.add(tVel.scale(dist / 42));
            shoot(level, from, aim);
            shots++;
        }
        if (at > 1.6) {
            endAct(1.1);
        }
    }

    // Gone, and a kick from behind.
    private void vanishKick(ServerLevel level, LivingEntity target) {
        Vec3 back = flatLook(target).scale(-1.5);
        if (phase == 0) {
            if (at < DT * 1.5) {
                vanish(level, boss.position());
            }
            if (at > 0.22) {
                appear(level, target.position().add(back));
                vel = tVel;
                phase = 1;
                at = 0;
                SubCommon.pose(boss, "soco.combo4");
                hitLater(new Hit(true, false, 20, 2.8, false, 18), 2);
            }
            return;
        }
        vel = tVel;
        if (at < 0.14) {
            Vec3 spot = target.position().add(flatLook(target).scale(-1.4));
            vel = tVel.add(spot.subtract(boss.position()).scale(damp(16) / DT));
        }
        if (at > 0.5) {
            endAct(1.2);
        }
    }

    private static Vec3 flatLook(LivingEntity e) {
        return flat(e.getLookAngle());
    }

    private void vanish(ServerLevel level, Vec3 at) {
        SubNet.send(level, SubNet.VANISH, 0, at.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.8f);
        boss.setInvisible(true);
    }

    private void appear(ServerLevel level, Vec3 spot) {
        if (level.noCollision(boss, boss.getBoundingBox().move(spot.subtract(boss.position())))) {
            boss.teleportTo(spot.x, spot.y, spot.z);
        }
        boss.setInvisible(false);
        SubNet.send(level, SubNet.VANISH, 0, boss.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
    }

    // Both on the boost into each other: the fists meet.
    private void clash(ServerLevel level, LivingEntity target) {
        Vec3 mid = center(boss).add(center(target)).scale(0.5);
        SubCommon.pose(boss, "soco.forte_golpe");
        DbCommon.hit(level, mid, 1.5f, Db.WHITE);
        DbCommon.hit(level, mid, 0.9f, color());
        DbCommon.screen(level, mid, 1);
        DbCommon.screen(level, mid, 4);
        SubCommon.sound(level, mid, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.4f);
        SubCommon.sound(level, mid, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
        Vec3 away = target.position().subtract(boss.position());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        knock(target, away.scale(17), 7, false);
        vel = away.scale(-17);
        knocked = 7;
        boost = false;
        endAct(0.9);
    }

    // ------------------------------------------------------------------ hitting

    private void hitLater(Hit hit, int ticks) {
        pending = hit;
        pendT = ticks;
    }

    private void resolveHit(ServerLevel level, LivingEntity target) {
        if (pending == null) {
            return;
        }
        if (pendT-- > 0) {
            return;
        }
        Hit h = pending;
        pending = null;
        if (center(boss).distanceTo(center(target)) < h.reach()) {
            hurt(level, target, h);
        }
    }

    private void hurt(ServerLevel level, LivingEntity target, Hit h) {
        Vec3 away = target.position().subtract(boss.position());
        away = away.lengthSqr() < 0.01 ? flat(boss.getLookAngle()) : away.normalize();
        Vec3 c = center(target).subtract(away.scale(0.4));
        DbCommon.hit(level, c, h.big() ? 0.85f : 0.45f, color());
        SubCommon.sound(level, c, h.big() ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK, 1f, h.big() ? 0.8f : 1.2f);
        Combat.hurtBasic(boss, target, h.damage());
        if (h.slam()) {
            knock(target, new Vec3(away.x * 4, -40, away.z * 4), 30, true);
            Stun.apply(target, 30);
            DbCommon.screen(level, c, 1);
            slammed = target;
            slamT = 0;
            return;
        }
        if (airborne(target)) {
            knock(target, away.scale(h.knock()).add(0, h.knock() * 0.15, 0), h.finisher() || h.knock() > 10 ? 12 : 4, false);
            Stun.apply(target, h.finisher() || h.knock() > 10 ? 12 : 4);
            if (h.knock() > 10) {
                DbCommon.screen(level, c, 1);
            }
        } else {
            Combat.push(target, away, h.knock() * 0.6 / 20, h.knock() > 8 ? 0.25 : 0);
            Stun.apply(target, h.knock() > 8 ? 10 : 3);
        }
    }

    // Throws the target at `velocity` (blocks per second). A flying player's own client moves them (FlightClient), so
    // they are told to fly off at that speed without control for `ticks`; anyone else is pushed.
    public static void knock(LivingEntity target, Vec3 velocity, int ticks, boolean slam) {
        if (target instanceof ServerPlayer player && Flight.flying(player)) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.FlightKnock(velocity, ticks, slam));
            return;
        }
        target.setDeltaMovement(velocity.scale(DT));
        target.hurtMarked = true;
    }

    // The slammed one hits the ground: a crater, the shake, the second blow, and a player stops flying.
    private void watchSlam(ServerLevel level) {
        if (slammed == null) {
            return;
        }
        slamT++;
        LivingEntity s = slammed;
        if (!s.isAlive() || s.level() != level || slamT > 80) {
            slammed = null;
            return;
        }
        double above = s.getY() - DbCommon.groundY(level, s.position());
        boolean down = s.onGround() || above < 0.6 || s instanceof Player p && !Flight.flying(p) && slamT > 2;
        if (!down) {
            return;
        }
        slammed = null;
        Vec3 feet = s.position();
        if (s instanceof ServerPlayer player && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        DbCommon.hit(level, feet.add(0, 0.3, 0), 1.3f, Db.WHITE);
        DbCommon.screen(level, feet, 4);
        DbCommon.bigCrater(level, feet.add(0, -0.5, 0), 3.5, 2, 4);
        SubCommon.sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 1.3f, 0.8f);
        Combat.hurtBasic(boss, s, 14);
        Stun.apply(s, 25);
    }

    // One ki ball, flown by the server tick by tick along the line the client draws (like Vegeta's Renzoku).
    private void shoot(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = boss.getLookAngle();
        }
        Vec3 path = dir.normalize().scale(Math.max(8, dir.length() + 8));
        int flight = Math.max(2, (int) Math.round(path.length() / (42 * DT)));
        int id = SubNet.newId();
        int color = color();
        SubNet.send(level, SubNet.SHOT, id, from, path, flight, color, 0);
        SubCommon.sound(level, from, SoundEvents.FIRECHARGE_USE, 0.6f, 1.7f);
        SoulBoss owner = boss;
        Tasks.add(age -> {
            if (!owner.isAlive() || age >= flight) {
                return true;
            }
            Vec3 a = from.add(path.scale(age / (double) flight));
            Vec3 b = from.add(path.scale(Math.min(1, (age + 1) / (double) flight)));
            var block = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : b;
            for (LivingEntity e : Combat.alongLine(level, a, end, 0.5, owner)) {
                SubNet.send(level, SubNet.SHOT, id, Combat.center(e), Vec3.ZERO, 0, color, 1);
                Combat.hurtMagic(owner, e, 7);
                Vec3 away = e.position().subtract(owner.position());
                knock(e, (away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize()).scale(5), 4, false);
                Stun.apply(e, 4);
                return true;
            }
            if (wall) {
                SubNet.send(level, SubNet.SHOT, id, end, Vec3.ZERO, 0, color, 1);
                return true;
            }
            return false;
        });
    }

    private int color() {
        return switch (boss.bossId()) {
            case "goku" -> Db.BLUE;
            case "vegeta" -> Db.GOLD;
            case "piccolo" -> Db.GREEN;
            case "kuririn" -> Db.ORANGE;
            case "freeza" -> Db.PINK;
            default -> Db.WHITE;
        };
    }

    // ------------------------------------------------------------------ the clients

    int state() {
        boolean fly = st == St.AR || st == St.MERGULHO;
        return fly ? Flight.FLYING | (boost ? Flight.BOOST : 0) | flags : 0;
    }

    private void send() {
        int state = boss.isAlive() ? state() : 0;
        if (state == sent) {
            return;
        }
        sent = state;
        PacketDistributor.sendToPlayersTrackingEntity(boss, new ModPayloads.FlightState(boss.getId(), state));
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof SoulBoss boss && boss.air != null && boss.air.sent != 0 && event.getEntity() instanceof ServerPlayer viewer) {
            PacketDistributor.sendToPlayer(viewer, new ModPayloads.FlightState(boss.getId(), boss.air.sent));
        }
    }

    // ------------------------------------------------------------------ SkyPassers

    // Starts it passing by (SkyPassers), already in the air at `velocity`.
    public void pass(Vec3 velocity, boolean boost, int flags) {
        passer = true;
        st = St.AR;
        vel = velocity;
        this.boost = boost;
        this.flags = flags;
    }

    public boolean passing() {
        return passer;
    }

    public static boolean passing(SoulBoss boss) {
        return boss.air != null && boss.air.passer;
    }

    public void steerTo(Vec3 wish, double top, double k) {
        steer(wish, top, k);
    }

    public void boost(boolean on) {
        boost = on;
    }

    public void flags(int flags) {
        this.flags = flags;
    }

    public void velocity(Vec3 velocity) {
        vel = velocity;
    }

    public Vec3 velocity() {
        return vel;
    }

    // From passing by to the fight (Vegeta's challenge, or hit by the player): it is already in the air.
    public void fight(LivingEntity target) {
        passer = false;
        flags = 0;
        st = St.AR;
        act = Act.NONE;
        cd = 0.9;
        boost = false;
        boss.setTarget(target);
    }
}
