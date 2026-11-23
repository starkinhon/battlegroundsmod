// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
    public static final ModConfigSpec.BooleanValue REALISMO = BUILDER
            .comment("Realistic volumes (tornado, fire, smoke, fireball, lava, wind) drawn over the world.")
            .define("realism", true);
    public static final ModConfigSpec.IntValue REALISMO_QUALITY = BUILDER
            .comment("Quality of the realistic volumes: 1 fast, 2 normal, 3 high (more steps and detail, heavier).")
            .defineInRange("realismQuality", 2, 1, 3);
    public static final ModConfigSpec.DoubleValue SHAKE = BUILDER
            .comment("How much the camera shakes (0 turns it off).")
            .defineInRange("shake", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue FORM_KANJI = BUILDER
            .comment("Kokushibo's form names in kanji beside him when he uses a Moon Breathing form (/souls kanji desativar).")
            .define("formKanji", true);

    public static final ModConfigSpec.BooleanValue SAITAMA_CUTS = BUILDER
            .comment("Saitama's serious moves cut the camera to a short shot of his face and of the blow (your own moves only).")
            .define("saitamaCameraCuts", true);

    public static final ModConfigSpec.IntValue INVENTORY_STYLE = BUILDER
            .comment("Survival inventory look: 0 soul colored dark, 1 soul colored light, 2 Minecraft's default.")
            .defineInRange("inventoryStyle", 0, 0, 2);

    // ---- the Experiência do jogador menu (client/SoulsSettingsScreen, key O). The defaults keep the game as it was.
    public static final ModConfigSpec.IntValue EFFECT_QUALITY = BUILDER
            .comment("Effects quality: 0 low, 1 medium, 2 high (the full look). Low and medium cut particles and the realistic volumes' detail.")
            .defineInRange("effectQuality", 2, 0, 2);
    public static final ModConfigSpec.BooleanValue FEW_DEBRIS = BUILDER
            .comment("Fewer blocks flying out of destruction, and they go away sooner.")
            .define("fewDebris", false);
    public static final ModConfigSpec.IntValue AURA_MODE = BUILDER
            .comment("Combat aura: 2 full, 1 light (fewer particles, no outline), 0 off.")
            .defineInRange("auraMode", 2, 0, 2);
    public static final ModConfigSpec.BooleanValue BLADE_TRAILS = BUILDER
            .comment("The blade's trail on sword moves.")
            .define("bladeTrails", true);
    public static final ModConfigSpec.BooleanValue AMBIENT = BUILDER
            .comment("The biomes' ambient particles (leaves, petals, mist sheets, ash, pollen...).")
            .define("ambientParticles", true);
    public static final ModConfigSpec.IntValue EFFECT_RANGE = BUILDER
            .comment("How far auras, blade trails and particles are drawn: 0 16 blocks, 1 32, 2 64, 3 no limit.")
            .defineInRange("effectRange", 3, 0, 3);
    public static final ModConfigSpec.BooleanValue OTHERS_LIGHT = BUILDER
            .comment("Other players' auras and blade trails in a light version (multiplayer).")
            .define("othersLight", false);
    public static final ModConfigSpec.BooleanValue AUTO_TRACK = BUILDER
            .comment("Missions are tracked on the corner by themselves (the structure you are in, the next step of the Caminho).")
            .define("autoTrackMissions", true);
    public static final ModConfigSpec.BooleanValue COOLDOWN_NUMBERS = BUILDER
            .comment("The seconds left on a skill's cooldown, over its icon.")
            .define("cooldownNumbers", true);
    public static final ModConfigSpec.BooleanValue HINTS = BUILDER
            .comment("Hint bubbles and cards (the soul swap card, the Souls world type bubble).")
            .define("hints", true);

    // The pixel blood (client/fx/BloodFx). Off by default (Stark 2026-11-23): the player turns it on in the Experiência do
    // jogador menu.
    public static final ModConfigSpec.IntValue BLOOD = BUILDER
            .comment("Pixel blood on hits: 0 off, 1 a little, 2 medium, 3 more.")
            .defineInRange("blood", 0, 0, 3);
    public static final ModConfigSpec.BooleanValue GORE = BUILDER
            .comment("Small flesh bits on the finishers (only with the blood on).")
            .define("gore", true);

    public static final ModConfigSpec.BooleanValue COMPACT_COLUMN = BUILDER
            .comment("The skills column on the right one GUI scale smaller (at GUI scale 4 it looks like scale 3).")
            .define("compactSkillColumn", false);

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

    public static boolean saitamaCuts() {
        return on(SAITAMA_CUTS);
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

    public static int inventoryStyle() {
        try {
            return INVENTORY_STYLE.get();
        } catch (IllegalStateException e) {
            return INVENTORY_STYLE.getDefault();
        }
    }

    // Picked with the buttons under the inventory (CrystalInventory); saved in the client config file.
    public static void setInventoryStyle(int value) {
        try {
            INVENTORY_STYLE.set(value);
            SPEC.save();
        } catch (IllegalStateException e) {
            // the file isn't loaded yet: it stays for this session only
        }
    }

    public static int realismQuality() {
        int q;
        try {
            q = REALISMO_QUALITY.get();
        } catch (IllegalStateException e) {
            q = REALISMO_QUALITY.getDefault();
        }
        // low effects: the fast volumes; medium: at most normal; high: as picked
        return Math.min(q, effectQuality() + 1);
    }

    // ---- the Experiência do jogador menu

    static int num(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    // Saves a value in the client config file (the menu's buttons). Before the file loads it does nothing.
    public static <T> void set(ModConfigSpec.ConfigValue<T> value, T to) {
        try {
            value.set(to);
            SPEC.save();
        } catch (IllegalStateException e) {
            // the file isn't loaded yet
        }
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        return on(value);
    }

    public static int get(ModConfigSpec.IntValue value) {
        return num(value);
    }

    public static int effectQuality() {
        return num(EFFECT_QUALITY);
    }

    // How many of the particles are kept: all on high.
    public static float particleScale() {
        return switch (effectQuality()) {
            case 0 -> 0.3f;
            case 1 -> 0.6f;
            default -> 1f;
        };
    }

    // A count of particles cut down by the quality (never to 0 when there was any).
    public static int particles(int count) {
        if (count <= 0 || effectQuality() >= 2) {
            return count;
        }
        return Math.max(1, Math.round(count * particleScale()));
    }

    public static boolean fewDebris() {
        return on(FEW_DEBRIS);
    }

    public static int auraMode() {
        return num(AURA_MODE);
    }

    public static boolean bladeTrails() {
        return on(BLADE_TRAILS);
    }

    public static boolean ambient() {
        return on(AMBIENT);
    }

    // Blocks; Double.MAX_VALUE with no limit (the default).
    public static double effectRange() {
        return switch (num(EFFECT_RANGE)) {
            case 0 -> 16;
            case 1 -> 32;
            case 2 -> 64;
            default -> Double.MAX_VALUE;
        };
    }

    public static boolean othersLight() {
        return on(OTHERS_LIGHT);
    }

    public static boolean autoTrack() {
        return on(AUTO_TRACK);
    }

    public static boolean cooldownNumbers() {
        return on(COOLDOWN_NUMBERS);
    }

    public static boolean compactColumn() {
        return on(COMPACT_COLUMN);
    }

    public static boolean hints() {
        return on(HINTS);
    }

    public static float shake() {
        try {
            return SHAKE.get().floatValue();
        } catch (IllegalStateException e) {
            return 1;
        }
    }

    public static int blood() {
        return num(BLOOD);
    }

    public static boolean gore() {
        return on(GORE);
    }
}
