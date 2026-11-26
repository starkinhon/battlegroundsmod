// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power;

import java.util.Map;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

// Skills that need the soul's weapon in the main hand (Stark, 2026-11-26): without it the key does nothing, the slot shows
// dim on the HUD and the action bar says which weapon is missing. Only the main hand counts (where the weapon display
// shows). Only players: bosses and bots never go through here. A key holding another soul's skill (Montar Kit, SkillSwap)
// is never locked, since that player has no way to hold the other soul's weapon.
// Keys by base slot (0 Z, 1 X, 2 C, SLOT_ULT V, SLOT_EXTRA B); a Shift variant follows its normal key.
public final class WeaponSkills {
    private static final Set<Integer> ALL = Set.of(0, 1, 2, PowerData.SLOT_ULT, PowerData.SLOT_EXTRA);
    // Chosen with Stark: Rukia and Byakuya keep their Kidō (B) free; Nanami's Colapso, Hora Extra and Contra are punches;
    // Aizen needs the sword only for the Kyōka Suigetsu (his kidō, Hōgyoku and finger are bare-handed); Sasuke's Kusanagi
    // comes with the soul but his jutsu are all hand signs, so nothing is locked.
    private static final Map<String, Set<Integer>> KEYS = Map.of(
            com.starkinhon.battlegroundsmod.power.powers.MoonDemon.ID, ALL,
            "miwa", ALL,
            "nobara", ALL,
            "rukia", Set.of(0, 1, 2, PowerData.SLOT_ULT),
            "byakuya", Set.of(0, 1, 2, PowerData.SLOT_ULT),
            "nanami", Set.of(0, 2),
            "manipulator", Set.of(1));

    private WeaponSkills() {
    }

    // The weapon this key of the player's own soul needs and the main hand doesn't hold, or null when it can be used.
    public static Item missing(Player player, Power power, int slot) {
        if (player == null || power == null) {
            return null;
        }
        Set<Integer> keys = KEYS.get(power.id());
        if (keys == null || !keys.contains(PowerData.baseSlot(slot)) || SkillSwap.ref(player, slot) != null) {
            return null;
        }
        Item weapon = PowerLogic.soulWeapon(power.id());
        return weapon == null || player.getMainHandItem().is(weapon) ? null : weapon;
    }

    // Kokushibo's sword stretch (H) needs the sword too (putting it back never does).
    // It is his own key, never moved by Montar Kit or SkillSwap, so it always checks the hand. The awakening (the
    // stretched sword) also ends by itself when the sword leaves the main hand (MoonDemon.onPlayerTick).
    public static Item missingForStretch(Player player) {
        if (player == null || !player.hasData(com.starkinhon.battlegroundsmod.ModAttachments.POWER)) {
            return null;
        }
        String power = player.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).power;
        if (!com.starkinhon.battlegroundsmod.power.powers.MoonDemon.ID.equals(power)) {
            return null;
        }
        Item weapon = PowerLogic.soulWeapon(power);
        return weapon == null || player.getMainHandItem().is(weapon) ? null : weapon;
    }

    public static Component message(Item weapon) {
        return Component.translatable("message.battlegroundsmod.precisa_arma", weapon.getDescription());
    }
}
