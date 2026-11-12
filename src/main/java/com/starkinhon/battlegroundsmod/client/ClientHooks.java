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
        // The punches have their own keyframe animations (soco.combo1..5); the Forja pose is the fallback.
        String keyframes = SkillAnimations.FIST_PREFIX + "combo" + (Math.floorMod(step, ComboSpec.get().golpes().size()) + 1);
        if (KeyframeAnimation.of(keyframes).isPresent()) {
            SkillAnimations.play(entityId, keyframes, false);
        } else {
            SkillAnimations.play(entityId, ComboSpec.get().golpe(step).animation());
        }
        var player = Minecraft.getInstance().player;
        if (player != null && player.getId() == entityId && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            player.swing(InteractionHand.MAIN_HAND, false);
        }
    }

    public static void quest(String id, int count, int goal, int x, int z, int flags) {
        QuestHud.set(id, count, goal, x, z, (flags & 1) != 0);
    }

    public static void flightState(int entityId, int state) {
        FlightClient.state(entityId, state);
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
        // Inside Gojo's domain only Gojo's moves show.
        if (!skill.startsWith("gojo.") && com.starkinhon.battlegroundsmod.client.fx.DomainScene.blocksCutscenes()) {
            return;
        }
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

    public static void ownedSound(int owner, net.minecraft.resources.ResourceLocation sound, Vec3 pos, float volume, float pitch, boolean screen,
            long seed) {
        com.starkinhon.battlegroundsmod.client.fx.SkillAudio.play(owner, sound, pos, volume, pitch, screen, seed);
    }

    // owner's move was cut off: its sounds stop and its effects fade out now.
    public static void stopOwnerFx(int owner) {
        com.starkinhon.battlegroundsmod.client.fx.SkillAudio.stop(owner);
        SkillEffects.stopOwner(owner);
    }

    public static void playEffects(String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int entityId, int ticks, int owner) {
        SkillEffects.play(skill, awakened, momento, pos, dir, entityId, ticks, owner);
        if ("olhos".equals(momento)) {
            Aura.skillUsed(skill, entityId);
        } else if ("olhosLigar".equals(momento) || "olhosDesligar".equals(momento)) {
            Aura.skillEyes(skill, entityId, "olhosLigar".equals(momento), ticks);
        }
    }

    public static void susanooArm(int casterId, int targetId, int phase, int a, int b) {
        com.starkinhon.battlegroundsmod.client.fx.SusanooArm.update(casterId, targetId, phase, a, b);
    }

    public static void sukunaCut(net.minecraft.world.phys.Vec3 origin, net.minecraft.world.phys.Vec3 velocity, net.minecraft.world.phys.Vec3 span, float reach, int seed) {
        com.starkinhon.battlegroundsmod.client.fx.DismantleRenderer.add(origin, velocity, span, reach, seed);
    }

    public static void sukunaSlash(com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaSlash p) {
        com.starkinhon.battlegroundsmod.client.fx.SukunaCutsRenderer.slash(p.origin(), p.dir(), p.across(), p.unit(), p.speed(), p.reach(), p.seed(),
                p.flags(), p.ground());
    }

    public static void sukunaBar(com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaBar p) {
        com.starkinhon.battlegroundsmod.client.fx.SukunaCutsRenderer.bar(p.center(), p.dir(), p.len(), p.thick(), p.shift(), p.skew(), p.rough(),
                p.s0(), p.s1(), p.seed(), p.star());
    }

    public static void sukunaWaffle(com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaWaffle p) {
        com.starkinhon.battlegroundsmod.client.fx.SukunaCutsRenderer.waffle(p.origin(), p.dir(), p.up(), p.size(), p.above(), p.speed(), p.reach(),
                p.seed());
    }

    public static void sukunaStar(com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaStar p) {
        com.starkinhon.battlegroundsmod.client.fx.SukunaCutsRenderer.star(p.pos(), p.size(), p.seed());
    }

    public static void sukunaCravada(com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaCravada p) {
        com.starkinhon.battlegroundsmod.client.fx.CravadaRenderer.event(p.entity(), p.kind(), p.ticks(), p.a(), p.b(), p.seed());
    }

    public static void sukunaClivar(com.starkinhon.battlegroundsmod.network.ModPayloads.SukunaClivar p) {
        com.starkinhon.battlegroundsmod.client.fx.ClivarRenderer.event(p.entity(), p.kind(), p.ticks(), p.a(), p.b(), p.seed());
    }

    public static void sukunaModel(float value) {
        com.starkinhon.battlegroundsmod.client.ModelBarHud.set(value);
    }

    public static void fuga(int casterId, int phase, net.minecraft.world.phys.Vec3 pos, net.minecraft.world.phys.Vec3 dir) {
        com.starkinhon.battlegroundsmod.client.fx.FugaRenderer.receive(casterId, phase, pos, dir);
    }

    public static void shoulderCutscene(int casterId, int targetId, int ticks) {
        if (com.starkinhon.battlegroundsmod.client.fx.DomainScene.blocksCutscenes()) {
            return;
        }
        if (CutscenePlayer.start("madara.susanoo", casterId, targetId, false)) {
            return;
        }
        ShoulderCutscene.start(casterId, targetId, ticks);
    }

    public static void bopCutscene(int casterId, int targetId, boolean awakened, int endTick) {
        if (com.starkinhon.battlegroundsmod.client.fx.DomainScene.blocksCutscenes()) {
            return;
        }
        if (CutscenePlayer.start("bills.destruction_bop", casterId, targetId, false)) {
            return;
        }
        BopCutscene.start(casterId, targetId, awakened, endTick);
    }

    public static void grabCutscene(int casterId, int targetId, boolean awakened, int impactTick) {
        if (com.starkinhon.battlegroundsmod.client.fx.DomainScene.blocksCutscenes()) {
            return;
        }
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
        com.starkinhon.battlegroundsmod.client.fx.FugaRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.DismantleRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.SukunaCutsRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.ClivarRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.CravadaRenderer.clear();
        com.starkinhon.battlegroundsmod.client.ModelBarHud.set(0);
        com.starkinhon.battlegroundsmod.client.fx.HakaiRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.EnergyRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.SphereRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.BlackBoxRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.AzulRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.VermelhoRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.BlackFlashRenderer.clear();
        LimpBody.clear();
        com.starkinhon.battlegroundsmod.client.fx.VoidFlashScene.clear();
        com.starkinhon.battlegroundsmod.client.fx.RoxoRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.AizenFx.clear();
        com.starkinhon.battlegroundsmod.client.fx.SwordFx.clear();
        com.starkinhon.battlegroundsmod.client.fx.PunchFxRenderer.clear();
        com.starkinhon.battlegroundsmod.client.fx.SubFx.clear();
    }

    public static void hideEntity(int entityId, int ticks) {
        Vanish.hide(entityId, ticks);
    }

    public static void ashDeath(int entityId) {
        AshDeath.start(entityId);
    }

    public static void hakaiCutscene(int casterId, int targetId, boolean awakened) {
        if (com.starkinhon.battlegroundsmod.client.fx.DomainScene.blocksCutscenes()) {
            return;
        }
        if (CutscenePlayer.start("bills.hakai", casterId, targetId, false)) {
            return;
        }
        HakaiCutscene.start(casterId, targetId, awakened);
    }

    public static void sukunaDomain(int casterId, net.minecraft.world.phys.Vec3 center, int mode, int a, int b, boolean part) {
        com.starkinhon.battlegroundsmod.client.fx.SukunaDomainScene.event(casterId, center, mode, a, b, part);
    }

    public static void expansionDuel(int gojoId, int sukunaId, int mode, int a, int b, int c) {
        com.starkinhon.battlegroundsmod.client.fx.DuelScene.event(gojoId, sukunaId, mode, a, b, c);
    }

    public static void gojoDomain(int casterId, net.minecraft.world.phys.Vec3 center, boolean awakened, int mode, int other) {
        com.starkinhon.battlegroundsmod.client.fx.DomainScene.event(casterId, center, awakened, mode, other);
    }

    public static void soulCeremony(int entityId, int killerId, String power, int color, boolean full) {
        com.starkinhon.battlegroundsmod.client.fx.SoulCeremonyFx.start(entityId, killerId, power, color, full);
    }

    public static void soulCeremonyDrop(int entityId, int itemId) {
        com.starkinhon.battlegroundsmod.client.fx.SoulCeremonyFx.drop(entityId, itemId);
    }

    public static void hakaiDeath(int entityId, boolean awakened) {
        AshDeath.startHakai(entityId, awakened);
    }

    public static void esferaCutscene(int casterId, int targetId, boolean awakened) {
        if (com.starkinhon.battlegroundsmod.client.fx.DomainScene.blocksCutscenes()) {
            return;
        }
        // The trip to space keeps running (it draws the space, its bars and its line); only the camera is the editor's
        // (CutsceneCamera gives back the view the player had before the first of them).
        EsferaCutscene.start(casterId, targetId, awakened);
        if (EsferaCutscene.active()) {
            CutscenePlayer.start("bills.esfera", casterId, targetId, true);
        }
    }

    public static void esferaFall(int targetId, Vec3 start, Vec3 lastHead, boolean awakened) {
        // The Esfera da Destruição Planetária: the sun comes down from the sky on top of the target, until it lands.
        var spec = com.starkinhon.battlegroundsmod.skill.EsferaSpec.get(awakened);
        int ticks = (int) Math.ceil(spec.queda().altura() / Math.max(0.2, spec.queda().velocidade())) + 1;
        com.starkinhon.battlegroundsmod.client.fx.EsferaRenderer.ride(targetId, lastHead, ticks, (float) spec.esfera().fallRadius());
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

    public static void blackBox(int id, int mode, Vec3 base, Vec3 size, boolean awakened) {
        com.starkinhon.battlegroundsmod.client.fx.BlackBoxRenderer.box(id, mode, base, size, awakened);
    }

    public static void azulControl(boolean on) {
        com.starkinhon.battlegroundsmod.client.fx.AzulRenderer.control(on);
    }

    public static void azul(int id, int mode, Vec3 pos, float radius, float charge, int style) {
        com.starkinhon.battlegroundsmod.client.fx.AzulRenderer.orb(id, mode, pos, radius, charge, style);
    }

    public static void vermelho(int id, int owner, int mode, float charge, boolean flip) {
        com.starkinhon.battlegroundsmod.client.fx.VermelhoRenderer.orb(id, owner, mode, charge, flip);
    }

    public static void vermelhoBlast(Vec3 from, Vec3 dir, float range, float radius, float speed, float charge) {
        com.starkinhon.battlegroundsmod.client.fx.VermelhoRenderer.blast(from, dir, range, radius, speed, charge);
    }

    public static void voidFlash(int caster, Vec3 center) {
        com.starkinhon.battlegroundsmod.client.fx.VoidFlashScene.start(caster, center);
    }

    public static void blackFlash(int kind, int entity, Vec3 pos, Vec3 dir, float charge, int ticks) {
        com.starkinhon.battlegroundsmod.client.fx.BlackFlashRenderer.event(kind, entity, pos, dir, charge, ticks);
    }

    public static void limp(int entity, int phase, int ticks, float strength) {
        LimpBody.event(entity, phase, ticks, strength);
    }

    public static void roxo(int id, int owner, int mode, int tick, float charge) {
        com.starkinhon.battlegroundsmod.client.fx.RoxoRenderer.orb(id, owner, mode, tick, charge);
    }

    public static void roxoShot(int id, int owner, Vec3 from, Vec3 dir, Vec3 motion, Vec3 size) {
        com.starkinhon.battlegroundsmod.client.fx.RoxoRenderer.shot(id, owner, from, dir, motion, size);
    }

    public static void roxoIrrestrito(Vec3 center, Vec3 size, Vec3 timing, int blast) {
        com.starkinhon.battlegroundsmod.client.fx.RoxoRenderer.irrestrito(center, size, timing, blast);
    }

    public static void subFx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        com.starkinhon.battlegroundsmod.client.fx.SubFx.receive(kind, id, pos, dir, a, b, c);
    }

    public static void punchFx(int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) {
        com.starkinhon.battlegroundsmod.client.fx.PunchFxRenderer.receive(kind, entityId, pos, dir, a, b);
    }

    public static void swordFx(int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) {
        com.starkinhon.battlegroundsmod.client.fx.SwordFx.receive(kind, entityId, pos, dir, a, b);
    }

    public static void aizenFx(int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) {
        com.starkinhon.battlegroundsmod.client.fx.AizenFx.receive(kind, entityId, pos, dir, a, b);
    }

    public static void energyBall(int id, int mode, Vec3 pos, float radius, float grow, boolean small) {
        com.starkinhon.battlegroundsmod.client.fx.EnergyRenderer.ball(id, mode, pos, radius, grow, small);
    }

    public static void planetSphere(int id, int mode, Vec3 pos, float radius, boolean giant) {
        com.starkinhon.battlegroundsmod.client.fx.SphereRenderer.sphere(id, mode, pos, radius, giant);
    }

    public static void planetBlast(Vec3 pos, boolean awakened) {
        com.starkinhon.battlegroundsmod.client.fx.SphereRenderer.blast(pos, awakened);
    }

    public static void bossBarStyle(UUID barId, String texture, int frames, int width, int height, int glow) {
        BossBars.style(barId, texture, frames, width, height, glow);
    }

    public static void fala(int entityId, String text, int color1, int color2) {
        FalaRender.add(entityId, text, color1, color2);
    }

    public static void bossBarToggle(boolean on) {
        BossBars.enabled = on;
    }

    public static void bossCutscene(int entityId, int zoom, int sky, int back, Vec3 lookAt) {
        if (com.starkinhon.battlegroundsmod.client.fx.DomainScene.blocksCutscenes()) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (CutscenePlayer.start("madara.boss_meteoro", entityId, player != null ? player.getId() : -1, false)) {
            return;
        }
        BossCutscene.start(entityId, zoom, sky, back, lookAt);
    }

    public static void auraState(int entityId, String power) {
        Aura.state(entityId, power);
    }

    public static void outfitState(int entityId, String power) {
        com.starkinhon.battlegroundsmod.client.outfit.Outfits.set(entityId, power);
    }

    public static void sheathState(int entityId, boolean carrying, boolean drawn, boolean stretched, boolean gourd) {
        com.starkinhon.battlegroundsmod.client.outfit.Sheaths.set(entityId, carrying, drawn, stretched);
        com.starkinhon.battlegroundsmod.client.outfit.GourdLayer.set(entityId, gourd);
    }

    public static void openChallenges(String universe) {
        Minecraft.getInstance().setScreen(new ChallengeScreen(universe));
    }

    // The universe screen; the first one (choosing where to live) isn't reopened over itself.
    public static void openUniverses(boolean first) {
        Minecraft mc = Minecraft.getInstance();
        if (first && (mc.screen instanceof UniverseScreen || mc.player == null)) {
            return;
        }
        if (first && mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            // Wait for the loading screen or a menu to close; the server asks again in a few seconds.
            return;
        }
        mc.setScreen(new UniverseScreen(first));
    }

    public static void openPowerSelect() {
        Minecraft.getInstance().setScreen(new PowerSelectScreen());
    }

    // Makima: the wheel of the mobs she keeps (C held) or the Ritual's sheet (B).
    public static void makimaOpen(int kind, int count, java.util.List<String> entries) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (kind == 0) {
            mc.setScreen(new MakimaWheel(count, entries));
        } else {
            mc.setScreen(new MakimaSheet(count, entries));
        }
    }
}
