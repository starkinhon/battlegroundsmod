package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.CutsceneSpec;
import com.starkinhon.battlegroundsmod.skill.IzanamiSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + X: Izanami. From the moment it is used, everything within raio is recorded for gravacaoTicks (30 s): where
// every living thing goes, its life, effects, fire and hunger, every block that changes, everything that appears.
// Nobody dies in that time: whoever would is held as a ghost (hidden, frozen, untouchable). Then the world stops, the
// camera goes to the caster's eyes, and it all plays backwards until it is as it was: damage, moves, deaths, blocks.
// Skills still going are cut off and their effects wiped. The caster can't use any skill while it records; at the end
// only the caster's cooldowns are reset, and the Izanami itself is spent until they die or change power.
// What it does not turn back: items in inventories and dropped items that were picked up.
public final class Izanami {
    public static final String SKILL = "madara.izanami";

    private record Saved(BlockState state, CompoundTag tag, int tick) {
    }

    // Everything needed to put one living entity back: its state when first seen and its path, tick by tick.
    private static final class Track {
        final LivingEntity entity;
        final float health;
        final float absorption;
        final List<MobEffectInstance> effects = new ArrayList<>();
        final int fire;
        final int food;
        final float saturation;
        final Vec3[] path;
        final float[] yaw;
        final float[] pitch;
        int first = -1;
        int last = -1;
        float healthAtStop;

        Track(LivingEntity entity, int ticks) {
            this.entity = entity;
            health = entity.getHealth();
            absorption = entity.getAbsorptionAmount();
            for (MobEffectInstance effect : entity.getActiveEffects()) {
                effects.add(new MobEffectInstance(effect));
            }
            fire = entity.getRemainingFireTicks();
            food = entity instanceof Player player ? player.getFoodData().getFoodLevel() : 0;
            saturation = entity instanceof Player player ? player.getFoodData().getSaturationLevel() : 0;
            path = new Vec3[ticks];
            yaw = new float[ticks];
            pitch = new float[ticks];
        }

        void record(int tick) {
            if (tick < 0 || tick >= path.length) {
                return;
            }
            if (first < 0) {
                first = tick;
            }
            last = tick;
            path[tick] = entity.position();
            yaw[tick] = entity.getYRot();
            pitch[tick] = entity.getXRot();
        }

        // Where it was on that tick (the nearest recorded one).
        Vec3 at(int tick) {
            if (first < 0) {
                return entity.position();
            }
            int t = Mth.clamp(tick, first, last);
            return path[t] != null ? path[t] : entity.position();
        }
    }

    private static final class Recording {
        final LivingEntity caster;
        final ServerLevel level;
        final Vec3 center;
        final IzanamiSpec spec;
        final long taskMark;
        final Map<LivingEntity, Track> tracks = new LinkedHashMap<>();
        // Blocks in the order they first changed (only the first change matters: it holds the old block).
        final Map<BlockPos, Saved> blocks = new HashMap<>();
        final List<BlockPos> order = new ArrayList<>();
        final List<Entity> spawned = new ArrayList<>();
        final Set<LivingEntity> ghosts = new HashSet<>();
        int tick;
        boolean recording = true;

        Recording(LivingEntity caster, ServerLevel level, IzanamiSpec spec) {
            this.caster = caster;
            this.level = level;
            this.center = caster.position();
            this.spec = spec;
            this.taskMark = Tasks.mark();
        }

        boolean near(Vec3 pos) {
            return pos.distanceToSqr(center) <= spec.raio() * spec.raio();
        }
    }

    private static final List<Recording> ACTIVE = new ArrayList<>();

    private Izanami() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        for (Recording r : ACTIVE) {
            if (r.caster == caster) {
                return false;
            }
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        IzanamiSpec spec = SkillSpecs.get(SKILL, IzanamiSpec.class, awakened);
        CutsceneSpec cutscene = spec.cutscene();
        int recordTicks = Math.max(1, spec.gravacaoTicks());
        int scene = cutscene != null && cutscene.ativa() ? cutscene.duracaoTicks() : 0;
        int back = Math.max(1, spec.voltarTicks());
        int total = recordTicks + scene + back;

        // No skills while it records (and while the world turns back).
        PowerLogic.seal(caster, total);
        Recording r = new Recording(caster, level, spec);
        ACTIVE.add(r);
        snapshot(r);

        Fx.effects(level, SKILL, awakened, "uso", caster, 0);
        Fx.effects(level, SKILL, awakened, "gravando", caster, recordTicks);
        Fx.sound(level, caster.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1.5f, 0.5f);
        Fx.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.6f);

        Tasks.addSystem(age -> {
            if (age < recordTicks) {
                r.tick = age;
                snapshot(r);
                if (caster instanceof ServerPlayer player && (recordTicks - age) % 20 == 0) {
                    player.displayClientMessage(Component.literal("Izanami " + (recordTicks - age) / 20 + "s").withStyle(ChatFormatting.DARK_RED),
                            true);
                }
                return false;
            }
            if (age == recordTicks) {
                stop(r, scene, back, awakened);
            }
            if (age == recordTicks + scene) {
                rewindStart(r, back, awakened);
            }
            if (age >= recordTicks + scene) {
                rewindStep(r, age - recordTicks - scene, back);
            }
            if (age >= total) {
                finish(r, awakened);
                return true;
            }
            return false;
        });
        return true;
    }

    // One tick of recording: who is around and where everyone tracked is.
    private static void snapshot(Recording r) {
        double radius = r.spec.raio();
        for (LivingEntity entity : r.level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(r.center, radius * 2, radius * 2, radius * 2),
                e -> !e.isSpectator() && r.near(e.position()))) {
            r.tracks.computeIfAbsent(entity, e -> new Track(e, Math.max(1, r.spec.gravacaoTicks())));
        }
        for (Track track : r.tracks.values()) {
            if (!track.entity.isRemoved()) {
                track.record(r.tick);
            }
        }
    }

    // Time stops: everyone is frozen and untouchable, what was going on is cut off, and the camera goes to the eyes.
    private static void stop(Recording r, int scene, int back, boolean awakened) {
        r.recording = false;
        // Only the moves of those caught in it (a player across the map keeps theirs).
        Tasks.cancelSince(r.taskMark, owner -> owner instanceof LivingEntity living && r.tracks.containsKey(living));
        int last = Math.max(1, r.spec.gravacaoTicks()) - 1;
        for (Track track : r.tracks.values()) {
            LivingEntity entity = track.entity;
            if (entity.isRemoved()) {
                continue;
            }
            track.healthAtStop = entity.getHealth();
            PowerLogic.interrupt(entity);
            Vec3 stopped = entity.position();
            // Still for the cutscene, then carried back along its own path, faster and faster to the start.
            Freeze.hold(entity, scene + back, age -> {
                if (age < scene) {
                    return stopped;
                }
                double k = Mth.clamp((age - scene + 1) / (double) back, 0, 1);
                return track.at((int) Math.round(last * (1 - k * k * (3 - 2 * k))));
            });
            Intangible.hold(entity, scene + back);
        }
        if (scene > 0) {
            Fx.effects(r.level, SKILL, awakened, "cutscene", r.caster, scene);
            CutsceneSpec cutscene = r.spec.cutscene();
            Tasks.addSystem(age -> {
                if (age >= cutscene.particlesTick()) {
                    if (r.caster.isAlive()) {
                        Fx.effects(r.level, SKILL, awakened, "olhos", r.caster, 0);
                        Fx.sound(r.level, r.caster.getEyePosition(), SoundEvents.ELDER_GUARDIAN_CURSE, 1.5f, 0.4f);
                    }
                    return true;
                }
                return false;
            });
            for (ServerPlayer viewer : viewers(r)) {
                PacketDistributor.sendToPlayer(viewer, new ModPayloads.FaceCutscene(r.caster.getId(), SKILL, awakened));
            }
        }
        Fx.sound(r.level, r.center, SoundEvents.BELL_RESONATE, 3f, 0.5f);
    }

    private static void rewindStart(Recording r, int back, boolean awakened) {
        for (ServerPlayer viewer : viewers(r)) {
            PacketDistributor.sendToPlayer(viewer, new ModPayloads.Rewind());
        }
        Fx.effects(r.level, SKILL, awakened, "voltar", r.caster, back);
        Fx.sound(r.level, r.center, SoundEvents.BEACON_DEACTIVATE, 3f, 0.4f);
        Fx.sound(r.level, r.center, SoundEvents.PORTAL_TRAVEL, 0.6f, 2f);
        // Whatever appeared in the meantime never existed.
        for (Entity entity : r.spawned) {
            if (!entity.isRemoved() && !(entity instanceof Player)) {
                entity.discard();
            }
        }
        r.spawned.clear();
        // The dead come back into view as they are carried back.
        for (LivingEntity ghost : r.ghosts) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(ghost, new ModPayloads.HideEntity(ghost.getId(), 0));
        }
    }

    // Blocks go back newest first, at the pace the bodies go back; life flows back too.
    private static void rewindStep(Recording r, int step, int back) {
        int record = Math.max(1, r.spec.gravacaoTicks());
        double k = Mth.clamp((step + 1) / (double) back, 0, 1);
        double eased = k * k * (3 - 2 * k);
        int until = (int) Math.round(record * (1 - eased));
        restoreBlocks(r, until);
        for (Track track : r.tracks.values()) {
            if (!track.entity.isRemoved() && track.entity.isAlive()) {
                track.entity.setHealth(Math.max(1, Mth.lerp((float) eased, track.healthAtStop, track.health)));
            }
        }
        if (step % 6 == 0) {
            Fx.sound(r.level, r.center, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 0.5f + (float) eased);
        }
    }

    private static void restoreBlocks(Recording r, int fromTick) {
        while (!r.order.isEmpty()) {
            BlockPos pos = r.order.get(r.order.size() - 1);
            Saved saved = r.blocks.get(pos);
            if (saved.tick() < fromTick) {
                break;
            }
            r.order.remove(r.order.size() - 1);
            r.level.setBlock(pos, saved.state(), Block.UPDATE_CLIENTS);
            if (saved.tag() != null) {
                BlockEntity blockEntity = r.level.getBlockEntity(pos);
                if (blockEntity != null) {
                    blockEntity.loadWithComponents(saved.tag(), r.level.registryAccess());
                    blockEntity.setChanged();
                }
            }
            r.blocks.remove(pos);
        }
    }

    private static void finish(Recording r, boolean awakened) {
        restoreBlocks(r, Integer.MIN_VALUE);
        ACTIVE.remove(r);
        for (Track track : r.tracks.values()) {
            LivingEntity entity = track.entity;
            if (entity.isRemoved()) {
                continue;
            }
            if (track.first < 0) {
                track.record(0);
            }
            Vec3 start = track.at(track.first);
            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(start.x, start.y, start.z, track.yaw[track.first], track.pitch[track.first]);
                player.getFoodData().setFoodLevel(track.food);
                player.getFoodData().setSaturation(track.saturation);
            } else {
                entity.moveTo(start.x, start.y, start.z, track.yaw[track.first], track.pitch[track.first]);
            }
            entity.setHealth(track.health);
            entity.setAbsorptionAmount(track.absorption);
            entity.removeAllEffects();
            for (MobEffectInstance effect : track.effects) {
                entity.addEffect(new MobEffectInstance(effect));
            }
            entity.setRemainingFireTicks(track.fire);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.fallDistance = 0;
            entity.hurtMarked = true;
        }
        for (LivingEntity ghost : r.ghosts) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(ghost, new ModPayloads.HideEntity(ghost.getId(), 0));
        }
        // Only the caster gets their skills back at once; the Izanami itself stays spent.
        PowerData data = r.caster.getData(ModAttachments.POWER);
        data.sealedUntil = 0;
        for (int i = 0; i < data.cooldowns.length; i++) {
            data.cooldowns[i] = i == PowerData.SLOT_SHIFT + 1 && !data.noCooldown ? PowerData.SPENT : 0;
        }
        data.busyTicks = 0;
        if (r.caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
        Fx.effects(r.level, SKILL, awakened, "fim", r.center, new Vec3(0, 1, 0));
        Fx.sound(r.level, r.center, SoundEvents.ENDER_EYE_DEATH, 3f, 0.5f);
    }

    private static List<ServerPlayer> viewers(Recording r) {
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : r.level.players()) {
            if (r.near(player.position()) || r.tracks.containsKey(player)) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    // ---------------------------------------------------------------- hooks

    // Level.setBlock, before the change (LevelMixin): keep the old block the first time a spot changes.
    public static void beforeSetBlock(Level level, BlockPos pos) {
        if (ACTIVE.isEmpty() || level.isClientSide) {
            return;
        }
        for (Recording r : ACTIVE) {
            if (r.recording && r.level == level && !r.blocks.containsKey(pos) && r.near(Vec3.atCenterOf(pos))) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                CompoundTag tag = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
                BlockPos key = pos.immutable();
                r.blocks.put(key, new Saved(level.getBlockState(pos), tag, r.tick));
                r.order.add(key);
            }
        }
    }

    // Anything that appears in the area while it records goes away when time turns back.
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (ACTIVE.isEmpty() || event.getLevel().isClientSide() || event.loadedFromDisk() || event.getEntity() instanceof Player) {
            return;
        }
        for (Recording r : ACTIVE) {
            if (r.recording && r.level == event.getLevel() && r.near(event.getEntity().position())) {
                r.spawned.add(event.getEntity());
            }
        }
    }

    // True while an Izanami would turn this one's death back: whoever kills it no matter what (the Hakai) must leave
    // the body in the world, or there is nothing to bring back.
    public static boolean holds(LivingEntity entity) {
        return covering(entity) != null;
    }

    private static Recording covering(LivingEntity entity) {
        if (ACTIVE.isEmpty() || entity.level().isClientSide()) {
            return null;
        }
        for (Recording r : ACTIVE) {
            if (r.level == entity.level() && (r.tracks.containsKey(entity) || r.near(entity.position()))) {
                return r;
            }
        }
        return null;
    }

    // Nobody dies while it records: they are held as ghosts until time turns back. Runs first (EventPriority.HIGHEST)
    // so nothing else treats it as a death.
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Recording r = covering(entity);
        if (r != null) {
            event.setCanceled(true);
            entity.setHealth(1);
            entity.clearFire();
            entity.removeAllEffects();
            if (r.ghosts.add(entity)) {
                r.tracks.computeIfAbsent(entity, e -> new Track(e, Math.max(1, r.spec.gravacaoTicks())));
                CutsceneSpec cutscene = r.spec.cutscene();
                int scene = cutscene != null && cutscene.ativa() ? cutscene.duracaoTicks() : 0;
                int left = Math.max(1, r.spec.gravacaoTicks() - r.tick) + (r.recording ? scene : 0);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.HideEntity(entity.getId(), left));
                Freeze.hold(entity, r.recording ? left - scene : 1);
                Intangible.hold(entity, left + r.spec.voltarTicks());
                if (entity.hasData(ModAttachments.POWER)) {
                    PowerLogic.interrupt(entity);
                }
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
