package com.starkinhon.battlegroundsmod.dummy;

import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;

// Test dummy: never walks, never dies (its health fills back after every hit) and shows the damage it took over its
// head: the last hit and the total of the current combo. Sneak + right click with an empty hand to pick it up.
// Only /kill (or the void) gets rid of it otherwise.
public class TrainingDummy extends PathfinderMob {
    // A combo ends after this long without a hit, and the total starts over.
    private static final int COMBO_RESET_TICKS = 60;

    private float lastHit;
    private float comboTotal;
    private int hits;
    private int sinceHit = COMBO_RESET_TICKS;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        refreshName();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
    }

    @Override
    public void tick() {
        Tasks.as(this, this::tickAsOwner);
    }

    private void tickAsOwner() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // Moves read the same data players use (combo step, stun, cooldowns), so it ticks like a player's.
        PowerLogic.tick(this);
        Movement.tick(this);
        if (sinceHit < COMBO_RESET_TICKS && ++sinceHit == COMBO_RESET_TICKS) {
            comboTotal = 0;
            hits = 0;
            refreshName();
        }
        serverTick();
    }

    // Runs on the server every tick, as this dummy's own task owner.
    protected void serverTick() {
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (isInvulnerableTo(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        // What really got through (armor, shields and the like already took their part).
        float dealt = Math.max(0, getMaxHealth() - getHealth());
        setHealth(getMaxHealth());
        if (dealt <= 0) {
            return;
        }
        lastHit = dealt;
        comboTotal += dealt;
        hits++;
        sinceHit = 0;
        refreshName();
    }

    private void refreshName() {
        Component name = getType().getDescription().copy().withStyle(ChatFormatting.GRAY);
        if (hits > 0) {
            name = Component.translatable("entity.battlegroundsmod.dummy.damage",
                    String.format("%.1f", lastHit), String.format("%.1f", comboTotal), hits).withStyle(ChatFormatting.GOLD);
        }
        setCustomName(name);
        setCustomNameVisible(true);
    }

    // Sneak + right click with an empty hand: back into its egg.
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide) {
                SpawnEggItem egg = SpawnEggItem.byId(getType());
                if (egg != null && !player.getAbilities().instabuild) {
                    player.getInventory().add(new ItemStack(egg));
                }
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }
}
