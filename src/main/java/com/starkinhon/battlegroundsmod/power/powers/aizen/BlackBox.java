package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.BlackBoxSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Black Box (Hado #90, Kurohitsugi), Aizen's Z (aizen.black_box.json). Held: a box of black light with violet edges
// builds itself around the target, shaped to the target's size, its walls rising from the ground, and it grows the longer
// the key is held (up to escalaMax times). Let go: it shuts, lances stab through it hitting everyone inside, then it caves
// in and shatters. The target is held still from the press to the shatter. The box is drawn on the clients
// (client/fx/BlackBoxRenderer) from what is sent here: every tick while it is held, once when it shuts.
// No blocks are broken.
public final class BlackBox {
    public static final String SKILL = BlackBoxSpec.SKILL;
    private static final double VIEW_DISTANCE = 160;
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextId;

    // Sent to the clients: 0 = held (value = charge 0..1), 1 = shut (value = final charge), 2 = gone.
    public static final int HELD = 0;
    public static final int SHUT = 1;
    public static final int GONE = 2;

    private BlackBox() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        BlackBoxSpec spec = BlackBoxSpec.get(awakened);
        BlackBoxSpec.Caixa caixa = spec.caixa();
        LivingEntity target = lock(caster, spec.alvo().alcance());
        if (target == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return false;
        }
        int grow = Math.max(1, caixa.crescerTicks());
        int full = Math.max(caixa.minTicks() + 1, caixa.maxTicks());
        // Bosses don't hold keys: they let go somewhere between a quick box and a big one.
        int bossCharge = caixa.minTicks() + RANDOM.nextInt(Math.max(1, grow));
        double scaleMax = Math.max(1, caixa.escalaMax());
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;
        // The box stands where the target stood when it was cast, sized to the target.
        Vec3 base = target.position();
        double halfWidth = Math.max(0.3, target.getBbWidth() / 2) + caixa.folga();
        double height = Math.max(0.6, target.getBbHeight()) + caixa.folga() * 1.5;
        int closeAt = Math.max(1, caixa.fecharTicks());
        BlackBoxSpec.Lancas lancas = spec.lancas();
        int stabs = Math.max(1, lancas.golpes());
        int stabTicks = Math.max(stabs, lancas.duracaoTicks());
        int caveIn = Math.max(1, lancas.quebrarTicks());
        int afterRelease = closeAt + stabTicks + caveIn;

        // Held still from the press until it shatters (let go right away if it is cut short).
        Freeze.hold(target, full + afterRelease + 2);
        data.busyTicks = full + 4;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        double[] scale = {1};
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0)
                : base.add(0, height * scale[0] / 2, 0);
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        SkillSounds.play(level, spec.sons(), "uso", 0, full, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", base.add(0, 0.05, 0), Vec3.ZERO);

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts || !target.isAlive()) {
                // Cut short: the box fades without shutting.
                send(level, id, GONE, base, halfWidth * scale[0], height * scale[0], 0, awakened);
                Freeze.free(target);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            double charge = Math.min(1, age / (double) grow);
            scale[0] = 1 + (scaleMax - 1) * smooth(charge);
            send(level, id, HELD, base, halfWidth * scale[0], height * scale[0], (float) charge, awakened);
            now.busyTicks = Math.max(now.busyTicks, 2);
            boolean letGo = caster instanceof Player ? !now.abilityHeld : age >= bossCharge;
            if (age + 1 < full && !(letGo && age + 1 >= Math.max(1, caixa.minTicks()))) {
                return false;
            }
            // Let go: the box shuts on whatever size it reached.
            now.busyTicks = closeAt + 2;
            caster.swing(InteractionHand.MAIN_HAND, true);
            shut(caster, level, spec, id, base, halfWidth * scale[0], height * scale[0], (float) charge, scale[0], scaleMax, target, awakened,
                    where, screen);
            return true;
        });
        return true;
    }

    private static void shut(LivingEntity caster, ServerLevel level, BlackBoxSpec spec, int id, Vec3 base, double halfWidth, double height,
            float charge, double scale, double scaleMax, LivingEntity target, boolean awakened, Function<String, Vec3> where,
            List<ServerPlayer> screen) {
        BlackBoxSpec.Lancas lancas = spec.lancas();
        int closeAt = Math.max(1, spec.caixa().fecharTicks());
        int stabs = Math.max(1, lancas.golpes());
        int stabTicks = Math.max(stabs, lancas.duracaoTicks());
        int caveIn = Math.max(1, lancas.quebrarTicks());
        int end = closeAt + stabTicks + caveIn;
        // Damage grows with the size it reached.
        double k = scaleMax > 1 ? (scale - 1) / (scaleMax - 1) : 1;
        float total = (float) Mth.lerp(Mth.clamp(k, 0, 1), spec.dano().minimo(), spec.dano().maximo());
        float lanceShare = Mth.clamp(spec.dano().lancas(), 0, 1);
        float perStab = total * lanceShare / stabs;
        float last = total * (1 - lanceShare);
        AABB box = new AABB(base.x - halfWidth, base.y - 0.1, base.z - halfWidth, base.x + halfWidth, base.y + height, base.z + halfWidth);
        Vec3 middle = box.getCenter();

        send(level, id, SHUT, base, halfWidth, height, charge, awakened);
        SkillSounds.play(level, spec.sons(), "fechar", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "fechar", middle, Vec3.ZERO);

        List<Integer> stabAt = new ArrayList<>();
        for (int i = 0; i < stabs; i++) {
            stabAt.add(closeAt + (int) Math.round(i * (stabTicks - 1) / (double) Math.max(1, stabs - 1)));
        }
        Tasks.add(age -> {
            if (age == closeAt + 2 && caster.isAlive()) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
            }
            if (stabAt.contains(age)) {
                for (LivingEntity inside : inside(level, box, caster)) {
                    Combat.hurtMagic(caster, inside, perStab);
                }
                SkillSounds.play(level, spec.sons(), "lanca", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "lanca", middle, Vec3.ZERO);
            }
            if (age < end) {
                return false;
            }
            for (LivingEntity inside : inside(level, box, caster)) {
                Combat.hurtMagic(caster, inside, last);
            }
            Freeze.free(target);
            SkillSounds.play(level, spec.sons(), "quebrar", 0, 0, where, screen);
            Fx.effects(level, SKILL, awakened, "quebrar", middle, Vec3.ZERO);
            return true;
        });
    }

    private static List<LivingEntity> inside(ServerLevel level, AABB box, LivingEntity caster) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && Combat.isTarget(e, caster));
    }

    // Bosses go for their own target; players for whoever they last fought if it is in front of them and in sight, else
    // whoever is under the crosshair.
    private static LivingEntity lock(LivingEntity caster, double range) {
        if (caster instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target != null && Combat.isTarget(target, caster) && target.distanceTo(caster) <= range ? target : null;
        }
        LivingEntity aimed = Combat.raycastTarget(caster, range);
        if (aimed != null) {
            return aimed;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= range && caster.hasLineOfSight(fought)
                && fought.getEyePosition().subtract(caster.getEyePosition()).normalize().dot(caster.getLookAngle()) > 0.8) {
            return fought;
        }
        return null;
    }

    private static void send(ServerLevel level, int id, int mode, Vec3 base, double halfWidth, double height, float value, boolean awakened) {
        ModPayloads.BlackBox payload = new ModPayloads.BlackBox(id, mode, base, new Vec3(halfWidth, height, value), awakened);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(base) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
