package com.starkinhon.soulsjjk.skill;

import java.util.List;
import java.util.Map;

// The charge bar (client/ChargeBar), from souls_skills/padrao.carga.json: how far the held attack has charged.
// It stands beside the stamina bar (StaminaSpec.Barra). First person: espacoPrimeiraPessoa GUI pixels to the right of
// the stamina bar, larguraPrimeiraPessoa wide, as tall as it. Third person: a curve with the same arc as the stamina's,
// espacoTerceiraPessoa blocks closer to the body (between the player and the stamina), espessura blocks thick.
// cores: per power id, the gradient [dark, light]; the bar goes from the dark at its bottom to the light at the tip
// as it fills. Powers not listed take their own color (the one of the U menu), darker to lighter.
public record ChargeSpec(int espacoPrimeiraPessoa, int larguraPrimeiraPessoa, double espacoTerceiraPessoa, double espessura,
        Map<String, List<String>> cores) {

    // The [dark, light] gradient of power, or the one made from its color.
    public int[] gradient(String power, int powerColor) {
        List<String> pair = cores == null ? null : cores.get(power);
        int dark = mix(powerColor, 0x000000, 0.62f);
        int light = mix(powerColor, 0xFFFFFF, 0.3f);
        if (pair != null && pair.size() >= 2) {
            dark = StaminaSpec.Barra.rgb(pair.get(0), dark);
            light = StaminaSpec.Barra.rgb(pair.get(1), light);
        }
        return new int[] {dark, light};
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    public static ChargeSpec get() {
        return SkillSpecs.get("padrao.carga", ChargeSpec.class, false);
    }
}
