package com.starkinhon.soulsjjk.client.fx;

import net.minecraftforge.common.ForgeConfigSpec;

// Client options for the screen effects (Mods > Battlegrounds > Config). Players who get bothered by flashing
// can turn the impact frames down to a soft grey version, or off.
public final class FxConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue IMPACT_FRAMES = BUILDER
            .comment("Black and white / inverted frames on big hits (anime style).")
            .define("impactFrames", true);
    public static final ForgeConfigSpec.BooleanValue REDUCED_FLASH = BUILDER
            .comment("Softer impact frames and flashes, for players sensitive to flashing lights.")
            .define("reducedFlash", false);
    public static final ForgeConfigSpec.BooleanValue DISTORTION = BUILDER
            .comment("Space bending around big powers.")
            .define("distortion", true);
    public static final ForgeConfigSpec.BooleanValue RADIAL_BLUR = BUILDER
            .comment("Speed blur toward the center of an impact.")
            .define("radialBlur", true);
    public static final ForgeConfigSpec.BooleanValue BLOOM = BUILDER
            .comment("Light from fire, flares and beams spills around them.")
            .define("bloom", true);
    public static final ForgeConfigSpec.BooleanValue REALISMO = BUILDER
            .comment("Realistic volumes (tornado, fire, smoke, fireball, lava, wind) drawn over the world.")
            .define("realism", true);
    public static final ForgeConfigSpec.IntValue REALISMO_QUALITY = BUILDER
            .comment("Quality of the realistic volumes: 1 fast, 2 normal, 3 high (more steps and detail, heavier).")
            .defineInRange("realismQuality", 2, 1, 3);
    public static final ForgeConfigSpec.DoubleValue SHAKE = BUILDER
            .comment("How much the camera shakes (0 turns it off).")
            .defineInRange("shake", 1.0, 0.0, 2.0);

    public static final ForgeConfigSpec.BooleanValue FORM_KANJI = BUILDER
            .comment("Kokushibo's form names in kanji beside him when he uses a Moon Breathing form (/souls kanji desativar).")
            .define("formKanji", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private FxConfig() {
    }

    // Reading a value before the file loads throws, so effects fall back to the defaults.
    static boolean on(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static boolean formKanji() {
        return on(FORM_KANJI);
    }

    // Turns the form kanji on or off and saves it in the client config file.
    public static void setFormKanji(boolean value) {
        try {
            FORM_KANJI.set(value);
            SPEC.save();
        } catch (IllegalStateException e) {
            // the file isn't loaded yet: it stays for this session only
        }
    }

    public static int realismQuality() {
        try {
            return REALISMO_QUALITY.get();
        } catch (IllegalStateException e) {
            return REALISMO_QUALITY.getDefault();
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
