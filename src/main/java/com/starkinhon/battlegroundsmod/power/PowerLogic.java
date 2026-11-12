package com.starkinhon.battlegroundsmod.power;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.combat.DashAttack;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Server-side rules: using abilities, ult charge, picking a power.
public final class PowerLogic {
    private static final float ULT_PASSIVE_PER_TICK = 0.03f;
    private static final float ULT_PER_DAMAGE = 2.0f;
    // Most of the ult one hit can give: a big skill no longer refills it in one go (Destruction Touch gave the whole bar).
    private static final float ULT_MAX_PER_HIT = 15f;
    private static final int SYNC_INTERVAL = 4;
    private static final double ULT_TITLE_RANGE = 48;
    // Powers whose ult draws its own title, so the vanilla one is not sent.
    private static final java.util.Set<String> OWN_ULT_TITLE = java.util.Set.of("strongest", "one_punch", "rubber_boy");

    private PowerLogic() {
    }

    public static Ability ability(Power power, int slot) {
        if (slot >= PowerData.SLOT_SHIFT) {
            int index = slot - PowerData.SLOT_SHIFT;
            return index < power.shiftAbilities().size() ? power.shiftAbilities().get(index) : null;
        }
        if (power == null) {
            // The soulless human (CombatMode.fighter): only the base fighting.
            return switch (slot) {
                case PowerData.SLOT_STRONG -> Melee.STRONG;
                case PowerData.SLOT_BARRAGE -> Melee.BARRAGE;
                case PowerData.SLOT_DASH_ATTACK -> DashAttack.ability();
                default -> null;
            };
        }
        return switch (slot) {
            case PowerData.SLOT_STRONG -> Melee.STRONG;
            case PowerData.SLOT_BARRAGE -> Melee.BARRAGE;
            case PowerData.SLOT_DASH_ATTACK -> DashAttack.ability();
            case PowerData.SLOT_EXTRA -> power.abilities().size() > 4 ? power.abilities().get(4) : null;
            default -> power.abilities().get(slot);
        };
    }

    // The move a fighter's key runs right now: as above, but Kokushibo's keys change with his sword stretched (MoonDemon).
    public static Ability ability(LivingEntity caster, Power power, int slot) {
        return com.starkinhon.battlegroundsmod.power.powers.MoonDemon.swap(caster, power, slot, ability(power, slot));
    }

    public static void tryUse(ServerPlayer player, int slot) {
        PowerData data = player.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (power == null && !(isBaseAttack(slot) && CombatMode.fighter(player))) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_power"), true);
            return;
        }
        if (slot >= 0 && slot < PowerData.SLOT_COUNT && player.isAlive() && !player.isSpectator()) {
            // Any skill key puts the fighter in combat mode (and gives it its full 30 s again) right away, before the
            // move itself: also the second presses below (closing the domain, stopping the Katon, commanding the Blue)
            // and a press that fails (cooldown, no target), since pressing a skill means fighting (Stark, 2026-11-07).
            enterCombat(player);
        }
        if (PowerData.isUlt(slot) && com.starkinhon.battlegroundsmod.power.powers.aizen.Hogyoku.dash(player)) {
            // V while evolved: Aizen's dash of cuts.
            return;
        }
        if (slot == com.starkinhon.battlegroundsmod.power.powers.aizen.KyokaSuigetsu.SLOT
                && com.starkinhon.battlegroundsmod.power.powers.aizen.KyokaSuigetsu.breakIllusion(player)) {
            // X with the illusion up: it breaks and he shows up.
            return;
        }
        if (PowerData.isUlt(slot) && com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.press(player)) {
            // V in a Duelo de Expansões: Gojo reopening the void in the tie (any other press is swallowed).
            return;
        }
        if (PowerData.isUlt(slot) && com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.cancel(player)) {
            // V with his domain open: Gojo closes it.
            return;
        }
        if (PowerData.isUlt(slot) && com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.press(player)) {
            // V with his Santuário open: Sukuna closes it (the second press right after the first: the fast version).
            return;
        }
        if (PowerData.isUlt(slot) && com.starkinhon.battlegroundsmod.power.powers.makima.Makima.ultAgain(player)) {
            // V with her chains or her souls out: Makima's "Morram."
            return;
        }
        if (slot == com.starkinhon.battlegroundsmod.power.powers.makima.Ratos.SLOT
                && com.starkinhon.battlegroundsmod.power.powers.makima.Makima.is(player)
                && com.starkinhon.battlegroundsmod.power.powers.makima.Ratos.press(player)) {
            // Shift+C with her rats out: Makima goes to the one she looks at.
            return;
        }
        if (slot == 0 && com.starkinhon.battlegroundsmod.power.powers.uchiha.Katon.cancel(player)) {
            // Z while Madara breathes fire: he stops it.
            return;
        }
        if (slot == 0 && com.starkinhon.battlegroundsmod.power.powers.gojo.Azul.press(player)) {
            // Z with his held Blue out: a command to it (park, shoot, circle), not a new Blue.
            return;
        }
        if (slot < 0 || slot >= PowerData.SLOT_COUNT || !canCast(player, data, slot)) {
            return;
        }
        boolean skill = !isBaseAttack(slot);
        if (skill && com.starkinhon.battlegroundsmod.combat.Stamina.exhausted(player)) {
            // Out of stamina: only the base fighting until it comes back.
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.stamina_exhausted"), true);
            return;
        }
        Ability ability = ability(player, power, slot);
        boolean ultimate = PowerData.isUlt(slot);
        // Creative mode skips the costs so moves can be recorded freely.
        boolean free = player.isCreative() || data.noCooldown;
        if (!free && ultimate && data.ult < PowerData.MAX_ULT && !com.starkinhon.battlegroundsmod.power.powers.opm.Opm.freeUlt(player)
                && !com.starkinhon.battlegroundsmod.power.powers.luffy.Luffy.freeUlt(player)) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.ult_not_ready"), true);
            return;
        }
        if (!cast(player, slot)) {
            return;
        }
        // (Luffy's V is each Gear's own move: his bar is the Tambores da Libertacao, spent by B)
        if (!free && ultimate && !com.starkinhon.battlegroundsmod.power.powers.luffy.Luffy.is(player)) {
            data.ult = 0;
        }
        if (skill) {
            com.starkinhon.battlegroundsmod.combat.Stamina.spend(player, ultimate);
        } else {
            com.starkinhon.battlegroundsmod.combat.Stamina.gain(player,
                    com.starkinhon.battlegroundsmod.skill.StaminaSpec.get().ganhoAtaqueBasePct());
        }
        if (!data.noCooldown) {
            data.cooldowns[slot] = ability.cooldown();
            if (skill && power != null) {
                com.starkinhon.battlegroundsmod.power.powers.MoonDemon.lockOthers(player, power, slot);
            }
        }
        sync(player);
    }

    // Forte, Barragem and Dash Attack: the base fighting, which gives stamina back instead of spending it.
    public static boolean isBaseAttack(int slot) {
        return slot == PowerData.SLOT_STRONG || slot == PowerData.SLOT_BARRAGE || slot == PowerData.SLOT_DASH_ATTACK;
    }

    // Being hit (stun, Cooldown effect) blocks every move but a counter (a beaten counter only out of punches, not a real stun).
    private static boolean canCast(LivingEntity caster, PowerData data, int slot) {
        Power power = Powers.get(data.power);
        Ability ability = power == null ? (caster instanceof Player && CombatMode.fighter(caster) ? ability(null, slot) : null)
                : ability(caster, power, slot);
        if (ability == null) {
            return false;
        }
        if (slot == PowerData.SLOT_ULT && com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.canClash(caster)) {
            // Answering the other one's domain with their own (the Duelo de Expansões): allowed though that domain has
            // them caught and held.
            return !caster.isSpectator() && caster.isAlive() && data.cooldowns[slot] <= 0 && !sealed(caster);
        }
        boolean counter = ability.counter() || ability.beatenCounter() && !Stun.real(caster);
        return !caster.isSpectator() && caster.isAlive() && data.cooldowns[slot] <= 0 && data.busyTicks <= 0 && !Freeze.isFrozen(caster)
                && (counter || Stun.canAttack(caster)) && !Defense.isGuarding(caster) && !sealed(caster)
                && !com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.blocks(caster)
                && !com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.blocks(caster);
    }

    public static boolean sealed(LivingEntity caster) {
        return caster.getData(ModAttachments.POWER).sealedUntil > caster.level().getGameTime();
    }

    // No skills for ticks: every slot shows the lock as its cooldown, and whatever the fighter was doing stops.
    public static void seal(LivingEntity target, int ticks) {
        PowerData data = target.getData(ModAttachments.POWER);
        data.sealedUntil = Math.max(data.sealedUntil, target.level().getGameTime() + ticks);
        for (int i = 0; i < data.cooldowns.length; i++) {
            data.cooldowns[i] = Math.max(data.cooldowns[i], ticks);
        }
        interrupt(target);
        if (target instanceof ServerPlayer player) {
            sync(player);
        }
    }

    // Cuts off the moves the fighter has going: wind-ups, fire still pouring out, a meteor not yet thrown, a counter
    // waiting, a queued punch, and the body animation that went with them.
    public static void interrupt(LivingEntity target) {
        PowerData data = target.getData(ModAttachments.POWER);
        data.interrupts++;
        data.busyTicks = 0;
        data.punchDelay = 0;
        data.punchQueued = false;
        Susanoo.cancel(target);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.PlaySkillAnimation(target.getId(), "", false));
        // Their sounds and effects stop with the move.
        com.starkinhon.battlegroundsmod.combat.SkillStop.stop(target);
    }

    // Runs a move of the caster's power, with no costs and no cooldown set: players go through tryUse, bosses set
    // their own cooldowns. Returns false when the move did not happen.
    public static boolean cast(LivingEntity caster, int slot) {
        PowerData data = caster.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (slot < 0 || slot >= PowerData.SLOT_COUNT || !canCast(caster, data, slot)) {
            return false;
        }
        ServerLevel level = Combat.level(caster);
        // In combat mode before the move runs, so the move starts with everything that comes with it already on (the
        // aura, Kokushibo's sword out of the sheath on the first frame, the ult bar). Bosses come through here too.
        enterCombat(caster);
        if (!ability(caster, power, slot).action().use(caster, level)) {
            return false;
        }
        data.lastCastSlot = slot;
        data.lastCastTime = level.getGameTime();
        // Kakashi's Sharingan remembers what was used near him.
        if (power != null) {
            com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.recordCast(caster, power.id(), slot);
        }
        if (power != null && PowerData.isUlt(slot)) {
            announceUlt(caster, power, slot);
        }
        return true;
    }

    // Starts combat mode (or gives it its full time again) and, for a player, tells everyone at once that the sword left
    // the sheath, instead of waiting for the next tick.
    public static void enterCombat(LivingEntity caster) {
        CombatMode.enter(caster);
        if (caster instanceof ServerPlayer player) {
            syncSheath(player);
        }
    }

    // Big title on the screen of everyone nearby. Makes ults read well on video.
    private static void announceUlt(LivingEntity player, Power power, int slot) {
        // Gojo's Domain Expansion writes its own words on the screen ("Expansão de Domínio:" then "Vazio Infinito."),
        // and its long name in the big vanilla title runs off the screen.
        if (OWN_ULT_TITLE.contains(power.id())) {
            return;
        }
        // Sukuna's Expansão writes its own words too, and its long name ran off both edges (test 2026-11-09).
        if (com.starkinhon.battlegroundsmod.power.powers.Sukuna.ID.equals(power.id()) && slot == 3) {
            return;
        }
        // Kokushibo's 6th Form (V with the normal sword) goes without the big title (Stark, 2026-11-08).
        if (com.starkinhon.battlegroundsmod.power.powers.MoonDemon.ID.equals(power.id())
                && !com.starkinhon.battlegroundsmod.power.powers.MoonDemon.stretched(player)) {
            return;
        }
        Component title = power.abilityName(slot).copy().withStyle(s -> s.withColor(power.color()).withBold(true));
        Component subtitle = player.getDisplayName().copy().withStyle(ChatFormatting.GRAY);
        for (ServerPlayer other : Combat.level(player).players()) {
            if (other.distanceTo(player) <= ULT_TITLE_RANGE) {
                other.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
                other.connection.send(new ClientboundSetTitleTextPacket(title));
                other.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
        }
    }

    public static boolean hasBook(ServerPlayer player) {
        return player.isCreative() || player.getInventory().contains(new ItemStack(ModItems.POWER_BOOK.get()));
    }

    public static void selectFromBook(ServerPlayer player, String id) {
        if (!hasBook(player)) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.need_book"), true);
            return;
        }
        // With universes and progression souls come only from beating their characters (creative still picks freely).
        if (Progression.on(player) && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.book_locked"), true);
            return;
        }
        Power power = Powers.get(id);
        if (power != null) {
            select(player, power);
        }
    }

    public static void select(ServerPlayer player, Power power) {
        PowerData data = player.getData(ModAttachments.POWER);
        data.power = power.id();
        data.ult = 0;
        data.outfit = true;
        data.resetCombat();
        Vec3 pos = player.position().add(0, 1, 0);
        Fx.burst(player.serverLevel(), Fx.dust(power.color(), 2f), pos, 60, 0.6, 0);
        Fx.burst(player.serverLevel(), ParticleTypes.TOTEM_OF_UNDYING, pos, 40, 0.5, 0.4);
        Fx.sound(player.serverLevel(), pos, SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.power_selected",
                power.name().copy().withStyle(s -> s.withColor(power.color()))), false);
        sync(player);
    }

    public static void clear(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        data.power = "";
        data.ult = 0;
        data.resetCombat();
        CombatMode.leave(player);
        sync(player);
    }

    // Only a real fight charges the ult: players, monsters (bosses and soul mobs too) and the training dummies.
    // Hitting cows or villagers no longer fills it.
    public static void addUltCharge(ServerPlayer player, LivingEntity target, float damageDealt) {
        PowerData data = player.getData(ModAttachments.POWER);
        if (!data.hasPower() || !chargesUlt(target)) {
            return;
        }
        float gain = Math.min(ULT_MAX_PER_HIT, damageDealt * ULT_PER_DAMAGE);
        data.ult = Math.min(PowerData.MAX_ULT, data.ult + gain);
    }

    private static boolean chargesUlt(LivingEntity target) {
        return target instanceof Player || target instanceof net.minecraft.world.entity.monster.Enemy
                || target instanceof com.starkinhon.battlegroundsmod.dummy.TrainingDummy;
    }

    public static void tick(LivingEntity player) {
        PowerData data = player.getData(ModAttachments.POWER);
        for (int i = 0; i < data.cooldowns.length; i++) {
            if (data.cooldowns[i] > 0) {
                data.cooldowns[i]--;
            }
        }
        if (data.noFallTicks > 0) {
            data.noFallTicks--;
        }
        if (data.punchDelay > 0) {
            data.punchDelay--;
        }
        if (data.punchQueued && data.punchDelay <= 0) {
            Melee.punch(player, null);
        }
        if (data.busyTicks > 0) {
            data.busyTicks--;
        }
        if (data.comboResetTicks > 0 && --data.comboResetTicks == 0) {
            data.comboStep = 0;
        }
        CombatMode.tick(player);
        com.starkinhon.battlegroundsmod.combat.Stamina.tick(player);
        // Bosses get their tier's damage only; their health and armor come from their own file.
        if (player instanceof Player) {
            Tiers.apply(player);
        }
        if (!player.level().isClientSide()) {
            com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody.tick(player);
        }
        if (data.hasPower()) {
            data.ult = Math.min(PowerData.MAX_ULT, data.ult + ULT_PASSIVE_PER_TICK);
        }
        if (player instanceof ServerPlayer serverPlayer && player.tickCount % SYNC_INTERVAL == 0) {
            sync(serverPlayer);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            String aura = auraOf(serverPlayer);
            if (!aura.equals(data.auraSent)) {
                data.auraSent = aura;
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new ModPayloads.AuraState(serverPlayer.getId(), aura));
            }
            String outfit = outfitOf(serverPlayer);
            if (!outfit.equals(data.outfitSent)) {
                data.outfitSent = outfit;
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, new ModPayloads.OutfitState(serverPlayer.getId(), outfit));
            }
            syncSheath(serverPlayer);
        }
    }

    // The aura other players should see on this one: their power while in combat mode, else none.
    public static String auraOf(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        return data.hasPower() && CombatMode.in(player) && player.isAlive() ? data.power : "";
    }

    // Someone just started seeing this player: tell them about the aura and the outfit.
    public static void sendAura(ServerPlayer player, ServerPlayer watcher) {
        PacketDistributor.sendToPlayer(watcher, new ModPayloads.AuraState(player.getId(), auraOf(player)));
        PacketDistributor.sendToPlayer(watcher, new ModPayloads.OutfitState(player.getId(), outfitOf(player)));
        PacketDistributor.sendToPlayer(watcher, sheathState(player, sheathCode(player)));
    }

    // Tells the watchers when Kokushibo's sword goes in or out of the sheath, or the sheath on or off the hip. Every tick,
    // and right away from the move that draws it (SwordArts), so the draw shows with the move's first frame.
    public static void syncSheath(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        int code = sheathCode(player);
        if (code != data.sheathSent) {
            data.sheathSent = code;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, sheathState(player, code));
        }
    }

    private static ModPayloads.SheathState sheathState(ServerPlayer player, int code) {
        return new ModPayloads.SheathState(player.getId(), (code & 1) != 0, (code & 2) != 0, (code & 4) != 0);
    }

    // 0: no sheath. 1: the sword rests in the sheath, also while in hand out of combat mode (Stark, 2026-11-07: it only
    // comes out to fight). 3: drawn, held in combat mode (or by someone without a power, who never enters it), or in the
    // off hand. +4: Kokushibo has the sword stretched (H): the sheath's flesh went into the blade, so there is no sheath on
    // the hip and the long sword stays in his hand even out of combat mode (Stark, 2026-11-08).
    public static int sheathCode(ServerPlayer player) {
        if (!carriesSheath(player)) {
            return 0;
        }
        boolean main = player.getMainHandItem().getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboSwordItem;
        boolean off = player.getOffhandItem().getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboSwordItem;
        boolean stretched = com.starkinhon.battlegroundsmod.power.powers.MoonDemon.stretched(player);
        boolean fighting = stretched || !player.getData(ModAttachments.POWER).hasPower() || com.starkinhon.battlegroundsmod.combat.CombatMode.in(player);
        return (main && fighting || off ? 3 : 1) | (stretched ? 4 : 0);
    }

    // Kokushibo's sheath goes on the hip of whoever has his sword anywhere in the inventory.
    public static boolean carriesSheath(ServerPlayer player) {
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboSwordItem) {
                return true;
            }
        }
        return player.getOffhandItem().getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboSwordItem;
    }

    // The 3D outfit others should draw on this player: the power's, unless they took it off. Powers without an
    // outfit send their id too; the clients just have nothing to draw for them.
    public static String outfitOf(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        return data.hasPower() && data.outfit ? data.power : "";
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ModPayloads.SyncPower.of(player.getData(ModAttachments.POWER)));
        com.starkinhon.battlegroundsmod.combat.Stamina.sync(player);
        Progression.sync(player);
    }
}
