package com.starkinhon.battlegroundsmod.boss;

import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.AirFight;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Intangible;
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
    // Bills too (Stark 2026-11-13: "o Bills também faz isso"), the only boss among them.
    public static final Set<String> FLIERS = Set.of("goku", "vegeta", "piccolo", "kuririn", "freeza", "bills");
    private static final double DT = 0.05;

    enum St { CHAO, DECOLA, AR, MERGULHO }

    // RECUA: off to a distance, then the long-range beam (the combat flight's prévia v2: Vegeta backs off for the Final Flash)
    enum Act { NONE, INVESTIDA, MARTELO, RAJADA, SUMICO, PERSEGUE, CHOQUE, RECUA }

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
    // whoever it fought last tick (see step)
    private LivingEntity chase;
    // flown by SkyPassers (not fighting): extra pose flags for the clients
    boolean passer;
    int flags;
    private int sent;

    // the beam: where it aims (locked 0.35 s before it fires), its stream ids, whether it hit already
    private Vec3 aim;
    private int ballId, beamId;
    private boolean beamHit;
    private double beamCd = 3;
    // the run's closest distance and its start (the page's AI.minD / AI.d0)
    private double minD, d0;
    // the guard held up against a dash coming, ticks left
    private int guardT;
    // thrown straight down by the Martelo (combat/AirFight.slam): no control until the ground
    private boolean falling;
    // whom it faces in the air (the stance looks at the target, not along the way it flies)
    private LivingEntity facing;

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
                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Golpes: investida, martelo, rajada, sumico, persegue, choque, recua"));
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
        return FLIERS.contains(boss.bossId()) && (!boss.isBoss() || "bills".equals(boss.bossId()));
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
        // In the air it keeps after whoever it fights even past its sight (the target goals let go at 32-48 blocks, and
        // it landed far off instead of catching a player fleeing on the boost, in the second test).
        if (target == null && st != St.CHAO && !passer && chase != null && chase.isAlive() && chase.level() == level
                && chase.distanceToSqr(boss) < 160 * 160 && !(chase instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            target = chase;
            boss.setTarget(chase);
        }
        if (target != null && (!target.isAlive() || target.level() != level || target.distanceToSqr(boss) > 160 * 160)) {
            target = null;
        }
        chase = target;
        trackTarget(target);
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
        airT = up ? airT + DT : 0;
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
        if (falling) {
            // slammed by the Martelo: straight into the ground, and there it stops flying
            double above = boss.getY() - DbCommon.groundY(level, boss.position());
            if (boss.onGround() || above < 0.6 || knocked <= 0) {
                falling = false;
                knocked = 0;
                land(level, false);
                send();
                return;
            }
            knocked--;
            vel = new Vec3(0, -42, 0);
            lastSet = vel.scale(DT);
            boss.setDeltaMovement(lastSet);
            send();
            return;
        }
        if (guardT > 0 && --guardT == 0) {
            Defense.stop(boss);
        }
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
        facing = target;
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
        boolean stance = facing != null && facing.isAlive() && st == St.AR && !passer && act != Act.PERSEGUE
                && !(act == Act.RECUA && phase == 0);
        if (stance) {
            // the fighting stance: always facing the target (it circles round it, it guards facing it)
            Vec3 to = facing.position().subtract(boss.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            boss.setYRot(yaw);
            boss.yBodyRot = yaw;
            boss.setYHeadRot(yaw);
        } else if (h > 1.5) {
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
        return (boosting(target) || AirFight.dashing(target)) && tVel.length() > 15 && tVel.normalize().dot(toP.scale(-1 / Math.max(1.0E-4, dist))) > 0.5 && dist < 18;
    }

    private void tactics(ServerLevel level, LivingEntity target, Vec3 toP, double dist) {
        at += DT;
        Vec3 pc = center(target), mc = center(boss);
        beamCd -= DT;
        // (the page's mob never guards the dash: removed, Stark asked for the page exactly, 2026-11-13)
        if (act == Act.NONE && guardT > 0) {
            vel = vel.scale(1 - damp(6));
            return;
        }
        if (act == Act.NONE) {
            boost = false;
            if (comingAtMe(target, toP, dist) && !AirFight.dashing(target)) {
                start(Act.CHOQUE);
            } else if (dist > 26) {
                start(Act.PERSEGUE);
            } else {
                // the page's combat scene (airTactics, sceneKey 'combate'): round the target at 9 +- 3.5 blocks,
                // 1.5 +- 2.5 over it, at 16 b/s; then 30% Investida, 20% Rajada, 14% Sumiço, 36% Recua (the beam)
                orbit += DT * 0.55;
                double rad = 9 + Math.sin(t * 0.7) * 3.5;
                Vec3 want = target.position().add(Math.cos(orbit) * rad, 1.5 + Math.sin(t * 0.9) * 2.5, Math.sin(orbit) * rad);
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
                    start(r < 0.3 ? Act.INVESTIDA : r < 0.5 ? Act.RAJADA : r < 0.64 ? Act.SUMICO : Act.RECUA);
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
            case RECUA -> beam(level, target, toP, dist, pc, mc);
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
            // Its run and the player's ki dash into each other: the exchange. Checked here, before its own hit range
            // (1.8 + the closing speed, about 7 blocks at 110 b/s), which used to turn the meeting into its plain combo
            // (Stark's first play: both dashed and no exchange).
            if (AirFight.dashing(target) && dist < 10 && !AirFight.exchanging(target)) {
                clash(level, target);
                return;
            }
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
            // the page: it went straight past (dodged): brakes and faces again, no flying on to the ground
            if (at < DT * 1.5) {
                minD = d0 = dist;
            } else {
                minD = Math.min(minD, dist);
            }
            if (at > 0.3 && minD < d0 - 2 && dist > minD + 3) {
                vel = vel.scale(0.35);
                endAct(0.6);
                return;
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
            if (above > 5 && boss.getRandom().nextDouble() < 0.7 && Stun.is(target)) {
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
        if (target instanceof Player || target instanceof SoulBoss) {
            // the punch exchange across the sky (combat/AirFight)
            boost = false;
            endAct(1.2);
            AirFight.exchange(level, boss, target);
            return;
        }
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
        if (AirFight.dodging(target) || Intangible.untouchableBy(target, boss)) {
            // dodged (the combat flight's iframes): the blow goes through air
            return;
        }
        if ((act == Act.INVESTIDA || act == Act.MARTELO || h.slam()) && AirFight.guardPunish(level, target, boss, h.slam() ? "martelo" : "investida")) {
            // guarded: it is left open (stunned and pushed back)
            Combat.hurtBasic(boss, target, h.damage() * 0.15f);
            pending = null;
            endAct(1.0);
            return;
        }
        Vec3 away = target.position().subtract(boss.position());
        away = away.lengthSqr() < 0.01 ? flat(boss.getLookAngle()) : away.normalize();
        Vec3 c = center(target).subtract(away.scale(0.4));
        DbCommon.hit(level, c, h.big() ? 0.85f : 0.45f, color());
        SubCommon.sound(level, c, h.big() ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK, 1f, h.big() ? 0.8f : 1.2f);
        Combat.hurtBasic(boss, target, h.damage());
        if (h.slam()) {
            DbCommon.screen(level, c, 1);
            AirFight.slam(level, boss, target);
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

    // One ki ball, flown by the server tick by tick along the line the client draws (like Vegeta's Renzoku).
    private void shoot(ServerLevel level, Vec3 from, Vec3 to) {
        LivingEntity target = boss.getTarget();
        if (target != null) {
            // homing a little (dodgeable), and the guard sends it up, or back on a parry (AirFight.kiBall)
            AirFight.kiBall(level, boss, from, to.subtract(from), target, color(), 42, 0, 7, false); // the page: straight, aimed ahead
            return;
        }
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

    public int kiColor() {
        return color();
    }

    private int color() {
        return switch (boss.bossId()) {
            case "bills" -> Db.PURPLE;
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
        boolean stance = st == St.AR && !passer && act != Act.PERSEGUE && !(act == Act.RECUA && phase == 0) && boss.getTarget() != null;
        return fly ? Flight.FLYING | (boost ? Flight.BOOST : 0) | (stance ? Flight.COMBAT : 0) | flags : 0;
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

    // ------------------------------------------------------------------ the beam (RECUA)

    // Off to ~34 blocks and up, then the charge (1.05 s, the ball growing in the hands), the aim locks 0.35 s before it
    // fires (a white flash: the moment to dodge), and the beam: 80 blocks, one big hit; a dodge (iframes) goes through.
    private void beam(ServerLevel level, LivingEntity target, Vec3 toP, double dist, Vec3 pc, Vec3 mc) {
        int color = color();
        float width = beamWidth();
        if (phase == 0) {
            boost = true;
            Vec3 away = flat(toP.scale(-1));
            Vec3 spot = target.position().add(away.scale(34)).add(0, 8, 0);
            double ground = DbCommon.groundY(level, spot);
            spot = new Vec3(spot.x, Math.max(spot.y, ground + 6), spot.z);
            steer(spot.subtract(boss.position()).normalize(), 34, 6);
            if (dist > 27 || at > 2.4) {
                phase = 1;
                at = 0;
                boost = false;
                ballId = SubNet.newId();
                beamId = SubNet.newId();
                aim = null;
                beamHit = false;
                SubCommon.pose(boss, "voo.feixe");
                // the charge lights its aura in its own color
                SubNet.send(level, SubNet.KI_AURA, boss.getId(), boss.position(), new Vec3(0.7, 0, 0), 46, auraColor(), 1.15f);
                SubCommon.sound(level, boss.position(), SoundEvents.BEACON_ACTIVATE, 1.4f, 1.3f);
            }
            return;
        }
        Vec3 hands = beamHands(target);
        if (phase == 1) {
            vel = vel.scale(1 - damp(6));
            float k = (float) Math.min(1, at / 1.05);
            SubNet.send(level, SubNet.KI_BALL, ballId, hands, Vec3.ZERO, (0.12f + 1.1f * k * k) * width / 1.15f * 0.8f + 0.06f, color + 16, 0);
            if (boss.tickCount % 6 == 0) {
                SubCommon.sound(level, hands, SoundEvents.BEACON_AMBIENT, 1.2f, 0.8f + k);
            }
            if (aim == null && at >= 0.7) {
                // the aim locks: from here it fires where the target WAS (a dodge now makes it miss)
                aim = pc;
                DbCommon.hit(level, hands, 0.9f, Db.WHITE);
                SubCommon.sound(level, hands, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 1.8f);
                SubCommon.sound(level, hands, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.5f);
            }
            if (at >= 1.05) {
                SubNet.send(level, SubNet.KI_BALL, ballId, hands, Vec3.ZERO, 0, color, 1);
                phase = 2;
                at = 0;
                SubCommon.pose(boss, "voo.feixe_solta");
                DbCommon.screen(level, hands, 45);
                SubCommon.sound(level, hands, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.2f);
                SubCommon.sound(level, hands, SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.7f);
            }
            return;
        }
        // phase 2: the beam
        vel = vel.scale(1 - damp(8)).add(aimDir(hands).scale(-0.6));
        boolean end = at >= 1.05;
        Vec3 dir = aimDir(hands);
        Vec3 far = DbCommon.beamEnd(boss, level, hands, dir, 80, width, true);
        Vec3 to = hands.add(far.subtract(hands).scale(Math.min(1, at / 0.22)));
        SubNet.send(level, SubNet.KI_BEAM, beamId, hands, to.subtract(hands), width, color, end ? 1 : 0);
        if (!beamHit) {
            for (LivingEntity e : Combat.alongLine(level, hands, to, width * 0.9, boss)) {
                if (Intangible.untouchableBy(e, boss) || AirFight.dodging(e)) {
                    continue;
                }
                beamHit = true;
                Vec3 c = Combat.center(e);
                if (Defense.check(e, boss) != Defense.Result.NONE) {
                    // guarded: chip damage, shoved far back
                    Combat.hurtMagic(boss, e, 4);
                    AirFight.throwAway(e, dir.scale(16), 8);
                    DbCommon.hit(level, c, 1.0f, color);
                } else {
                    Combat.hurtMagic(boss, e, 24);
                    AirFight.throwAway(e, dir.scale(30).add(0, 3, 0), 14);
                    Stun.apply(e, 14);
                    DbCommon.hit(level, c, 1.6f, color);
                    DbCommon.screen(level, c, 1);
                }
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.3f, 0.9f);
                break;
            }
        }
        if (boss.tickCount % 6 == 0) {
            SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.6f);
        }
        if (end) {
            DbCommon.boom(level, to, 3.2f, color, 4);
            beamCd = 13 + boss.getRandom().nextDouble() * 6;
            endAct(1.2);
        }
    }

    private Vec3 aimDir(Vec3 hands) {
        Vec3 d = (aim == null ? hands.add(boss.getLookAngle()) : aim).subtract(hands);
        return d.lengthSqr() < 1.0E-4 ? boss.getLookAngle() : d.normalize();
    }

    private Vec3 beamHands(LivingEntity target) {
        Vec3 look = (aim != null ? aim : Combat.center(target)).subtract(center(boss));
        look = look.lengthSqr() < 1.0E-4 ? boss.getLookAngle() : look.normalize();
        return center(boss).add(0, 0.25, 0).add(look.scale(0.8));
    }

    // Each one's aura (Stark 2026-11-13): Goku light, Vegeta purple, Freeza pink, Piccolo and Kuririn white, Bills purple.
    private int auraColor() {
        return switch (boss.bossId()) {
            case "goku" -> Db.KAME;
            case "vegeta", "bills" -> Db.PURPLE;
            case "freeza" -> Db.PINK;
            default -> Db.WHITE;
        };
    }

    // Final Flash and Kamehameha wide, the Makankosappo and the Death Beam thin.
    private float beamWidth() {
        return switch (boss.bossId()) {
            case "vegeta" -> 1.25f;
            case "goku" -> 1.1f;
            case "piccolo" -> 0.45f;
            case "freeza" -> 0.32f;
            case "kuririn" -> 0.8f;
            default -> 0.95f;
        };
    }

    // ------------------------------------------------------------------ the player's combat flight (combat/AirFight)

    // The player started a ki dash at it: it answers with its own, they meet halfway (the exchange).
    public void answerDash(LivingEntity player) {
        if (st == St.AR && !Stun.is(boss) && (act == Act.NONE || act == Act.RAJADA) && boss.getTarget() == player) {
            start(Act.CHOQUE);
        }
    }

    // On its own boost straight at `who` (the exchange starts when the player dashes into that).
    public boolean rushingAt(LivingEntity who) {
        return st == St.AR && boss.getTarget() == who && boost && phase == 0
                && (act == Act.INVESTIDA || act == Act.PERSEGUE || act == Act.CHOQUE);
    }

    // Zanzoken away from `from` (a dash about to land, a long combo): gone, and back aside and above. False when it can't.
    public boolean zanzoken(LivingEntity from) {
        if (st != St.AR || Stun.real(boss) || !(boss.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 to = boss.position().subtract(from.position());
        Vec3 f = flat(to);
        Vec3 side = new Vec3(-f.z, 0, f.x).scale(boss.getRandom().nextBoolean() ? 1 : -1);
        vanish(level, boss.position());
        appear(level, boss.position().add(side.scale(4.5)).add(f.scale(1.5)).add(0, 1.5, 0));
        act = Act.NONE;
        pending = null;
        knocked = 0;
        cd = 0.6;
        return true;
    }

    // How many air combo hits in a row it has taken: a long combo, it slips out (Zanzoken) instead of eating all of it.
    public void comboTaken(int hits) {
        if (hits >= 8) {
            LivingEntity target = boss.getTarget();
            if (target != null && zanzoken(target)) {
                cd = 0.3;
            }
        }
    }

    // Slammed down (the Martelo, a grab): straight into the ground, it lands there (AirFight.slam opens the crater).
    public void slammed() {
        falling = true;
        knocked = 60;
        act = Act.NONE;
        pending = null;
        boost = false;
        boss.setInvisible(false);
    }

    // Held still for the exchange (AirFight moves it from clash to clash).
    public void hold(int ticks) {
        thrown(Vec3.ZERO, ticks);
    }

    // Thrown at `velocity` without control for `ticks`.
    public void thrown(Vec3 velocity, int ticks) {
        vel = velocity;
        knocked = Math.max(knocked, ticks);
        act = Act.NONE;
        pending = null;
        boost = false;
        lastSet = vel.scale(DT);
        boss.setDeltaMovement(lastSet);
        boss.setInvisible(false);
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
