package com.starkinhon.battlegroundsmod.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dungeon's Modo Caos (challenge/Dungeon, Stark 2026-11-19: "derrotei o Shikamaru, quero o C dele"): a player's Z, X or C can
// run a skill of ANOTHER soul. The player's own soul stays the same (its passive, its B, its ult, its Shift variants of the other
// keys); only that key changes. Skills already work in any caster's hands (bosses use the same moves, Shigaraki's Absorver and
// Kakashi's Sharingan run other souls' skills), so the stolen skill is the owner's own Ability, nothing copied or rebuilt.
//
// Robustness (it must never break a soul): the swaps live in their own attachment (ModAttachments.SKILL_SWAPS), never inside the
// PowerData or the soul's Sincronia, so the soul itself is never touched. A swap that no longer resolves (unknown soul, a slot
// it lacks) is simply ignored and the key runs the soul's own skill. Only Z, X and C (slots 0-2) can be swapped: the ult and the
// B carry domains, transformations and the soul's own bar, which belong to their owner. They are cleared when a run starts and
// when the real character comes back (Dungeon.putBack, also after a crash at the next login).
public final class SkillSwap {
    // The keys that can hold another soul's skill: Z, X, C.
    public static final int KEYS = 3;

    public record Ref(Power power, int slot) {
    }

    // Client: the local player's swaps, as the server last sent them (SyncSkillSwaps).
    private static final Map<Integer, Ref> CLIENT = new HashMap<>();
    // Client: goes up each time the swaps change (the HUD shows the skill names again).
    public static int clientVersion;

    private SkillSwap() {
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    public static boolean swappable(int slot) {
        return slot >= 0 && slot < KEYS;
    }

    // The skill of another soul this key runs, or null (the soul's own). Players only: bosses always use their own kit.
    public static Ref ref(LivingEntity caster, int slot) {
        if (!(caster instanceof Player player) || !swappable(slot)) {
            return null;
        }
        if (player.level().isClientSide) {
            return CLIENT.get(slot);
        }
        return parse(player.getData(ModAttachments.SKILL_SWAPS)).get(slot);
    }

    public static Ability ability(LivingEntity caster, int slot) {
        Ref ref = ref(caster, slot);
        return ref == null ? null : PowerLogic.ability(ref.power(), ref.slot());
    }

    // A Shift press on a swapped key runs the swapped skill too (the soul's own Shift variant of that key would be a skill that
    // no longer matches what the key shows).
    public static int redirect(LivingEntity caster, int slot) {
        if (slot >= PowerData.SLOT_SHIFT && slot < PowerData.SLOT_SHIFT + KEYS) {
            int base = slot - PowerData.SLOT_SHIFT;
            if (ref(caster, base) != null) {
                return base;
            }
        }
        return slot;
    }

    // Server: puts power's skill at fromSlot on the player's key toSlot. False when it can't (not Z/X/C, no such skill).
    public static boolean set(ServerPlayer player, int toSlot, Power power, int fromSlot) {
        if (!swappable(toSlot) || power == null || !swappable(fromSlot) || PowerLogic.ability(power, fromSlot) == null) {
            return false;
        }
        Map<Integer, Ref> map = parse(player.getData(ModAttachments.SKILL_SWAPS));
        map.put(toSlot, new Ref(power, fromSlot));
        store(player, map);
        PowerData data = player.getData(ModAttachments.POWER);
        // a fresh key: the old skill's cooldown does not carry over
        data.cooldowns[toSlot] = 0;
        data.cooldowns[PowerData.SLOT_SHIFT + toSlot] = 0;
        sync(player);
        PowerLogic.sync(player);
        return true;
    }

    public static void remove(ServerPlayer player, int slot) {
        Map<Integer, Ref> map = parse(player.getData(ModAttachments.SKILL_SWAPS));
        if (map.remove(slot) != null) {
            store(player, map);
            sync(player);
        }
    }

    public static void clear(ServerPlayer player) {
        if (!player.getData(ModAttachments.SKILL_SWAPS).isEmpty()) {
            player.setData(ModAttachments.SKILL_SWAPS, List.of());
        }
        sync(player);
    }

    public static Map<Integer, Ref> all(ServerPlayer player) {
        return parse(player.getData(ModAttachments.SKILL_SWAPS));
    }

    // The name of the skill a key runs now (its swap's, or the soul's own).
    public static Component name(Player player, Power own, int slot) {
        Ref ref = ref(player, slot);
        if (ref != null) {
            return ref.power().abilityName(ref.slot());
        }
        return own == null ? Component.empty() : own.abilityName(slot);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncSkillSwaps(List.copyOf(player.getData(ModAttachments.SKILL_SWAPS))));
    }

    // Client: from the server.
    public static void receive(List<String> entries) {
        Map<Integer, Ref> next = parse(entries);
        if (!next.equals(CLIENT)) {
            clientVersion++;
        }
        CLIENT.clear();
        CLIENT.putAll(next);
    }

    private static void store(ServerPlayer player, Map<Integer, Ref> map) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<Integer, Ref> e : map.entrySet()) {
            list.add(e.getKey() + ":" + e.getValue().power().id() + ":" + e.getValue().slot());
        }
        player.setData(ModAttachments.SKILL_SWAPS, List.copyOf(list));
    }

    // "slot:power:slot" entries; anything that does not resolve any more is left out.
    private static Map<Integer, Ref> parse(List<String> entries) {
        Map<Integer, Ref> map = new HashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                continue;
            }
            try {
                int to = Integer.parseInt(parts[0]);
                int from = Integer.parseInt(parts[2]);
                Power power = Powers.get(parts[1]);
                if (swappable(to) && swappable(from) && power != null && PowerLogic.ability(power, from) != null) {
                    map.put(to, new Ref(power, from));
                }
            } catch (NumberFormatException ignored) {
                // a broken entry is dropped
            }
        }
        return map;
    }
}
