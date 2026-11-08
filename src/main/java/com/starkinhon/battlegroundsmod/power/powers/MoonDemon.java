package com.starkinhon.battlegroundsmod.power.powers;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.MoonFormSkill;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.YoruNoMiya;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import com.starkinhon.battlegroundsmod.combat.SwordArts;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// O Demonio da Lua (Kokushibo), Moon Breathing. Kit (Stark, 2026-11-07): Z, X, C and B the 1st, 2nd, 3rd and 5th Forms,
// V the 6th (the last form of the standard kit, the biggest and most destructive). All five look exactly as the pages Stark
// approved (forja/kokushibo-formas/).
// The stretched sword (Stark, 2026-11-08): a key of its own (H) stretches the sword and puts the awakened forms on the same
// keys, Z 7th, X 8th, C 9th, B 10th, V 14th, and Shift + V the 16th (the last and strongest, homing); pressed again, the
// sword comes back and so does the normal kit. Shift + V does nothing new with the normal sword.
public final class MoonDemon {
    public static final String ID = "moon_demon";
    // Between two stretches of the sword, so the key can't be spammed.
    private static final int STRETCH_COOLDOWN = 20;

    private static final Ability SEVENTH = new Ability(160, 0, MoonFormSkill::seventh);
    private static final Ability EIGHTH = new Ability(220, 0, MoonFormSkill::eighth);
    private static final Ability NINTH = new Ability(200, 0, MoonFormSkill::ninth);
    // The 14th also goes off mid combo, a defense and attack in one (Stark, 2026-11-08).
    private static final Ability FOURTEENTH = new Ability(400, 0, MoonFormSkill::fourteenth).asBeatenCounter();
    private static final Ability TENTH = new Ability(300, 0, MoonFormSkill::tenth);
    private static final Ability SIXTEENTH = new Ability(400, 0, MoonFormSkill::sixteenth);

    private MoonDemon() {
    }

    public static Power create() {
        return new Power(ID, 0x9B4DCA, 1.1f, List.of(
                new Ability(80, 0, YoruNoMiya::use),
                new Ability(120, 0, MoonFormSkill::second).asBeatenCounter(),
                new Ability(160, 0, MoonFormSkill::third),
                new Ability(400, 0, MoonFormSkill::sixth),
                new Ability(240, 0, MoonFormSkill::fifth).asBeatenCounter()),
                // Shift + V: the 16th with the sword stretched (see swap)
                Arrays.asList(null, null, null, SIXTEENTH));
    }

    public static boolean stretched(LivingEntity who) {
        return who != null && who.getData(ModAttachments.POWER).moonSword;
    }

    // The move one of his keys runs now: the awakened form with the sword stretched, else the normal one.
    public static Ability swap(LivingEntity caster, Power power, int slot, Ability normal) {
        if (power == null || !ID.equals(power.id())) {
            return normal;
        }
        boolean stretched = stretched(caster);
        if (!stretched) {
            return slot == PowerData.SLOT_SHIFT_ULT ? PowerLogic.ability(power, PowerData.SLOT_ULT) : normal;
        }
        return switch (slot) {
            case 0 -> SEVENTH;
            case 1 -> EIGHTH;
            case 2 -> NINTH;
            case PowerData.SLOT_ULT -> FOURTEENTH;
            case PowerData.SLOT_EXTRA -> TENTH;
            case PowerData.SLOT_SHIFT_ULT -> SIXTEENTH;
            default -> normal;
        };
    }

    // The name a slot shows (the awakened form's with the sword stretched).
    public static Component abilityName(Power power, Player who, int slot) {
        if (power == null || !ID.equals(power.id())) {
            return power == null ? Component.empty() : power.abilityName(slot);
        }
        boolean stretched = stretched(who);
        if (!stretched) {
            return power.abilityName(slot == PowerData.SLOT_SHIFT_ULT ? PowerData.SLOT_ULT : slot);
        }
        int index = switch (slot) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case PowerData.SLOT_ULT -> 3;
            case PowerData.SLOT_EXTRA -> 4;
            case PowerData.SLOT_SHIFT_ULT -> 8;
            default -> -1;
        };
        return index < 0 ? power.abilityName(slot) : Component.translatable("ability." + BattlegroundsMod.MODID + "." + ID + ".awakened." + index);
    }

    // The H key: the sword stretches (the flesh swells and grows its three blades) or comes back.
    public static void stretch(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        if (!ID.equals(data.power) || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - data.moonSwordAt < STRETCH_COOLDOWN) {
            return;
        }
        data.moonSwordAt = now;
        data.moonSword = !data.moonSword;
        if (data.moonSword) {
            SubCommon.sound(player.serverLevel(), player.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.6f);
            SubCommon.sound(player.serverLevel(), player.position(), SoundEvents.SLIME_BLOCK_BREAK, 1.6f, 0.5f);
            SubCommon.sound(player.serverLevel(), player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.8f, 0.6f);
        } else {
            SubCommon.sound(player.serverLevel(), player.position(), SoundEvents.SLIME_BLOCK_PLACE, 1.4f, 0.7f);
        }
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.moon_sword." + (data.moonSword ? "on" : "off"))
                .withStyle(s -> s.withColor(data.moonSword ? 0xC9A2FF : 0xE8C26A)), true);
        PowerLogic.sync(player);
        // the sword out of the sheath, so everyone sees it stretch (or shrink back) in his hand
        PowerLogic.enterCombat(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ModPayloads.SwordFx(SwordArts.FX_MOON_STRETCH, player.getId(),
                player.position(), Vec3.ZERO, data.moonSword ? 1 : 0, 0));
    }
}
