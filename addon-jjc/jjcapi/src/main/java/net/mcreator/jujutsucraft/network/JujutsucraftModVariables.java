package net.mcreator.jujutsucraft.network;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;

// Compile-only stand-in for JujutsuCraft's class (only the members the addon uses). The real one comes from the
// JujutsuCraft jar at runtime; this is never packed in the addon's jar.
public class JujutsucraftModVariables {
    public static final Capability<PlayerVariables> PLAYER_VARIABLES_CAPABILITY = null;

    public static class PlayerVariables {
        public boolean noChangeTechnique;
        public boolean SecondTechnique;
        public boolean PhysicalAttack;
        public boolean PassiveTechnique;
        public double PlayerCursePower;
        public double PlayerCursePowerMAX;
        public double PlayerCurseTechnique;
        public double PlayerCurseTechnique2;
        public double PlayerSelectCurseTechnique;
        public double PlayerSelectCurseTechniqueCost;
        public String PlayerSelectCurseTechniqueName;

        public void syncPlayerVariables(Entity entity) {
        }
    }
}
