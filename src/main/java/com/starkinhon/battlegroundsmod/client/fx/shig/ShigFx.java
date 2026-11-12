package com.starkinhon.battlegroundsmod.client.fx.shig;

import com.starkinhon.battlegroundsmod.client.fx.AshDeath;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.Shigaraki;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

// Shigaraki's effects on the client (power/powers/shigaraki tells them through SubNet, kinds 320 to 339): the approved
// preview's (forja/shigaraki-tentaculos) shapes, timings and shaders, drawn with the other world effects (FxWorld).
public final class ShigFx {
    private static float partial;

    private ShigFx() {
    }

    // The effects' clock in seconds (the game's pause-safe ticks, ScreenFx.now, plus this frame's partial tick).
    static double clock() {
        return (ScreenFx.now() + partial) / 20.0;
    }

    public static boolean handles(int kind) {
        return kind >= Shigaraki.SEED && kind <= Shigaraki.END;
    }

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        TentacleFx.prepare();
        switch (kind) {
            case Shigaraki.SEED -> DecayBlocks.seed(id, pos, dir, a, b, c > 0.5f);
            case Shigaraki.BODY -> {
                int mode = Math.round(a);
                if (mode == 0) {
                    AshDeath.startDecay(id, pos, (float) dir.x, (int) dir.y);
                } else if (mode == 1) {
                    AshDeath.decayDied(id);
                } else {
                    AshDeath.decayOver(id);
                }
            }
            case Shigaraki.TOUCH -> {
                // the grab's impact frame (manga, negative and positive), only on the hit and only near it
                var player = Minecraft.getInstance().player;
                if (a < 0.5f && b > 0.5f && player != null && player.position().distanceTo(dir) < 40) {
                    ScreenFx.impactFrame(new int[] {4, 3, 4, 3}, 1, 0xFFFFFF, dir, 0);
                }
            }
            case Shigaraki.SCREAM -> ScreamFx.receive(id, pos, dir, a);
            case Shigaraki.TRAP -> TrapFx.receive(id, pos, dir, a, b, c);
            case Shigaraki.ABSORB -> AbsorbFx.receive(id, pos, dir, a, b, c);
            case Shigaraki.USE -> {
                AbsorbFx.use(id, pos, dir, a, b);
                var player = Minecraft.getInstance().player;
                if (Math.round(a) != Shigaraki.ICE && b >= 0 && player != null && player.position().distanceTo(dir) < 40) {
                    int delay = Math.round(a) == Shigaraki.FIRE ? 2 + (int) Math.ceil(pos.distanceTo(dir) / 26 * 20) : 2;
                    ScreenFx.impactFrame(new int[] {4, 3}, 1, 0xFFFFFF, dir, delay);
                }
            }
            case Shigaraki.REGEN -> RegenFx.receive(id, b);
            case Shigaraki.TENTACLES -> TentacleFx.receive(id, pos, dir, a);
            case Shigaraki.END -> EndFx.receive(id, pos, dir, a, b, c);
            default -> {
            }
        }
    }

    public static boolean active() {
        return DecayBlocks.active() || TentacleFx.active() || ScreamFx.active() || TrapFx.active() || AbsorbFx.active() || RegenFx.active()
                || EndFx.active();
    }

    public static void tick() {
        if (Minecraft.getInstance().level == null) {
            DecayBlocks.clear();
            TentacleFx.clear();
            ScreamFx.clear();
            TrapFx.clear();
            AbsorbFx.clear();
            RegenFx.clear();
            EndFx.clear();
            return;
        }
        EndFx.tick();
        DecayBlocks.tick();
        RegenFx.tick();
    }

    // With the world effects (FxWorld.draw: camera-relative, the view's rotation in ModelViewMat).
    public static void render(Vec3 camera, float partialTick) {
        partial = partialTick;
        if (!active()) {
            return;
        }
        DecayBlocks.render(camera);
        TrapFx.render(camera);
        TentacleFx.render(camera);
        RegenFx.render(camera);
        AbsorbFx.render(camera, partialTick);
        ScreamFx.render(camera);
        EndFx.render(camera);
    }
}
