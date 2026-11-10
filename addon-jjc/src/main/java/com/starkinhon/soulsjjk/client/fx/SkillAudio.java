package com.starkinhon.soulsjjk.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// The sounds of the moves, played here so they can be cut off: each one is kept under its owner (the fighter whose move
// it is, or any other key such as a Domain's) until it ends, and stop(owner) silences all of them at once. The pause
// menu pauses them like every vanilla sound.
public final class SkillAudio {
    private record Playing(SoundInstance sound, long born) {
    }

    private static final Map<Object, List<Playing>> PLAYING = new HashMap<>();

    private SkillAudio() {
    }

    // A sound from the server (ModPayloads.OwnedSound). screen: for our ears only, wherever we are.
    public static void play(int owner, ResourceLocation id, Vec3 pos, float volume, float pitch, boolean screen, long seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (screen && minecraft.player != null) {
            pos = minecraft.player.getEyePosition();
        }
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        play(owner, new SimpleSoundInstance(event, SoundSource.PLAYERS, volume, pitch, RandomSource.create(seed), pos.x, pos.y, pos.z));
    }

    public static void play(Object owner, SoundInstance sound) {
        Minecraft.getInstance().getSoundManager().play(sound);
        PLAYING.computeIfAbsent(owner, k -> new ArrayList<>()).add(new Playing(sound, ScreenFx.now()));
    }

    public static void stop(Object owner) {
        List<Playing> list = PLAYING.remove(owner);
        if (list == null) {
            return;
        }
        var manager = Minecraft.getInstance().getSoundManager();
        for (Playing p : list) {
            manager.stop(p.sound());
        }
    }

    // Forgets the ones that ended on their own (given a couple of ticks to start).
    public static void tick() {
        if (PLAYING.isEmpty()) {
            return;
        }
        var manager = Minecraft.getInstance().getSoundManager();
        long now = ScreenFx.now();
        PLAYING.values().forEach(list -> list.removeIf(p -> now - p.born() > 2 && !manager.isActive(p.sound())));
        PLAYING.values().removeIf(List::isEmpty);
    }

    public static void clear() {
        PLAYING.clear();
    }
}
