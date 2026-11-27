// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Miwa (SubAlma of Jujutsu, tier 8), remade from the approved preview (forja/miwa-kit, style Realista+Anime, Stark
// 2026-11-11): New Shadow Style, the swordswoman of patience and the counter: weak if you only swing, deadly if you read.
// Passive, Bainha Calma: every second without attacking fills 1 of 3 marks (+15% each; it was 25% before the rebalance of 2026-11-20); the next cut spends them.
// Z Batto (1st update, approved playable preview forja/miwa-kit/miwa-jogo.js, Stark 2026-11-27): hold, a ring closes on
//   the ground round her; let go while it touches the inner one (+-0.2 s) = Batto Perfeito: a 7 block draw through the
//   target, 30 damage, 1.5 s stun, no cooldown and 3 s off her other skills. Out of time = a 4 block draw, 14 damage.
// X Simple Domain: comes out at once and never locks her (to combo with). The circle (4 blocks) stays 8 s where she cast
//   it; with her inside, whoever steps in is cut on their own and thrown out (up to 3 cuts), shots are cut down, a
//   Domain Expansion has no hold on her, the Batto's ring closes twice as fast and the Lua Partida doubles. Techniques
//   from outside: small ones are cut, a beam splits round her with half its damage, an ult or a boss goes through.
//   Each automatic cut fills a mark. X again undoes it.
// C Line Cut: 8 blocks in an instant through the target, a slit left in the ground; the cut opens after she sheathes.
// B New Shadow Counter: a 1.2 s window (a ring round her shows it); a blow in it vanishes, she appears behind the attacker already cutting (2 s stun).
//   Nobody hits: she is open for a moment.
// V Batto: Lua Partida (ult): eyes shut, the circle grows to 12 blocks (24 cast in her Simple Domain) for 5 s; whoever comes in is marked (still uncut).
//   At the end, or V again, one single draw cuts every one marked together and the ground inside the circle (3 s stun).
// The sword never cracks (Stark). Stuns follow the tier rule (JjCommon.tierStun).
public final class Miwa {
    // Stark 2026-11-26: "o X é completamente inútil": at 2.2 blocks nobody stepped in. 4 blocks now.
    private static final double RADIUS = 4.0;
    // 8 s (approved preview 2026-11-27), up to 3 automatic cuts.
    private static final int DOMAIN_TICKS = 160;
    private static final int DOMAIN_CUTS = 3;
    // A technique hitting for this much or less counts as a small shot the Simple Domain cuts down.
    private static final float SMALL_SHOT = 12;
    // Stark 2026-11-26: nobody walked into the ult. 12 blocks now (was 8): whoever is near when it starts is marked, and
    // V again cuts at once.
    private static final double LUA_R = 12;
    private static final int LUA_TICKS = 100;
    // The ring touches the inner one at 0.7 s (0.35 s inside her Simple Domain); the window is +-0.2 s round it.
    private static final int PERFECT_AT = 14;
    private static final int PERFECT_AT_DOMAIN = 7;
    private static final int PERFECT_WIN = 4;
    private static final float RING_IN = 0.6f;
    private static final float RING_OUT = 2.3f;
    // 1.2 s (test with friends, 2026-11-25: 0.8 s was too tight to read).
    private static final int COUNTER_TICKS = 24;
    // In the counter's window.
    public static final String COUNTER = "jj_miwa_counter";
    // Where her Simple Domain stands, while it stands.
    private static final Map<UUID, Vec3> DOMAINS = new HashMap<>();
    private static final Set<UUID> LUA = new HashSet<>();
    // Bainha Calma: when she last cut, and marks the Simple Domain gave her on top.
    private static final Map<UUID, long[]> CALM = new HashMap<>();

    private Miwa() {
    }

    public static Power create() {
        return new Power("miwa", 0x6FB8E8, 0.95f, List.of(
                // The approved playable preview's cooldowns (miwa-jogo.js CD): Z 2.5 s, C 5 s, B 8 s (Stark 2026-11-27: "mal ataca").
                new Ability(50, 0, Miwa::batto),
                new Ability(300, 0, Miwa::simpleDomain),
                new Ability(100, 0, Miwa::lineCut),
                new Ability(1000, 0, Miwa::lua),
                new Ability(160, 0, Miwa::counterStance)));
    }

    // ---------- Bainha Calma ----------

    public static int marks(LivingEntity e) {
        long[] c = CALM.get(e.getUUID());
        long now = e.level().getGameTime();
        if (c == null) {
            return 3;
        }
        return (int) Math.min(3, (now - c[0]) / 20 + c[1]);
    }

    // The next cut spends the marks: its damage multiplier.
    private static float spend(LivingEntity caster) {
        int m = marks(caster);
        CALM.put(caster.getUUID(), new long[] {caster.level().getGameTime(), 0});
        if (m > 0) {
            SubCommon.warn(caster, "miwa.marks", m);
        }
        return 1 + 0.15f * m;
    }

    private static void addMark(LivingEntity caster) {
        long[] c = CALM.computeIfAbsent(caster.getUUID(), k -> new long[] {caster.level().getGameTime(), 0});
        c[1] = Math.min(3, c[1] + 1);
    }

    // Any blow of hers resets the calm (called from JjCommon for every hit she lands).
    public static void struck(LivingEntity miwa) {
        long[] c = CALM.get(miwa.getUUID());
        long now = miwa.level().getGameTime();
        if (c == null) {
            CALM.put(miwa.getUUID(), new long[] {now, 0});
        } else {
            c[0] = now;
        }
    }

    // ---------- the pieces of every cut ----------

    private static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.64, 0);
    }

    // The scabbard's mouth at her left hip.
    private static Vec3 scabbard(LivingEntity e) {
        Vec3 f = SubCommon.flat(e.getLookAngle());
        Vec3 left = new Vec3(f.z, 0, -f.x);
        return e.position().add(left.scale(0.31)).add(f.scale(0.25)).add(0, 0.95 * e.getBbHeight() / 1.8, 0);
    }

    private static void click(ServerLevel level, LivingEntity caster) {
        click(level, caster, 0.7f);
    }

    private static void click(ServerLevel level, LivingEntity caster, float size) {
        SubNet.send(level, SubNet.MW_GLINT, 0, scabbard(caster), Vec3.ZERO, size, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.TRIPWIRE_CLICK_ON, 1.2f, 1.8f);
    }

    // The draw's arc round her (style 0 a draw, 1 the ult's ring, 2 its inner ring); width < 0 sweeps the other way.
    private static void arc(ServerLevel level, Vec3 at, Vec3 fwd, float r, float span, float width, int style) {
        SubNet.send(level, SubNet.MW_SLASH, style, at, fwd, r, span, width);
    }

    // The cut on a body: angle and height of the line, front (1) or back (-1), how wide it opens, life, when it opens (s).
    private static void cutLine(ServerLevel level, LivingEntity t, double angle, double y, int side, float big, float life, float openAt) {
        SubNet.send(level, SubNet.MW_CUT, t.getId(), Combat.center(t), new Vec3(angle, y, side), big, life, openAt);
    }

    private static void hurt(LivingEntity caster, ServerLevel level, LivingEntity t, float damage, int stun, boolean full) {
        Combat.hurt(caster, t, damage);
        if (stun > 0) {
            Stun.apply(t, stun, full);
        }
        SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.6f);
        SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.4f);
    }

    // ---------- Z: Batto ----------

    private static boolean batto(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int at = inOwnDomain(caster) ? PERFECT_AT_DOMAIN : PERFECT_AT;
        // Let go on its own a little after the window.
        int most = at + PERFECT_WIN + 5;
        // Bosses let go in the window now and then (a Batto Perfeito), otherwise early.
        int bossHold = SubCommon.RANDOM.nextFloat() < 0.4f ? at : 3 + SubCommon.RANDOM.nextInt(Math.max(1, at - PERFECT_WIN - 3));
        int ring = SubNet.newId(), inner = SubNet.newId();
        SubCommon.pose(caster, "jj.batto_stance");
        SubCommon.busy(caster, most + 12);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.6f);
        if (at == PERFECT_AT_DOMAIN) {
            SubCommon.warn(caster, "miwa.fast_ring");
        }
        Tasks.add(age -> {
            boolean win = Math.abs(age - at) <= PERFECT_WIN;
            if (guard.broken()) {
                rings(level, caster, ring, inner, 0, win, true);
                return true;
            }
            if (age == at - PERFECT_WIN) {
                // The moment to let go: a big glint on the scabbard, the click and a bell only she hears.
                click(level, caster, 1.4f);
                if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.9f);
                }
            } else if (win && age % 3 == 0) {
                SubNet.send(level, SubNet.MW_GLINT, 0, scabbard(caster), Vec3.ZERO, 0.9f, 0, 0);
            }
            boolean held = SubCommon.held(caster, age, bossHold);
            if (age < 3 || (held && age < most)) {
                rings(level, caster, ring, inner, age / (float) at, win, false);
                Charge.report(caster, Math.min(1, age / (double) at));
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                return false;
            }
            rings(level, caster, ring, inner, 1, win, true);
            draw(caster, level, win);
            return true;
        });
        return true;
    }

    // The ring that closes on the ground round her (k = 0 open .. 1 on the inner one) and the inner one, white in the window.
    private static void rings(ServerLevel level, LivingEntity caster, int ring, int inner, float k, boolean win, boolean end) {
        Vec3 feet = caster.position();
        float r = RING_IN + (RING_OUT - RING_IN) * Math.max(0, 1 - k);
        SubNet.send(level, SubNet.MW_RING, ring, feet, Vec3.ZERO, 1, Math.max(RING_IN * 0.7f, r), end ? 1 : 0);
        SubNet.send(level, SubNet.MW_RING, inner, feet, Vec3.ZERO, win ? 1 : 0.45f, RING_IN, end ? 1 : 0);
    }

    // The draw itself: she crosses 4 blocks (7 on a Batto Perfeito) through whoever is in the way.
    private static void draw(LivingEntity caster, ServerLevel level, boolean perfect) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        double reach = perfect ? 7 : 4;
        int dash = perfect ? 3 : 2;
        LivingEntity aimed = JjCommon.target(caster, reach + 1.5);
        Vec3 dir = aimed != null ? SubCommon.flat(aimed.position().subtract(caster.position())) : SubCommon.flat(caster.getLookAngle());
        // She crosses THROUGH whoever she cuts (the preview), never stopping against them: past them by a step.
        double length = aimed != null ? Math.max(reach, aimed.distanceTo(caster) + aimed.getBbWidth() * 0.5 + 1.2) : reach;
        Vec3 start = caster.position();
        Vec3 goal = start.add(dir.scale(length));
        // Never into a wall: stops at the last free spot.
        for (double d = 0.5; d <= length; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            if (SubCommon.solid(level, p.add(0, 0.5, 0)) || SubCommon.solid(level, p.add(0, 1.5, 0))) {
                goal = start.add(dir.scale(Math.max(0, d - 0.6)));
                break;
            }
        }
        Vec3 end = goal;
        Vec3[] at = {start};
        SubCommon.pose(caster, "jj.batto_draw");
        SubCommon.busy(caster, dash + 8);
        JjCommon.carry(caster, dash, at);
        Vec3 c = chest(caster);
        float r = perfect ? 3.6f : 3.0f;
        arc(level, c, dir, r, 2.3f, perfect ? -0.2f : -0.15f, 0);
        if (perfect) {
            arc(level, c.add(0, 0.05, 0), dir, r * 0.8f, 2.4f, -0.1f, 0);
        }
        Vec3 path = end.subtract(start);
        if (path.length() > 0.5) {
            SubNet.send(level, SubNet.MW_STREAK, 0, start.add(0, 1.1, 0), path, perfect ? 0.32f : 0.24f, 0.45f, 0);
        }
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.8f);
        float mult = spend(caster);
        if (perfect) {
            // Batto Perfeito: no cooldown, and 3 s off the others (X, C, V, B).
            JjCommon.shorten(caster, 0, 0);
            com.starkinhon.battlegroundsmod.power.PowerData data = caster.getData(ModAttachments.POWER);
            for (int slot : new int[] {1, 2, 3, com.starkinhon.battlegroundsmod.power.PowerData.SLOT_EXTRA}) {
                for (int key : com.starkinhon.battlegroundsmod.power.SkillSwap.cooldownKeys(caster, null, slot)) {
                    data.cooldowns[key] = Math.max(0, data.cooldowns[key] - 60);
                }
            }
            if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
                com.starkinhon.battlegroundsmod.power.PowerLogic.sync(player);
            }
            SubCommon.warn(caster, "miwa.perfect");
        }
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                return true;
            }
            at[0] = start.lerp(end, Math.min(1, (age + 1) / (float) dash));
            if (age < dash) {
                return false;
            }
            Freeze.free(caster);
            caster.getData(ModAttachments.POWER).busyTicks = 4;
            // Who she cut: whoever is on the path she crossed, where they stand now that she is through (the preview
            // checks it 0.09 s into the draw, against everyone within 1.3 of the line).
            List<LivingEntity> hit = Combat.alongLine(level, start.add(0, 1, 0), end.add(0, 1, 0), 1.3, caster);
            if (!perfect && hit.isEmpty()) {
                SubCommon.warn(caster, "miwa.miss");
                JjCommon.shorten(caster, 0, 30);
            }
            Tasks.later(2, () -> click(level, caster));
            // The cut lands as she comes through (the preview: 0.09 s into the draw), not half a second later.
            Tasks.later(1, () -> {
                boolean first = true;
                for (LivingEntity t : hit) {
                    if (!t.isAlive()) {
                        continue;
                    }
                    cutLine(level, t, 0.9, 1.12, 1, perfect ? 1.5f : 1f, perfect ? 1.9f : 1.4f, 0.04f);
                    if (perfect) {
                        hurt(caster, level, t, 30 * mult, JjCommon.tierStun(caster, t, 30), true);
                        Combat.push(t, dir, 1.4, 0.3);
                    } else {
                        hurt(caster, level, t, 14 * mult, JjCommon.control(caster, t, 6), false);
                        Combat.push(t, dir, 0.6, 0.15);
                    }
                    if (first) {
                        DbCommon.screen(level, Combat.center(t), perfect ? 44 : 43);
                        first = false;
                    }
                }
                if (perfect) {
                    // Batto Perfeito cuts the stone beside her: a thin cut through the arc at chest height.
                    if (hit.isEmpty()) {
                        DbCommon.screen(level, chest(caster), 44);
                    }
                    Vec3 o = chest(caster);
                    float base = (float) Math.atan2(dir.z, dir.x);
                    for (int i = 0; i <= 10; i++) {
                        float a = base - 1.15f + 2.3f * i / 10f;
                        for (double d = 1.4; d <= r; d += 0.9) {
                            JjCommon.breakAt(level, BlockPos.containing(o.add(Mth.cos(a) * d, 0, Mth.sin(a) * d)), o, false);
                        }
                    }
                }
            });
            return true;
        });
    }

    // ---------- X: Simple Domain ----------

    // Is she standing in her own Simple Domain right now?
    public static boolean inOwnDomain(LivingEntity e) {
        Vec3 c = DOMAINS.get(e.getUUID());
        return c != null && new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).length() < RADIUS && Math.abs(e.getY() - c.y) < 2.5;
    }

    // X comes out even in the middle of another skill of hers (Stark 2026-11-27: "pra combar").
    public static boolean castsWhileBusy(String power, int slot) {
        return "miwa".equals(power) && slot == 1;
    }

    private static boolean simpleDomain(LivingEntity caster, ServerLevel level) {
        if (DOMAINS.remove(caster.getUUID()) != null) {
            return false;
        }
        Vec3 center = caster.position();
        int id = SubNet.newId();
        DOMAINS.put(caster.getUUID(), center);
        // At once and without locking her: no stance, no busy.
        DbCommon.cooldown(caster, 1, 10);
        SubCommon.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        SubCommon.sound(level, center, SoundEvents.TRIPWIRE_CLICK_ON, 1.2f, 1.8f);
        boolean mob = !(caster instanceof Player);
        int mobTicks = 120;
        if (mob) {
            // Mobs keep still in it (they would walk out of it at once).
            Freeze.holdLookFree(caster, mobTicks);
        }
        Set<Integer> inside = new HashSet<>();
        Vec3[] flash = {new Vec3(0, 0, -1)};
        int[] flashes = {0};
        int[] cuts = {0};
        Tasks.add(age -> {
            boolean end = !caster.isAlive() || DOMAINS.get(caster.getUUID()) != center || age >= (mob ? mobTicks : DOMAIN_TICKS);
            Vec3 fl = new Vec3(flash[0].x, flashes[0], flash[0].z);
            if (end) {
                if (DOMAINS.get(caster.getUUID()) == center) {
                    DOMAINS.remove(caster.getUUID());
                }
                if (mob) {
                    Freeze.free(caster);
                }
                SubCommon.clearState(caster, JjCommon.SIMPLE_DOMAIN);
                SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, center, fl, 1, (float) RADIUS, 1);
                SubCommon.sound(level, center, SoundEvents.BEACON_DEACTIVATE, 1f, 1.6f);
                DbCommon.cooldown(caster, 1, 300);
                return true;
            }
            SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, center, fl, Math.min(1, age / 4f), (float) RADIUS, 0);
            // The circle only works with her inside it.
            boolean in = inOwnDomain(caster);
            if (in) {
                SubCommon.setState(caster, JjCommon.SIMPLE_DOMAIN, 3);
            } else {
                SubCommon.clearState(caster, JjCommon.SIMPLE_DOMAIN);
            }
            Set<Integer> nowIn = new HashSet<>();
            for (LivingEntity e : Combat.inRadius(level, center.add(0, 0.9, 0), RADIUS + 0.3, caster)) {
                if (Math.abs(e.getY() - center.y) > 2.5) {
                    continue;
                }
                nowIn.add(e.getId());
                // Whoever steps in is cut on their own and thrown out, up to 3 times.
                if (in && !inside.contains(e.getId()) && cuts[0] < DOMAIN_CUTS) {
                    cuts[0]++;
                    Vec3 to = SubCommon.flat(e.position().subtract(caster.position()));
                    flash[0] = SubCommon.flat(e.position().subtract(center));
                    flashes[0]++;
                    SubCommon.pose(caster, "jj.batto_draw");
                    arc(level, chest(caster), to, 2.4f, 2.0f, flashes[0] % 2 == 0 ? 0.14f : -0.14f, 0);
                    cutLine(level, e, -0.61, 1.175, 1, 1, 1.4f, 0.08f);
                    hurt(caster, level, e, 7, JjCommon.control(caster, e, 15), false);
                    Combat.push(e, flash[0], 1.2, 0.3);
                    DbCommon.screen(level, Combat.center(e), 43);
                    addMark(caster);
                    Tasks.later(3, () -> click(level, caster));
                }
            }
            inside.clear();
            inside.addAll(nowIn);
            // Shots that come in are cut down.
            if (in) {
                for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(RADIUS, 2.5, RADIUS))) {
                    if (p.getOwner() != caster && p.position().subtract(center).horizontalDistance() < RADIUS + 0.2) {
                        flash[0] = SubCommon.flat(p.position().subtract(center));
                        flashes[0]++;
                        cutShot(level, caster, p.position());
                        p.discard();
                        addMark(caster);
                    }
                }
            }
            return false;
        });
        return false;
    }

    // A shot cut down in the air: her draw towards it, a glint where it splits.
    private static void cutShot(ServerLevel level, LivingEntity caster, Vec3 at) {
        Vec3 to = SubCommon.flat(at.subtract(caster.position()));
        arc(level, chest(caster), to, 2.2f, 1.6f, 0.1f, 0);
        SubNet.send(level, SubNet.MW_GLINT, 0, at, Vec3.ZERO, 0.9f, 0, 0);
        SubCommon.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 2f);
    }

    // A technique on her from outside her circle while she stands in it (JjCommon.onIncomingDamage). Returns the damage left:
    // a small one (a ki blast, a shot) is cut, a beam splits round her with half, an ult or a boss goes through untouched.
    public static float domainTakes(LivingEntity miwa, ServerLevel level, LivingEntity enemy, float amount) {
        Vec3 c = DOMAINS.get(miwa.getUUID());
        if (c == null || !inOwnDomain(miwa) || !Combat.technique() || enemy == miwa
                || new Vec3(enemy.getX() - c.x, 0, enemy.getZ() - c.z).length() < RADIUS + 0.5 || JjCommon.isBoss(enemy)) {
            return amount;
        }
        if (enemy.hasData(ModAttachments.POWER)) {
            var data = enemy.getData(ModAttachments.POWER);
            if (com.starkinhon.battlegroundsmod.power.PowerData.isUlt(data.lastCastSlot) && level.getGameTime() - data.lastCastTime <= 200) {
                return amount;
            }
        }
        Vec3 from = chest(miwa).add(SubCommon.flat(enemy.position().subtract(miwa.position())).scale(0.9));
        cutShot(level, miwa, from);
        addMark(miwa);
        if (amount <= SMALL_SHOT) {
            return 0;
        }
        // The beam splits on her blade: two arcs opening to the sides.
        Vec3 to = SubCommon.flat(enemy.position().subtract(miwa.position()));
        arc(level, chest(miwa), to, 1.8f, 2.6f, 0.12f, 0);
        arc(level, chest(miwa), to, 1.8f, 2.6f, -0.12f, 0);
        SubCommon.sound(level, from, SoundEvents.ANVIL_LAND, 0.4f, 1.8f);
        return amount * 0.5f;
    }

    // ---------- C: Line Cut ----------

    private static boolean lineCut(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        double length = 8;
        int dash = 3;
        LivingEntity t = JjCommon.target(caster, length + 2);
        Vec3 dir = t != null ? SubCommon.flat(t.position().subtract(caster.position())) : SubCommon.flat(caster.getLookAngle());
        Vec3 start = caster.position();
        Vec3 goal = start.add(dir.scale(length));
        // Never into a wall: stops at the last free spot.
        for (double d = 0.5; d <= length; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            if (SubCommon.solid(level, p.add(0, 0.5, 0)) || SubCommon.solid(level, p.add(0, 1.5, 0))) {
                goal = start.add(dir.scale(Math.max(0, d - 0.6)));
                break;
            }
        }
        Vec3 end = goal;
        Vec3[] at = {start};
        SubCommon.pose(caster, "jj.line_cut");
        SubCommon.busy(caster, dash + 12);
        JjCommon.carry(caster, dash, at);
        SubCommon.sound(level, start, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.9f);
        List<LivingEntity> hit = Combat.alongLine(level, start.add(0, 1, 0), end.add(0, 1, 0), 0.9, caster);
        Vec3 path = end.subtract(start);
        SubNet.send(level, SubNet.MW_STREAK, 0, start.add(0, 1.1, 0).subtract(dir.scale(0.3)), path.add(dir.scale(0.6)), 0.32f, 0.6f, 0);
        if (path.length() > 0.5) {
            SubNet.send(level, SubNet.MW_GASH, 0, start.add(0, 0.03, 0).add(dir.scale(0.2)), path, 2.6f, 0, 0);
        }
        float mult = spend(caster);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                return true;
            }
            at[0] = start.lerp(end, Math.min(1, (age + 1) / (float) dash));
            if (age < dash) {
                return false;
            }
            Freeze.free(caster);
            if (hit.isEmpty()) {
                SubCommon.warn(caster, "miwa.miss");
                JjCommon.shorten(caster, 2, 50);
                Tasks.later(4, () -> click(level, caster));
                return true;
            }
            for (LivingEntity e : hit) {
                Stun.apply(e, 8);
            }
            Tasks.later(4, () -> click(level, caster));
            Tasks.later(7, () -> {
                boolean first = true;
                for (LivingEntity e : hit) {
                    if (e.isAlive()) {
                        cutLine(level, e, -0.29, 1.15, 1, 1, 1.4f, 0.03f);
                        hurt(caster, level, e, 10 * mult, JjCommon.tierStun(caster, e, 25), true);
                        if (first) {
                            DbCommon.screen(level, Combat.center(e), 43);
                            first = false;
                        }
                    }
                }
            });
            return true;
        });
        return true;
    }

    // ---------- B: New Shadow Counter ----------

    private static boolean counterStance(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "jj.miwa_counter");
        SubCommon.busy(caster, COUNTER_TICKS);
        SubCommon.setState(caster, COUNTER, COUNTER_TICKS);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.9f);
        int id = SubNet.newId();
        Vec3 center = caster.position();
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, COUNTER);
            if (!on || !caster.isAlive() || age >= COUNTER_TICKS) {
                SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, caster.position(), Vec3.ZERO, 1, 0.75f, 1);
                if (on) {
                    // Nobody hit: she is open for a moment.
                    SubCommon.clearState(caster, COUNTER);
                    SubCommon.busy(caster, 10);
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 3, false, false));
                    SubCommon.warn(caster, "miwa.exposed");
                }
                return true;
            }
            float r = 1.1f * (1.4f - 0.4f * SubCommon.smooth(age / (float) COUNTER_TICKS));
            SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, caster.position(), Vec3.ZERO, 1, r, 0);
            return false;
        });
        return true;
    }

    // A blow landed in the window: it vanishes, and she is behind the attacker already cutting.
    public static void counter(LivingEntity miwa, ServerLevel level, LivingEntity enemy) {
        SubCommon.clearState(miwa, COUNTER);
        Vec3 hitAt = chest(miwa).add(SubCommon.flat(enemy.position().subtract(miwa.position())).scale(0.35));
        SubNet.send(level, SubNet.MW_GLINT, 0, hitAt, Vec3.ZERO, 1.1f, 0, 0);
        SubCommon.sound(level, hitAt, SoundEvents.ANVIL_LAND, 0.5f, 2f);
        Vec3 away = SubCommon.flat(enemy.position().subtract(miwa.position()));
        if (away.lengthSqr() < 1.0E-4) {
            away = SubCommon.flat(enemy.getLookAngle()).scale(-1);
        }
        Vec3 behind = enemy.position().add(away.scale(enemy.getBbWidth() * 0.5 + 1.2));
        if (SubCommon.solid(level, behind.add(0, 0.5, 0)) || SubCommon.solid(level, behind.add(0, 1.5, 0))) {
            behind = miwa.position();
        }
        Vec3 face = SubCommon.flat(enemy.position().subtract(behind));
        Vec3 to = behind;
        Tasks.later(2, () -> {
            if (!miwa.isAlive()) {
                return;
            }
            float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
            miwa.teleportTo(to.x, to.y, to.z);
            miwa.setYRot(yaw);
            miwa.setYHeadRot(yaw);
            miwa.yBodyRot = yaw;
            SubCommon.pose(miwa, "jj.batto_draw");
            arc(level, chest(miwa), face, 2.6f, 2.4f, 0.15f, 0);
            SubCommon.sound(level, to, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.8f);
            float mult = spend(miwa);
            Tasks.later(2, () -> click(level, miwa));
            Tasks.later(4, () -> {
                if (!enemy.isAlive()) {
                    return;
                }
                cutLine(level, enemy, 0.74, 1.15, -1, 1.3f, 2.2f, 0.03f);
                hurt(miwa, level, enemy, 12 * mult, JjCommon.tierStun(miwa, enemy, 40), true);
                DbCommon.screen(level, Combat.center(enemy), 44);
            });
        });
    }

    // ---------- V: Batto: Lua Partida ----------

    // V again while the Lua Partida's circle is open: the draw now (the circle holds her and keeps her busy, so the press
    // never reaches lua() through canCast).
    public static boolean luaPress(LivingEntity caster) {
        return LUA.remove(caster.getUUID());
    }

    private static boolean lua(LivingEntity caster, ServerLevel level) {
        if (LUA.contains(caster.getUUID())) {
            LUA.remove(caster.getUUID());
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 center = caster.position();
        // Cast in her own Simple Domain, the circle doubles (approved preview 2026-11-27).
        boolean doubled = inOwnDomain(caster);
        double R = doubled ? LUA_R * 2 : LUA_R;
        int rings = doubled ? 8 : 4;
        int id = SubNet.newId();
        LUA.add(caster.getUUID());
        boolean mob = !(caster instanceof Player);
        int ticks = mob ? 60 : LUA_TICKS;
        SubCommon.pose(caster, "jj.miwa_lua");
        SubCommon.busy(caster, ticks + 10);
        DbCommon.cooldown(caster, 3, 20);
        Freeze.holdLookFree(caster, ticks + 6);
        SubCommon.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.9f);
        Set<LivingEntity> marked = new LinkedHashSet<>();
        Vec3[] flash = {new Vec3(0, 0, -1)};
        int[] flashes = {0};
        Tasks.add(age -> {
            Vec3 fl = new Vec3(flash[0].x, flashes[0], flash[0].z);
            if (guard.broken() || !caster.isAlive()) {
                LUA.remove(caster.getUUID());
                Freeze.free(caster);
                SubCommon.clearState(caster, JjCommon.SIMPLE_DOMAIN);
                SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, center, fl, 1, (float) R, 1);
                DbCommon.cooldown(caster, 3, 600);
                return true;
            }
            boolean release = !LUA.contains(caster.getUUID()) || age >= ticks;
            SubCommon.setState(caster, JjCommon.SIMPLE_DOMAIN, 3);
            if (!release) {
                SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, center, fl, Math.min(1, age / 14f), (float) R, 0);
                for (LivingEntity e : Combat.inRadius(level, center.add(0, 1, 0), R, caster)) {
                    if (Math.abs(e.getY() - center.y) < 3 && marked.add(e)) {
                        flash[0] = SubCommon.flat(e.position().subtract(center));
                        flashes[0]++;
                        SubCommon.sound(level, e.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.6f);
                    }
                }
                marked.removeIf(e -> !e.isAlive());
                for (LivingEntity e : marked) {
                    SubNet.send(level, SubNet.MW_MARK, e.getId(), e.position(), Vec3.ZERO, 1, 0, 0);
                }
                return false;
            }
            // The one draw.
            LUA.remove(caster.getUUID());
            Freeze.free(caster);
            SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, center, fl, 1, (float) R, 1);
            SubCommon.pose(caster, "jj.batto_draw");
            Vec3 fwd = SubCommon.flat(caster.getLookAngle());
            Vec3 ring = center.add(0, 1.0, 0);
            arc(level, ring, fwd, (float) R, Mth.TWO_PI, -0.05f, 1);
            arc(level, ring.add(0, 0.02, 0), fwd, (float) R * 0.55f, Mth.TWO_PI, -0.09f, 2);
            DbCommon.screen(level, ring, 59);
            SubCommon.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.7f);
            SubCommon.sound(level, center, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.2f, 1.4f);
            float mult = spend(caster);
            List<LivingEntity> cut = new ArrayList<>(marked);
            Tasks.later(3, () -> click(level, caster));
            Tasks.later(4, () -> {
                int i = 0;
                for (LivingEntity e : cut) {
                    if (!e.isAlive()) {
                        continue;
                    }
                    cutLine(level, e, 0, 1.0, 1, 1.6f, 3.4f, 0.02f + 0.02f * i++);
                    hurt(caster, level, e, 22 * mult, JjCommon.tierStun(caster, e, 60), true);
                }
            });
            // The ground inside the circle is cut too: one thin slab at the cut's height, ring by ring from her outwards.
            // (spread over more ticks when doubled, so thousands of blocks never come down in one tick)
            for (int ringI = 0; ringI < rings; ringI++) {
                double r0 = 1.2 + ringI * (R - 1.2) / rings, r1 = 1.2 + (ringI + 1) * (R - 1.2) / rings;
                Tasks.later(4 + ringI, () -> {
                    int y = Mth.floor(ring.y);
                    for (int x = Mth.floor(center.x - r1); x <= Mth.floor(center.x + r1); x++) {
                        for (int z = Mth.floor(center.z - r1); z <= Mth.floor(center.z + r1); z++) {
                            double d = Math.hypot(x + 0.5 - center.x, z + 0.5 - center.z);
                            if (d >= r0 && d < r1) {
                                JjCommon.breakAt(level, new BlockPos(x, y, z), center, SubCommon.RANDOM.nextInt(6) == 0);
                            }
                        }
                    }
                });
            }
            DbCommon.cooldown(caster, 3, 1000);
            return true;
        });
        return false;
    }

    static void clear() {
        DOMAINS.clear();
        LUA.clear();
        CALM.clear();
    }
}
