package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.AzulSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Blue (Azul), Gojo's Z (gojo.azul.json), the same as the Skill Visualizer's "Z · Azul" scenes.
// The orb shows in his hand at once. Let go within segurado.decidirTicks it is the tap: the small orb flies to where he
// aims, locks there and pulls whoever is near slowly for clique.duracaoTicks. Held longer it charges between his hands,
// growing up to orbe.escalaMax times; let go (or held too long) it follows his aim for segurado.duracaoTicks, pulls much
// harder (whoever reaches it is held spinning inside), tears blocks out of the ground and crushes them in the middle,
// then implodes. The orb is drawn on the clients (client/fx/AzulRenderer) from what is sent here every tick.
public final class Azul {
    public static final String SKILL = AzulSpec.SKILL;
    // The held one's body animation (both hands on the orb while it charges).
    public static final String HOLD_ANIMATION = "gojo.azul_maximo";
    private static final double VIEW_DISTANCE = 160;
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextId;

    public static final int ALIVE = 0;
    public static final int IMPLODED = 1;
    public static final int GONE = 2;

    private enum Phase { DECIDE, FLY, LOCKED, CHARGE, FOLLOW }

    private Azul() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        return use(caster, level, false);
    }

    // Shift + Z: the very same Blue, drawn with the mix look (the glass ball) instead of the realism one.
    public static boolean useMix(LivingEntity caster, ServerLevel level) {
        return use(caster, level, true);
    }

    private static boolean use(LivingEntity caster, ServerLevel level, boolean shift) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        AzulSpec spec = AzulSpec.get(awakened);
        AzulSpec.Orbe orbe = spec.orbe();
        AzulSpec.Clique clique = spec.clique();
        AzulSpec.Segurado segurado = spec.segurado();
        AzulSpec.Visual visual = spec.visual();
        int style = visual == null ? (shift ? 3 : 2) : shift ? (visual.estiloShift() != null ? visual.estiloShift() : 3) : visual.estilo();
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;
        int decide = Math.max(1, segurado.decidirTicks());
        int chargeTicks = Math.max(1, segurado.carregarTicks());
        int maxHold = Math.max(chargeTicks, segurado.maxSegurarTicks());
        double baseRadius = Math.max(0.05, orbe.raio());
        double scaleMax = Math.max(1, orbe.escalaMax());
        // Bosses don't hold keys: half the time a tap, else a charge of their own length.
        boolean bossHolds = RANDOM.nextBoolean();
        int bossCharge = Math.max(4, chargeTicks / 3 + RANDOM.nextInt(Math.max(1, chargeTicks)));
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();

        // What changes from tick to tick.
        Phase[] phase = {Phase.DECIDE};
        Vec3[] orb = {holdAt(caster, 0, baseRadius)};
        double[] radius = {baseRadius};
        double[] charge = {0};
        int[] phaseStart = {0};
        Vec3[] flyFrom = {orb[0]};
        Vec3[] flyTo = {orb[0]};
        int[] flyTicks = {1};
        boolean[] following = {true};
        List<FallingBlockEntity> torn = new ArrayList<>();
        int[] tornCount = {0};
        int[] lastCrushFx = {-10};

        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : orb[0];
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", orb[0], caster.getLookAngle());
        data.busyTicks = decide + 2;

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            boolean casterGone = !caster.isAlive() || caster.isRemoved() || !power.equals(now.power);
            boolean cut = now.interrupts != interrupts;
            if (casterGone || cut && (phase[0] == Phase.DECIDE || phase[0] == Phase.CHARGE)) {
                // Cut short: it fades without imploding.
                send(level, id, GONE, orb[0], radius[0], (float) charge[0], style);
                release(torn);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            if (cut) {
                // Stunned while it is out: it stays where it is.
                following[0] = false;
            }
            boolean held = caster instanceof Player ? now.abilityHeld : bossHolds && age < decide + bossCharge;
            int t = age - phaseStart[0];
            switch (phase[0]) {
                case DECIDE -> {
                    orb[0] = holdAt(caster, 0, baseRadius);
                    now.busyTicks = Math.max(now.busyTicks, 2);
                    if (age + 1 >= decide) {
                        phaseStart[0] = age + 1;
                        if (held) {
                            phase[0] = Phase.CHARGE;
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                                    new ModPayloads.PlaySkillAnimation(caster.getId(), HOLD_ANIMATION, awakened));
                            SkillSounds.play(level, spec.sons(), "carregar", 0, chargeTicks, where, screen);
                            Fx.effects(level, SKILL, awakened, "carregar", orb[0], caster.getLookAngle());
                        } else {
                            phase[0] = Phase.FLY;
                            flyFrom[0] = orb[0];
                            flyTo[0] = tapTarget(caster, clique.distancia(), baseRadius);
                            flyTicks[0] = Math.max(1, (int) Math.ceil(flyFrom[0].distanceTo(flyTo[0]) / Math.max(0.1, clique.velocidade())));
                            caster.swing(InteractionHand.MAIN_HAND, true);
                            SkillSounds.play(level, spec.sons(), "lancar", 0, 0, where, screen);
                            Fx.effects(level, SKILL, awakened, "lancar", orb[0], flyTo[0].subtract(flyFrom[0]).normalize());
                        }
                    }
                }
                case FLY -> {
                    double k = Math.min(1, (t + 1) / (double) flyTicks[0]);
                    orb[0] = flyFrom[0].lerp(flyTo[0], k);
                    if (k >= 1) {
                        phase[0] = Phase.LOCKED;
                        phaseStart[0] = age + 1;
                        int left = Math.max(1, clique.duracaoTicks() - flyTicks[0]);
                        SkillSounds.play(level, spec.sons(), "travar", 0, left, where, screen);
                        Fx.effects(level, SKILL, awakened, "travar", orb[0], Vec3.ZERO);
                    }
                }
                case LOCKED -> {
                    pullTap(level, caster, orb[0], baseRadius, clique, age);
                    if (flyTicks[0] + t + 1 >= clique.duracaoTicks()) {
                        send(level, id, GONE, orb[0], radius[0], 0, style);
                        SkillSounds.play(level, spec.sons(), "fim", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "fim", orb[0], Vec3.ZERO);
                        return true;
                    }
                }
                case CHARGE -> {
                    charge[0] = Math.min(1, (t + 1) / (double) chargeTicks);
                    radius[0] = baseRadius * (1 + (scaleMax - 1) * smooth(charge[0]));
                    orb[0] = holdAt(caster, smooth(charge[0]), radius[0]);
                    now.busyTicks = Math.max(now.busyTicks, 2);
                    if (!held || t + 1 >= maxHold) {
                        phase[0] = Phase.FOLLOW;
                        phaseStart[0] = age + 1;
                        caster.swing(InteractionHand.MAIN_HAND, true);
                        // The push of the hand that sends it out.
                        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
                        SkillSounds.play(level, spec.sons(), "soltar", 0, Math.max(1, segurado.duracaoTicks()), where, screen);
                        Fx.effects(level, SKILL, awakened, "soltar", orb[0], caster.getLookAngle());
                    }
                }
                case FOLLOW -> {
                    if (following[0]) {
                        orb[0] = orb[0].lerp(followTarget(caster, segurado.mira()), t < 6 ? 0.3 : Mth.clamp(segurado.seguir(), 0, 1));
                    }
                    double c = charge[0];
                    pullHeld(level, caster, orb[0], radius[0], baseRadius * scaleMax, c, segurado, age);
                    tearBlocks(level, orb[0], radius[0], c, segurado, torn, tornCount);
                    int crushed = steerBlocks(orb[0], radius[0], torn);
                    if (crushed > 0 && age - lastCrushFx[0] >= 3) {
                        lastCrushFx[0] = age;
                        SkillSounds.play(level, spec.sons(), "esmagar", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "esmagar", orb[0], Vec3.ZERO);
                    }
                    if (t + 1 >= Math.max(1, segurado.duracaoTicks())) {
                        // It implodes: everyone in it is hit once more, the blocks still flying fall.
                        float last = (float) (segurado.danoFim() * (0.4 + 0.6 * c));
                        for (LivingEntity hit : Combat.inRadius(level, orb[0], radius[0] * 1.6 + 1, caster)) {
                            Combat.hurtMagic(caster, hit, last);
                        }
                        release(torn);
                        send(level, id, IMPLODED, orb[0], radius[0], (float) c, style);
                        SkillSounds.play(level, spec.sons(), "fimForte", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "fimForte", orb[0], Vec3.ZERO);
                        return true;
                    }
                }
            }
            send(level, id, ALIVE, orb[0], radius[0], (float) charge[0], style);
            return false;
        });
        return true;
    }

    // Between his hands, in front of the chest: higher and further out the bigger it is (the Visualizer's holdAt).
    private static Vec3 holdAt(LivingEntity caster, double c, double radius) {
        Vec3 forward = Combat.flatLook(caster);
        return caster.position().add(0, 1.45 + 0.4 * c, 0).add(forward.scale(0.9 + radius * 1.05));
    }

    // Where the tap's orb locks: where he aims, stopped short of a wall. Bosses put it on their target.
    private static Vec3 tapTarget(LivingEntity caster, double distance, double radius) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            Vec3 at = Combat.center(mob.getTarget());
            Vec3 eye = caster.getEyePosition();
            Vec3 to = at.subtract(eye);
            return to.length() > distance * 2 ? eye.add(to.normalize().scale(distance)) : at;
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 hit = Combat.raycastPoint(caster, distance);
        double d = hit.distanceTo(eye);
        return d < distance - 0.01 ? eye.add(caster.getLookAngle().scale(Math.max(1, d - radius - 0.3))) : hit;
    }

    // Where the held one goes: segurado.mira blocks ahead of his eyes (the mouse steers it). Bosses aim at their target.
    private static Vec3 followTarget(LivingEntity caster, double reach) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            return Combat.center(mob.getTarget());
        }
        return caster.getEyePosition().add(caster.getLookAngle().scale(reach));
    }

    // The tap: whoever is near is pulled slowly along the ground until stuck to it.
    private static void pullTap(ServerLevel level, LivingEntity caster, Vec3 orb, double r, AzulSpec.Clique clique, int age) {
        double reach = Math.max(1, clique.raioPuxar());
        int every = Math.max(1, clique.danoACadaTicks());
        for (LivingEntity e : Combat.inRadius(level, orb, reach, caster)) {
            if (skip(e)) {
                continue;
            }
            Vec3 to = orb.subtract(Combat.center(e));
            double d = to.length();
            if (d > reach) {
                continue;
            }
            Vec3 dm = e.getDeltaMovement();
            if (d > r + 0.7) {
                double speed = Math.min(clique.maxPuxar(), clique.puxar() + (clique.maxPuxar() - clique.puxar()) * (1 - d / reach));
                Vec3 flat = new Vec3(to.x, 0, to.z);
                Vec3 want = flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(speed) : Vec3.ZERO;
                e.setDeltaMovement(dm.x + (want.x - dm.x) * 0.5, dm.y, dm.z + (want.z - dm.z) * 0.5);
            } else {
                e.setDeltaMovement(dm.x * 0.5, dm.y, dm.z * 0.5);
                if (age % every == 0 && clique.dano() > 0) {
                    Combat.hurtMagic(caster, e, clique.dano());
                }
            }
            e.hurtMarked = true;
        }
    }

    // The held one: pulled hard from far away (dropped items too), and held spinning inside it once there.
    private static void pullHeld(ServerLevel level, LivingEntity caster, Vec3 orb, double r, double rMax, double c, AzulSpec.Segurado s, int age) {
        double reach = Math.max(1, Mth.lerp(c, s.raioPuxar() * 0.5, s.raioPuxar()));
        double maxSpeed = Mth.lerp(c, s.maxPuxar() * 0.4, s.maxPuxar());
        int every = Math.max(1, s.danoACadaTicks());
        float damage = (float) (s.dano() * (0.4 + 0.6 * c));
        AABB box = new AABB(orb, orb).inflate(reach);
        for (Entity e : level.getEntities(caster, box, x -> x.isAlive() && !x.isSpectator() && !(x instanceof FallingBlockEntity)
                && (x instanceof ItemEntity || x instanceof LivingEntity living && Combat.isTarget(living, caster)))) {
            if (e instanceof LivingEntity living && skip(living)) {
                continue;
            }
            Vec3 to = orb.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > reach) {
                continue;
            }
            Vec3 want;
            double blend;
            if (d > Math.max(r, rMax * 0.5 * c)) {
                double speed = Math.max(s.puxar(), maxSpeed * (0.4 + 0.6 * (1 - d / reach)));
                want = to.normalize().scale(Math.min(speed, d * 0.6));
                blend = 0.35;
            } else {
                // Inside: held in the middle, turning around it.
                Vec3 around = new Vec3(-to.z, 0, to.x).normalize().scale(0.15 * r);
                want = to.scale(0.4).add(around);
                blend = 0.5;
            }
            e.setDeltaMovement(e.getDeltaMovement().lerp(want, blend));
            e.fallDistance = 0;
            e.hurtMarked = true;
            if (e instanceof LivingEntity living && d < r * 1.3 + 0.5 && age % every == 0 && damage > 0) {
                Combat.hurtMagic(caster, living, damage);
            }
        }
    }

    private static boolean skip(LivingEntity e) {
        return e instanceof Player player && (player.isSpectator() || player.isCreative() && player.getAbilities().flying);
    }

    // Blocks torn off the top of the ground near it (only charged at least halfway, and with the gamerule on).
    private static void tearBlocks(ServerLevel level, Vec3 orb, double r, double c, AzulSpec.Segurado s, List<FallingBlockEntity> torn, int[] count) {
        if (c < 0.5 || s.blocosPorTick() <= 0 || !BlockRules.canBreak(level)) {
            return;
        }
        double reach = s.raioBlocos() * Mth.clamp((c - 0.5) * 2 * 0.5 + 0.5, 0, 1);
        if (reach <= 0.5) {
            return;
        }
        for (int i = 0; i < s.blocosPorTick() && count[0] < s.maxBlocos(); i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI;
            double rr = Math.sqrt(RANDOM.nextDouble()) * reach;
            int x = Mth.floor(orb.x + Math.cos(a) * rr);
            int z = Mth.floor(orb.z + Math.sin(a) * rr);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
            if (!level.isLoaded(pos)) {
                continue;
            }
            double d = Vec3.atCenterOf(pos).distanceTo(orb);
            if (d > reach || RANDOM.nextDouble() > 1.2 - d / reach) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                continue;
            }
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.dropItem = false;
            block.setNoGravity(true);
            block.noPhysics = true;
            block.setDeltaMovement(0, 0.25, 0);
            torn.add(block);
            count[0]++;
        }
    }

    // The torn blocks spiral in and are crushed in the middle. Returns how many were crushed this tick.
    private static int steerBlocks(Vec3 orb, double r, List<FallingBlockEntity> torn) {
        int crushed = 0;
        for (FallingBlockEntity block : torn) {
            if (block.isRemoved()) {
                continue;
            }
            Vec3 to = orb.subtract(block.position().add(0, 0.5, 0));
            double d = to.length();
            if (d < Math.max(0.6, r * 0.55)) {
                block.discard();
                crushed++;
                continue;
            }
            Vec3 in = to.normalize();
            Vec3 around = to.cross(new Vec3(0, 1, 0));
            around = around.lengthSqr() > 1.0E-6 ? around.normalize() : Vec3.ZERO;
            Vec3 v = block.getDeltaMovement().add(in.scale(0.06 + 0.04 * RANDOM.nextDouble())).add(around.scale(0.05)).scale(0.9);
            if (v.length() > 0.9) {
                v = v.normalize().scale(0.9);
            }
            block.setDeltaMovement(v);
            block.time = 1;
        }
        torn.removeIf(Entity::isRemoved);
        return crushed;
    }

    // When it is over, the blocks still flying fall back down.
    private static void release(List<FallingBlockEntity> torn) {
        for (FallingBlockEntity block : torn) {
            if (!block.isRemoved()) {
                block.setNoGravity(false);
                block.noPhysics = false;
                block.setDeltaMovement(block.getDeltaMovement().scale(0.5));
            }
        }
        torn.clear();
    }

    private static void send(ServerLevel level, int id, int mode, Vec3 pos, double radius, float charge, int style) {
        ModPayloads.Azul payload = new ModPayloads.Azul(id, mode, pos, (float) radius, charge, style);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
