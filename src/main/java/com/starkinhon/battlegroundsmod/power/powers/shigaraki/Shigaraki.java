package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.Arrays;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Tomura Shigaraki (My Hero Academia), a Divine Soul. Ported from the approved preview
// (/mnt/project-files/forja/shigaraki-tentaculos, https://claude.ai/artifact/TxdF1xeyPc1i5BoeQ21Jvk): the game repeats the
// preview's timeline (seconds x 20), its shapes and its shaders (client/fx/shig).
//   Z Toque da Decadência (Toque): dash and grab, the body dries and crumbles from the hand; missed, a living crack.
//   X Onda Sônica (Onda): the scream, a cone of 46 blocks that throws everyone and shatters glass.
//   C Mãos do Chão (MaosDoChao): a hand print on the ground, a trap of hands that hold whoever steps on it.
//   B Absorver (Absorver, All For One): sees everyone's power, steals one for 15 s; B again uses it.
//   Shift+Z Regeneração (Regeneracao): stands still, the body remakes itself; strong heal, costs stamina.
//   V Tentáculos de Mãos (Tentaculos, the ult): five tentacles of hands out of his back; touched, decayed.
//   Shift+V Fim do Mundo (FimDoMundo): five fingers on the ground, the Decadência takes everything to the horizon.
public final class Shigaraki {
    public static final String ID = "shigaraki";
    public static final int COLOR = 0x8E1515;

    // ---- what the skills tell the clients (power/powers/sub/SubNet's channel; client/fx/shig/ShigFx draws them) ----
    // A seed of Decadência in the blocks (Decay.seed): id = the caster (his ground is safe), pos = the seed, dir = (speed,
    // reach, the ground's height), a = Decay.GROUND or STRUCTURE, b = the safe radius, c = 1 when blocks really go (the gamerules).
    public static final int SEED = 200;
    // The Decadência on a body (Decay.body): id = the body, pos = the touch, dir = (speed, ticks it takes, ticks so far),
    // a = 0 starts, 1 it died of it (crumbles), 2 over (lived).
    public static final int BODY = 201;
    // Z: id = caster, pos = where the dash starts, dir = where it ends, a = 0 grab, 1 miss; b = 1 at the contact (the
    // impact frame, only on the grab).
    public static final int TOUCH = 202;
    // X: id = caster, pos = the mouth, dir = the scream's way (flat), a = 0 breathes in, 1 the scream goes out.
    public static final int SCREAM = 203;
    // C: id = the trap, pos = the print's centre, dir = the way from Shigaraki to it, a = 0 the slap (the crack runs),
    // 1 armed, 2 caught (b = who), 3 gone.
    public static final int TRAP = 204;
    // B: id = caster. a = 0 the vision (b = ticks), 1 the drain (b = the victim, c = the element), 2 stolen (b = ticks
    // left, c = the element), 3 lost it.
    public static final int ABSORB = 205;
    // B again, the stolen power used: id = caster, pos = from, dir = to, a = the element, b = the target (or -1).
    public static final int USE = 206;
    // Shift+Z: id = caster, a = ticks.
    public static final int REGEN = 207;
    // V: id = caster, pos = his feet, dir = his facing (flat), a = 0 starts, 1 cut short.
    public static final int TENTACLES = 208;
    // Shift+V: id = caster, pos = his feet, dir = facing; a = 0 kneels, 1 the touch (pos = the hand, dir.y = 1 when blocks
    // really go, b = speed, c = reach), 2 cut short before the touch.
    public static final int END = 209;

    // The three powers B tells apart (when the victim has no soul of the mod, by what it is).
    public static final int FIRE = 0, LIGHTNING = 1, ICE = 2;

    private Shigaraki() {
    }

    public static Power create() {
        return new Power(ID, COLOR, 1.3f, List.of(
                new Ability(80, 0, Toque::use),
                new Ability(200, 0, Onda::use),
                new Ability(240, 0, MaosDoChao::use),
                new Ability(1200, 0, Tentaculos::use),
                new Ability(0, 0, Absorver::use)),
                Arrays.asList(new Ability(700, 0, Regeneracao::use), null, null, new Ability(3600, 0, FimDoMundo::use), null));
    }

    // The block under the feet (the preview's ground seeds sit on a block's centre, y = -0.5).
    static Vec3 groundSeed(Vec3 feet) {
        return new Vec3(Mth.floor(feet.x) + 0.5, Mth.floor(feet.y - 0.01) + 0.5, Mth.floor(feet.z) + 0.5);
    }

    // Flat direction from a to b (a's look when they are on top of each other).
    static Vec3 flatTo(LivingEntity from, Vec3 to) {
        Vec3 d = to.subtract(from.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        return flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(from) : flat.normalize();
    }

    // The end of a dash, exact: a player moves on their own client, where the dash's velocities drift on (friction) between
    // the server's ticks or get dropped (two in one client tick), so they end blocks short or past the goal; put them on it,
    // unless a wall stands between (the dash stops at walls).
    static void landOn(LivingEntity caster, Vec3 goal) {
        if (caster instanceof ServerPlayer p) {
            double dx = goal.x - p.getX(), dz = goal.z - p.getZ();
            int n = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 0.5);
            for (int i = 1; i <= n; i++) {
                if (!p.level().noCollision(p, p.getBoundingBox().move(dx * i / n, 0, dz * i / n))) {
                    return;
                }
            }
            p.connection.teleport(goal.x, p.getY(), goal.z, p.getYRot(), p.getXRot());
        }
    }

    static PowerData data(LivingEntity caster) {
        return caster.getData(ModAttachments.POWER);
    }
}
