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
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.HakaiSpec;
import com.starkinhon.battlegroundsmod.skill.PlanetaryDestructionSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
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

// B: Planetary Destruction (bills.planetary_destruction.json, see PlanetaryDestructionSpec). He points at the target
// and a tiny sphere of destruction forms at his finger while the key is held; let go, it flies up over him and chases
// the target from up there, then implodes into a giant sun that comes down on it. The hit is an anime explosion that
// rises in smoke, runs back into a point and blows up again with a shockwave. The ball is sent to the clients every
// tick (ModPayloads.PlanetSphere) and the explosion once (ModPayloads.PlanetBlast); both are drawn by
// client/fx/PlanetaryRenderer, the screen effects come from the skill's vfx list.
// Shift + B is the older version, where he rises into the sky (DestructionTouch).
public final class PlanetaryDestruction {
    public static final String SKILL = PlanetaryDestructionSpec.SKILL;
    private static final double VIEW_DISTANCE = 256;
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextId;

    private PlanetaryDestruction() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        PlanetaryDestructionSpec spec = PlanetaryDestructionSpec.get(awakened);
        PlanetaryDestructionSpec.Carga carga = spec.carga();
        PlanetaryDestructionSpec.Bola bola = spec.bola();
        int form = Math.max(1, carga.formarTicks());
        int full = Math.max(form, carga.maxTicks());
        // Bosses don't hold keys: they let go a little after the ball formed.
        int bossCharge = form + 10 + RANDOM.nextInt(20);
        float small = (float) Math.max(0.05, bola.raio());
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;

        data.busyTicks = full + 4;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Vec3[] pos = {fingertip(caster)};
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : pos[0];
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        SkillSounds.play(level, spec.sons(), "uso", 0, full, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", pos[0], Vec3.ZERO);

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            boolean gone = !caster.isAlive() || caster.isRemoved() || !power.equals(now.power);
            if (gone || knockedOut(caster, now, interrupts, spec.cancelarStunTicks())) {
                send(level, id, 2, pos[0], 0, false);
                if (!gone) {
                    Fx.effects(level, SKILL, awakened, "cancelar", pos[0], Vec3.ZERO);
                    SkillSounds.play(level, spec.sons(), "cancelar", 0, 0, where, screen);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                }
                return true;
            }
            pos[0] = fingertip(caster);
            double k = Math.min(1, (age + 1) / (double) form);
            send(level, id, 0, pos[0], (float) (small * (0.3 + 0.7 * smooth(k))), false);
            now.busyTicks = Math.max(now.busyTicks, 2);
            boolean letGo = caster instanceof Player ? !now.abilityHeld : age >= bossCharge;
            if (age + 1 < full && !(letGo && age + 1 >= Math.max(1, carga.minTicks()))) {
                return false;
            }
            // Let go: the arm comes down and the ball flies up.
            now.busyTicks = 4;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
            caster.swing(InteractionHand.MAIN_HAND, true);
            LivingEntity target = lock(caster, spec.alvo().alcance());
            SkillSounds.play(level, spec.sons(), "soltar", 0, 0, onde -> pos[0], screen);
            Fx.effects(level, SKILL, awakened, "soltar", pos[0], new Vec3(0, 1, 0));
            fly(caster, level, spec, awakened, id, pos[0], target, screen);
            return true;
        });
        return true;
    }

    // Up over him, after the target from up there, the implosion, then down on the target.
    private static void fly(LivingEntity caster, ServerLevel level, PlanetaryDestructionSpec spec, boolean awakened, int id, Vec3 start,
            LivingEntity locked, List<ServerPlayer> screen) {
        PlanetaryDestructionSpec.Bola bola = spec.bola();
        float small = (float) Math.max(0.05, bola.raio());
        float big = (float) Math.max(small, bola.raioGigante());
        int rise = Math.max(1, bola.subirTicks());
        int implode = Math.max(rise, bola.implodirTicks());
        int shrink = Math.max(1, bola.encolherTicks());
        int grow = Math.max(1, bola.crescerTicks());
        int burst = implode + shrink;
        int life = Math.max(burst + grow + 1, bola.vidaMaxTicks());
        Vec3 top = caster.position().add(0, caster.getBbHeight() + bola.subir(), 0);
        Vec3[] pos = {start};
        Vec3[] vel = {Vec3.ZERO};
        float[] radius = {small};
        LivingEntity[] target = {locked};
        Destructible.Attack attack = Destructible.track(level, caster, start, small, () -> send(level, id, 2, pos[0], 0, false));
        // Everyone close enough hears it, and the target's screen too.
        List<ServerPlayer> heard = new ArrayList<>(screen);
        if (locked instanceof ServerPlayer player && !heard.contains(player)) {
            heard.add(player);
        }
        Tasks.add(age -> {
            if (attack.gone()) {
                return true;
            }
            if (target[0] != null && (!target[0].isAlive() || target[0].isRemoved() || !Combat.isTarget(target[0], caster))) {
                target[0] = null;
            }
            if (target[0] == null && caster.isAlive()) {
                target[0] = lock(caster, spec.alvo().alcance());
            }
            if (target[0] == null && spec.alvo().detectar() > 0) {
                target[0] = Combat.inRadius(level, pos[0], spec.alvo().detectar(), caster).stream()
                        .min(Comparator.comparingDouble(e -> Combat.center(e).distanceToSqr(pos[0]))).orElse(null);
            }

            boolean giant = age >= burst;
            if (age < rise) {
                // Straight up over him, slowing down at the top.
                double k = smooth((age + 1) / (double) rise);
                Vec3 next = start.lerp(top, k);
                vel[0] = next.subtract(pos[0]);
                pos[0] = next;
            } else {
                Vec3 goal;
                if (target[0] != null) {
                    goal = giant ? Combat.center(target[0]) : target[0].position().add(0, target[0].getBbHeight() + bola.alturaSobreAlvo(), 0);
                } else if (caster.isAlive()) {
                    Vec3 aim = aimPoint(caster, spec.alvo().alcance());
                    goal = giant ? aim : aim.add(0, bola.alturaSobreAlvo(), 0);
                } else {
                    goal = pos[0].add(0, giant ? -1 : 0, 0);
                }
                // Imploding it holds still; huge it is slower and heavier.
                double speed = age >= implode && age < burst + grow ? 0 : giant ? bola.velocidadeGigante() : bola.velocidade();
                Vec3 to = goal.subtract(pos[0]);
                // While small it settles over the target instead of running past it.
                double want = giant ? speed : Math.min(speed, to.length() * 0.25);
                Vec3 wish = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize().scale(want);
                vel[0] = vel[0].lerp(wish, Mth.clamp(bola.curva(), 0.01, 1));
                if (speed == 0) {
                    vel[0] = Vec3.ZERO;
                }
                if (vel[0].length() > Math.max(speed, 1.0E-3)) {
                    vel[0] = vel[0].normalize().scale(speed);
                }
                Vec3 from = pos[0];
                Vec3 next = from.add(vel[0]);
                if (giant) {
                    Vec3 hit = touch(level, caster, from, next, radius[0]);
                    if (hit != null || age >= life) {
                        Vec3 at = hit != null ? hit : next;
                        attack.end();
                        send(level, id, 2, at, 0, true);
                        blast(caster, level, spec, awakened, at, heard);
                        return true;
                    }
                }
                pos[0] = next;
            }

            // The implosion: in to a point, then all at once out to its full size.
            if (age < implode) {
                radius[0] = small;
            } else if (age < burst) {
                radius[0] = (float) (small * (1 - 0.6 * smooth((age - implode + 1) / (double) shrink)));
            } else {
                double k = Math.min(1, (age - burst + 1) / (double) grow);
                // A little overshoot, then it settles.
                radius[0] = (float) (big * (k < 1 ? k * 1.12 : 1 + 0.12 * Math.max(0, 1 - (age - burst - grow) / 6.0)));
            }
            if (age == burst) {
                Fx.effects(level, SKILL, awakened, "implodir", pos[0], Vec3.ZERO);
                SkillSounds.play(level, spec.sons(), "implodir", 0, 0, onde -> pos[0], heard);
            }
            attack.move(pos[0]);
            attack.resize(radius[0]);
            send(level, id, 0, pos[0], radius[0], giant);
            return false;
        });
    }

    // Where the big ball touches the ground or a body on its way from one point to the next, or null.
    private static Vec3 touch(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 to, double radius) {
        Vec3 step = to.subtract(from);
        Vec3 dir = step.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : step.normalize();
        var clip = level.clip(new ClipContext(from, to.add(dir.scale(radius * 0.8)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (clip.getType() != HitResult.Type.MISS) {
            return clip.getLocation();
        }
        // Anything under it counts as soon as its bottom reaches the ground.
        var below = level.clip(new ClipContext(to, to.subtract(0, radius * 0.8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (below.getType() != HitResult.Type.MISS) {
            return below.getLocation();
        }
        List<LivingEntity> bodies = Combat.inRadius(level, to, radius * 0.9, caster);
        return bodies.isEmpty() ? null : Combat.center(bodies.get(0));
    }

    // The hit: the first blast now, the rewind and the second, bigger blast later, on the same timing the clients draw.
    private static void blast(LivingEntity caster, ServerLevel level, PlanetaryDestructionSpec spec, boolean awakened, Vec3 hit,
            List<ServerPlayer> heard) {
        PlanetaryDestructionSpec.Explosao explosao = spec.explosao();
        Vec3 at = new Vec3(hit.x, ground(level, hit.add(0, 1, 0), caster), hit.z);
        int rewind = explosao.rewindAt();
        int second = explosao.secondAt();
        ModPayloads.PlanetBlast payload = new ModPayloads.PlanetBlast(at, awakened);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        Fx.effects(level, SKILL, awakened, "explosao", at, new Vec3(0, 1, 0));
        SkillSounds.play(level, spec.sons(), "explosao", 0, rewind, onde -> at, heard);
        damage(caster, level, at, explosao.raio(), explosao.dano(), explosao.empurrao(), false, awakened);
        Tasks.later(rewind, () -> {
            Fx.effects(level, SKILL, awakened, "rebobinar", at, new Vec3(0, 1, 0), second - rewind);
            SkillSounds.play(level, spec.sons(), "rebobinar", 0, second - rewind, onde -> at, heard);
        });
        Tasks.later(second, () -> {
            Fx.effects(level, SKILL, awakened, "explosao2", at, new Vec3(0, 1, 0));
            SkillSounds.play(level, spec.sons(), "explosao2", 0, 0, onde -> at, heard);
            // The shockwave first (a push and a little damage far out), then the blast itself.
            if (explosao.raioOnda() > explosao.raio2()) {
                for (LivingEntity hitBy : Combat.inRadius(level, at, explosao.raioOnda(), caster)) {
                    double d = Combat.center(hitBy).distanceTo(at);
                    if (d > explosao.raio2()) {
                        double k = (d - explosao.raio2()) / (explosao.raioOnda() - explosao.raio2());
                        Combat.hurt(caster, hitBy, (float) (explosao.danoOnda() * (1 - k)));
                        Combat.pushFrom(hitBy, at, explosao.empurrao() * (1 - k), 0.4);
                    }
                }
            }
            damage(caster, level, at, explosao.raio2(), explosao.dano2(), explosao.empurrao() * 1.4, true, awakened);
            for (Destructible.Attack erased : Destructible.eraseAlong(level, at, at, explosao.raio2(), caster)) {
                Fx.effects(level, Hakaizinho.SKILL, awakened, "pulverizar", erased.pos(), Vec3.ZERO);
            }
            if (explosao.quebrarBlocos() && explosao.raioCratera() > 0 && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                crater(level, at, explosao.raioCratera(), explosao.profundidadeCratera(), 10);
            }
        });
    }

    // Damage falling to half at the edge, a push away and up; erase: whoever is left under the Hakai's limit is gone.
    private static void damage(LivingEntity caster, ServerLevel level, Vec3 at, double radius, float dano, double push, boolean erase,
            boolean awakened) {
        radius = Math.max(1, radius);
        HakaiSpec hakai = HakaiSpec.get(awakened);
        for (LivingEntity hit : Combat.inRadius(level, at, radius, caster)) {
            double k = Math.min(1, Combat.center(hit).distanceTo(at) / radius);
            if (erase && hakai.canErase(hit)) {
                Hakai.disintegrate(caster, hit, awakened);
                continue;
            }
            Combat.hurt(caster, hit, (float) (dano * (1 - k * 0.5)));
            Combat.pushFrom(hit, at, push * (1 - k * 0.5), 0.5 + 0.4 * (1 - k));
        }
    }

    // A bowl pulverized out of the ground, a ring per tick from the middle out: no drops, no debris.
    private static void crater(ServerLevel level, Vec3 at, double radius, double depth, int ticks) {
        int r = (int) Math.ceil(radius);
        int up = (int) Math.ceil(radius * 0.6);
        BlockPos center = BlockPos.containing(at);
        Tasks.repeat(ticks, tick -> {
            double from = radius * tick / ticks;
            double to = radius * (tick + 1) / ticks;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double flat = Math.sqrt(dx * dx + dz * dz);
                    if (flat < from || flat >= to && tick < ticks - 1 || flat > radius) {
                        continue;
                    }
                    double k = flat / radius;
                    int bottom = -(int) Math.round(depth * Math.sqrt(Math.max(0, 1 - k * k)));
                    int top = (int) Math.round(up * (1 - k * 0.5));
                    for (int dy = bottom; dy <= top; dy++) {
                        BlockPos block = center.offset(dx, dy, dz);
                        if (!level.isLoaded(block)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(block);
                        if (state.isAir() || state.getDestroySpeed(level, block) < 0) {
                            continue;
                        }
                        level.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        });
    }

    // Grabbed, frozen, sealed (anything that cuts his moves off) or stunned for long enough.
    private static boolean knockedOut(LivingEntity caster, PowerData data, int interrupts, int stunTicks) {
        if (data.interrupts != interrupts || Freeze.isFrozen(caster)) {
            return true;
        }
        MobEffectInstance stun = caster.getEffect(ModEffects.ATORDOADO);
        return stun != null && stun.getDuration() >= Math.max(1, stunTicks);
    }

    // Bosses go for their own target; players for whoever they are fighting, else whoever is under the crosshair.
    private static LivingEntity lock(LivingEntity caster, double range) {
        if (caster instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target != null && Combat.isTarget(target, caster) && target.distanceTo(caster) <= range ? target : null;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought && fought.isAlive()
                && Combat.isTarget(fought, caster) && fought.distanceTo(caster) <= range) {
            return fought;
        }
        return Combat.raycastTarget(caster, range);
    }

    // Where the caster's crosshair points, up to range blocks away (the first block in the way).
    private static Vec3 aimPoint(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        var hit = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    // The top of the ground under a point (or the point itself when there is nothing within 32 blocks below).
    private static double ground(ServerLevel level, Vec3 from, LivingEntity caster) {
        var hit = level.clip(new ClipContext(from, from.subtract(0, 32, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
        return hit.getType() == HitResult.Type.MISS ? from.y - 1 : hit.getLocation().y;
    }

    // The tip of his right index finger, with the arm held out in front of him at the target.
    private static Vec3 fingertip(LivingEntity caster) {
        Vec3 flat = Combat.flatLook(caster);
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return caster.position().add(0, caster.getBbHeight() * 0.78, 0).add(right.scale(0.35)).add(caster.getLookAngle().scale(0.95));
    }

    private static void send(ServerLevel level, int id, int mode, Vec3 pos, float radius, boolean giant) {
        ModPayloads.PlanetSphere payload = new ModPayloads.PlanetSphere(id, mode, pos, radius, giant);
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
