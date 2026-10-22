package com.starkinhon.battlegroundsmod.client.fx;

import net.neoforged.neoforge.common.ModConfigSpec;

// Client options for the screen effects (Mods > Battlegrounds > Config). Players who get bothered by flashing
// can turn the impact frames down to a soft grey version, or off.
public final class FxConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES = BUILDER
            .comment("Black and white / inverted frames on big hits (anime style).")
            .define("impactFrames", true);
    public static final ModConfigSpec.BooleanValue REDUCED_FLASH = BUILDER
            .comment("Softer impact frames and flashes, for players sensitive to flashing lights.")
            .define("reducedFlash", false);
    public static final ModConfigSpec.BooleanValue DISTORTION = BUILDER
            .comment("Space bending around big powers.")
            .define("distortion", true);
    public static final ModConfigSpec.BooleanValue RADIAL_BLUR = BUILDER
            .comment("Speed blur toward the center of an impact.")
            .define("radialBlur", true);
    public static final ModConfigSpec.BooleanValue BLOOM = BUILDER
            .comment("Light from fire, flares and beams spills around them.")
            .define("bloom", true);
    public static final ModConfigSpec.DoubleValue SHAKE = BUILDER
            .comment("How much the camera shakes (0 turns it off).")
            .defineInRange("shake", 1.0, 0.0, 2.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private FxConfig() {
    }

    // Reading a value before the file loads throws, so effects fall back to the defaults.
    static boolean on(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static float shake() {
        try {
            return SHAKE.get().floatValue();
        } catch (IllegalStateException e) {
            return 1;
        }
    }
}
