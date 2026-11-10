package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.AzulSpec;
import com.starkinhon.battlegroundsmod.skill.RoxoSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Purple (Roxo), Gojo's C (gojo.roxo.json, see RoxoSpec), the Visualizer's "Gojo · Roxo" tab as Stark approved it.
// Held: the Red (on his right) and the Blue (on his left) appear behind him at the height of his chest, come round to the
// front and press together slowly until they are one Purple. Still held, it grows (up to 4x). Let go: it is launched
// straight where he aims, speeding up, erasing everything in its path (blocks only with the gamerule
// soulsBlockDestruction, no drops; without it, it ends on the first wall). Let go before it formed: it finishes forming
// and leaves at its base size. Like the Blue and the Red, it only goes when the key is let go (PR #127).
// The Unlimited Purple (irrestrito): a Red fired at full charge into his own Blue parked at full charge (Vermelho.fire
// finds it with Azul.claimParked). Both implode, a Purple with no contour grows in half a second to the size of Madara's
// meteor and blows up in every direction out to 100 blocks. Gojo is inside, untouched (the Infinity).
// Everything is drawn on the clients (client/fx/RoxoRenderer): the forming one from its tick (they place it on his body
// themselves), the launched one and the Unlimited one from one message each.
public final class Roxo {
    public static final String SKILL = RoxoSpec.SKILL;
    // Arms open while the spheres come round, then the hands brought together in front of the chest, held.
    public static final String HOLD_ANIMATION = "gojo.roxo_segurando";
    public static final int COOLDOWN = 300;
    public static final int ALIVE = 0;
    public static final int GONE = 2;
    private static final double VIEW_DISTANCE = 192;
    private static final double IRR_VIEW_DISTANCE = 448;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static int nextId;

    private Roxo() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        RoxoSpec spec = RoxoSpec.get(awakened);
        RoxoSpec.Formar formar = spec.formar();
        int s0 = Math.max(1, formar.surgirTicks());
        int s1 = s0 + Math.max(1, formar.virTicks());
        int s2 = s1 + Math.max(1, formar.misturarTicks());
        int touch = touchTick(spec);
        int grow = Math.max(1, spec.segurado().crescerTicks());
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;
        // Bosses don't hold keys: half the time it leaves as soon as it forms, else it grows for a while first.
        // A boss with tactics (the Gojo boss) says how much it grows this time (BossOrder: no hold = leaves as it forms).
        BossOrder.Order order = BossOrder.take(caster);
        int bossGrow = order != null ? (order.hold() ? (int) Math.round(grow * Mth.clamp(order.charge(), 0, 1)) : 0)
                : RANDOM.nextBoolean() ? 0 : grow / 3 + RANDOM.nextInt(Math.max(1, Math.max(grow, spec.segurado().maxSegurarTicks()) - grow / 3 + 1));
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        double[] charge = {0};
        boolean[] released = {false};
        boolean[] growing = {false};
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : heldAt(caster, spec, charge[0]);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), HOLD_ANIMATION, awakened));
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where, screen);
        SkillSounds.play(level, spec.sons(), "surgir", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", heldAt(caster, spec, 0), caster.getLookAngle());
        Fx.effects(level, SKILL, awakened, "surgirVermelho", local(caster, -0.95, 1.2, -0.85), Combat.flatLook(caster));
        Fx.effects(level, SKILL, awakened, "surgirAzul", local(caster, 0.95, 1.2, -0.85), Combat.flatLook(caster));
        data.busyTicks = s2 + 2;
        send(level, caster, id, ALIVE, 0, 0);

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts) {
                // Cut short: it fades without leaving.
                send(level, caster, id, GONE, age, (float) charge[0]);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            now.busyTicks = Math.max(now.busyTicks, 2);
            // Players: only the key (however long). Bosses: their own growing time.
            boolean held = caster instanceof Player ? com.starkinhon.battlegroundsmod.combat.Stamina.holding(caster) : age < s2 + bossGrow;
            if (!held) {
                released[0] = true;
            }
            if (age == touch) {
                SkillSounds.play(level, spec.sons(), "tocar", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "tocar", heldAt(caster, spec, 0), Combat.flatLook(caster));
            }
            if (age < s2) {
                send(level, caster, id, ALIVE, age, 0);
                return false;
            }
            if (age == s2) {
                SkillSounds.play(level, spec.sons(), "formar", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "formar", heldAt(caster, spec, 0), Combat.flatLook(caster));
            }
            if (released[0]) {
                launch(level, caster, id, spec, awakened, charge[0], aim(caster, heldAt(caster, spec, charge[0]), spec.tiro().alcance()), screen);
                return true;
            }
            if (!growing[0]) {
                growing[0] = true;
                SkillSounds.play(level, spec.sons(), "carregar", 0, grow, where, screen);
                Fx.effects(level, SKILL, awakened, "carregar", heldAt(caster, spec, 0), Combat.flatLook(caster));
            }
            charge[0] = Math.min(1, (age - s2 + 1) / (double) grow);
            com.starkinhon.battlegroundsmod.combat.Charge.report(caster, charge[0]);
            send(level, caster, id, ALIVE, age, (float) charge[0]);
            return false;
        });
        return true;
    }

    // Launched: straight along dir from where it was held, speeding up, erasing everything within its radius.
    private static void launch(ServerLevel level, LivingEntity caster, int id, RoxoSpec spec, boolean awakened, double c, Vec3 dir,
            List<ServerPlayer> screen) {
        RoxoSpec.Tiro tiro = spec.tiro();
        double radius = radiusAt(spec, c);
        Vec3 from = heldAt(caster, spec, c);
        double v0 = Math.max(0.1, tiro.velocidade());
        double accel = Math.max(0, tiro.aceleracao());
        double vMax = Math.max(v0, tiro.velocidadeMax());
        // The tunnel it erases is much wider than the ball (Stark: about twice the full Red's).
        double start = radius * Math.max(0.5, tiro.apagar());
        double erase = Math.max(radius * Math.max(0.5, tiro.apagar()), Mth.lerp(c, tiro.raioDestruicao(), tiro.raioDestruicaoMax()));
        float damage = Mth.lerp((float) c, tiro.dano(), tiro.danoMax());
        boolean breaks = BlockRules.canBreak(level);
        double reach = Math.max(1, tiro.alcance());
        if (!breaks) {
            // It can't erase the world: it ends on the first wall.
            var hit = level.clip(new ClipContext(from, from.add(dir.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.MISS) {
                reach = Math.max(1, hit.getLocation().distanceTo(from) - radius * 0.5);
            }
        }
        double range = reach;
        caster.swing(InteractionHand.MAIN_HAND, true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        ModPayloads.RoxoShot shot = new ModPayloads.RoxoShot(id, caster.getId(), from, dir, new Vec3(v0, accel, vMax), new Vec3(radius, range, c));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(from) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, shot);
            }
        }
        double[] flown = {0};
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : from.add(dir.scale(flown[0]));
        SkillSounds.play(level, spec.sons(), "lancar", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "lancar", from, dir);
        Set<Integer> hitIds = new HashSet<>();
        Tasks.add(age -> {
            double a = flown[0];
            double b = Math.min(range, a + Math.min(vMax, v0 + accel * age));
            flown[0] = b;
            Vec3 pa = from.add(dir.scale(a));
            Vec3 pb = from.add(dir.scale(b));
            for (LivingEntity e : Combat.alongLine(level, pa, pb, erase, caster)) {
                // The same cone as the tunnel near where it was launched.
                Vec3 rel = Combat.center(e).subtract(from);
                double along = Math.max(0, rel.dot(dir));
                if (rel.subtract(dir.scale(along)).length() > Math.min(erase, start + along * 0.9) + e.getBbWidth() / 2 || !hitIds.add(e.getId())) {
                    continue;
                }
                Combat.hurtMagic(caster, e, damage);
                e.setDeltaMovement(dir.scale(0.6).add(0, 0.2, 0));
                e.hurtMarked = true;
                Fx.effects(level, SKILL, awakened, "apagarAlvo", Combat.center(e), dir);
                SkillSounds.play(level, spec.sons(), "apagarAlvo", 0, 0, onde -> Combat.center(e), screen);
            }
            if (breaks) {
                eraseSweep(level, pa, pb, erase, Math.max(1, tiro.blocosPorTick()), from, dir, start);
            }
            if (b >= range) {
                SkillSounds.play(level, spec.sons(), "sumir", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "sumir", pb, dir);
                return true;
            }
            return false;
        });
    }

    // The Unlimited Purple at center: the Red and the Blue implode into it, it grows fast, then blows up in every
    // direction. Called when a full Red reaches his own parked full Blue (Vermelho.fire).
    public static void irrestrito(ServerLevel level, LivingEntity caster, Vec3 center, boolean awakened) {
        RoxoSpec spec = RoxoSpec.get(awakened);
        RoxoSpec.Irrestrito irr = spec.irrestrito();
        if (irr == null) {
            return;
        }
        int implode = Math.max(1, irr.implodirTicks());
        int growTicks = Math.max(1, irr.crescerTicks());
        int blastTicks = Math.max(1, irr.explosaoTicks());
        int born = implode;
        int big = born + growTicks;
        int boom = big + Math.max(0, irr.esperaTicks());
        double raio = Math.max(1, irr.raio());
        double reach = Math.max(raio, irr.raioExplosao());
        double azulR = azulRadius(awakened);
        boolean breaks = BlockRules.canBreak(level);
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : center;

        ModPayloads.RoxoIrrestrito payload = new ModPayloads.RoxoIrrestrito(center, new Vec3(raio, reach, azulR),
                new Vec3(implode, growTicks, boom - big), blastTicks);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) < IRR_VIEW_DISTANCE * IRR_VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        SkillSounds.play(level, spec.sons(), "colidir", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "colidir", center, new Vec3(0, 0, 1));

        Set<Integer> swallowed = new HashSet<>();
        Set<Integer> blasted = new HashSet<>();
        double[] grown = {0};
        List<long[]> columns = new ArrayList<>();
        int[] next = {0};
        int budget = Math.max(1, irr.blocosPorTick());
        Tasks.add(age -> {
            // Gojo is inside it all, untouched: the ground going from under him doesn't hurt either.
            caster.fallDistance = 0;
            if (age < born) {
                // Everything near is pulled into the implosion.
                for (LivingEntity e : Combat.inRadius(level, center, irr.raioPuxar(), caster)) {
                    Vec3 in = center.subtract(Combat.center(e));
                    if (in.lengthSqr() > 0.25) {
                        e.setDeltaMovement(in.normalize().scale(irr.puxar()));
                        e.hurtMarked = true;
                    }
                }
                return false;
            }
            if (age == born) {
                SkillSounds.play(level, spec.sons(), "nascer", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "nascer", center, new Vec3(0, 0, 1));
            }
            if (age <= big) {
                // The Purple growing: whatever it touches is erased.
                double r = Mth.lerp(smooth((age - born) / (double) growTicks), 0.6, raio);
                for (LivingEntity e : Combat.inRadius(level, center, r + 0.5, caster)) {
                    if (swallowed.add(e.getId())) {
                        Combat.hurtMagic(caster, e, irr.danoRoxo());
                    }
                }
                if (breaks) {
                    eraseShell(level, center, grown[0] - 1, r);
                }
                grown[0] = r;
                return false;
            }
            if (age < boom) {
                return false;
            }
            int t = age - boom;
            if (t == 0) {
                SkillSounds.play(level, spec.sons(), "explodir", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "explodir", center, new Vec3(0, 1, 0));
                if (breaks) {
                    craterColumns(center, reach, columns);
                }
            }
            double front = reach * easeOut(Math.min(1, (t + 1) / (double) blastTicks));
            for (LivingEntity e : Combat.inRadius(level, center, front, caster)) {
                if (!blasted.add(e.getId())) {
                    continue;
                }
                double k = Math.min(1, Combat.center(e).distanceTo(center) / reach);
                Combat.hurtMagic(caster, e, Mth.lerp((float) k, irr.danoCentro(), irr.danoBorda()));
                Combat.pushFrom(e, center, irr.empurrao() * (1 - 0.6 * k), 0.6 + 0.6 * (1 - k));
            }
            if (breaks) {
                crater(level, center, reach, irr, columns, next, front, budget);
            }
            // Done when the blast is out and the ground has caught up (never stuck for long).
            return t >= blastTicks && next[0] >= columns.size() || t > blastTicks + 600;
        });
    }

    // The Blue's full radius (the Unlimited Purple draws it imploding).
    private static double azulRadius(boolean awakened) {
        AzulSpec spec = AzulSpec.get(awakened);
        return Math.max(0.05, spec.orbe().raio()) * Math.max(1, spec.orbe().escalaMax());
    }

    // Erases the blocks within radius of the segment a-b (its edge a little ragged), up to budget. Near where it was
    // launched the tunnel opens like a cone from the ball's own size (start), so the ground he stands on stays.
    private static void eraseSweep(ServerLevel level, Vec3 a, Vec3 b, double radius, int budget, Vec3 origin, Vec3 dir, double start) {
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(1.0E-6, ab.lengthSqr());
        int minX = Mth.floor(Math.min(a.x, b.x) - radius), maxX = Mth.floor(Math.max(a.x, b.x) + radius);
        int minY = Mth.floor(Math.min(a.y, b.y) - radius), maxY = Mth.floor(Math.max(a.y, b.y) + radius);
        int minZ = Mth.floor(Math.min(a.z, b.z) - radius), maxZ = Mth.floor(Math.max(a.z, b.z) + radius);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX && budget > 0; x++) {
            for (int y = minY; y <= maxY && budget > 0; y++) {
                for (int z = minZ; z <= maxZ && budget > 0; z++) {
                    Vec3 mid = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    double t = Mth.clamp(mid.subtract(a).dot(ab) / len2, 0, 1);
                    double along = Math.max(0, mid.subtract(origin).dot(dir));
                    double edge = Math.min(radius, start + along * 0.9) * (0.92 + 0.16 * hash(x, y, z));
                    if (mid.distanceToSqr(a.add(ab.scale(t))) > edge * edge) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (eraseBlock(level, pos)) {
                        budget--;
                    }
                }
            }
        }
    }

    // Erases the blocks between the radii inner and outer around center (the Unlimited Purple growing).
    private static void eraseShell(ServerLevel level, Vec3 center, double inner, double outer) {
        int r = (int) Math.ceil(outer);
        BlockPos base = BlockPos.containing(center);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double in2 = inner > 0 ? inner * inner : -1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    double d2 = center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    double edge = outer * (0.94 + 0.12 * hash(pos.getX(), pos.getY(), pos.getZ()));
                    if (d2 > edge * edge || d2 < in2) {
                        continue;
                    }
                    eraseBlock(level, pos);
                }
            }
        }
    }

    // Every column of the blast, nearest first: {x, z, distance * 1000}.
    private static void craterColumns(Vec3 center, double reach, List<long[]> out) {
        int r = (int) Math.ceil(reach);
        int cx = Mth.floor(center.x);
        int cz = Mth.floor(center.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= reach) {
                    out.add(new long[] {cx + dx, cz + dz, Math.round(d * 1000)});
                }
            }
        }
        out.sort(Comparator.comparingLong(c -> c[2]));
    }

    // The blast tearing the ground, column by column as its front passes (up to budget blocks a tick): everything
    // standing in the sphere is gone, and the ground under it is dug into a bowl, profundidade deep under the middle
    // and nothing at the edge. Unloaded ground is left as it is.
    private static void crater(ServerLevel level, Vec3 center, double reach, RoxoSpec.Irrestrito irr, List<long[]> columns, int[] next,
            double front, int budget) {
        int cy = Mth.floor(center.y);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int done = 0;
        while (next[0] < columns.size() && done < budget) {
            long[] col = columns.get(next[0]);
            double d = col[2] / 1000.0;
            if (d > front) {
                return;
            }
            next[0]++;
            int x = (int) col[0];
            int z = (int) col[1];
            pos.set(x, cy, z);
            if (!level.isLoaded(pos)) {
                continue;
            }
            double k = d / reach;
            double span = Math.sqrt(Math.max(0, reach * reach - d * d));
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int top = (int) Math.min(surface, Math.min(cy + span, cy + irr.altura()));
            int dig = (int) Math.round(irr.profundidade() * Math.sqrt(Math.max(0, 1 - k * k)) * (0.85 + 0.3 * hash(x, 7, z)));
            int bottom = (int) Math.max(Math.max(cy - span, ground - dig), level.getMinBuildHeight());
            for (int y = top; y >= bottom; y--) {
                pos.set(x, y, z);
                if (eraseBlock(level, pos)) {
                    done++;
                }
            }
        }
    }

    // No drops, no neighbor updates; air, liquids, containers and unbreakable blocks stay.
    private static boolean eraseBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, pos, state, Block.UPDATE_CLIENTS);
        return true;
    }

    // The formed Purple's radius at charge c (formed it is 10% bigger than each sphere).
    static double radiusAt(RoxoSpec spec, double c) {
        double base = Math.max(0.05, spec.orbe().raio()) * 1.1;
        return base * (1 + (Math.max(1, spec.orbe().escalaMax()) - 1) * smooth(c));
    }

    // Where the formed Purple sits: in front of his chest, higher and further out the bigger it is (the Visualizer's held).
    static Vec3 heldAt(LivingEntity caster, RoxoSpec spec, double c) {
        double radius = radiusAt(spec, c);
        double grow = radius - Math.max(0.05, spec.orbe().raio()) * 1.1;
        return local(caster, 0, Math.max(1.3, radius + 0.2), 1.35 + grow * 1.1);
    }

    // A point in his own space: x to his left, y up, z forward (the Visualizer's Gojo, who faces +z), scaled with him.
    static Vec3 local(LivingEntity caster, double x, double y, double z) {
        Vec3 forward = Combat.flatLook(caster);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double scale = caster.getBbHeight() / 1.8;
        return caster.position().add(forward.scale(z * scale)).add(right.scale(-x * scale)).add(0, y * scale, 0);
    }

    // The tick the two spheres touch (the Visualizer's touchT).
    private static int touchTick(RoxoSpec spec) {
        RoxoSpec.Formar f = spec.formar();
        int s1 = Math.max(1, f.surgirTicks()) + Math.max(1, f.virTicks());
        int mix = Math.max(1, f.misturarTicks());
        for (int t = s1; t < s1 + mix; t++) {
            if (0.68 * (1 - smooth((t - s1) / (double) mix)) < spec.orbe().raio()) {
                return t;
            }
        }
        return s1;
    }

    // From where it is held straight to what is under the crosshair (the first block or body on the line of sight, else
    // the far end of its range), so it goes exactly where the player aims (Stark: very easy to aim). Bosses: their target.
    private static Vec3 aim(LivingEntity caster, Vec3 from, double range) {
        Vec3 point;
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            point = Combat.center(mob.getTarget());
        } else {
            Vec3 eye = caster.getEyePosition();
            Vec3 far = eye.add(caster.getLookAngle().scale(range));
            var hit = caster.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            point = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : far;
            LivingEntity target = Combat.raycastTarget(caster, Math.min(range, point.distanceTo(eye)));
            if (target != null) {
                point = Combat.center(target);
            }
        }
        Vec3 dir = point.subtract(from);
        return dir.lengthSqr() > 1.0 ? dir.normalize() : caster.getLookAngle();
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double easeOut(double t) {
        double u = 1 - Mth.clamp(t, 0, 1);
        return 1 - u * u * u;
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static void send(ServerLevel level, LivingEntity caster, int id, int mode, int tick, float charge) {
        ModPayloads.Roxo payload = new ModPayloads.Roxo(id, caster.getId(), mode, tick, charge);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(caster) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
