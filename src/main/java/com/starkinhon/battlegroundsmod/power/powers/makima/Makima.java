package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.BossFalas;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Makima, the Control Devil (Divine Soul, Chainsaw Man), ported skill by skill from the 3D previews Stark approved on
// 2026-11-11 (forja/makima-*). Not strong or destructive: control, manipulation, puppets and chains.
// Passive Primeiro-Ministro: a blow that would kill her goes to a mob within 200 blocks (her puppets first) instead.
// Z Bang (finger gun; hold to lock up to 3; a target at 15% life or less is blown apart), X Corrente (a chain binds,
// stuns and drains stamina), Shift+X Ordem (Venha / Ajoelhe / Woof / Morra, by how the target stands), C Fantoche (mobs
// she kills are kept, up to 9; up to 3 out at once, hold C to pick), Shift+C Ratos (her eyes in the rats: enemies seen
// through walls; again on a rat to sink and come out by it), B Ritual dos Nomes (a sheet: one name per puppet, each one
// crushed crushes the target, up to 60 blocks through walls; without puppets her own blood, 20% of her life), V the
// souls she has killed rise as chained puppets with their own kit (none: chains out of the ground on everyone around),
// V again: "Morram".
public final class Makima {
    public static final String ID = "makima";
    // Her voice: the line's dark and bright shades (BossFalas).
    public static final int[] VOICE = {0xA3122A, 0xFFD6DC};
    // The puppets' voice, when they repeat a name.
    public static final int[] PUPPET_VOICE = {0x8A6A20, 0xF2E2C6};

    private Makima() {
    }

    public static Power create() {
        return new Power(ID, 0xC9665A, 0.95f, List.of(
                new Ability(0, 0, Bang::use),
                new Ability(0, 0, Corrente::use),
                new Ability(0, 0, Fantoche::use),
                new Ability(0, 0, Makima::ult),
                new Ability(0, 0, Ritual::use)),
                Arrays.asList(null, new Ability(0, 0, Ordem::use), new Ability(0, 0, Ratos::use), null, null));
    }

    public static boolean is(LivingEntity entity) {
        return entity != null && entity.hasData(ModAttachments.POWER) && ID.equals(entity.getData(ModAttachments.POWER).power);
    }

    // V: the souls she has killed, or the chains out of the ground when she has none (the boss too).
    private static boolean ult(LivingEntity caster, ServerLevel level) {
        if (Almas.canSummon(caster)) {
            return Almas.summon(caster, level);
        }
        return Corrente.ground(caster, level);
    }

    // V while her chains or her souls are out: "Morram." (PowerLogic.tryUse asks before the ult's own checks.)
    public static boolean ultAgain(LivingEntity caster) {
        if (!is(caster)) {
            return false;
        }
        return Corrente.morram(caster) || Almas.morram(caster);
    }

    // The rule of the whole kit: someone she sees as beneath her. Stunned for real, out of stamina, a player under half
    // stamina, a mob under half life, or anyone of a lower tier than hers.
    public static boolean inferior(LivingEntity caster, LivingEntity target) {
        if (Stun.real(target) || Stamina.exhausted(target)) {
            return true;
        }
        if (target instanceof Player) {
            if (Stamina.get(target) < Stamina.max(target) * 0.5) {
                return true;
            }
        } else if (target.getHealth() < target.getMaxHealth() * 0.5f) {
            return true;
        }
        return Tiers.of(target) > Tiers.of(caster);
    }

    public static boolean boss(LivingEntity target) {
        return target instanceof SoulBoss boss && boss.isBoss() && !Puppets.isPuppet(target);
    }

    // Her ring eyes lit for ticks (they stay lit while she uses her power).
    public static void eyes(LivingEntity entity, int ticks) {
        if (entity.level() instanceof ServerLevel level) {
            MkNet.send(level, MkNet.EYES, entity.getId(), entity.position(), ticks, 0, 0);
        }
    }

    // Someone obeying her: her rings in their eyes.
    public static void obeyEyes(LivingEntity entity, int ticks) {
        if (entity.level() instanceof ServerLevel level) {
            MkNet.send(level, MkNet.EYES, entity.getId(), entity.position(), ticks, 1, 0);
        }
    }

    // A line over her head (a lang key under fala.battlegroundsmod.makima., or a name written as it is).
    public static void say(LivingEntity speaker, String key) {
        BossFalas.line(speaker, "fala.battlegroundsmod.makima." + key, VOICE);
    }

    public static void sayText(LivingEntity speaker, String text, int[] colors) {
        BossFalas.line(speaker, text, colors);
    }

    // The world goes gray around pos for ticks.
    public static void gray(ServerLevel level, Vec3 pos, int ticks, float strength) {
        MkNet.send(level, MkNet.GRAY, 0, pos, ticks, strength, 0);
    }

    // Where her right hand is, held out in front (the Bang's fingertip, the X's chain).
    public static Vec3 hand(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        float yaw = caster.getYRot() * (float) Math.PI / 180f;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return caster.getEyePosition().add(0, -0.32, 0).add(right.scale(0.34)).add(look.scale(0.75));
    }

    // Hers and not to be hit by her own moves: herself and her puppets.
    public static boolean ours(LivingEntity caster, LivingEntity other) {
        return other == caster || Puppets.ownerOf(other) == caster;
    }
}
