package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.util.Mth;

// The shape of a power's constellation: one star per skill, linked to the skill it grows from. Every power gets the
// same layout from its skills: the core at the bottom, the normal skills on an arc above it, each Shift variant further
// out past its skill, and the ult at the top (its Shift variant above it). x and y go from 0 to 1 over the drawing area.
//
// parent is the slot of the skill a node hangs from (CORE for the core). It is what an unlock system will use later:
// a skill opens once its parent is open (see unlocked). For now every skill is open.
public final class SkillTree {
    public static final int CORE = -1;

    public record Node(int slot, int parent, float x, float y, boolean ult, boolean shift) {
    }

    public static final float CORE_X = 0.5f;
    public static final float CORE_Y = 0.92f;

    private SkillTree() {
    }

    public static List<Node> build(Power power) {
        List<Node> nodes = new ArrayList<>();
        List<Integer> normals = new ArrayList<>();
        for (int slot : power.slots()) {
            if (!PowerData.isUlt(slot)) {
                normals.add(slot);
            }
        }
        int count = normals.size();
        for (int i = 0; i < count; i++) {
            int slot = normals.get(i);
            float angle = count == 1 ? 0 : Mth.lerp(i / (float) (count - 1), -64f, 64f) * Mth.DEG_TO_RAD;
            float sin = Mth.sin(angle);
            float cos = Mth.cos(angle);
            nodes.add(new Node(slot, CORE, CORE_X + sin * 0.31f, CORE_Y - cos * 0.40f, false, false));
            int variant = power.shiftSlot(power.slotKey(slot));
            if (variant != slot) {
                nodes.add(new Node(variant, slot, CORE_X + sin * 0.47f, CORE_Y - cos * 0.66f, false, true));
            }
        }
        nodes.add(new Node(PowerData.SLOT_ULT, CORE, CORE_X, 0.34f, true, false));
        int ultVariant = power.shiftSlot(PowerData.SLOT_ULT);
        if (ultVariant != PowerData.SLOT_ULT) {
            nodes.add(new Node(ultVariant, PowerData.SLOT_ULT, CORE_X, 0.06f, true, true));
        }
        return nodes;
    }

    // Whether the player has this skill. Every skill is open for now; this is where an unlock system plugs in.
    public static boolean unlocked(PowerData data, Power power, int slot) {
        return true;
    }
}
