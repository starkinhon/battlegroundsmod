package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.skill.MeteoroSpec;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// The cloud layer the Meteoro punches through, like in the anime. Each puff is a flat block display (vanilla clouds
// are boxes too), so the meteor can open a real hole: puffs it touches shrink away. Settings are the "nuvens" block.
// Display fields are set through NBT, the same way /data merge does it.
public final class MeteoroClouds {
    // Marks our puffs so ones left in the world by a crash or restart are thrown away when they load again.
    static final String TAG = "battlegroundsmod_meteor_cloud";
    private static final Set<Entity> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int POP_TICKS = 6;

    private record Puff(Display.BlockDisplay display, Vec3 center, double size) {
    }

    private final ServerLevel level;
    private final MeteoroSpec.Nuvens nuvens;
    private final List<Puff> puffs = new ArrayList<>();

    private MeteoroClouds(ServerLevel level, MeteoroSpec.Nuvens nuvens) {
        this.level = level;
        this.nuvens = nuvens;
    }

    public static boolean isStray(Entity entity) {
        return entity.getTags().contains(TAG) && !LIVE.contains(entity);
    }

    // Lays the layer around where the meteor's path crosses the cloud height. Returns null when turned off.
    static MeteoroClouds form(ServerLevel level, MeteoroSpec.Nuvens nuvens, Meteoro.Fall fall, Vec3 target) {
        if (nuvens == null || !nuvens.ativa()) {
            return null;
        }
        MeteoroClouds clouds = new MeteoroClouds(level, nuvens);
        BlockState block = block(nuvens.bloco());
        Vec3 center = fall.crossing(target.y + nuvens.altura());
        double step = Math.max(1, nuvens.tamanho());
        double radius = nuvens.raio();
        for (double x = -radius; x <= radius; x += step) {
            for (double z = -radius; z <= radius; z += step) {
                if (x * x + z * z > radius * radius || RANDOM.nextDouble() > nuvens.densidade()) {
                    continue;
                }
                double size = step * (0.9 + RANDOM.nextDouble() * 0.7);
                Vec3 pos = center.add(x + (RANDOM.nextDouble() - 0.5) * step, (RANDOM.nextDouble() - 0.5) * nuvens.espessura(),
                        z + (RANDOM.nextDouble() - 0.5) * step);
                clouds.spawn(block, pos, size);
            }
        }
        // Grow in from nothing a tick after spawning, so clients see the interpolation.
        Tasks.later(1, () -> clouds.puffs.forEach(puff -> clouds.scale(puff, 1, nuvens.formarTicks())));
        return clouds;
    }

    // Every puff the meteor touches pops, which opens the hole.
    void pierce(Vec3 meteor, double meteorRadius) {
        if (!nuvens.buraco()) {
            return;
        }
        double reach = meteorRadius * (1 + nuvens.folgaBuraco());
        puffs.removeIf(puff -> {
            if (puff.center().distanceTo(meteor) > reach + puff.size() * 0.5) {
                return false;
            }
            Vec3 away = puff.center().subtract(meteor).normalize();
            Fx.burst(level, ParticleTypes.CLOUD, puff.center(), 6, puff.size() * 0.3, 0.1);
            Fx.moving(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, puff.center(), away.scale(0.15));
            scale(puff, 0, POP_TICKS);
            Tasks.later(POP_TICKS + 1, () -> remove(puff));
            return true;
        });
    }

    // After the impact the rest of the layer stays a while, then fades.
    void fade() {
        List<Puff> left = new ArrayList<>(puffs);
        puffs.clear();
        int fade = Math.max(1, nuvens.sumirTicks());
        Tasks.later(nuvens.manterTicks(), () -> left.forEach(puff -> scale(puff, 0, fade)));
        Tasks.later(nuvens.manterTicks() + fade + 1, () -> left.forEach(MeteoroClouds::remove));
    }

    private void spawn(BlockState block, Vec3 pos, double size) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(block));
        tag.put("transformation", transformation(size, 0, RANDOM.nextFloat() * (float) Math.PI * 2));
        tag.putFloat("view_range", 8);
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("sky", 15);
        brightness.putInt("block", 15);
        tag.put("brightness", brightness);
        display.load(tag);
        display.moveTo(pos.x, pos.y, pos.z, 0, 0);
        display.addTag(TAG);
        LIVE.add(display);
        if (level.addFreshEntity(display)) {
            puffs.add(new Puff(display, pos, size));
        }
    }

    // Scales a puff to a fraction of its full size over some ticks, keeping its spin.
    private void scale(Puff puff, double amount, int ticks) {
        Display.BlockDisplay display = puff.display();
        if (display.isRemoved()) {
            return;
        }
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        CompoundTag old = tag.getCompound("transformation");
        tag.put("transformation", transformation(puff.size(), amount, angleOf(old)));
        tag.putInt("interpolation_duration", Math.max(0, ticks));
        tag.putInt("start_interpolation", 0);
        display.load(tag);
    }

    private static void remove(Puff puff) {
        LIVE.remove(puff.display());
        puff.display().discard();
    }

    // A flat box size x size*0.35 x size, centered on the entity and turned around the vertical axis.
    private static CompoundTag transformation(double size, double amount, float angle) {
        float w = (float) (size * amount);
        float h = (float) (size * 0.35 * amount);
        double half = angle / 2;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        // Rotate the centering offset with the box so the puff stays where it was placed.
        float tx = -(cos * w / 2 + sin * w / 2);
        float tz = -(-sin * w / 2 + cos * w / 2);
        CompoundTag tag = new CompoundTag();
        tag.put("left_rotation", floats(0, (float) Math.sin(half), 0, (float) Math.cos(half)));
        tag.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("translation", floats(tx, -h / 2, tz));
        tag.put("scale", floats(w, h, w));
        return tag;
    }

    private static float angleOf(CompoundTag transformation) {
        ListTag rotation = transformation.getList("left_rotation", 5);
        return rotation.size() == 4 ? (float) (2 * Math.atan2(rotation.getFloat(1), rotation.getFloat(3))) : 0;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private static BlockState block(String id) {
        try {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            return block == Blocks.AIR ? Blocks.WHITE_CONCRETE.defaultBlockState() : block.defaultBlockState();
        } catch (Exception e) {
            return Blocks.WHITE_CONCRETE.defaultBlockState();
        }
    }
}
