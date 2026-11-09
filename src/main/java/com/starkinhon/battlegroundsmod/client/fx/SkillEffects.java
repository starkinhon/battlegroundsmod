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
    // owner: whose move it is (-1 unknown), so stopOwner can take its effects away when the move is cut off.
    private record Pending(String skill, Efeito e, Vec3 pos, Vec3 dir, int entity, int ticks, long at, int owner) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    // The owner of the effect being started now (read by FxWorld, Realismo and ScreenFx when they keep it).
    private static int starting = -1;

    private SkillEffects() {
    }

    public static void play(String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int entity, int ticks) {
        play(skill, awakened, momento, pos, dir, entity, ticks, -1);
    }

    public static void play(String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int entity, int ticks, int owner) {
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
                start(skill, e, pos, dir, entity, ticks, owner);
            } else {
                PENDING.add(new Pending(skill, e, pos, dir, entity, ticks, ScreenFx.now() + delay, owner));
            }
        }
    }

    static int owner() {
        return starting;
    }

    // owner's move was cut off (stunned, sealed, died, the Domain shattered): what was still to come never starts and
    // what is on now fades out quickly. Attacks already in flight are drawn by their own renderers and keep going.
    public static void stopOwner(int owner) {
        if (owner < 0) {
            return;
        }
        PENDING.removeIf(p -> p.owner() == owner);
        FxWorld.stopOwner(owner);
        Realismo.remove(new Realismo.Owner(owner));
        ScreenFx.stopOwner(owner);
        KatonBallRenderer.stopOwner(owner);
    }

    public static void clear() {
        PENDING.clear();
        FxWorld.clear();
        SkillAudio.clear();
        SusanooArm.clear();
        Realismo.clear();
    }

    public static void tick() {
        if (Minecraft.getInstance().level == null) {
            PENDING.clear();
            FxWorld.clear();
            SusanooArm.clear();
            DomainScene.clear();
            SukunaDomainScene.clear();
            SkillAudio.clear();
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
            start(p.skill(), p.e(), p.pos(), p.dir(), p.entity(), p.ticks(), p.owner());
        }
    }

    private static void start(String skill, Efeito e, Vec3 pos, Vec3 dir, int entity, int ticks, int owner) {
        starting = owner;
        try {
            begin(skill, e, pos, dir, entity, ticks);
        } finally {
            starting = -1;
        }
    }

    private static void begin(String skill, Efeito e, Vec3 pos, Vec3 dir, int entity, int ticks) {
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
            case "realismo" -> {
                Vec3 at = pos.add(dir.scale(FxWorld.d(e.deslocamento(), 0))).add(0, FxWorld.d(e.altura(), 0), 0);
                int type = Realismo.type(e.modo());
                // (left without vidaTicks, a volume lasts as long as its kind usually does)
                int span = e.vidaTicks() == null && !Boolean.TRUE.equals(e.duraMomento()) && type >= 0 ? Math.round(Realismo.defaults(type)[2] * 20) : life;
                Realismo.add(type, at, dir, (float) FxWorld.d(e.velocidade(), 0), (float) FxWorld.d(e.raio(), 0),
                        (float) FxWorld.d(e.comprimento(), 0), forca, FxDraw.parse(e.cor(), 0xFFFFFF), span,
                        e.seguir() == null ? -1 : entity, e.seguir(), starting < 0 ? null : new Realismo.Owner(starting));
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

    // "branco", "cor", "invertido", "manga", "mangaInvertido", and the Black Flash's "bfInvertido", "bfManga", "bfVermelho",
    // "bfVermelhoInvertido"; anything else is a normal frame in between.
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
                // the Black Flash's (Gojo): shaking, diffuse
                case "bfInvertido" -> 6;
                case "bfManga" -> 7;
                case "bfVermelho" -> 8;
                case "bfVermelhoInvertido" -> 9;
                default -> -1;
            };
        }
        return modes;
    }
}
