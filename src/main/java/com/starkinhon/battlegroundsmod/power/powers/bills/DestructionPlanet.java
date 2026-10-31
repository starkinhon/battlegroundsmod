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
import com.starkinhon.battlegroundsmod.skill.DestructionPlanetSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + V: the Destruction Touch on a planet (bills.destruction_touch_planeta.json, see DestructionPlanetSpec). Bills
// snaps his fingers and leaves for outer space in a cutscene only he sees (client/PlanetCutscene): half of the square
// Earth of the C blows up like in the anime. Everyone else sees him vanish in a flash. When he is back the target lights
// up for a moment and the blast of the V goes off where it stands, pulverizing everything around.
public final class DestructionPlanet {
    public static final String SKILL = DestructionPlanetSpec.SKILL;

    private DestructionPlanet() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        DestructionPlanetSpec spec = DestructionPlanetSpec.get(awakened);
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
        int back = spec.returnTick();
        int impact = spec.impactTick();

        // Away in space: he stands still where he was, untouchable, and the others don't see him.
        data.busyTicks = back;
        Freeze.hold(caster, back);
        if (spec.cutscene().intangivel()) {
            Intangible.hold(caster, back);
        }
        if (spec.cutscene().ativa() && caster instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.PlanetCutscene(caster.getId(), awakened));
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Tasks.later(space, () -> {
            if (caster.isAlive()) {
                PacketDistributor.sendToPlayersTrackingEntity(caster, new ModPayloads.HideEntity(caster.getId(), back - space));
                Fx.effects(level, SKILL, awakened, "espaco", caster, back - space);
            }
        });

        Vec3[] lastFeet = {victim.position()};
        Function<String, Vec3> where = onde -> "alvo".equals(onde) ? lastFeet[0] : caster.position().add(0, 1, 0);
        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        if (victim instanceof ServerPlayer player) {
            screen.add(player);
        }
        List<ServerPlayer> mine = screen.isEmpty() || !(caster instanceof ServerPlayer) ? List.of() : List.of(screen.get(0));
        SkillSounds.play(level, spec.sons(), "uso", 0, space, where, screen);
        SkillSounds.play(level, spec.sons(), "espaco", space, spec.flashTick() - space, where, mine);
        SkillSounds.play(level, spec.sons(), "clarao", spec.flashTick(), spec.pauseTick() - spec.flashTick(), where, mine);
        SkillSounds.play(level, spec.sons(), "planeta", spec.blastTick(), spec.returnTick() - spec.blastTick(), where, mine);
        Fx.effects(level, SKILL, awakened, "uso", caster, space);

        Tasks.add(age -> {
            if (victim.isAlive() && !victim.isRemoved()) {
                lastFeet[0] = victim.position();
            }
            if (age < back) {
                return false;
            }
            if (age == back) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), 0));
                Fx.effects(level, SKILL, awakened, "voltar", caster, 0);
                // The target lights up: it is coming.
                Fx.effects(level, SKILL, awakened, "aviso", victim, impact - back);
                SkillSounds.play(level, spec.sons(), "aviso", 0, impact - back, where, screen);
                return false;
            }
            if (age < impact) {
                return false;
            }
            Vec3 at = lastFeet[0];
            DestructionTouch.explode(caster, level, spec.explosao(), at, awakened);
            SkillSounds.play(level, spec.sons(), "impacto", 0, 0, onde -> at, screen);
            Fx.effects(level, SKILL, awakened, "impacto", at, new Vec3(0, 1, 0));
            return true;
        });
        return true;
    }
}
