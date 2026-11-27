// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The pixel blood (Stark 2026-11-23, prévia "Sangue Pixel"): a few square drops on the blows, a splash on the sword
// cuts, a bit more on the finishers (Makima's Bang, Sukuna's cuts, a killing blow). The server only says who bled, how
// and in what color; the drops, the stains and the bits are all drawn by client/fx/BloodFx, which also keeps the caps
// and the player's option (Experiência do jogador, off by default).
//
// Every hit of the tick on the same body becomes ONE message (the strongest kind wins, powers add up), so an area move
// that hits 30 times in a second never floods anyone. A move that wants its own look marks it (mark) right after its
// hurt; the plain damage event fills in everything else.
public final class Blood {
    public static final int PUNCH = 0;
    public static final int HEAVY = 1;
    public static final int SLASH = 2;
    public static final int PIERCE = 3;
    public static final int EXPLOSION = 4;
    // Makima's Bang: a hole through the chest, the spray out the back.
    public static final int BANG = 5;
    // One of Sukuna's cuts: drops along the cut's line (cut = the line's direction).
    public static final int CLEAVE = 6;
    // Inumaki's "Explode": the body bursts from inside, a jet of drops up and out, bits, a pool when it kills.
    public static final int BURST = 7;
    // Inumaki's "Get crushed" finishing: the body is pressed into a pool, drops spraying out round it.
    public static final int SQUASH = 8;

    public static final int FLAG_FATAL = 1;

    // Kinds by how strong they look: a stronger mark replaces a weaker one in the same tick.
    private static final int[] RANK = {0, 2, 3, 1, 1, 5, 4, 6, 6};
    // At most this many bodies bleed per tick in the whole server (an area ult on a crowd).
    private static final int MAX_PER_TICK = 40;

    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    private static final class Pending {
        final LivingEntity target;
        int kind;
        int flags;
        float power;
        Vec3 dir;
        Vec3 cut = Vec3.ZERO;

        Pending(LivingEntity target, int kind, float power, Vec3 dir) {
            this.target = target;
            this.kind = kind;
            this.power = power;
            this.dir = dir;
        }
    }

    private Blood() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Blood::onDamage);
        NeoForge.EVENT_BUS.addListener(Blood::onServerTick);
    }

    // A move's own blood (after its hurt landed). dir = the blow's way (attacker to target), cut = a slash's line or ZERO.
    public static void mark(LivingEntity target, int kind, float power, Vec3 dir, Vec3 cut) {
        if (target == null || !(target.level() instanceof ServerLevel)) {
            return;
        }
        Pending p = PENDING.get(target.getId());
        if (p == null) {
            p = new Pending(target, kind, power, flat(dir));
            PENDING.put(target.getId(), p);
        } else {
            if (RANK[kind] >= RANK[p.kind]) {
                p.kind = kind;
                p.dir = flat(dir);
            }
            p.power = Math.max(p.power, power);
        }
        if (cut != null && cut.lengthSqr() > 1e-4) {
            p.cut = cut.normalize();
        }
        if (target.isDeadOrDying()) {
            p.flags |= FLAG_FATAL;
        }
    }

    public static void mark(LivingEntity target, int kind, float power, LivingEntity attacker) {
        mark(target, kind, power, attacker == null ? Vec3.ZERO : target.position().subtract(attacker.position()), Vec3.ZERO);
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? Vec3.ZERO : f.normalize();
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel) || event.getNewDamage() < 0.5f) {
            return;
        }
        DamageSource source = event.getSource();
        int kind = kindOf(source, event.getNewDamage());
        if (kind < 0) {
            return;
        }
        Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        Vec3 dir = from == null ? Vec3.ZERO : target.position().subtract(from.position());
        if (from instanceof Projectile && from.getDeltaMovement().lengthSqr() > 1e-4) {
            dir = from.getDeltaMovement();
        }
        // ~8 damage is a normal blow; big ones spray more (the client caps it)
        float power = Math.max(0.6f, Math.min(1.8f, event.getNewDamage() / 8f));
        Pending had = PENDING.get(target.getId());
        if (had != null) {
            // a move already marked its own look this tick: only add the strength and the death
            had.power = Math.max(had.power, power);
            if (target.isDeadOrDying()) {
                had.flags |= FLAG_FATAL;
            }
            return;
        }
        mark(target, kind, power, dir, Vec3.ZERO);
    }

    // -1: this damage doesn't bleed (fire, magic and energy, falling, drowning, hunger, the void, poison...).
    private static int kindOf(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.LIGHTNING_BOLT) || source.is(DamageTypes.SONIC_BOOM)) {
            return -1;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) {
            return PIERCE;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            // cactus, berry bush, falling anvil, cramming...
            return source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_BLOCK) ? PUNCH : -1;
        }
        if (bladed(attacker)) {
            return SLASH;
        }
        return damage >= 14 ? HEAVY : PUNCH;
    }

    // The blade souls cut even without a sword in hand (Kokushibo's moon blades, Sukuna's cuts, Byakuya's petals...).
    private static final Set<String> BLADE_POWERS = Set.of("moon_demon", "sukuna", "byakuya", "rukia", "toji", "miwa", "nanami");

    private static boolean bladed(LivingEntity attacker) {
        ItemStack hand = attacker.getMainHandItem();
        if (hand.getItem() instanceof com.starkinhon.battlegroundsmod.item.NobaraHammerItem) {
            return false;
        }
        if (hand.getItem() instanceof SwordItem || hand.getItem() instanceof AxeItem) {
            return true;
        }
        return Combat.technique() && BLADE_POWERS.contains(powerOf(attacker));
    }

    // (hasData first: getData would hang a power on every cow that gets hit)
    private static String powerOf(LivingEntity entity) {
        if (!entity.hasData(ModAttachments.POWER)) {
            return "";
        }
        String power = entity.getData(ModAttachments.POWER).power;
        return power == null ? "" : power;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        int sent = 0;
        for (Pending p : PENDING.values()) {
            LivingEntity t = p.target;
            if (t.isRemoved() && !t.isDeadOrDying()) {
                continue;
            }
            int color = color(t);
            if (color == 0) {
                continue;
            }
            if (t.isDeadOrDying()) {
                p.flags |= FLAG_FATAL;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(t, new ModPayloads.BloodHit(t.getId(), (byte) p.kind, (byte) p.flags,
                    p.power, color, (float) p.dir.x, (float) p.dir.z, (float) p.cut.x, (float) p.cut.y, (float) p.cut.z));
            if (++sent >= MAX_PER_TICK) {
                break;
            }
        }
        PENDING.clear();
    }

    // ------------------------------------------------------------------ the color of each one's blood (0 = none)

    public static final int RED = 0x9E1416;
    // Namekians (Piccolo, Kami, the Namekian mobs): purple, like the anime from the Saiyan arc on (it was red at first).
    public static final int NAMEK = 0x6A2A86;
    // Freeza and his race (Freeza Poder Total too): dark magenta, Resurrection F made it purple.
    public static final int FREEZA = 0x8A1E5E;
    // Bills: purple (Stark 2026-11-23). The anime never showed it.
    public static final int BILLS = 0x5E2A9E;
    // Cell and Cell Jr.: green (the anime went between green and purple).
    public static final int CELL = 0x3E7A2A;
    // Plant men (Saibamen) and slimes: green.
    public static final int PLANT = 0x4E8A2C;
    // The white Zetsu and the Wood clones: white sap (Obito's Zetsu half bleeds white).
    public static final int SAP = 0xD8D2B8;
    // Cursed spirits: a darker blood, almost black.
    public static final int CURSE = 0x4A0E1E;
    // Bugs and giant arthropods: a yellow-green hemolymph.
    public static final int BUG = 0x8C9A2A;
    // Endermen and the End's creatures.
    public static final int ENDER = 0x6E2A8E;
    public static final int MAGMA = 0xB0501A;
    public static final int ZOMBIE = 0x5A1A12;

    private static final Map<String, Integer> BY_POWER = Map.of(
            "piccolo", NAMEK,
            "freeza", FREEZA, "freeza_total", FREEZA,
            "destruction_god", BILLS);

    private static final Map<String, Integer> BY_TYPE = new HashMap<>();

    static {
        // no blood at all: machines, puppets, clones, illusions, spirits made of nothing, bones and stone
        for (String id : new String[] {"androide_8", "androide_gero", "jj_mechamaru", "marionete", "garca_papel", "naruto_clone",
                "aizen_illusion", "dummy", "dummy_attacker", "boneco_amaldicoado", "malevolent_shrine", "ninja_thrown", "jj_boneco",
                "jj_espantalho", "jj_golem", "jj_lanterna", "jj_sino", "jj_metro",
                "skeleton", "stray", "bogged", "wither_skeleton", "wither", "skeleton_horse", "iron_golem", "snow_golem", "blaze",
                "vex", "allay", "ghast", "armor_stand", "shulker", "breeze", "phantom", "creaking"}) {
            BY_TYPE.put(id, 0);
        }
        BY_TYPE.put("namekiano", NAMEK);
        BY_TYPE.put("piccolo", NAMEK);
        BY_TYPE.put("freeza", FREEZA);
        BY_TYPE.put("freeza_total", FREEZA);
        BY_TYPE.put("bills", BILLS);
        BY_TYPE.put("cell_jr", CELL);
        BY_TYPE.put("saibaman", PLANT);
        BY_TYPE.put("clone_madeira", SAP);
        BY_TYPE.put("zetsu_branco", SAP);
        BY_TYPE.put("maldicao_menor", CURSE);
        BY_TYPE.put("maldicao_grau2", CURSE);
        for (String id : new String[] {"centopeia", "escorpiao_areia", "inseto_gigante", "caranguejo_gigante", "spider", "cave_spider",
                "silverfish", "bee"}) {
            BY_TYPE.put(id, BUG);
        }
        BY_TYPE.put("slime", PLANT);
        BY_TYPE.put("creeper", PLANT);
        BY_TYPE.put("magma_cube", MAGMA);
        BY_TYPE.put("enderman", ENDER);
        BY_TYPE.put("endermite", ENDER);
        BY_TYPE.put("ender_dragon", ENDER);
        for (String id : new String[] {"zombie", "husk", "drowned", "zombie_villager", "zombified_piglin", "zoglin", "zombie_horse"}) {
            BY_TYPE.put(id, ZOMBIE);
        }
    }

    public static int color(LivingEntity entity) {
        String power = powerOf(entity);
        Integer c = BY_POWER.get(power);
        if (c != null) {
            return c;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        c = BY_TYPE.get(id);
        if (c != null) {
            return c;
        }
        // every curse of the Jujutsu world (jj_*) except the ones above
        if (id.startsWith("jj_")) {
            return CURSE;
        }
        return RED;
    }
}
