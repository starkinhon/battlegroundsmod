package com.starkinhon.battlegroundsmod.client;

import java.util.UUID;

import com.starkinhon.battlegroundsmod.client.cutscene.CutscenePlayer;
import com.starkinhon.battlegroundsmod.client.fx.MeteorRenderer;
import com.starkinhon.battlegroundsmod.client.fx.AshDeath;
import com.starkinhon.battlegroundsmod.client.fx.Aura;
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

    public static void guardPose(int entityId, boolean on) {
        SkillAnimations.guard(entityId, on);
    }

    public static void hitstop(int entityId, int ticks) {
        SkillAnimations.hold(entityId, ticks);
    }

    // Each skill's cutscene first looks for one made in the Editor de Cutscenes (client/cutscene); with none, the
    // hand-made one plays.
    public static void startFaceCutscene(int entityId, String skill, boolean awakened) {
        var player = Minecraft.getInstance().player;
        int watcher = player != null && player.getId() != entityId ? player.getId() : -1;
        if (CutscenePlayer.start(skill, entityId, watcher, false)) {
            return;
        }
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
        if ("olhos".equals(momento)) {
            Aura.skillUsed(skill, entityId);
        } else if ("olhosLigar".equals(momento) || "olhosDesligar".equals(momento)) {
            Aura.skillEyes(skill, entityId, "olhosLigar".equals(momento), ticks);
        }
    }

    public static void susanooArm(int casterId, int targetId, int phase, int a, int b) {
        com.starkinhon.battlegroundsmod.client.fx.SusanooArm.update(casterId, targetId, phase, a, b);
    }

    public static void shoulderCutscene(int casterId, int targetId, int ticks) {
        if (CutscenePlayer.start("madara.susanoo", casterId, targetId, false)) {
            return;
        }
        ShoulderCutscene.start(casterId, targetId, ticks);
    }

    public static void bopCutscene(int casterId, int targetId, boolean awakened, int endTick) {
        if (CutscenePlayer.start("bills.destruction_bop", casterId, targetId, false)) {
            return;
        }
        BopCutscene.start(casterId, targetId, awakened, endTick);
    }

    public static void grabCutscene(int casterId, int targetId, boolean awakened, int impactTick) {
        if (CutscenePlayer.start("madara.agarrao", casterId, targetId, false)) {
            return;
        }
        GrabCutscene.start(casterId, targetId, awakened, impactTick);
    }

    public static void katonBall(int id, int casterId, int mode, Vec3 pos, Vec3 velocity, float radius, float maxRadius, int ticks) {
        com.starkinhon.battlegroundsmod.client.fx.KatonBallRenderer.update(id, casterId, mode, pos, velocity, radius, maxRadius, ticks);
    }

    // The Izanami turned time back: whatever skill effects were still showing never happened.
    public static void rewind() {
        SkillEffects.clear();
        MeteorRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.KatonBallRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.HakaiRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.EnergyRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.PlanetaryRenderer.clear();
    }

    public static void hideEntity(int entityId, int ticks) {
        Vanish.hide(entityId, ticks);
    }

    public static void ashDeath(int entityId) {
        AshDeath.start(entityId);
    }

    public static void hakaiCutscene(int casterId, int targetId, boolean awakened) {
        if (CutscenePlayer.start("bills.hakai", casterId, targetId, false)) {
            return;
        }
        HakaiCutscene.start(casterId, targetId, awakened);
    }

    public static void hakaiDeath(int entityId, boolean awakened) {
        AshDeath.startHakai(entityId, awakened);
    }

    public static void esferaCutscene(int casterId, boolean awakened) {
        // The trip to space keeps running (it draws the space, its bars and its line); only the camera is the editor's.
        var before = Minecraft.getInstance().options.getCameraType();
        EsferaCutscene.start(casterId, awakened);
        if (EsferaCutscene.active() && CutscenePlayer.start("bills.esfera", casterId, -1, true)) {
            // The trip already switched to third person: give back the view the player had before it.
            CutscenePlayer.restoreTo(before);
        }
    }

    public static void esferaFall(int targetId, Vec3 start, Vec3 lastHead, boolean awakened) {
        com.starkinhon.battlegroundsmod.client.fx.EsferaRenderer.fall(targetId, start, lastHead, awakened);
    }

    public static void eraseAttack(String skill, Vec3 pos, float radius) {
        com.starkinhon.battlegroundsmod.client.fx.HakaiRenderer.erase(skill, pos, radius);
    }

    public static void hakaiBall(int id, int mode, Vec3 pos, int targetId, float radius, boolean awakened) {
        com.starkinhon.battlegroundsmod.client.fx.HakaiRenderer.ball(id, mode, pos, targetId, radius, awakened);
    }

    public static void hakaiShot(Vec3 start, Vec3 velocity, int ticks, boolean awakened) {
        com.starkinhon.battlegroundsmod.client.fx.HakaiRenderer.shot(start, velocity, ticks, awakened);
    }

    public static void energyBall(int id, int mode, Vec3 pos, float radius, float grow, boolean small) {
        com.starkinhon.battlegroundsmod.client.fx.EnergyRenderer.ball(id, mode, pos, radius, grow, small);
    }

    public static void planetSphere(int id, int mode, Vec3 pos, float radius, boolean giant) {
        com.starkinhon.battlegroundsmod.client.fx.PlanetaryRenderer.sphere(id, mode, pos, radius, giant);
    }

    public static void planetBlast(Vec3 pos, boolean awakened) {
        com.starkinhon.battlegroundsmod.client.fx.PlanetaryRenderer.blast(pos, awakened);
    }

    public static void bossBarStyle(UUID barId, String texture, int frames, int width, int height, int glow) {
        BossBars.style(barId, texture, frames, width, height, glow);
    }

    public static void bossBarToggle(boolean on) {
        BossBars.enabled = on;
    }

    public static void bossCutscene(int entityId, int zoom, int sky, int back, Vec3 lookAt) {
        var player = Minecraft.getInstance().player;
        if (CutscenePlayer.start("madara.boss_meteoro", entityId, player != null ? player.getId() : -1, false)) {
            return;
        }
        BossCutscene.start(entityId, zoom, sky, back, lookAt);
    }

    public static void auraState(int entityId, String power) {
        Aura.state(entityId, power);
    }

    public static void openPowerSelect() {
        Minecraft.getInstance().setScreen(new PowerSelectScreen());
    }
}
