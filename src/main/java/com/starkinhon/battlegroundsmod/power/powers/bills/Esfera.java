package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Agarrao;
import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + C: Esfera da Destruição Planetária (bills.esfera.json, see EsferaSpec; the normal C is DestructionSphere).
// Bills puts his hands behind his back and vanishes in black streaks, taking the target with him to outer space: a
// cutscene only the two of them see (client/EsferaCutscene, client/fx/SpaceScene), where the target lies adrift below
// him, he charges the sphere between his hands and throws it, and it hits the target and carries it down. Everyone else sees
// both of them gone (frozen, untouchable and hidden where they stood). Back in the world Bills lands where he was,
// facing the spot queda.distancia blocks ahead of him, and the target falls from queda.altura blocks up straight down
// onto it with the sun on top of it: it blows up in flames, opening a crater.
public final class Esfera {
    public static final String SKILL = EsferaSpec.SKILL;
    private static final double VIEW_DISTANCE = 256;

    private Esfera() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        EsferaSpec spec = EsferaSpec.get(awakened);
        LivingEntity target = Combat.raycastTarget(caster, spec.alvo().alcance());
        if (target == null && data.foughtId >= 0 && level.getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= spec.alvo().alcance()) {
            target = fought;
        }
        if (target == null && caster instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null
                && Combat.isTarget(mob.getTarget(), caster) && mob.getTarget().distanceTo(caster) <= spec.alvo().alcance()) {
            target = mob.getTarget();
        }
        if (target == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return false;
        }
        LivingEntity victim = target;
        int tp = spec.tpTick();
        int blast = spec.blastTick();
        int end = spec.endTick();
        // Laid out from where he stands and faces now, the same way on the clients.
        Vec3 forward = Vec3.directionFromRotation(0, caster.getYRot());
        Vec3 home = caster.position();

        // Away in space: both stand still where they were, untouchable, and the others don't see them.
        data.busyTicks = end + 2;
        // (Let go a tick before the end, so the landing below can move and turn them.)
        Freeze.hold(caster, end - 1);
        Freeze.hold(victim, end - 1);
        if (spec.cutscene().intangivel()) {
            Intangible.hold(caster, end);
            Intangible.hold(victim, end);
        }
        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        if (victim instanceof ServerPlayer player) {
            screen.add(player);
        }
        if (spec.cutscene().ativa()) {
            for (ServerPlayer player : screen) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.EsferaCutscene(caster.getId(), victim.getId(), awakened));
            }
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL + "#bills", awakened));
        Function<String, Vec3> where = onde -> "alvo".equals(onde) ? victim.position().add(0, 1, 0) : caster.position().add(0, 1, 0);
        SkillSounds.play(level, spec.sons(), "uso", 0, tp, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster, tp);

        Tasks.later(tp, () -> {
            if (!caster.isAlive()) {
                return;
            }
            // Gone in black streaks, the target with him.
            Fx.effects(level, SKILL, awakened, "tp", caster.position(), forward);
            Fx.effects(level, SKILL, awakened, "tp", victim.position(), forward);
            SkillSounds.play(level, spec.sons(), "tp", 0, 0, where, screen);
            hideFromOthers(level, caster, victim, end - tp);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.PlaySkillAnimation(victim.getId(), SKILL + "#alvo", awakened));
            SkillSounds.play(level, spec.sons(), "espaco", spec.spaceTick() - tp, end - spec.spaceTick(), where, screen);
        });
        SkillSounds.play(level, spec.sons(), "carga", spec.chargeTick(), spec.throwTick() - spec.chargeTick(), where, screen);
        SkillSounds.play(level, spec.sons(), "lancar", spec.throwTick(), spec.flyTick() - spec.throwTick(), where, screen);
        SkillSounds.play(level, spec.sons(), "implodir", spec.flyTick(), blast - spec.flyTick(), where, screen);
        SkillSounds.play(level, spec.sons(), "contato", blast, end - blast, where, screen);

        Tasks.later(blast, () -> {
            // The sun hits it in space (no explosion up there; danoEspaco 0 by default).
            if (victim.isAlive() && spec.danoEspaco() > 0) {
                Combat.hurt(caster, victim, spec.danoEspaco());
            }
        });

        Tasks.later(end, () -> {
            hideFromOthers(level, caster, victim, 0);
            if (caster.isAlive()) {
                // Back on the ground in black streaks, facing where the target will come down.
                Fx.effects(level, SKILL, awakened, "voltar", caster.position(), forward);
                SkillSounds.play(level, spec.sons(), "voltar", 0, 0, onde -> caster.position().add(0, 1, 0), screen);
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.PlaySkillAnimation(victim.getId(), "", false));
            if (!victim.isAlive() || victim.isRemoved()) {
                return;
            }
            Vec3 from = caster.isAlive() ? caster.position() : home;
            EsferaSpec.Queda queda = spec.queda();
            Vec3 spot = from.add(forward.scale(queda.distancia()));
            double groundY = ground(level, new Vec3(spot.x, from.y + 40, spot.z), 120);
            Vec3 top = new Vec3(spot.x, groundY + queda.altura(), spot.z);
            float yaw = (float) (Mth.atan2(spot.z - from.z, spot.x - from.x) * Mth.RAD_TO_DEG) - 90;
            if (caster instanceof ServerPlayer player) {
                player.teleportTo(level, from.x, from.y, from.z, yaw, -10);
            } else if (caster.isAlive()) {
                caster.setYRot(yaw);
                caster.setYHeadRot(yaw);
                caster.setYBodyRot(yaw);
            }
            if (victim instanceof ServerPlayer player) {
                player.teleportTo(level, top.x, top.y, top.z, yaw + 180, 60);
            } else {
                victim.teleportTo(top.x, top.y, top.z);
            }
            fall(caster, level, spec, victim, groundY, awakened, screen);
        });
        return true;
    }

    // The target coming down from the sky, straight, into a crater.
    private static void fall(LivingEntity caster, ServerLevel level, EsferaSpec spec, LivingEntity victim, double groundY, boolean awakened,
            List<ServerPlayer> screen) {
        EsferaSpec.Queda queda = spec.queda();
        double speed = Math.max(0.2, queda.velocidade());
        int max = (int) Math.ceil(queda.altura() / speed) + 60;
        Fx.effects(level, SKILL, awakened, "queda", victim, max);
        // The sun comes down with it, on top of it.
        Vec3 head = victim.position().add(0, victim.getBbHeight(), 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.EsferaFall(victim.getId(), head, head, awakened));
        SkillSounds.play(level, spec.sons(), "queda", 0, (int) Math.ceil(queda.altura() / speed), onde -> victim.position(), screen);
        Tasks.add(age -> {
            if (!victim.isAlive() || victim.isRemoved()) {
                return true;
            }
            victim.fallDistance = 0;
            Vec3 pos = victim.position();
            var below = level.clip(new ClipContext(pos, pos.subtract(0, speed + 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
            boolean landed = victim.onGround() || below.getType() != HitResult.Type.MISS || age >= max;
            if (!landed) {
                victim.setDeltaMovement(0, -speed, 0);
                victim.hurtMarked = true;
                return false;
            }
            Vec3 at = below.getType() != HitResult.Type.MISS ? below.getLocation() : pos;
            victim.setDeltaMovement(0, 0, 0);
            victim.hurtMarked = true;
            land(caster, level, spec, victim, at, awakened, screen);
            return true;
        });
    }

    private static void land(LivingEntity caster, ServerLevel level, EsferaSpec spec, LivingEntity victim, Vec3 at, boolean awakened,
            List<ServerPlayer> screen) {
        EsferaSpec.Queda queda = spec.queda();
        Combat.hurt(caster, victim, queda.dano());
        double radius = Math.max(1, queda.raio());
        for (LivingEntity hit : Combat.inRadius(level, at, radius, caster)) {
            if (hit == victim) {
                continue;
            }
            double k = Math.min(1, Combat.center(hit).distanceTo(at) / radius);
            Combat.hurt(caster, hit, (float) (queda.danoArea() * (1 - k * 0.6)));
            Combat.pushFrom(hit, at, queda.empurrao() * (1 - k * 0.5), 0.5 + 0.4 * (1 - k));
        }
        Fx.effects(level, SKILL, awakened, "impacto", at, new Vec3(0, 1, 0));
        SkillSounds.play(level, spec.sons(), "impacto", 0, 0, onde -> at, screen);
        if (queda.quebrarBlocos() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            Agarrao.crater(level, at.add(0, 0.5, 0), queda.raioCratera(), queda.profundidadeCratera());
            // The crater opens under it: it ends up at the bottom.
            Tasks.later(2, () -> victim.setDeltaMovement(0, -1.5, 0));
        }
    }

    // While they are in space nobody else sees them (0 ticks: back again). Neither of them is hidden from the two
    // screens in the cutscene, which draw them in space.
    private static void hideFromOthers(ServerLevel level, LivingEntity caster, LivingEntity victim, int ticks) {
        for (ServerPlayer player : level.players()) {
            if (player == caster || player == victim || player.distanceToSqr(caster) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new ModPayloads.HideEntity(caster.getId(), ticks));
            PacketDistributor.sendToPlayer(player, new ModPayloads.HideEntity(victim.getId(), ticks));
        }
    }

    // The top of the ground under a point, looking down up to reach blocks (the point less 40 when there is none).
    private static double ground(ServerLevel level, Vec3 from, double reach) {
        var hit = level.clip(new ClipContext(from, from.subtract(0, reach, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? from.y - 40 : hit.getLocation().y;
    }
}
