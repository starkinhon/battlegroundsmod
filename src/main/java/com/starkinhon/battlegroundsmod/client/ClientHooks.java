package com.starkinhon.battlegroundsmod.client;

import java.util.UUID;

import com.starkinhon.battlegroundsmod.client.fx.MeteorRenderer;
import com.starkinhon.battlegroundsmod.client.fx.AshDeath;
import com.starkinhon.battlegroundsmod.client.fx.SkillEffects;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;
import com.starkinhon.battlegroundsmod.skill.CutsceneSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

// Client-only entry points called from common code. Only call these behind a level.isClientSide check.
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void playSkillAnimation(int entityId, String skill, boolean awakened) {
        SkillAnimations.play(entityId, skill, awakened);
    }

    // The local player also swings the hand seen in first person (the body pose only shows from outside).
    public static void comboAnimation(int entityId, int step) {
        SkillAnimations.play(entityId, ComboSpec.get().golpe(step).animation());
        var player = Minecraft.getInstance().player;
        if (player != null && player.getId() == entityId && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            player.swing(InteractionHand.MAIN_HAND, false);
        }
    }

    public static void hitstop(int entityId, int ticks) {
        SkillAnimations.hold(entityId, ticks);
    }

    public static void startFaceCutscene(int entityId, String skill, boolean awakened) {
        FaceCutscene.start(entityId, SkillSpecs.get(skill, CutsceneSpec.Holder.class, awakened).cutscene());
    }

    public static void screenShake(float strength, int ticks) {
        ScreenShake.start(strength, ticks);
    }

    public static void meteorFall(String skill, boolean awakened, Vec3 start, Vec3 end, int delay, int fallTicks) {
        MeteorRenderer.start(skill, awakened, start, end, delay, fallTicks);
    }

    public static void playEffects(String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int entityId, int ticks) {
        SkillEffects.play(skill, awakened, momento, pos, dir, entityId, ticks);
    }

    public static void shoulderCutscene(int casterId, int targetId, int ticks) {
        ShoulderCutscene.start(casterId, targetId, ticks);
    }

    public static void ashDeath(int entityId) {
        AshDeath.start(entityId);
    }

    public static void bossBarStyle(UUID barId, String texture, int frames, int width, int height) {
        BossBars.style(barId, texture, frames, width, height);
    }

    public static void bossBarToggle(boolean on) {
        BossBars.enabled = on;
    }

    public static void bossCutscene(int entityId, int zoom, int sky, int back, Vec3 lookAt) {
        BossCutscene.start(entityId, zoom, sky, back, lookAt);
    }

    public static void openPowerSelect() {
        Minecraft.getInstance().setScreen(new PowerSelectScreen());
    }
}
