package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.DestructionEnergySpec;
import com.starkinhon.battlegroundsmod.skill.DestructionRainSpec;
import com.starkinhon.battlegroundsmod.skill.Som;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Destruction Energy, Bills' Z (bills.destruction_energy.json) and Shift + Z (bills.destruction_energy_chuva.json).
// Z: a ball of light with rings of energy turning around it forms over his raised hand while the key is held and is
// thrown when it comes up; it flies fast and homes in on the target (or follows his crosshair until it finds one) and
// blows up with a crater. Shift + Z: he rises over the ground, untouchable, balls of the same energy come out of his
// body on every side and are shot one after another at the target, then he glides down. The balls are drawn on the
// clients from the position sent every tick (client/fx/EnergyRenderer); a hakaizinho can erase the ones in flight.
public final class DestructionEnergy {
    public static final String SKILL = DestructionEnergySpec.SKILL;
    public static final String RAIN = DestructionRainSpec.SKILL;
    private static final double VIEW_DISTANCE = 160;
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextId;

    private DestructionEnergy() {
    }

    // How a ball flies once it is shot, from either file.
    private record Flight(String skill, boolean awakened, double radius, double speed, double turn, double reach, double detect,
            DestructionEnergySpec.Explosao explosao, List<Som> sons, boolean small) {
    }

    // ---------- Z ----------

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        DestructionEnergySpec spec = DestructionEnergySpec.get(awakened);
        DestructionEnergySpec.Carga carga = spec.carga();
        DestructionEnergySpec.Bola bola = spec.bola();
        int form = Math.max(1, carga.formarTicks());
        int full = Math.max(form, carga.maxTicks());
        // Bosses don't hold keys: they let go once the ball is formed, a little later or sooner each time.
        int bossCharge = form + RANDOM.nextInt(Math.max(1, form));
        double radius = Math.max(0.2, bola.raio());
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;

        data.busyTicks = full + 4;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Vec3[] pos = {overHand(caster, carga, radius)};
        Function<String, Vec3> where = onde -> "bola".equals(onde) ? pos[0] : caster.position().add(0, 1, 0);
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        SkillSounds.play(level, spec.sons(), "uso", 0, full, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster, form);

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts) {
                send(level, id, 2, pos[0], 0, 0, false);
                return true;
            }
            pos[0] = overHand(caster, carga, radius);
            double k = Math.min(1, (age + 1) / (double) form);
            send(level, id, 0, pos[0], (float) (radius * (0.2 + 0.8 * smooth(k))), (float) k, false);
            now.busyTicks = Math.max(now.busyTicks, 2);
            boolean letGo = caster instanceof Player ? !now.abilityHeld : age >= bossCharge;
            if (age + 1 < full && !(letGo && age + 1 >= Math.max(1, carga.minTicks()))) {
                return false;
            }
            // Thrown: the hand comes down and the ball goes.
            now.busyTicks = 4;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
            caster.swing(InteractionHand.MAIN_HAND, true);
            LivingEntity target = lock(caster, spec.alvo().alcance());
            Vec3 aim = target != null ? Combat.center(target) : aimPoint(caster, bola.alcance());
            Vec3 dir = aim.subtract(pos[0]);
            dir = dir.lengthSqr() < 1.0E-6 ? caster.getLookAngle() : dir.normalize();
            double size = radius * (0.2 + 0.8 * smooth(k));
            Flight flight = new Flight(SKILL, awakened, size, bola.velocidade(), bola.curva(), bola.alcance(), spec.alvo().detectar(), spec.explosao(),
                    spec.sons(), false);
            SkillSounds.play(level, spec.sons(), "lancar", 0, 0, onde -> pos[0], screen);
            Fx.effects(level, SKILL, awakened, "lancar", pos[0], dir);
            fly(caster, level, flight, id, pos[0], dir.scale(bola.velocidade()), target, screen);
            return true;
        });
        return true;
    }

    private static Vec3 overHand(LivingEntity caster, DestructionEnergySpec.Carga carga, double radius) {
        return caster.position().add(0, caster.getBbHeight() + 0.6 + carga.acima() + radius, 0).add(Combat.flatLook(caster).scale(0.3));
    }

    // ---------- Shift + Z ----------

    private record Orb(int id, Vec3 dir, double distance, int born) {
    }

    public static boolean rain(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        DestructionRainSpec spec = DestructionRainSpec.get(awakened);
        DestructionRainSpec.Voo voo = spec.voo();
        DestructionRainSpec.Bolas bolas = spec.bolas();
        DestructionRainSpec.Tiro tiro = spec.tiro();
        int rise = Math.max(1, voo.subirTicks());
        int count = Math.max(1, bolas.quantidade());
        int create = Math.max(1, bolas.criarTicks());
        int every = Math.max(1, bolas.intervaloTicks());
        int fireAt = rise + create + Math.max(0, bolas.pausaTicks());
        int end = fireAt + count * every;
        double radius = Math.max(0.15, bolas.raio());
        double groundY = ground(level, caster);
        double topY = Math.max(caster.getY(), groundY + voo.altura());
        String power = data.power;
        int interrupts = data.interrupts;
        Flight flight = new Flight(RAIN, awakened, radius, tiro.velocidade(), tiro.curva(), tiro.alcance(), spec.alvo().detectar(), spec.explosao(),
                spec.sons(), true);

        List<Orb> orbs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomDir();
            double distance = Mth.lerp(RANDOM.nextDouble(), bolas.distanciaMin(), bolas.distanciaMax());
            orbs.add(new Orb(nextId++, dir, distance, rise + i * create / count));
        }

        data.busyTicks = end + 4;
        data.noFallTicks = Math.max(data.noFallTicks, end + voo.planarTicks() + 100);
        Runnable unshield = Intangible.shield(caster, end + 2);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), RAIN, awakened));
        Function<String, Vec3> where = onde -> caster.position().add(0, 1, 0);
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        SkillSounds.play(level, spec.sons(), "uso", 0, end, where, screen);
        Fx.effects(level, RAIN, awakened, "uso", caster.position(), new Vec3(0, 1, 0));
        caster.setNoGravity(true);

        int[] fired = {0};
        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            boolean gone = !caster.isAlive() || caster.isRemoved() || !power.equals(now.power);
            if (gone || knockedOut(caster, now, interrupts, spec.cancelarStunTicks())) {
                // Knocked out of it: the balls still around him fade and he falls.
                for (int i = fired[0]; i < orbs.size(); i++) {
                    Orb orb = orbs.get(i);
                    if (age >= orb.born()) {
                        Vec3 at = orbAt(caster, orb, age);
                        send(level, orb.id(), 2, at, 0, 0, true);
                        Fx.effects(level, RAIN, awakened, "sumir", at, Vec3.ZERO);
                    }
                }
                caster.setNoGravity(false);
                unshield.run();
                if (!gone) {
                    Fx.effects(level, RAIN, awakened, "cancelar", caster.position().add(0, 1, 0), Vec3.ZERO);
                    SkillSounds.play(level, spec.sons(), "cancelar", 0, 0, where, screen);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                }
                return true;
            }
            now.busyTicks = Math.max(now.busyTicks, 2);
            caster.fallDistance = 0;
            if (age < rise) {
                // Up to the height, slowing down as he gets there.
                double dy = Mth.clamp((topY - caster.getY()) * 0.3, 0, 1.4);
                Vec3 motion = caster.getDeltaMovement();
                caster.setDeltaMovement(motion.x * 0.5, dy, motion.z * 0.5);
            } else {
                // Hangs up there: barely drifts, can still turn to aim.
                Vec3 motion = caster.getDeltaMovement();
                caster.setDeltaMovement(motion.x * 0.5, 0, motion.z * 0.5);
            }
            caster.hurtMarked = true;

            // Balls coming out of the body and hanging around him.
            for (int i = fired[0]; i < orbs.size(); i++) {
                Orb orb = orbs.get(i);
                int t = age - orb.born();
                if (t < 0) {
                    continue;
                }
                Vec3 at = orbAt(caster, orb, age);
                if (t == 0) {
                    Fx.effects(level, RAIN, awakened, "criar", at, orb.dir());
                    SkillSounds.play(level, spec.sons(), "criar", 0, 0, onde -> at, screen);
                }
                double k = Math.min(1, (t + 1) / 10.0);
                send(level, orb.id(), 0, at, (float) (radius * (0.2 + 0.8 * smooth(k))), (float) k, true);
            }

            // One after another at the target.
            while (fired[0] < orbs.size() && age >= fireAt + fired[0] * every) {
                Orb orb = orbs.get(fired[0]++);
                Vec3 at = orbAt(caster, orb, age);
                LivingEntity target = lock(caster, spec.alvo().alcance());
                Vec3 aim = target != null ? Combat.center(target) : aimPoint(caster, tiro.alcance());
                Vec3 to = aim.subtract(at);
                to = to.lengthSqr() < 1.0E-6 ? caster.getLookAngle() : to.normalize();
                // Leaves outwards first, then curves in: a spray of shots instead of a single line.
                Vec3 start = orb.dir().scale(0.6).add(to).normalize().scale(tiro.velocidade());
                SkillSounds.play(level, spec.sons(), "lancar", 0, 0, onde -> at, screen);
                Fx.effects(level, RAIN, awakened, "lancar", at, to);
                fly(caster, level, flight, orb.id(), at, start, target, screen);
            }

            if (age >= end) {
                // All shot: he glides back down.
                caster.setNoGravity(false);
                caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(1, voo.planarTicks()), 0, false, false, false));
                now.busyTicks = 4;
                unshield.run();
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            return false;
        });
        return true;
    }

    // Grabbed, frozen, sealed (anything that cuts his moves off) or stunned for long enough.
    private static boolean knockedOut(LivingEntity caster, PowerData data, int interrupts, int stunTicks) {
        if (data.interrupts != interrupts || Freeze.isFrozen(caster)) {
            return true;
        }
        MobEffectInstance stun = caster.getEffect(ModEffects.ATORDOADO);
        return stun != null && stun.getDuration() >= Math.max(1, stunTicks);
    }

    // Where a ball hanging around him is: pushed out of his body to its spot over 8 ticks, bobbing a little.
    private static Vec3 orbAt(LivingEntity caster, Orb orb, int age) {
        int t = Math.max(0, age - orb.born());
        double out = Mth.lerp(smooth(t / 8.0), 0.4, orb.distance());
        double bob = Math.sin((age + orb.id() * 7) * 0.15) * 0.15;
        return Combat.center(caster).add(orb.dir().scale(out)).add(0, bob, 0);
    }

    private static Vec3 randomDir() {
        double y = RANDOM.nextDouble() * 2 - 1;
        double a = RANDOM.nextDouble() * Mth.TWO_PI;
        double r = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    // The top of the ground under him (or his own height when there is nothing below within 64 blocks).
    private static double ground(ServerLevel level, LivingEntity caster) {
        Vec3 from = caster.position();
        var hit = level.clip(new ClipContext(from, from.subtract(0, 64, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
        return hit.getType() == HitResult.Type.MISS ? from.y : hit.getLocation().y;
    }

    // ---------- the flight, shared ----------

    // Fast and homing: after target while it is alive; with no target it follows the caster's crosshair until someone
    // comes within detect blocks of it. Blows up on the first block or body it touches.
    private static void fly(LivingEntity caster, ServerLevel level, Flight f, int id, Vec3 start, Vec3 velocity, LivingEntity locked,
            List<ServerPlayer> screen) {
        double speed = Math.max(0.2, f.speed());
        int ticks = (int) Math.ceil(Math.max(speed, f.reach()) / speed);
        Vec3[] pos = {start};
        Vec3[] vel = {velocity};
        LivingEntity[] target = {locked};
        Destructible.Attack attack = Destructible.track(level, caster, start, f.radius(), () -> send(level, id, 2, pos[0], 0, 0, f.small()));
        Tasks.add(age -> {
            if (attack.gone()) {
                return true;
            }
            if (age >= ticks) {
                Fx.effects(level, f.skill(), f.awakened(), "sumir", pos[0], Vec3.ZERO);
                send(level, id, 2, pos[0], 0, 0, f.small());
                attack.end();
                return true;
            }
            if (target[0] != null && (!target[0].isAlive() || target[0].isRemoved() || !Combat.isTarget(target[0], caster))) {
                target[0] = null;
            }
            if (target[0] == null && f.detect() > 0) {
                target[0] = Combat.inRadius(level, pos[0], f.detect(), caster).stream()
                        .min(Comparator.comparingDouble(e -> Combat.center(e).distanceToSqr(pos[0]))).orElse(null);
            }
            Vec3 goal = target[0] != null ? Combat.center(target[0])
                    : caster.isAlive() ? aimPoint(caster, f.reach()) : pos[0].add(vel[0]);
            Vec3 to = goal.subtract(pos[0]);
            Vec3 want = to.lengthSqr() < 1.0E-6 ? vel[0] : to.normalize().scale(speed);
            Vec3 next = vel[0].lerp(want, Mth.clamp(f.turn(), 0.01, 1));
            vel[0] = next.lengthSqr() < 1.0E-6 ? want : next.normalize().scale(speed);

            Vec3 from = pos[0];
            Vec3 to2 = from.add(vel[0]);
            Vec3 dir = vel[0].normalize();
            Vec3 at = null;
            var clip = level.clip(new ClipContext(from, to2.add(dir.scale(f.radius())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (clip.getType() != HitResult.Type.MISS) {
                at = clip.getLocation().subtract(dir.scale(f.radius() * 0.5));
            } else if (!Combat.alongLine(level, from, to2, f.radius() + 0.5, caster).isEmpty()) {
                at = to2;
            }
            if (at != null) {
                Vec3 boom = at;
                attack.end();
                send(level, id, 2, boom, 0, 0, f.small());
                explode(caster, level, f, boom, screen);
                return true;
            }
            pos[0] = to2;
            attack.move(to2);
            send(level, id, 0, to2, (float) f.radius(), 1, f.small());
            return false;
        });
    }

    private static void explode(LivingEntity caster, ServerLevel level, Flight f, Vec3 at, List<ServerPlayer> screen) {
        DestructionEnergySpec.Explosao explosao = f.explosao();
        double radius = Math.max(1, explosao.raio());
        for (LivingEntity hit : Combat.inRadius(level, at, radius, caster)) {
            double k = Math.min(1, Combat.center(hit).distanceTo(at) / radius);
            Combat.hurt(caster, hit, (float) (explosao.dano() * (1 - k * 0.5)));
            Combat.pushFrom(hit, at, explosao.empurrao() * (1 - k * 0.5), explosao.altura());
        }
        Fx.effects(level, f.skill(), f.awakened(), "explosao", at, new Vec3(0, 1, 0));
        SkillSounds.play(level, f.sons(), "explosao", 0, 0, onde -> at, screen);
        if (explosao.tremor() > 0) {
            for (ServerPlayer viewer : level.players()) {
                double distance = viewer.position().distanceTo(at);
                if (distance < radius * 4) {
                    PacketDistributor.sendToPlayer(viewer, new ModPayloads.ScreenShake((float) (explosao.tremor() * (1 - distance / (radius * 4))), 12));
                }
            }
        }
        if (explosao.raioBlocos() > 0 && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            crater(level, at, explosao.raioBlocos());
        }
    }

    // A round hole, opened from the middle out over 3 ticks. Unbreakable blocks stay; no drops, no neighbor updates.
    private static void crater(ServerLevel level, Vec3 pos, double radius) {
        BlockPos center = BlockPos.containing(pos);
        int r = (int) Math.ceil(radius * 1.1);
        List<List<BlockPos>> waves = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double d = (x * x + y * y + z * z) / (radius * radius);
                    if (d > 0.85 + RANDOM.nextDouble() * 0.3) {
                        continue;
                    }
                    waves.get((int) Math.min(2, Math.sqrt(d) * 3)).add(center.offset(x, y, z));
                }
            }
        }
        for (int i = 0; i < waves.size(); i++) {
            List<BlockPos> wave = waves.get(i);
            Tasks.later(i, () -> {
                for (BlockPos block : wave) {
                    BlockState state = level.getBlockState(block);
                    if (!state.isAir() && state.getDestroySpeed(level, block) >= 0) {
                        level.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            });
        }
    }

    // ---------- targets and aim ----------

    // Bosses go for their own target; players for whoever they last fought if it is in front of them and in sight, else
    // whoever is under the crosshair.
    private static LivingEntity lock(LivingEntity caster, double range) {
        if (caster instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target != null && Combat.isTarget(target, caster) && target.distanceTo(caster) <= range ? target : null;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= range && caster.hasLineOfSight(fought)
                && fought.getEyePosition().subtract(caster.getEyePosition()).normalize().dot(caster.getLookAngle()) > 0.8) {
            return fought;
        }
        return Combat.raycastTarget(caster, range);
    }

    // Where the caster's crosshair points, up to range blocks away (the first block in the way).
    private static Vec3 aimPoint(LivingEntity caster, double range) {
        if (caster instanceof Mob mob && mob.getTarget() != null) {
            return Combat.center(mob.getTarget());
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        var hit = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static void send(ServerLevel level, int id, int mode, Vec3 pos, float radius, float grow, boolean small) {
        ModPayloads.EnergyBall payload = new ModPayloads.EnergyBall(id, mode, pos, radius, grow, small);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
