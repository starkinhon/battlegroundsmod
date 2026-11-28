// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.SkillSwap;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

// Kakashi's Sharingan, made clear for a fight (test with friends, Stark 2026-11-25: "o método de cópia tá confuso"). Before, C
// copied "the last skill anyone used near him in the last 10 s", which the player never saw: he pressed C blind and got a random
// skill or "nada pra copiar". Now the eye MEMORIZES: each copyable skill used within 24 blocks of a Kakashi goes into his
// Sharingan right then (a soft sound and "Sharingan: Rasengan" on the action bar), the C slot shows that skill's icon with a red
// bar running out (15 s), and C uses exactly what is shown. A newer skill seen takes the place of the older one only after 10 s
// (LOCK); using the copy puts C on a 20 s recharge (Stark 2026-11-27). Same idea as before: one copy, 20% stronger.
public final class SharinganMemory {
    // How far the eye sees a skill being used, and for how long it keeps it.
    public static final double RANGE = 24;
    public static final int KEEP = 300;
    // Once it copies something the eye holds it this long before a newer skill can take its place (Stark 2026-11-27: it
    // kept copying one thing over the other).
    public static final int LOCK = 200;
    // The Sharingan Copy is Kakashi's C (slot 2); in the Dungeon's Montar Kit it can sit on any key of another soul.
    private static final int COPY_SLOT = 2;

    public record Seen(NrCommon.Cast cast, long until) {
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    // Client: what the local player's Sharingan holds (power id, slot), until when (ms), and when it was seen (ms, the slot's flash).
    private static String clientPower = "";
    private static int clientSlot = -1;
    private static long clientUntil;
    private static long clientTotal = 1;
    public static long clientSeenAt;

    private SharinganMemory() {
    }

    // The key that runs the Sharingan Copy for this entity, or -1.
    public static int copyKey(LivingEntity e) {
        if (e instanceof Player player) {
            for (int target : SkillSwap.TARGETS) {
                SkillSwap.Ref ref = SkillSwap.ref(player, target);
                if (ref != null && "kakashi".equals(ref.power().id()) && ref.slot() == COPY_SLOT) {
                    return target;
                }
            }
        }
        if (!e.hasData(ModAttachments.POWER) || !"kakashi".equals(e.getData(ModAttachments.POWER).power)) {
            return -1;
        }
        return e instanceof Player player && SkillSwap.ref(player, COPY_SLOT) != null ? -1 : COPY_SLOT;
    }

    // A skill was just used: every Sharingan near sees it.
    static void saw(ServerLevel level, NrCommon.Cast cast) {
        if (!NrCommon.copyable(cast)) {
            return;
        }
        AABB box = AABB.ofSize(cast.at(), RANGE * 2, RANGE * 2, RANGE * 2);
        for (LivingEntity k : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != cast.caster() && e.isAlive() && e.position().distanceToSqr(cast.at()) <= RANGE * RANGE)) {
            if (copyKey(k) < 0) {
                continue;
            }
            Seen held = SEEN.get(k.getUUID());
            if (held != null && cast.time() < held.cast().time() + LOCK && cast.time() <= held.until()) {
                continue;
            }
            SEEN.put(k.getUUID(), new Seen(cast, cast.time() + KEEP));
            if (k instanceof ServerPlayer player) {
                Power p = Powers.get(cast.power());
                Component name = p != null ? p.abilityName(cast.slot()) : Component.literal("?");
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.kakashi.seen", name)
                        .withStyle(ChatFormatting.RED), true);
                player.playNotifySound(SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.5f, 1.8f);
                PacketDistributor.sendToPlayer(player, new ModPayloads.SharinganSeen(cast.power(), cast.slot(), KEEP));
            }
        }
    }

    // What C copies now (the skill shown on the slot), or null.
    public static NrCommon.Cast current(LivingEntity kakashi) {
        Seen s = SEEN.get(kakashi.getUUID());
        if (s == null) {
            return null;
        }
        if (kakashi.level().getGameTime() > s.until || s.cast.caster().level() != kakashi.level()) {
            forget(kakashi);
            return null;
        }
        return s.cast;
    }

    // Used (or gone): the eye is empty again until it sees another skill.
    public static void forget(LivingEntity kakashi) {
        if (SEEN.remove(kakashi.getUUID()) != null && kakashi instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.SharinganSeen("", -1, 0));
        }
    }

    // A copy that could not go off goes back into the eye with the time it had left.
    static void keep(LivingEntity kakashi, NrCommon.Cast cast) {
        long until = Math.max(kakashi.level().getGameTime() + 60, cast.time() + KEEP);
        SEEN.put(kakashi.getUUID(), new Seen(cast, until));
        if (kakashi instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.SharinganSeen(cast.power(), cast.slot(),
                    (int) (until - kakashi.level().getGameTime())));
        }
    }

    public static void clear() {
        SEEN.clear();
    }

    // ---------- client ----------

    public static void receive(String power, int slot, int ticks) {
        clientPower = power;
        clientSlot = slot;
        clientTotal = Math.max(1, ticks * 50L);
        clientUntil = Util.getMillis() + ticks * 50L;
        if (ticks > 0) {
            clientSeenAt = Util.getMillis();
        }
    }

    // The skill the local Sharingan holds, or null.
    public static SkillSwap.Ref clientHeld() {
        if (clientSlot < 0 || Util.getMillis() > clientUntil) {
            return null;
        }
        Power p = Powers.get(clientPower);
        return p == null || clientSlot >= PowerData.SLOT_COUNT ? null : new SkillSwap.Ref(p, clientSlot);
    }

    // 1 just seen, 0 forgotten.
    public static float clientLeft() {
        return Math.max(0, Math.min(1, (clientUntil - Util.getMillis()) / (float) clientTotal));
    }
}
