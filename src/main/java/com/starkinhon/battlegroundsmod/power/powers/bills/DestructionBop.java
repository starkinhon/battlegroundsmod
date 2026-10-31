package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.fx.SoulParticleOptions;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Agarrao;
import com.starkinhon.battlegroundsmod.skill.DestructionBopSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// X: Destruction Bop (bills.destruction_bop.json). Bills appears in front of whoever he aims at and the target freezes
// in place. One flick of his finger sends them flying up; he vanishes, comes back above and behind them and slaps
// them straight down into the ground, which breaks open the vanilla way. The target is stunned and sealed (no skills,
// not even a counter) from the freeze until they get out of the hole. Only the two fighters get the cutscene.
public final class DestructionBop {
    public static final String SKILL = DestructionBopSpec.SKILL;

    private DestructionBop() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        DestructionBopSpec spec = DestructionBopSpec.get(awakened);
        LivingEntity target = lock(caster, spec.alvo().alcance());
        if (target == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return false;
        }

        int flick = spec.flickTick();
        int top = spec.topTick();
        int vanish = spec.vanishTick();
        int appear = spec.appearTick();
        int slap = spec.slapTick();
        int ground = spec.groundTick();
        int land = spec.landTick();
        int stuck = Math.max(1, spec.chao().presoTicks());
        int end = Math.max(land, ground) + Math.max(0, spec.cutscene().depoisTicks());
        data.busyTicks = land + 4;

        // He comes from where he stood: forward points from him to the target.
        Vec3 forward = flatTo(caster, target);
        float faceTarget = yaw(forward);
        Vec3 start = target.position();
        Vec3 floor = floor(level, start, target);
        Vec3 high = ceiling(level, floor, spec.peteleco().altura(), target);

        // In front of the target first, then up behind it for the slap, then back down beside the hole.
        Vec3 front = standSpot(level, caster, floor.subtract(forward.scale(Math.max(0.8, spec.alvo().distancia()))), caster.position());
        Vec3 air = high.add(forward.scale(spec.tapa().atras())).add(0, spec.tapa().acima(), 0);
        Vec3 beside = standSpot(level, caster, floor.subtract(forward.scale(Math.max(2.5, spec.chao().raioCratera() * 0.7))), front);

        smoke(level, caster.position(), caster.getBbHeight());
        Fx.effects(level, SKILL, awakened, "sumir", caster, 0);
        place(caster, front, faceTarget, 0);
        Freeze.hold(caster, vanish);
        Fx.effects(level, SKILL, awakened, "aparecer", caster, 0);
        Fx.effects(level, SKILL, awakened, "uso", caster, flick);
        SkillSounds.play(level, spec.sons(), "uso", 0, flick, onde -> caster.position(), screens(caster, target));
        SkillSounds.play(level, spec.sons(), "aparecer", 0, 0, onde -> caster.position(), screens(caster, target));

        // The target faces him, frozen where it stood, then flies up, hangs there and is slapped down.
        float faceCaster = faceTarget + 180;
        target.setYRot(faceCaster);
        target.setYHeadRot(faceCaster);
        target.setYBodyRot(faceCaster);
        target.setXRot(0);
        Vec3 groundAt = floor;
        Freeze.hold(target, ground, age -> {
            if (age < flick) {
                return start;
            }
            if (age < top) {
                double k = (age - flick) / (double) Math.max(1, top - flick);
                // Fast off the ground, slowing down at the top.
                double eased = 1 - (1 - k) * (1 - k) * (1 - k);
                return start.lerp(high, eased);
            }
            if (age < slap) {
                // A little float while hanging.
                return high.add(0, 0.15 * Math.sin((age - top) * 0.6), 0);
            }
            double k = (age - slap) / (double) Math.max(1, ground - slap);
            return high.lerp(groundAt, k * k);
        });
        Stun.apply(target, ground + stuck);
        // Whatever the target was doing is cut off (this also stops its animation, so its own goes after).
        PowerLogic.seal(target, ground + stuck);
        if (spec.cutscene().intangivel()) {
            Intangible.hold(caster, land + 2);
            Intangible.hold(target, ground + 2);
        }
        if (spec.cutscene().ativa()) {
            for (ServerPlayer player : screens(caster, target)) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.BopCutscene(caster.getId(), target.getId(), awakened, end));
            }
        }
        animation(caster, "bills", awakened);
        animation(target, "alvo", awakened);
        Fx.effects(level, SKILL, awakened, "parar", target, flick);
        SkillSounds.play(level, spec.sons(), "parar", 0, flick, onde -> Combat.center(target), screens(caster, target));

        // The flick.
        Tasks.later(flick, () -> {
            if (!target.isAlive()) {
                return;
            }
            Combat.hurt(caster, target, spec.peteleco().dano());
            Vec3 finger = Combat.center(target).subtract(forward.scale(target.getBbWidth() * 0.5));
            Fx.effects(level, SKILL, awakened, "peteleco", finger, new Vec3(0, 1, 0));
            Fx.effects(level, SKILL, awakened, "subir", target, top - flick);
            SkillSounds.play(level, spec.sons(), "peteleco", 0, 0, onde -> finger, screens(caster, target));
            SkillSounds.play(level, spec.sons(), "subir", 0, top - flick, onde -> Combat.center(target), screens(caster, target));
        });
        // Wind streaks while flying up and falling down.
        Tasks.add(age -> {
            if (age >= ground || !target.isAlive()) {
                return true;
            }
            if (age >= flick && age < top) {
                streaks(level, target, new Vec3(0, -1, 0), spec);
            } else if (age >= slap) {
                streaks(level, target, new Vec3(0, 1, 0), spec);
            }
            return false;
        });

        // Gone from the ground, back up in the air behind them with the hand up.
        Tasks.later(vanish, () -> {
            if (!caster.isAlive()) {
                return;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), appear - vanish + 1));
            smoke(level, caster.position(), caster.getBbHeight());
            Fx.effects(level, SKILL, awakened, "sumir", caster, 0);
            SkillSounds.play(level, spec.sons(), "sumir", 0, 0, onde -> caster.position(), screens(caster, target));
            place(caster, air, faceTarget + 180, 45);
            Freeze.hold(caster, land - vanish);
        });
        Tasks.later(appear, () -> {
            if (!caster.isAlive()) {
                return;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), 0));
            Fx.effects(level, SKILL, awakened, "aparecer", caster, 0);
            SkillSounds.play(level, spec.sons(), "aparecer", 0, 0, onde -> caster.position(), screens(caster, target));
        });

        // The slap.
        Tasks.later(slap, () -> {
            if (!target.isAlive()) {
                return;
            }
            Combat.hurt(caster, target, spec.tapa().dano());
            Vec3 hand = Combat.center(target).add(0, target.getBbHeight() * 0.5, 0);
            Fx.effects(level, SKILL, awakened, "tapa", hand, new Vec3(0, -1, 0));
            SkillSounds.play(level, spec.sons(), "tapa", 0, 0, onde -> hand, screens(caster, target));
        });

        // Into the ground.
        Tasks.later(ground, () -> {
            if (!target.isAlive()) {
                return;
            }
            DestructionBopSpec.Chao chao = spec.chao();
            Combat.hurt(caster, target, chao.dano());
            if (chao.quebrarBlocos()) {
                Agarrao.crater(level, groundAt, chao.raioCratera(), chao.profundidadeCratera());
            }
            Vec3 bottom = floor(level, groundAt.add(0, 0.5, 0), target, chao.profundidadeCratera() + 2);
            target.resetFallDistance();
            Freeze.hold(target, stuck, age -> bottom);
            Fx.effects(level, SKILL, awakened, "chao", groundAt, forward);
            SkillSounds.play(level, spec.sons(), "chao", 0, 0, onde -> groundAt, screens(caster, target));
            dust(level, groundAt, spec);
        });

        // Back down beside the hole, calm, looking at what he did.
        Tasks.later(land, () -> {
            if (!caster.isAlive()) {
                return;
            }
            smoke(level, caster.position(), caster.getBbHeight());
            Fx.effects(level, SKILL, awakened, "sumir", caster, 0);
            place(caster, beside, faceTarget, 20);
            caster.resetFallDistance();
            Fx.effects(level, SKILL, awakened, "pousar", caster, 0);
            SkillSounds.play(level, spec.sons(), "pousar", 0, 0, onde -> caster.position(), screens(caster, target));
        });
        return true;
    }

    // Whoever he is fighting, if close enough and in front; else whoever he aims at.
    private static LivingEntity lock(LivingEntity caster, double range) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= range && caster.hasLineOfSight(fought)
                && flatTo(caster, fought).dot(Combat.flatLook(caster)) > 0.5) {
            return fought;
        }
        return Combat.raycastTarget(caster, range);
    }

    // Moves him there facing that way, players through their connection so their own screen follows.
    private static void place(LivingEntity entity, Vec3 pos, float yRot, float xRot) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.setYHeadRot(yRot);
        entity.setYBodyRot(yRot);
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(pos.x, pos.y, pos.z, yRot, xRot);
        } else {
            entity.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
        }
        entity.hurtMarked = true;
    }

    // Where he can stand at spot (same height, else a step up or down), or the fallback.
    private static Vec3 standSpot(ServerLevel level, LivingEntity caster, Vec3 spot, Vec3 fallback) {
        for (double dy : new double[] {0, 1, -1}) {
            Vec3 at = spot.add(0, dy, 0);
            if (level.noCollision(caster, caster.getBoundingBox().move(at.subtract(caster.position())))) {
                return at;
            }
        }
        return fallback;
    }

    // The floor under a point (a target already in the air falls from where it is), at most 40 blocks down.
    private static Vec3 floor(ServerLevel level, Vec3 pos, LivingEntity owner) {
        return floor(level, pos.add(0, 0.5, 0), owner, 40);
    }

    private static Vec3 floor(ServerLevel level, Vec3 from, LivingEntity owner, double depth) {
        var hit = level.clip(new ClipContext(from, from.add(0, -depth, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? from.subtract(0, 0.5, 0) : hit.getLocation();
    }

    // How high the flick gets them: height blocks up, or just under a ceiling.
    private static Vec3 ceiling(ServerLevel level, Vec3 floor, double height, LivingEntity target) {
        Vec3 from = floor.add(0, target.getBbHeight(), 0);
        var hit = level.clip(new ClipContext(from, from.add(0, height, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        double up = hit.getType() == HitResult.Type.MISS ? height : Math.max(0, hit.getLocation().y - from.y - 0.3);
        return floor.add(0, up, 0);
    }

    private static void streaks(ServerLevel level, LivingEntity target, Vec3 back, DestructionBopSpec spec) {
        DestructionBopSpec.Rastro rastro = spec.rastro();
        if (!rastro.ativa()) {
            return;
        }
        RandomSource random = level.getRandom();
        int color = rgb(rastro.cor(), 0xFFFFFF);
        int color2 = rgb(rastro.cor2(), 0xB98CFF);
        Vec3 center = Combat.center(target);
        for (int i = 0; i < rastro.densidade(); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = 0.5 + random.nextDouble() * 0.6;
            Vec3 at = center.add(Math.cos(angle) * r, (random.nextDouble() - 0.5) * target.getBbHeight(), Math.sin(angle) * r);
            Fx.moving(level, ModParticles.streak(random.nextInt(3) == 0 ? color2 : color, 1.2f, 5 + random.nextInt(3)), at, back.scale(0.4));
        }
    }

    // A quick dark-purple puff where he vanishes or comes back.
    private static void smoke(ServerLevel level, Vec3 at, double height) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 10; i++) {
            Vec3 pos = at.add(random.nextGaussian() * 0.3, 0.2 + random.nextDouble() * height, random.nextGaussian() * 0.3);
            Vec3 velocity = new Vec3(random.nextGaussian() * 0.04, 0.02, random.nextGaussian() * 0.04);
            Fx.moving(level, ModParticles.puff(0x3A2A4F, 0.25f + random.nextFloat() * 0.3f, 8 + random.nextInt(6), 0.9f), pos, velocity);
        }
    }

    // The dust wave rolling out along the ground and specks of dirt flying out of the hole.
    private static void dust(ServerLevel level, Vec3 pos, DestructionBopSpec spec) {
        DestructionBopSpec.Chao chao = spec.chao();
        RandomSource random = level.getRandom();
        int color = rgb(chao.corOnda(), 0xB8A58A);
        int points = 48;
        for (double y : new double[] {0.15, 0.6}) {
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2 / points;
                Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Fx.moving(level, ModParticles.puff(color, 0.35f + random.nextFloat() * 0.2f, 10 + random.nextInt(6), 0.93f),
                        pos.add(out.scale(0.6)).add(0, y, 0), out.scale(chao.velocidadeOnda()));
            }
        }
        double radius = chao.raioCratera();
        for (int i = 0; i < chao.poeira(); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * radius * 0.6;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            int speckColor = random.nextBoolean() ? 0x6B4A2F : 0x8A7458;
            SoulParticleOptions speck = new SoulParticleOptions(ModParticles.DUST.get(), speckColor, 0.08f + random.nextFloat() * 0.08f,
                    20 + random.nextInt(20), 0.97f, 0.9f);
            Fx.moving(level, speck, pos.add(out.scale(r)).add(0, 0.3, 0),
                    out.scale(0.15 + random.nextDouble() * 0.35).add(0, 0.3 + random.nextDouble() * 0.45, 0));
        }
    }

    private static void animation(LivingEntity entity, String part, boolean awakened) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), SKILL + "#" + part, awakened));
    }

    // The players whose screen this is about ("tela" sounds, the cutscene).
    private static List<ServerPlayer> screens(LivingEntity caster, LivingEntity target) {
        List<ServerPlayer> out = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            out.add(player);
        }
        if (target instanceof ServerPlayer player) {
            out.add(player);
        }
        return out;
    }

    private static float yaw(Vec3 dir) {
        return (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
    }

    private static Vec3 flatTo(LivingEntity from, LivingEntity to) {
        Vec3 d = to.position().subtract(from.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        return flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(from) : flat.normalize();
    }

    private static int rgb(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
