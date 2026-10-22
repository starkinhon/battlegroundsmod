package com.starkinhon.battlegroundsmod.client;

import java.util.UUID;

import com.starkinhon.battlegroundsmod.client.fx.MeteorRenderer;
import com.starkinhon.battlegroundsmod.skill.CutsceneSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

// Client-only entry points called from common code. Only call these behind a level.isClientSide check.
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void playSkillAnimation(int entityId, String skill, boolean awakened) {
        SkillAnimations.play(entityId, skill, awakened);
    }

    public static void startFaceCutscene(int entityId, String skill, boolean awakened) {
        FaceCutscene.start(entityId, SkillSpecs.get(skill, CutsceneSpec.Holder.class, awakened).cutscene());
    }

    public static void screenShake(float strength, int ticks) {
        ScreenShake.start(strength, ticks);
    }

    public static void meteorFall(String skill, boolean awakened, Vec3 start, Vec3 end, int delay) {
        MeteorRenderer.start(skill, awakened, start, end, delay);
    }

    public static void bossBarStyle(UUID barId, String texture, int frames, int width, int height) {
        BossBars.style(barId, texture, frames, width, height);
    }

    public static void bossBarToggle(boolean on) {
        BossBars.enabled = on;
    }

    public static void openPowerSelect() {
        Minecraft.getInstance().setScreen(new PowerSelectScreen());
    }
}
