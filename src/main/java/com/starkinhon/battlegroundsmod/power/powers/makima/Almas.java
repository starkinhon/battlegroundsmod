package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.boss.BossSpec;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// V Almas (forja/makima-almas, approved 2026-11-11). The souls she has killed (a Gojo, a Sukuna, a player who had a
// soul) come up out of a dark ground, chains first and the body pulled out jerkily after them, wrapped at the chest and
// arms, a chain from her back to each: up to 2, fighting with their own kit (the bosses' AI) for 40 s. V again:
// "Morram." and they both execute their targets at the same moment (the Gojo's Blue crushes, the cuts of the Sukuna),
// an impact frame, and they sink back (a soul is never lost: it goes back to her).
public final class Almas {
    private static final String STOCK = "makima_souls";
    private static final int MAX_STOCK = 8;
    private static final int MAX_OUT = 2;
    private static final int LIFE = 800;
    private static final int RISE_AT = 11;
    private static final int RISE = 26;
    private static final int EXECUTE = 19;
    private static final float EXECUTE_DAMAGE = 140;

    private Almas() {
    }

    static List<String> stock(LivingEntity owner) {
        List<String> list = new ArrayList<>();
        ListTag tag = owner.getPersistentData().getList(STOCK, Tag.TAG_STRING);
        for (int i = 0; i < tag.size(); i++) {
            list.add(tag.getString(i));
        }
        return list;
    }

    private static void save(LivingEntity owner, List<String> list) {
        ListTag tag = new ListTag();
        for (String s : list) {
            tag.add(StringTag.valueOf(s));
        }
        owner.getPersistentData().put(STOCK, tag);
    }

    // Someone with a soul died by her hand: the soul is hers (the entity type its body comes back as).
    static void take(LivingEntity owner, LivingEntity dead) {
        String type = null;
        if (dead instanceof SoulBoss boss) {
            type = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
        } else if (dead instanceof Player player) {
            type = bodyOf(player.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).power);
        }
        if (type == null) {
            return;
        }
        List<String> list = stock(owner);
        if (list.contains(type)) {
            return;
        }
        if (list.size() >= MAX_STOCK) {
            list.remove(0);
        }
        list.add(type);
        save(owner, list);
        SubCommon.warn(owner, "makima.alma_nova", dead.getName());
    }

    // The body a power comes back in: the boss or SubAlma mob that carries it.
    private static String bodyOf(String power) {
        if (power == null || power.isEmpty()) {
            return null;
        }
        List<EntityType<?>> types = new ArrayList<>(List.of(ModEntities.MADARA.get(), ModEntities.BILLS.get(), ModEntities.GOJO.get(),
                ModEntities.KOKUSHIBO.get(), ModEntities.SUKUNA.get()));
        for (var t : ModEntities.SUB_MOBS) {
            types.add(t.get());
        }
        for (EntityType<?> t : types) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath();
            try {
                BossSpec spec = BossSpec.get(id);
                if (spec != null && power.equals(spec.poder())) {
                    return BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
                }
            } catch (RuntimeException e) {
                // A type without a spec file: not a soul's body.
            }
        }
        return null;
    }

    static void back(LivingEntity owner, String type) {
        List<String> list = stock(owner);
        if (!list.contains(type)) {
            list.add(type);
            save(owner, list);
        }
    }

    public static boolean canSummon(LivingEntity caster) {
        return !(caster instanceof SoulBoss) && !stock(caster).isEmpty();
    }

    public static boolean summon(LivingEntity caster, ServerLevel level) {
        List<String> list = stock(caster);
        int room = MAX_OUT - Puppets.count(caster, Puppets.SOUL);
        if (room <= 0) {
            SubCommon.warn(caster, "makima.v_full");
            return false;
        }
        // The last ones she took come first.
        List<String> picked = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0 && picked.size() < room; i--) {
            picked.add(list.get(i));
        }
        Makima.say(caster, "levantem");
        SubCommon.pose(caster, "makima.levantem");
        Makima.eyes(caster, 110);
        Makima.gray(level, caster.position(), 66, 0.8f);
        SubCommon.busy(caster, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_EMERGE, 0.9f, 0.8f);
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        int made = 0;
        for (int i = 0; i < picked.size(); i++) {
            String type = picked.get(i);
            EntityType<?> et = EntityType.byString(type).orElse(null);
            Entity e = et == null ? null : et.create(level);
            if (!(e instanceof LivingEntity soul)) {
                list.remove(type);
                continue;
            }
            list.remove(type);
            Vec3 spot = SubCommon.ground(level, caster.position().add(side.scale(i == 0 ? -2.4 : 2.4)).add(look.scale(1.2)));
            int delay = RISE_AT + i * 10;
            int index = i;
            soul.moveTo(spot.x, spot.y, spot.z, caster.getYRot() + (i == 0 ? 15 : -15), 0);
            if (soul instanceof net.minecraft.world.entity.Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            }
            if (soul instanceof SoulBoss boss) {
                boss.puppetOf(caster);
            }
            MkNet.send(level, MkNet.POOL, caster.getId(), spot, 2.2f, LIFE / 10, 0);
            MkNet.send(level, MkNet.BITS, caster.getId(), spot, new Vec3(0, 0.6, 0), 12, 0.6f, 0);
            Tasks.later(delay, () -> {
                if (!caster.isAlive()) {
                    return;
                }
                level.addFreshEntity(soul);
                Puppets.add(caster, soul, Puppets.SOUL, type, LIFE);
                MkNet.send(level, MkNet.SINK, soul.getId(), spot, 1, RISE, 1);
                Freeze.hold(soul, RISE + 4);
                SubCommon.sound(level, spot, SoundEvents.CHAIN_PLACE, 1.4f, 0.5f);
                SubCommon.sound(level, spot, SoundEvents.ROOTED_DIRT_BREAK, 1.3f, 0.5f);
                Tasks.later(RISE, () -> {
                    MkNet.send(level, MkNet.DUST, soul.getId(), spot, 3, 0, 0);
                    SubCommon.sound(level, spot, SoundEvents.CHAIN_HIT, 1.2f, 0.6f);
                });
            });
            made++;
        }
        save(caster, list);
        if (made == 0) {
            return Corrente.ground(caster, level);
        }
        DbCommon.cooldown(caster, PowerData.SLOT_ULT, 1800);
        return true;
    }

    // V again: "Morram." Both execute at once, then sink back.
    public static boolean morram(LivingEntity caster) {
        List<LivingEntity> souls = Puppets.of(caster, Puppets.SOUL);
        if (souls.isEmpty() || !(caster.level() instanceof ServerLevel level)) {
            return false;
        }
        Makima.say(caster, "morram");
        SubCommon.pose(caster, "makima.morram_almas");
        Makima.eyes(caster, 110);
        Makima.gray(level, caster.position(), 70, 0.9f);
        for (LivingEntity soul : souls) {
            Puppets.Entry entry = Puppets.entry(soul);
            if (entry == null) {
                continue;
            }
            LivingEntity target = soul instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
            if (target == null || !target.isAlive() || target.distanceTo(soul) > 30 || Makima.ours(caster, target)) {
                target = nearest(level, caster, soul);
            }
            LivingEntity t = target;
            Freeze.hold(soul, 70);
            MkNet.send(level, MkNet.SOUL_KILL, soul.getId(), soul.position(), new Vec3(t == null ? -1 : t.getId(), 0, 0), 0, 0, 0);
            if (t != null) {
                Runnable release = Freeze.hold(t, EXECUTE + 4);
                Stun.apply(t, EXECUTE + 20);
                Tasks.later(EXECUTE, () -> {
                    release.run();
                    if (!t.isAlive()) {
                        return;
                    }
                    Vec3 c = Combat.center(t);
                    MkNet.send(level, MkNet.IMPACT, caster.getId(), c, 1, 1, 0);
                    MkNet.send(level, MkNet.SHELL, t.getId(), c, 2.3f, 0, t.getId() * 1.7f);
                    MkNet.send(level, MkNet.MIST, t.getId(), c, new Vec3(1, 0.2, 0), 50, 0, 0);
                    MkNet.send(level, MkNet.MIST, t.getId(), c, new Vec3(-1, 0.2, 0), 50, 0, 0);
                    SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.7f);
                    if (Makima.boss(t)) {
                        Combat.hurt(soul, t, EXECUTE_DAMAGE);
                        Stun.apply(t, 40);
                    } else {
                        if (t.getHealth() <= EXECUTE_DAMAGE * 1.5f || !(t instanceof Player)) {
                            MkNet.send(level, MkNet.BITS, t.getId(), c, Vec3.ZERO, 42, 1, 1);
                        }
                        Corrente.kill(soul, t, EXECUTE_DAMAGE);
                    }
                });
            }
            // Then they sink back (the soul goes back to her).
            Tasks.later(66, () -> {
                Puppets.Entry e = Puppets.entry(soul);
                if (e != null) {
                    Puppets.sink(e);
                }
            });
        }
        DbCommon.cooldown(caster, PowerData.SLOT_ULT, 1800);
        return true;
    }

    private static LivingEntity nearest(ServerLevel level, LivingEntity caster, LivingEntity soul) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Combat.inRadius(level, soul.position(), 24, soul)) {
            if (Makima.ours(caster, e) || !e.isAlive()) {
                continue;
            }
            double d = e.distanceToSqr(soul);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
