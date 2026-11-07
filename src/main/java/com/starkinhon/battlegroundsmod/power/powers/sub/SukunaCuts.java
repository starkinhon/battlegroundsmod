package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Sukuna's cuts (SubAlma of Jujutsu Kaisen, PROVISIONAL: the Stark will redo it properly later), the Visualizador's
// Sukuna tab. Every cut is a black slit with a glowing white rim (shape 27).
// Z Dismantle (Kai): a flick of the hand and the cut flies almost unseen; it only shows where it lands. Held: he just
//   looks and the area in front becomes a storm of cuts.
// X Cleave (Hachi): he dashes and touches the target; red lines measure it and the right cut comes at once (harder on
//   tougher targets). Shift + X: Spider Web, the cuts run over the ground in lines and rings.
// C Fuga (Kamino): "Open." Fire gathers in his hands into a flaming arrow; he draws and lets go, and it blows up.
// V Domain Expansion, Malevolent Shrine: the sky turns red, the shrine rises behind him and everything in the area is cut
//   without pause; at the end the Fuga sets it all ablaze.
// B World Cut: with the vow (a three-line chant), the palm sweeps the air and space itself is cut.
public final class SukunaCuts {
    private SukunaCuts() {
    }

    public static Power create() {
        return new Power("sukuna_cuts", 0xC4182A, 1.1f, List.of(
                new Ability(45, 0, SukunaCuts::dismantle),
                new Ability(120, 0, SukunaCuts::cleave),
                new Ability(300, 0, SukunaCuts::fuga),
                new Ability(100, 0, SukunaCuts::shrine),
                new Ability(1200, 0, SukunaCuts::worldCut)),
                Arrays.asList(null, new Ability(160, 0, SukunaCuts::web), null, null));
    }

    // A slit on a body: random slant across its middle.
    private static void slitOn(ServerLevel level, LivingEntity t, float size) {
        Vec3 d = new Vec3(SubCommon.RANDOM.nextDouble() - 0.5, (SubCommon.RANDOM.nextDouble() - 0.5) * 1.4, SubCommon.RANDOM.nextDouble() - 0.5).normalize();
        Vec3 c = Combat.center(t).add((SubCommon.RANDOM.nextDouble() - 0.5) * t.getBbWidth() * 0.5, (SubCommon.RANDOM.nextDouble() - 0.5) * t.getBbHeight() * 0.5,
                (SubCommon.RANDOM.nextDouble() - 0.5) * t.getBbWidth() * 0.5);
        SubNet.send(level, SubNet.SLASH, 0, c, d, size, 0.22f + size * 0.05f, SubNet.STYLE_SUKUNA);
    }

    // ---------- Z: Dismantle ----------

    private static boolean dismantle(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int decide = 6;
        int bossHold = SubCommon.RANDOM.nextInt(3) == 0 ? 50 : 0;
        SubCommon.pose(caster, "sub.gesto");
        Map<Integer, Integer> lastHit = new HashMap<>();
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            SubCommon.busy(caster, 2);
            boolean held = SubCommon.held(caster, age, bossHold);
            if (age + 1 < decide) {
                return false;
            }
            if (age + 1 == decide && !held) {
                singleCut(caster, level);
                return true;
            }
            // The storm: the area in front is cut over and over while held (up to 3 seconds).
            Vec3 look = SubCommon.flat(caster.getLookAngle());
            Vec3 center = caster.position().add(look.scale(7)).add(0, 1, 0);
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            for (int i = 0; i < 3; i++) {
                Vec3 p = center.add(look.scale((SubCommon.RANDOM.nextDouble() - 0.5) * 9)).add(side.scale((SubCommon.RANDOM.nextDouble() - 0.5) * 9))
                        .add(0, (SubCommon.RANDOM.nextDouble() - 0.3) * 3, 0);
                SubNet.send(level, SubNet.SLASH, 0, p, Byakuya.randomDir(), 0.8f + SubCommon.RANDOM.nextFloat() * 1.6f, 0.15f, SubNet.STYLE_SUKUNA);
            }
            if (age % 2 == 0) {
                level.sendParticles(com.starkinhon.battlegroundsmod.combat.Fx.dust(0xFF2A10, 0.8f), center.x, center.y, center.z, 6, 4, 1.5, 4, 0.02);
                SubCommon.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.4f);
            }
            if (age % 4 == 0) {
                for (LivingEntity t : Combat.inRadius(level, center, 5.5, caster)) {
                    Integer last = lastHit.get(t.getId());
                    if (last != null && age - last < 4) {
                        continue;
                    }
                    lastHit.put(t.getId(), age);
                    t.invulnerableTime = 0;
                    Combat.hurt(caster, t, 1.6f);
                    slitOn(level, t, 0.9f);
                }
            }
            if (held && age < decide + 60) {
                return false;
            }
            // A longer rest after the storm.
            PowerData data = caster.getData(ModAttachments.POWER);
            data.cooldowns[0] = Math.max(data.cooldowns[0], 140);
            return true;
        });
        return true;
    }

    private static void singleCut(LivingEntity caster, ServerLevel level) {
        Vec3 start = caster.getEyePosition();
        Vec3 end = Combat.raycastPoint(caster, 26);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.8f);
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : Combat.alongLine(level, start, end, 0.8, caster)) {
            double d = t.distanceTo(caster);
            if (d < best) {
                best = d;
                first = t;
            }
        }
        if (first == null) {
            // Missed: it still opens where it lands.
            SubNet.send(level, SubNet.SLASH, 0, end, Byakuya.randomDir(), 1.2f, 0.25f, SubNet.STYLE_SUKUNA);
            return;
        }
        Combat.hurt(caster, first, 9);
        Stun.apply(first, 6);
        slitOn(level, first, 1.4f);
        SubCommon.sound(level, first.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
    }

    // ---------- X: Cleave ----------

    private static boolean cleave(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 12);
        if (target == null) {
            return false;
        }
        Vec3 dir = SubCommon.flat(target.position().subtract(caster.position()));
        Vec3 stop = target.position().subtract(dir.scale(1.1));
        if (!SubCommon.solid(level, stop.add(0, 0.5, 0)) && !SubCommon.solid(level, stop.add(0, 1.5, 0))) {
            float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
            if (caster instanceof ServerPlayer player) {
                player.teleportTo(level, stop.x, stop.y, stop.z, yaw, player.getXRot());
            } else {
                caster.teleportTo(stop.x, stop.y, stop.z);
            }
        }
        SubCommon.pose(caster, "sub.palma");
        SubCommon.busy(caster, 22);
        Stun.apply(target, 22);
        SubNet.send(level, SubNet.CLEAVE_SCAN, target.getId(), target.position(), Vec3.ZERO, 16, 0, 0);
        SubCommon.sound(level, target.position(), SoundEvents.SCULK_CLICKING, 1.2f, 1.2f);
        Tasks.later(16, () -> {
            if (!target.isAlive() || !caster.isAlive()) {
                return;
            }
            // Cleave adjusts to the target: the tougher it is, the deeper the cut.
            float damage = Math.min(26, 7 + target.getMaxHealth() * 0.12f);
            target.invulnerableTime = 0;
            Combat.hurt(caster, target, damage);
            for (int i = 0; i < 6; i++) {
                slitOn(level, target, 1.1f);
            }
            SubNet.send(level, SubNet.IMPACT, 0, Combat.center(target), 2.2f, SubNet.COLOR_RED);
            SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
            SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.5f);
            Combat.push(target, dir, 0.6, 0.2);
        });
        return true;
    }

    // ---------- Shift + X: Spider Web ----------

    private static boolean web(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "sub.cravar");
        SubCommon.busy(caster, 12);
        Tasks.later(6, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 center = SubCommon.ground(level, caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(2)));
            float radius = 7.5f;
            SubNet.send(level, SubNet.WEB, 0, center, Vec3.ZERO, radius, 0, 0);
            SubCommon.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.3f);
            SubCommon.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
            net.minecraft.world.level.block.state.BlockState below = level.getBlockState(net.minecraft.core.BlockPos.containing(center.subtract(0, 0.2, 0)));
            if (!below.isAir()) {
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below), center.x, center.y + 0.2, center.z, 140,
                        radius * 0.5, 0.3, radius * 0.5, 0.3);
            }
            for (LivingEntity t : Combat.inRadius(level, center, radius, caster)) {
                if (t.getY() - center.y > 2.5) {
                    continue;
                }
                Combat.hurt(caster, t, 8);
                Stun.apply(t, 14);
                t.setDeltaMovement(t.getDeltaMovement().add(0, 0.7, 0));
                t.hurtMarked = true;
                slitOn(level, t, 1f);
            }
        });
        return true;
    }

    // ---------- C: Fuga ----------

    private static boolean fuga(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int form = 30;
        SubCommon.pose(caster, "sub.maos");
        SubCommon.busy(caster, form + 6);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("ability.battlegroundsmod.sukuna_cuts.abrir"), true);
        }
        SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
        int id = SubNet.newId();
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.FUGA, id, Byakuya.hand(caster), caster.getLookAngle(), 0, 0, 1);
                return true;
            }
            if (age < form) {
                if (age == form - 10) {
                    SubCommon.pose(caster, "sub.arco");
                }
                if (age % 6 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.FIRE_AMBIENT, 1.2f, 0.8f);
                }
                SubNet.send(level, SubNet.FUGA, id, Byakuya.hand(caster), caster.getLookAngle(), 0, 0, 0);
                return false;
            }
            launchFuga(caster, level, id);
            return true;
        });
        return true;
    }

    private static void launchFuga(LivingEntity caster, ServerLevel level, int id) {
        Vec3 dir = caster.getLookAngle();
        Vec3[] pos = {Byakuya.hand(caster)};
        double speed = 2.4;
        double range = 56;
        SubCommon.sound(level, caster.position(), SoundEvents.BLAZE_SHOOT, 1.4f, 0.6f);
        Tasks.add(age -> {
            Vec3 next = pos[0].add(dir.scale(speed));
            boolean hit = !level.noCollision(new net.minecraft.world.phys.AABB(next, next).inflate(0.3))
                    || !Combat.alongLine(level, pos[0], next, 0.9, caster).isEmpty();
            pos[0] = next;
            boolean end = hit || age * speed >= range;
            SubNet.send(level, SubNet.FUGA, id, pos[0], dir, 1, 0, end ? 1 : 0);
            if (age % 2 == 0) {
                level.sendParticles(ParticleTypes.FLAME, next.x, next.y, next.z, 4, 0.2, 0.2, 0.2, 0.02);
            }
            if (end) {
                blast(caster, level, pos[0], 4.5f, 15);
            }
            return end;
        });
    }

    private static void blast(LivingEntity caster, ServerLevel level, Vec3 at, float radius, float damage) {
        SubNet.send(level, SubNet.EXPLOSION, 0, at, Vec3.ZERO, radius, 1, 0);
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.7f);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 20, radius * 0.4, radius * 0.3, radius * 0.4, 0.1);
        for (LivingEntity t : Combat.inRadius(level, at, radius, caster)) {
            t.invulnerableTime = 0;
            Combat.hurtMagic(caster, t, damage);
            t.setRemainingFireTicks(120);
            Combat.pushFrom(t, at, 1, 0.5);
        }
    }

    // ---------- V: Malevolent Shrine ----------

    private static boolean shrine(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 center = caster.position();
        Vec3 face = SubCommon.flat(caster.getLookAngle());
        float radius = 16;
        int ticks = 220;
        SubCommon.pose(caster, "sub.sinal");
        SubCommon.busy(caster, 30);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("ability.battlegroundsmod.sukuna_cuts.dominio"), true);
        }
        SubNet.send(level, SubNet.SHRINE, caster.getId(), center, face, radius, ticks, 0);
        SubNet.send(level, SubNet.AURA, caster.getId(), center, Vec3.ZERO, ticks, 3, 0);
        SubCommon.sound(level, center, SoundEvents.WITHER_SPAWN, 1.2f, 0.5f);
        SubCommon.sound(level, center, SoundEvents.BELL_RESONATE, 2f, 0.5f);
        Map<Integer, Integer> lastHit = new HashMap<>();
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.SHRINE, caster.getId(), center, face, 0, 0, 0);
                SubNet.send(level, SubNet.AURA, caster.getId(), center, Vec3.ZERO, 0, 3, 1);
                return true;
            }
            if (age < 40) {
                return false;
            }
            if (age < ticks - 30) {
                // Dismantle and Cleave without pause on everything in the area.
                for (int i = 0; i < 4; i++) {
                    double ang = SubCommon.RANDOM.nextDouble() * Mth.TWO_PI;
                    double r = radius * Math.sqrt(SubCommon.RANDOM.nextDouble());
                    Vec3 p = SubCommon.ground(level, center.add(Math.cos(ang) * r, 0, Math.sin(ang) * r)).add(0, 0.3 + SubCommon.RANDOM.nextDouble() * 3, 0);
                    SubNet.send(level, SubNet.SLASH, 0, p, Byakuya.randomDir(), 0.8f + SubCommon.RANDOM.nextFloat() * 2f, 0.16f, SubNet.STYLE_SUKUNA);
                }
                if (age % 3 == 0) {
                    SubCommon.sound(level, center.add((SubCommon.RANDOM.nextDouble() - 0.5) * radius, 1, (SubCommon.RANDOM.nextDouble() - 0.5) * radius),
                            SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.4f + SubCommon.RANDOM.nextFloat() * 0.6f);
                    level.sendParticles(com.starkinhon.battlegroundsmod.combat.Fx.dust(0xFF2A10, 1f), center.x, center.y + 2, center.z, 10, radius * 0.5, 2,
                            radius * 0.5, 0.02);
                }
                if (age % 5 == 0) {
                    for (LivingEntity t : Combat.inRadius(level, center, radius, caster)) {
                        Integer last = lastHit.get(t.getId());
                        if (last != null && age - last < 5) {
                            continue;
                        }
                        lastHit.put(t.getId(), age);
                        t.invulnerableTime = 0;
                        Combat.hurt(caster, t, 1.4f);
                        slitOn(level, t, 1f);
                    }
                }
                return false;
            }
            if (age == ticks - 30) {
                // "Open": the Fuga sets the dust of the cuts on fire.
                if (caster instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("ability.battlegroundsmod.sukuna_cuts.abrir"), true);
                }
                SubCommon.sound(level, center, SoundEvents.FIRECHARGE_USE, 2f, 0.5f);
            }
            if (age == ticks - 10) {
                blast(caster, level, center.add(face.scale(4)).add(0, 1, 0), radius * 0.6f, 12);
                for (int i = 0; i < 10; i++) {
                    double ang = i / 10.0 * Mth.TWO_PI;
                    Vec3 p = SubCommon.ground(level, center.add(Math.cos(ang) * radius * 0.7, 0, Math.sin(ang) * radius * 0.7)).add(0, 1, 0);
                    SubNet.send(level, SubNet.EXPLOSION, 0, p, Vec3.ZERO, 2.5f, 1, 0);
                }
            }
            return age >= ticks;
        });
        return true;
    }

    // ---------- B: World Cut ----------

    private static boolean worldCut(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int chant = 54;
        SubCommon.pose(caster, "sub.sinal");
        SubCommon.busy(caster, chant + 10);
        String[] lines = {"ability.battlegroundsmod.sukuna_cuts.voto1", "ability.battlegroundsmod.sukuna_cuts.voto2",
            "ability.battlegroundsmod.sukuna_cuts.voto3"};
        for (int i = 0; i < 3; i++) {
            int k = i;
            Tasks.later(i * 18, () -> {
                if (caster instanceof ServerPlayer player && !guard.broken()) {
                    player.displayClientMessage(Component.translatable(lines[k]), true);
                }
                SubCommon.sound(level, caster.position(), SoundEvents.BELL_BLOCK, 1f, 0.5f + k * 0.1f);
            });
        }
        Tasks.later(chant, () -> {
            if (guard.broken()) {
                return;
            }
            SubCommon.pose(caster, "sub.varrer");
            Vec3 look = SubCommon.flat(caster.getLookAngle());
            Vec3 side = look.cross(new Vec3(0, 1, 0));
            Vec3 at = caster.position().add(look.scale(14)).add(0, 1, 0);
            SubNet.send(level, SubNet.WORLD_CUT, 0, at, side, 40, 0, 0);
            SubCommon.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.5f);
            SubCommon.sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);
            // Everything on the cut's plane in front of him (a vertical sheet straight ahead) is split.
            Vec3 origin = caster.position();
            for (LivingEntity t : Combat.inRadius(level, origin.add(look.scale(30)), 32, caster)) {
                Vec3 rel = t.position().subtract(origin);
                double ahead = rel.dot(look);
                double off = Math.abs(rel.dot(side));
                if (ahead > 0 && ahead < 60 && off < 2.2 + t.getBbWidth() * 0.5) {
                    t.invulnerableTime = 0;
                    Combat.hurtMagic(caster, t, 40);
                    SubNet.send(level, SubNet.SLASH, 0, Combat.center(t), new Vec3(0, 1, 0), 2.5f, 0.4f, SubNet.STYLE_SUKUNA);
                }
            }
        });
        return true;
    }
}
