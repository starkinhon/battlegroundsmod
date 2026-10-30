package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.skill.VfxSpec;
import com.starkinhon.battlegroundsmod.skill.VfxSpec.Efeito;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

// Plays a skill's "vfx" list on this client: the server says which moment happened where, the timing and look
// come from the skill file. Screen effects get weaker with distance (alcance) and are skipped past it.
public final class SkillEffects {
    private record Pending(String skill, Efeito e, Vec3 pos, Vec3 dir, int entity, int ticks, long at) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private SkillEffects() {
    }

    public static void play(String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int entity, int ticks) {
        List<Efeito> efeitos;
        try {
            VfxSpec spec = SkillSpecs.get(skill, VfxSpec.class, awakened);
            efeitos = spec.vfx();
        } catch (RuntimeException e) {
            BattlegroundsMod.LOGGER.warn("No effects for {}", skill, e);
            return;
        }
        if (efeitos == null) {
            return;
        }
        for (Efeito e : efeitos) {
            if (e == null || e.tipo() == null || !momento.equals(e.momento()) || Boolean.FALSE.equals(e.ativa())) {
                continue;
            }
            int delay = Math.max(0, FxWorld.i(e.inicio(), 0));
            if (delay == 0) {
                start(skill, e, pos, dir, entity, ticks);
            } else {
                PENDING.add(new Pending(skill, e, pos, dir, entity, ticks, ScreenFx.now() + delay));
            }
        }
    }

    public static void clear() {
        PENDING.clear();
        FxWorld.clear();
    }

    public static void tick() {
        if (Minecraft.getInstance().level == null) {
            PENDING.clear();
            FxWorld.clear();
            return;
        }
        long now = ScreenFx.now();
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(p -> {
            if (p.at() <= now) {
                due.add(p);
                return true;
            }
            return false;
        });
        for (Pending p : due) {
            start(p.skill(), p.e(), p.pos(), p.dir(), p.entity(), p.ticks());
        }
    }

    private static void start(String skill, Efeito e, Vec3 pos, Vec3 dir, int entity, int ticks) {
        var player = Minecraft.getInstance().player;
        // Effects tied to someone start on them, wherever the server measured.
        Vec3 anchored = FxAnchor.point(entity, e.seguir(), 1);
        if (anchored != null) {
            pos = anchored;
        }
        double distance = player == null ? 0 : player.getEyePosition().distanceTo(pos);
        double reach = FxWorld.d(e.alcance(), 64);
        float near = (float) Math.max(0, 1 - distance / Math.max(1, reach));
        int life = Boolean.TRUE.equals(e.duraMomento()) && ticks > 0 ? ticks : Math.max(1, FxWorld.i(e.vidaTicks(), 20));
        int cor = FxDraw.parse(e.cor(), 0xFFFFFF);
        float forca = (float) FxWorld.d(e.forca(), 1);
        switch (e.tipo()) {
            case "impactFrame" -> {
                if (near > 0) {
                    ScreenFx.impactFrame(modes(e.sequencia()), FxWorld.i(e.ticksPorQuadro(), 1), FxDraw.parse(e.cor(), 0xD01010), pos, 0);
                }
            }
            case "flash" -> {
                if (near > 0) {
                    ScreenFx.flash(cor, forca * near, life);
                }
            }
            case "blurRadial" -> {
                if (near > 0) {
                    ScreenFx.radialBlur(pos, forca * near, life);
                }
            }
            case "tremor" -> {
                if (near > 0) {
                    ScreenShake.start(forca * near, life);
                }
            }
            case "parar" -> FxWorld.stopFollowing(skill, entity);
            case "vinheta" -> ScreenFx.vignette(FxDraw.parse(e.cor(), 0xFF7A10), forca, life);
            case "fov" -> {
                if (near > 0) {
                    ScreenFx.fovKick(forca * near, life);
                }
            }
            case "distorcao" -> {
                Vec3 at = pos.add(dir.scale(FxWorld.d(e.deslocamento(), 0))).add(0, FxWorld.d(e.altura(), 0), 0);
                ScreenFx.warp(at, (float) FxWorld.d(e.raio(), 6), forca, (float) FxWorld.d(e.velocidade(), 8), warpMode(e.modo()), dir, cor,
                        (float) FxWorld.d(e.tinta(), 0), life, entity, e.seguir(), (float) FxWorld.d(e.altura(), 0));
            }
            default -> FxWorld.spawn(skill, e, pos, dir, life, entity);
        }
    }

    private static int warpMode(String modo) {
        if (modo == null) {
            return 0;
        }
        return switch (modo) {
            case "redemoinho" -> 1;
            case "lente" -> 2;
            case "tela" -> 3;
            default -> 0;
        };
    }

    // "branco", "cor", "invertido", "manga", "mangaInvertido"; anything else is a normal frame in between.
    private static int[] modes(List<String> sequencia) {
        if (sequencia == null || sequencia.isEmpty()) {
            return new int[] {0, 2, 1, 3, 4, 0};
        }
        int[] modes = new int[sequencia.size()];
        for (int i = 0; i < modes.length; i++) {
            String s = sequencia.get(i);
            modes[i] = s == null ? -1 : switch (s) {
                case "branco" -> 0;
                case "cor" -> 1;
                case "invertido" -> 2;
                case "manga" -> 3;
                case "mangaInvertido" -> 4;
                default -> -1;
            };
        }
        return modes;
    }
}
