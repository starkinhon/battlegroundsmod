package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.RoxoSpec;
import com.starkinhon.battlegroundsmod.skill.VermelhoSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Red (Vermelho), Gojo's X (gojo.vermelho.json, see VermelhoSpec): the reverse of the Blue, a massive repulsion.
// The red orb lights up at his fingertip at once, only sparks leaking out of it. Let go within segurado.decidirTicks it
// fires at the lightest charge; held it charges (past segurado.cargaRabos its tails form and spin around it) and fires
// only when the key is let go. Firing, the orb bursts into a wall of repulsion straight where he aims: a cylinder that
// carves a tunnel through the ground and walls (with the gamerule soulsBlockDestruction; without it, it stops at the
// first wall) and throws whoever it reaches far away. The charge makes it much longer and wider.
// Shift + X: the move he used on Toji. He appears behind the target he aims at, floating upside down, raises the
// finger and fires it point blank.
// The orb and the blast are drawn on the clients (client/fx/VermelhoRenderer): the orb from what is sent here every
// tick (the clients put it on his hand themselves), the blast from one message when it fires.
public final class Vermelho {
    public static final String SKILL = VermelhoSpec.SKILL;
    // The finger raised for as long as it is held (a pose with a very long wind-up, stopped when it fires).
    public static final String HOLD_ANIMATION = "gojo.vermelho_segurando";
    // The Ability's cooldown is the tap's (Stark 2026-11-09, balance against Sukuna: 7 s -> 5 s, so Gojo has an answer at
    // range); the held one keeps its 7 s, counted from the press as before (HELD_COOLDOWN, raised when it fires).
    public static final int COOLDOWN = 100, HELD_COOLDOWN = 140;
    public static final int SHIFT_COOLDOWN = 300;
    private static final double VIEW_DISTANCE = 160;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    public static final int ALIVE = 0;
    public static final int FIRED = 1;
    public static final int GONE = 2;
    private static int nextId;

    private Vermelho() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        VermelhoSpec spec = VermelhoSpec.get(awakened);
        VermelhoSpec.Segurado segurado = spec.segurado();
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;
        int decide = Math.max(1, segurado.decidirTicks());
        int chargeTicks = Math.max(1, segurado.carregarTicks());
        // Bosses don't hold keys: half the time a tap, else a charge of their own length.
        // A boss with tactics (the Gojo boss) says how this time (BossOrder); aimAtBlue = into his own parked Blue.
        BossOrder.Order order = BossOrder.take(caster);
        boolean bossHolds = order != null ? order.hold() : RANDOM.nextBoolean();
        int bossCharge = order != null ? Math.max(1, (int) Math.ceil(chargeTicks * Mth.clamp(order.charge(), 0, 1)) + 1)
                : Math.min(Math.max(chargeTicks, segurado.maxSegurarTicks()), chargeTicks / 3 + RANDOM.nextInt(chargeTicks + 1));
        boolean atBlue = order != null && order.aimAtBlue();
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : fingertip(caster);

        double[] charge = {0};
        boolean[] charging = {false};
        boolean[] tails = {false};
        int[] chargeStart = {0};
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), HOLD_ANIMATION, awakened));
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", fingertip(caster), caster.getLookAngle());
        data.busyTicks = decide + 2;
        send(level, caster, id, ALIVE, 0);

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts) {
                // Cut short: it fades without firing.
                send(level, caster, id, GONE, (float) charge[0]);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            boolean held = caster instanceof Player ? com.starkinhon.battlegroundsmod.combat.Stamina.holding(caster) : bossHolds && age < decide + bossCharge;
            now.busyTicks = Math.max(now.busyTicks, 2);
            if (!charging[0]) {
                if (age + 1 < decide) {
                    send(level, caster, id, ALIVE, 0);
                    return false;
                }
                if (!held) {
                    fire(level, caster, id, spec, awakened, 0, caster.getLookAngle(), null, screen);
                    return true;
                }
                charging[0] = true;
                chargeStart[0] = age + 1;
                SkillSounds.play(level, spec.sons(), "carregar", 0, chargeTicks, where, screen);
                Fx.effects(level, SKILL, awakened, "carregar", fingertip(caster), caster.getLookAngle());
            }
            charge[0] = Math.min(1, (age + 1 - chargeStart[0]) / (double) chargeTicks);
            if (held) {
                com.starkinhon.battlegroundsmod.combat.Charge.report(caster, charge[0]);
            }
            if (!tails[0] && charge[0] >= segurado.cargaRabos()) {
                tails[0] = true;
                SkillSounds.play(level, spec.sons(), "rabos", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "rabos", fingertip(caster), caster.getLookAngle());
            }
            // Players let it go only when they let go of X, however long that is; bosses after their charge.
            if (!held) {
                if (!now.noCooldown) {
                    now.cooldowns[1] = Math.max(now.cooldowns[1], HELD_COOLDOWN - age);
                    if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.starkinhon.battlegroundsmod.power.PowerLogic.sync(player);
                    }
                }
                fire(level, caster, id, spec, awakened, charge[0], atBlue ? aimAtBlue(caster, awakened) : aim(caster), null, screen);
                return true;
            }
            send(level, caster, id, ALIVE, (float) charge[0]);
            return false;
        });
        return true;
    }

    // Shift + X: behind the target, the finger raised, point blank.
    public static boolean useShift(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        VermelhoSpec spec = VermelhoSpec.get(awakened);
        VermelhoSpec.Shift shift = spec.shift();
        LivingEntity target = caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() ? mob.getTarget()
                : Combat.raycastTarget(caster, shift.alcanceAlvo());
        if (target == null || target.distanceTo(caster) > shift.alcanceAlvo() + 2) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.vermelho_no_target"), true);
            }
            return false;
        }
        Vec3 spot = behind(caster, target, shift);
        if (spot == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.vermelho_no_target"), true);
            }
            return false;
        }
        int id = nextId++;
        String power = data.power;
        int prep = Math.max(2, shift.preparoTicks());
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : fingertip(caster);
        // No trail, no smoke: he simply is somewhere else.
        Vec3 face = Combat.center(target).subtract(spot.add(0, caster.getEyeHeight(), 0));
        float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(face.y, Math.sqrt(face.x * face.x + face.z * face.z)) * Mth.RAD_TO_DEG);
        if (caster instanceof ServerPlayer player) {
            player.connection.teleport(spot.x, spot.y, spot.z, yaw, pitch);
        } else {
            caster.teleportTo(spot.x, spot.y, spot.z);
            caster.setYRot(yaw);
            caster.setYHeadRot(yaw);
            caster.setXRot(pitch);
        }
        boolean gravity = caster.isNoGravity();
        caster.setNoGravity(true);
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), HOLD_ANIMATION, awakened));
        SkillSounds.play(level, spec.sons(), "teleporte", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "teleporte", spot.add(0, 1, 0), caster.getLookAngle());
        data.busyTicks = prep + 4;
        send(level, caster, id, ALIVE, 0, true);

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power)) {
                send(level, caster, id, GONE, 0, true);
                caster.setNoGravity(gravity);
                return true;
            }
            // Floating still, relaxed.
            caster.setDeltaMovement(Vec3.ZERO);
            caster.hurtMarked = true;
            double c = shift.carga() * Math.min(1, (age + 1) / (double) prep);
            if (age + 1 < prep) {
                send(level, caster, id, ALIVE, (float) c, true);
                return false;
            }
            caster.setNoGravity(gravity);
            Vec3 dir = target.isAlive() ? Combat.center(target).subtract(upsideDownTip(caster)).normalize() : caster.getLookAngle();
            fire(level, caster, id, spec, awakened, shift.carga(), dir, shift, screen);
            return true;
        });
        return true;
    }

    // Fires: the orb bursts into the wall of repulsion, a tunnel growing from his fingertip along dir.
    private static void fire(ServerLevel level, LivingEntity caster, int id, VermelhoSpec spec, boolean awakened, double c, Vec3 dir,
            VermelhoSpec.Shift shift, List<ServerPlayer> screen) {
        VermelhoSpec.Tiro tiro = spec.tiro();
        // Shift: he hangs upside down, so it leaves his finger, low.
        Vec3 tip = shift != null ? upsideDownTip(caster) : fingertip(caster);
        Vec3 from = shift != null ? tip : caster.getEyePosition().add(dir.scale(0.6)).add(0, -0.2, 0);
        double range = Mth.lerp(c, tiro.alcance(), tiro.alcanceMax());
        double radius = Mth.lerp(c, tiro.raio(), tiro.raioMax());
        double speed = Math.max(0.5, Mth.lerp(c, tiro.velocidade(), tiro.velocidadeMax()));
        float damage = shift != null ? shift.dano() : Mth.lerp((float) c, tiro.dano(), tiro.danoMax());
        double throwSpeed = shift != null ? shift.empurrao() : Mth.lerp(c, tiro.empurrao(), tiro.empurraoMax());
        boolean breaks = BlockRules.canBreak(level);
        if (!breaks) {
            // It can't carve the world: it stops on the first wall.
            var hit = level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.MISS) {
                range = Math.max(2, hit.getLocation().distanceTo(from));
            }
        }
        // Fired at full charge into his own Blue parked at full charge: the tunnel stops at the Blue and both implode into
        // the Unlimited Purple (Roxo.irrestrito) when the front gets there.
        RoxoSpec roxo = RoxoSpec.get(awakened);
        double irrMin = roxo.irrestrito() != null ? roxo.irrestrito().cargaMin() : 2;
        Azul.Live azul = shift == null && c >= irrMin ? Azul.claimParked(caster, from, dir, range, radius, irrMin) : null;
        if (azul != null) {
            range = Math.max(1, azul.pos().subtract(from).dot(dir) - azul.radius() * 0.5);
        }
        double length = range;
        caster.swing(InteractionHand.MAIN_HAND, true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        send(level, caster, id, FIRED, (float) c, shift != null);
        ModPayloads.VermelhoBlast blast = new ModPayloads.VermelhoBlast(from, dir, (float) length, (float) radius, (float) speed, (float) c);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(from) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, blast);
            }
        }
        Vec3 end = from.add(dir.scale(length));
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : "impacto".equals(onde) ? end : tip;
        SkillSounds.play(level, spec.sons(), "disparo", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "disparo", tip, dir);
        if (c >= spec.segurado().cargaRabos() || shift != null) {
            SkillSounds.play(level, spec.sons(), "disparoForte", 0, 0, where, screen);
            Fx.effects(level, SKILL, awakened, "disparoForte", tip, dir);
        }

        Set<Integer> hitIds = new HashSet<>();
        double[] front = {0};
        int[] debris = {0};
        Tasks.add(age -> {
            double a = front[0];
            double b = Math.min(length, a + speed);
            front[0] = b;
            Vec3 pa = from.add(dir.scale(a));
            Vec3 pb = from.add(dir.scale(b));
            // Whoever the front reaches is thrown along it.
            for (LivingEntity e : Combat.alongLine(level, pa, pb, radius, caster)) {
                if (!hitIds.add(e.getId())) {
                    continue;
                }
                Combat.hurt(caster, e, damage);
                if (shift != null) {
                    // The point blank one catches them off guard: stunned while they fly.
                    Stun.apply(e, shift.stunTicks());
                }
                Vec3 v = dir.scale(throwSpeed).add(0, tiro.subir() * throwSpeed * 0.25, 0);
                e.setDeltaMovement(v);
                e.hurtMarked = true;
                Fx.effects(level, SKILL, awakened, "acerto", Combat.center(e), dir);
                SkillSounds.play(level, spec.sons(), "acerto", 0, 0, onde -> Combat.center(e), screen);
            }
            // The repulsion throws everything around the path away from it too: whoever is just outside the tunnel is
            // shoved aside and forward, and loose things (items, debris, arrows) fly off with the front.
            double band = radius * 2.2;
            for (Entity e : level.getEntities(caster, new AABB(pa, pb).inflate(band), x -> !(x instanceof Player p && p.isSpectator()))) {
                if (hitIds.contains(e.getId())) {
                    continue;
                }
                Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(from);
                double t = rel.dot(dir);
                if (t < a - radius || t > b + radius) {
                    continue;
                }
                Vec3 off = rel.subtract(dir.scale(t));
                double d = off.length();
                if (d > band) {
                    continue;
                }
                hitIds.add(e.getId());
                double k = 1 - d / band;
                Vec3 out = d < 1.0E-3 ? Vec3.ZERO : off.scale(1 / d);
                Vec3 v = out.scale(throwSpeed * (0.35 + 0.5 * k)).add(dir.scale(throwSpeed * 0.6 * k)).add(0, 0.3 + 0.4 * k, 0);
                if (e instanceof LivingEntity living) {
                    if (!Combat.isTarget(living, caster)) {
                        continue;
                    }
                    Combat.hurt(caster, living, damage * 0.35f);
                }
                e.setDeltaMovement(v);
                e.hurtMarked = true;
            }
            if (breaks) {
                carve(level, caster, from, dir, Math.max(1.2, a), b, radius, tiro, debris);
            }
            if (b >= length && azul != null && azul.alive()) {
                azul.consume();
                Roxo.irrestrito(level, caster, azul.pos(), awakened);
                return true;
            }
            if (b >= length) {
                SkillSounds.play(level, spec.sons(), "impacto", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "impacto", end, dir);
                return true;
            }
            return false;
        });
    }

    // Carves the cylinder between a and b blocks along dir: every block whose middle is within radius of the line (its
    // edge a little ragged), up to tiro.blocosPorTick. Some fly off to the sides as debris (the funnel of the blast).
    private static void carve(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir, double a, double b, double radius, VermelhoSpec.Tiro tiro,
            int[] debris) {
        if (b <= a) {
            return;
        }
        Vec3 pa = from.add(dir.scale(a));
        Vec3 pb = from.add(dir.scale(b));
        int minX = Mth.floor(Math.min(pa.x, pb.x) - radius), maxX = Mth.floor(Math.max(pa.x, pb.x) + radius);
        int minY = Mth.floor(Math.min(pa.y, pb.y) - radius), maxY = Mth.floor(Math.max(pa.y, pb.y) + radius);
        int minZ = Mth.floor(Math.min(pa.z, pb.z) - radius), maxZ = Mth.floor(Math.max(pa.z, pb.z) + radius);
        int budget = Math.max(1, tiro.blocosPorTick());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX && budget > 0; x++) {
            for (int y = minY; y <= maxY && budget > 0; y++) {
                for (int z = minZ; z <= maxZ && budget > 0; z++) {
                    Vec3 mid = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    Vec3 rel = mid.subtract(from);
                    double t = rel.dot(dir);
                    if (t < a || t >= b) {
                        continue;
                    }
                    double off = rel.subtract(dir.scale(t)).length();
                    double edge = radius * (0.88 + 0.24 * hash(x, y, z));
                    if (off > edge) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                        continue;
                    }
                    budget--;
                    if (debris[0] < tiro.pedacos() && RANDOM.nextInt(18) == 0) {
                        debris[0]++;
                        FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
                        block.dropItem = false;
                        Vec3 side = rel.subtract(dir.scale(t));
                        side = side.lengthSqr() < 1.0E-4 ? new Vec3(RANDOM.nextDouble() - 0.5, 0.5, RANDOM.nextDouble() - 0.5) : side.normalize();
                        block.setDeltaMovement(side.scale(0.6 + 0.6 * RANDOM.nextDouble()).add(dir.scale(0.5)).add(0, 0.3, 0));
                        block.hurtMarked = true;
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // Where he appears for the Shift: behind the target (or to its sides, or in front, when that is blocked), floating.
    private static Vec3 behind(LivingEntity caster, LivingEntity target, VermelhoSpec.Shift shift) {
        Vec3 back = Combat.flatLook(target).reverse();
        for (double turn : new double[] {0, 40, -40, 80, -80, 180}) {
            Vec3 spot = target.position().add(back.yRot((float) Math.toRadians(turn)).scale(shift.atras())).add(0, shift.flutuar(), 0);
            if (caster.level().noCollision(caster, caster.getBoundingBox().move(spot.subtract(caster.position())))) {
                return spot;
            }
        }
        return null;
    }

    // Players aim with their eyes; bosses at their target.
    private static Vec3 aim(LivingEntity caster) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            return Combat.center(mob.getTarget()).subtract(caster.getEyePosition()).normalize();
        }
        return caster.getLookAngle();
    }

    // A boss firing the Unlimited Purple: straight at his own Blue parked at full charge (from where fire starts the
    // tunnel), so the Red runs into it. No such Blue: at the target as usual.
    private static Vec3 aimAtBlue(LivingEntity caster, boolean awakened) {
        RoxoSpec roxo = RoxoSpec.get(awakened);
        double min = roxo.irrestrito() != null ? roxo.irrestrito().cargaMin() : 2;
        Azul.Live blue = Azul.parkedBlue(caster, min);
        if (blue == null) {
            return aim(caster);
        }
        Vec3 to = blue.pos().subtract(caster.getEyePosition().add(0, -0.2, 0));
        return to.lengthSqr() > 1.0E-4 ? to.normalize() : aim(caster);
    }

    // His fingertip at the end of the outstretched arm (gojo.vermelho_segurando: Red side forward, body on a diagonal).
    // The clients work out their own the same way (VermelhoRenderer.fingertip).
    static Vec3 fingertip(LivingEntity caster) {
        Vec3 forward = Combat.flatLook(caster);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double scale = caster.getBbHeight() / 1.8;
        return caster.position().add(forward.scale(0.93 * scale)).add(right.scale(0.27 * scale)).add(0, 1.37 * scale, 0);
    }

    // The fingertip while he hangs upside down (Shift): his body turned about its middle, so the right hand is on the
    // other side and low (the clients draw it the same way).
    static Vec3 upsideDownTip(LivingEntity caster) {
        Vec3 forward = Combat.flatLook(caster);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double scale = caster.getBbHeight() / 1.8;
        return caster.position().add(forward.scale(0.93 * scale)).add(right.scale(-0.27 * scale)).add(0, caster.getBbHeight() - 1.37 * scale, 0);
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static void send(ServerLevel level, LivingEntity caster, int id, int mode, float charge) {
        send(level, caster, id, mode, charge, false);
    }

    private static void send(ServerLevel level, LivingEntity caster, int id, int mode, float charge, boolean flip) {
        ModPayloads.Vermelho payload = new ModPayloads.Vermelho(id, caster.getId(), mode, charge, flip);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(caster) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
