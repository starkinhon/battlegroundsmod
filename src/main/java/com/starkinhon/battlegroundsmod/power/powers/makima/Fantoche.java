package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// C Fantoche (forja/makima-fantoche, approved 2026-11-11). Every mob she kills is pulled to her by a chain and sinks into
// her shadow: kept, up to 9. C lets one out: a pool opens beside her and it comes up jerkily, pulled by the nape, a chain
// from her back to it and her rings in its eyes; then it fights for her (Puppets). Up to 3 out at once. Holding C opens
// a wheel to pick which one (client/MakimaWheel).
public final class Fantoche {
    private static final String STOCK = "makima_stock";
    static final int MAX_STOCK = 9;
    static final int MAX_OUT = 3;
    private static final int RISE = 17;
    private static final int HOLD = 6;

    private Fantoche() {
    }

    // ---------- the stock (kept in her saved data: it survives leaving the world) ----------

    record Stored(String type, String name) {
    }

    static List<Stored> stock(LivingEntity owner) {
        List<Stored> list = new ArrayList<>();
        ListTag tag = owner.getPersistentData().getList(STOCK, Tag.TAG_COMPOUND);
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag c = tag.getCompound(i);
            list.add(new Stored(c.getString("t"), c.getString("n")));
        }
        return list;
    }

    private static void save(LivingEntity owner, List<Stored> list) {
        ListTag tag = new ListTag();
        for (Stored s : list) {
            CompoundTag c = new CompoundTag();
            c.putString("t", s.type());
            c.putString("n", s.name());
            tag.add(c);
        }
        owner.getPersistentData().put(STOCK, tag);
    }

    // A mob she killed: pulled to her by a chain and sunk into her shadow (MkNet.STORE), kept if there is room.
    static void store(LivingEntity owner, LivingEntity dead) {
        List<Stored> list = stock(owner);
        if (list.size() >= MAX_STOCK || !(owner.level() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        list.add(new Stored(key.toString(), dead.getName().getString()));
        save(owner, list);
        MkNet.send(level, MkNet.STORE, dead.getId(), dead.position(), owner.getId(), list.size(), 0);
        Tasks.later(12, () -> SubCommon.sound(level, owner.position(), SoundEvents.SCULK_BLOCK_SPREAD, 1.0f, 0.6f));
    }

    // The Makima boss comes with her own puppets kept (Stark 2026-11-12: she starts with 3), once per body.
    public static void seed(LivingEntity owner, List<String> types) {
        if (owner.getPersistentData().getBoolean("makima_seeded")) {
            return;
        }
        owner.getPersistentData().putBoolean("makima_seeded", true);
        List<Stored> list = stock(owner);
        for (String type : types) {
            if (list.size() < MAX_STOCK) {
                EntityType<?> t = EntityType.byString(type).orElse(null);
                list.add(new Stored(type, t == null ? type : t.getDescription().getString()));
            }
        }
        save(owner, list);
    }

    // How many she keeps in her shadow.
    public static int kept(LivingEntity owner) {
        return stock(owner).size();
    }

    // How many of her mobs are out now.
    public static int out(LivingEntity owner) {
        return Puppets.count(owner, Puppets.MOB);
    }

    // A puppet that sank back by her (not killed): kept again.
    static void back(LivingEntity owner, String type, LivingEntity puppet) {
        List<Stored> list = stock(owner);
        if (list.size() < MAX_STOCK) {
            list.add(new Stored(type, puppet.getName().getString()));
            save(owner, list);
        } else {
            SubCommon.warn(owner, "makima.c_cheio", puppet.getName().getString());
        }
    }

    // ---------- C ----------

    public static boolean use(LivingEntity caster, ServerLevel level) {
        List<Stored> list = stock(caster);
        boolean anyOut = !Puppets.of(caster, -1).isEmpty();
        if (!(caster instanceof ServerPlayer player)) {
            if (list.isEmpty() || Puppets.count(caster, Puppets.MOB) >= MAX_OUT) {
                return false;
            }
            // A boss lets out the toughest one it has.
            int best = 0;
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).type().compareTo(list.get(best).type()) > 0) {
                    best = i;
                }
            }
            return summon(caster, level, best);
        }
        if (list.isEmpty() && !anyOut) {
            SubCommon.warn(caster, "makima.c_none");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (!SubCommon.held(caster, age, 0)) {
                // A tap: the first one kept.
                if (stock(caster).isEmpty()) {
                    SubCommon.warn(caster, "makima.c_none");
                } else if (Puppets.count(caster, Puppets.MOB) >= MAX_OUT) {
                    SubCommon.warn(caster, "makima.c_full");
                } else {
                    summon(caster, level, 0);
                }
                return true;
            }
            if (age >= HOLD) {
                // Held: the wheel, with the ones kept and the ones out (her mobs and her souls) to command.
                List<String> names = new ArrayList<>();
                for (Stored s : stock(caster)) {
                    names.add(s.name() + "|" + s.type());
                }
                names.addAll(Puppets.wheel(caster));
                PacketDistributor.sendToPlayer(player, new ModPayloads.MkOpen(0, Puppets.count(caster, Puppets.MOB), names));
                return true;
            }
            return false;
        });
        return true;
    }

    // The one picked on the wheel.
    public static void pick(ServerPlayer player, int index) {
        if (!Makima.is(player) || index < 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (Puppets.count(player, Puppets.MOB) >= MAX_OUT) {
            SubCommon.warn(player, "makima.c_full");
            return;
        }
        summon(player, level, index);
    }

    private static boolean summon(LivingEntity caster, ServerLevel level, int index) {
        List<Stored> list = stock(caster);
        if (index >= list.size()) {
            return false;
        }
        Stored s = list.get(index);
        EntityType<?> type = EntityType.byString(s.type()).orElse(null);
        Entity made = type == null ? null : type.create(level);
        if (!(made instanceof LivingEntity puppet)) {
            // Its kind is gone (a mod removed): it rots away.
            list.remove(index);
            save(caster, list);
            return false;
        }
        list.remove(index);
        save(caster, list);
        // Beside her, a little behind, each one its own spot.
        int out = Puppets.count(caster, Puppets.MOB);
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        if (look.lengthSqr() < 1e-4) {
            look = new Vec3(0, 0, 1);
        }
        look = look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        double[][] spots = {{-1.6, -1.4}, {1.7, -1.2}, {0.3, -2.6}};
        double[] sp = spots[Math.min(out, 2)];
        Vec3 at = SubCommon.ground(level, caster.position().add(side.scale(sp[0])).add(look.scale(sp[1])));
        puppet.moveTo(at.x, at.y, at.z, caster.getYRot(), 0);
        if (puppet instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        }
        if (caster instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss) {
            // The boss's puppets are her shield (the Primeiro-Ministro takes the fatal blow on them): tougher than the mob
            // was, so killing them is a fight of its own and not a single swing.
            net.minecraft.world.entity.ai.attributes.AttributeInstance hp = puppet.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(Math.max(hp.getBaseValue() * 3, 60));
            }
        }
        puppet.setHealth(puppet.getMaxHealth());
        if (s.name() != null && !s.name().isEmpty() && !s.name().equals(puppet.getType().getDescription().getString())) {
            puppet.setCustomName(net.minecraft.network.chat.Component.literal(s.name()));
        }
        level.addFreshEntity(puppet);
        Puppets.add(caster, puppet, Puppets.MOB, s.type(), -1);
        MkNet.send(level, MkNet.POOL, puppet.getId(), at, Math.max(1.5f, puppet.getBbWidth() * 2f), RISE + 50, 0);
        MkNet.send(level, MkNet.SINK, puppet.getId(), at, 1, RISE, 0);
        Freeze.hold(puppet, RISE + 4);
        SubCommon.pose(caster, "makima.fantoche");
        Makima.eyes(caster, 70);
        SubCommon.sound(level, at, SoundEvents.SCULK_BLOCK_SPREAD, 1.3f, 0.5f);
        SubCommon.sound(level, at, SoundEvents.CHAIN_PLACE, 1.0f, 0.6f);
        Tasks.later(RISE, () -> {
            MkNet.send(level, MkNet.DUST, puppet.getId(), at, 2.4f, 0, 0);
            SubCommon.sound(level, at, SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.7f);
        });
        DbCommon.cooldown(caster, 2, 30);
        return true;
    }
}
