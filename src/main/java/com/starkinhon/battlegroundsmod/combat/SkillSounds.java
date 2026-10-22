package com.starkinhon.battlegroundsmod.combat;

import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.skill.Som;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

// Plays a skill's "sons" list the way the Forja schedules it (soundSchedule there). Volume above 1 is not louder,
// it only carries further: 16 blocks per point of volume, like vanilla.
public final class SkillSounds {
    private SkillSounds() {
    }

    // Plays every sound tied to momento, which happens "at" ticks from now. duration is how long that moment lasts (used by endless repeats); where
    // turns an "onde" into a position, asked again each time the sound plays so it follows moving things, and may
    // return null to skip. "tela" sounds play for the screen players only, without distance.
    public static void play(ServerLevel level, List<Som> sons, String momento, int at, int duration, Function<String, Vec3> where,
            List<ServerPlayer> screen) {
        if (sons == null) {
            return;
        }
        for (Som som : sons) {
            if (som == null || !momento.equals(som.momento())) {
                continue;
            }
            SoundEvent event = event(som.som());
            if (event == null) {
                continue;
            }
            int every = Math.max(0, som.repetirACadaTicks());
            int count = every <= 0 ? 1 : som.vezes() > 0 ? som.vezes() : Math.max(1, (int) Math.ceil(duration / (double) every));
            for (int i = 0; i < count; i++) {
                double k = every > 0 ? (duration > 0 && som.vezes() <= 0 ? i * every / (double) duration : count > 1 ? i / (double) (count - 1) : 0) : 0;
                float pitch = (float) (som.pitch() + (som.pitchFim() - som.pitch()) * Math.min(1, k));
                int delay = Math.max(0, at) + Math.max(0, som.atrasoTicks()) + i * every;
                Runnable sound = () -> {
                    if ("tela".equals(som.onde())) {
                        for (ServerPlayer player : screen) {
                            player.playNotifySound(event, SoundSource.PLAYERS, som.volume(), pitch);
                        }
                        return;
                    }
                    Vec3 pos = where.apply(som.onde());
                    if (pos != null) {
                        Fx.sound(level, pos, event, som.volume(), pitch);
                    }
                };
                if (delay == 0) {
                    sound.run();
                } else {
                    Tasks.later(delay, sound);
                }
            }
        }
    }

    private static SoundEvent event(String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.getOptional(location).orElseGet(() -> SoundEvent.createVariableRangeEvent(location));
    }
}
