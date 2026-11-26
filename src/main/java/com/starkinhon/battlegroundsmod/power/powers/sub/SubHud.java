// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.SkillSwap;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Kame;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Piccolo;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.Inumaki;

import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// What a SubAlma's player must SEE to use a skill in a fight (test with friends, Stark 2026-11-25: the skills they called
// confusing all hid a rule or a timing). Two things, sent to that player only, and only when they change:
// - a key that is ready because of the fight: Piccolo's C (Hellzone) and Kame's V (Mafuba) only work on a stunned target,
//   so their slot lights up while one is in the aim;
// - a small bar by the crosshair: Inumaki's throat (it bursts at full) and how long Shikamaru's shadow still holds (the
//   4 s ceiling or the stamina, whichever ends first).
public final class SubHud {
    public static final int NONE = 0;
    public static final int THROAT = 1;
    public static final int SHADOW = 2;

    // Server: a meter a skill reported this tick (Shikamaru's shadow): {kind, value, game time}.
    private static final Map<UUID, float[]> REPORTED = new HashMap<>();
    // Server: what each player was last sent: {ready slot, kind, value in 1/50}.
    private static final Map<UUID, int[]> SENT = new HashMap<>();

    // Client: the last state from the server.
    private static int clientReady = -1;
    private static int clientKind = NONE;
    private static float clientValue;
    private static long clientAt;

    private SubHud() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SubHud::onTick);
        NeoForge.EVENT_BUS.addListener(SubHud::onLogout);
    }

    // A running skill says how full its meter is now (it fades out a few ticks after the skill stops reporting).
    public static void meter(LivingEntity caster, int kind, float value) {
        if (caster instanceof ServerPlayer) {
            REPORTED.put(caster.getUUID(), new float[] {kind, value, caster.level().getGameTime()});
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 2 != 0
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        String power = player.getData(ModAttachments.POWER).power;
        int ready = -1;
        int kind = NONE;
        float value = 0;
        if ("piccolo".equals(power) && SkillSwap.ref(player, 2) == null && player.getData(ModAttachments.POWER).cooldowns[2] <= 0
                && Piccolo.hellzoneReady(player, level)) {
            ready = 2;
        } else if ("kame".equals(power) && SkillSwap.ref(player, PowerData.SLOT_ULT) == null && Kame.mafubaReady(player)) {
            ready = PowerData.SLOT_ULT;
        }
        if ("inumaki".equals(power)) {
            value = Inumaki.throatFraction(player);
            kind = value > 0.005f ? THROAT : NONE;
        }
        float[] r = REPORTED.get(player.getUUID());
        if (r != null) {
            if (level.getGameTime() - (long) r[2] <= 3) {
                kind = (int) r[0];
                value = r[1];
            } else {
                REPORTED.remove(player.getUUID());
            }
        }
        int[] now = {ready, kind, kind == NONE ? 0 : Math.round(value * 50)};
        int[] last = SENT.get(player.getUUID());
        if (last != null && last[0] == now[0] && last[1] == now[1] && last[2] == now[2]) {
            return;
        }
        if (last == null && ready < 0 && kind == NONE) {
            return;
        }
        SENT.put(player.getUUID(), now);
        PacketDistributor.sendToPlayer(player, new ModPayloads.SubHudState(ready, kind, now[2] / 50f));
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        REPORTED.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        SENT.clear();
        REPORTED.clear();
    }

    // ---------- client ----------

    public static void receive(int ready, int kind, float value) {
        clientReady = ready;
        clientKind = kind;
        clientValue = value;
        clientAt = Util.getMillis();
    }

    // The key lit as ready, or -1 (the server sends only changes, so the last one holds).
    public static int clientReady() {
        return clientReady;
    }

    public static int clientKind() {
        return clientKind;
    }

    public static float clientValue() {
        return clientValue;
    }

    public static long clientAt() {
        return clientAt;
    }
}
