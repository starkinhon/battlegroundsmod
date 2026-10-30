package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// C: Esfera da Destruição (bills.esfera.json). Bills leaves for outer space in a cutscene only he sees (client/
// EsferaCutscene): over the square Earth he charges a sun of destruction, says his line and throws it at the planet.
// Everyone else sees him vanish in a flash. When he is back, a sun the size of two meteors comes down slowly from 300
// blocks up following the target's head (client/fx/EsferaRenderer draws it with the same path), shaking the screen
// and burning it orange at the edges, and blows up on it with a reach of 200 blocks.
public final class Esfera {
    public static final String SKILL = EsferaSpec.SKILL;

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
        if (target == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return false;
        }
        LivingEntity victim = target;
        int space = spec.spaceTick();
        int fall = spec.fallTick();
        int impact = spec.impactTick();

        // Away in space: he stands still where he was, untouchable, and the others don't see him.
        data.busyTicks = fall;
        Freeze.hold(caster, fall);
        if (spec.cutscene().intangivel()) {
            Intangible.hold(caster, fall);
        }
        if (spec.cutscene().ativa() && caster instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.EsferaCutscene(caster.getId(), awakened));
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Tasks.later(space, () -> {
            if (caster.isAlive()) {
                PacketDistributor.sendToPlayersTrackingEntity(caster, new ModPayloads.HideEntity(caster.getId(), fall - space));
                Fx.effects(level, SKILL, awakened, "espaco", caster, fall - space);
            }
        });

        Vec3[] lastHead = {head(victim)};
        Function<String, Vec3> where = onde -> "alvo".equals(onde) ? lastHead[0] : caster.position().add(0, 1, 0);
        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        SkillSounds.play(level, spec.sons(), "uso", 0, space, where, screen);
        SkillSounds.play(level, spec.sons(), "espaco", space, spec.throwTick() - space, where, screen);
        SkillSounds.play(level, spec.sons(), "lancar", spec.throwTick(), fall - spec.throwTick(), where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster, space);

        Vec3[] start = {Vec3.ZERO};
        Destructible.Attack[] attack = {null};
        Tasks.add(age -> {
            if (victim.isAlive() && !victim.isRemoved()) {
                lastHead[0] = head(victim);
            }
            if (age < fall) {
                return false;
            }
            if (attack[0] != null) {
                // Erased on the way down (a hakaizinho): no impact.
                if (attack[0].gone()) {
                    return true;
                }
                attack[0].move(EsferaSpec.fallAt(start[0], lastHead[0], (age - fall) / (double) Math.max(1, impact - fall)));
            }
            if (age == fall) {
                // Back in the world: the sphere comes down from high above, a little from where Bills stands.
                Vec3 from = victim.position().subtract(caster.position());
                Vec3 side = new Vec3(-from.x, 0, -from.z);
                side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize();
                double height = spec.esfera().alturaQueda();
                start[0] = lastHead[0].add(side.scale(height * 0.15)).add(0, height, 0);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), 0));
                attack[0] = Destructible.track(level, caster, start[0], spec.esfera().raioQueda(),
                        () -> Destructible.sendErase(level, SKILL, attack[0].pos(), attack[0].radius()));
                Fx.effects(level, SKILL, awakened, "voltar", caster, 0);
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(lastHead[0]) < 400 * 400) {
                        PacketDistributor.sendToPlayer(player, new ModPayloads.EsferaFall(victim.getId(), start[0], lastHead[0], awakened));
                    }
                }
                // The whistle of the fall is heard where the target is (the sphere is too high up at first to be heard).
                SkillSounds.play(level, spec.sons(), "queda", 0, impact - fall, onde -> lastHead[0], screen);
                Fx.effects(level, SKILL, awakened, "queda", victim, impact - fall);
                return false;
            }
            if (age < impact) {
                // Everyone around feels it coming: the screen shakes a little and burns orange at the edges.
                if ((age - fall) % 20 == 0) {
                    Fx.effects(level, SKILL, awakened, "aviso", lastHead[0], new Vec3(0, -1, 0));
                }
                return false;
            }
            attack[0].end();
            Vec3 at = lastHead[0];
            explode(caster, level, spec, at);
            SkillSounds.play(level, spec.sons(), "impacto", 0, 0, onde -> at, screen);
            Fx.effects(level, SKILL, awakened, "impacto", at, new Vec3(0, -1, 0));
            return true;
        });
        return true;
    }

    private static void explode(LivingEntity caster, ServerLevel level, EsferaSpec spec, Vec3 at) {
        EsferaSpec.Impacto impacto = spec.impacto();
        double radius = Math.max(1, impacto.raio());
        for (LivingEntity hit : Combat.inRadius(level, at, radius, caster)) {
            double k = Math.min(1, Combat.center(hit).distanceTo(at) / radius);
            Combat.hurt(caster, hit, (float) (impacto.danoCentro() + (impacto.danoBorda() - impacto.danoCentro()) * k));
            Combat.pushFrom(hit, at, impacto.empurrao() * (1 - k * 0.5), 0.5 + 0.4 * (1 - k));
        }
        if (impacto.quebrarBlocos()) {
            Agarrao.crater(level, at.subtract(0, 1.6, 0), impacto.raioCratera(), impacto.profundidadeCratera());
        }
    }

    private static Vec3 head(LivingEntity entity) {
        return entity.position().add(0, entity.getBbHeight(), 0);
    }
}
