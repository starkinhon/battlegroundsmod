package com.starkinhon.battlegroundsmod.client.blocky;

import com.starkinhon.battlegroundsmod.animal.db.Androide8;
import com.starkinhon.battlegroundsmod.animal.db.Bora;
import com.starkinhon.battlegroundsmod.animal.db.LadraoUrso;
import com.starkinhon.battlegroundsmod.animal.db.Namekiano;
import com.starkinhon.battlegroundsmod.animal.db.PeixeGigante;

import net.minecraft.util.Mth;

// The preview's animations (tools/mobs/blocky/*.js, anim()), the same numbers: idle always, the walk weighted by how
// fast it moves (limbSwingAmount), the attack from the server's entity event. t is in seconds like the preview.
public final class DbAnims {
    private DbAnims() {
    }

    private static float s(float x) {
        return Mth.sin(x);
    }

    private static float sm(float a, float b, float v) {
        float t = Mth.clamp((v - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static final float DEG = Mth.DEG_TO_RAD;

    // ---------- Ladrão Urso ----------
    public static void urso(BlockyModel<LadraoUrso> m, LadraoUrso e, float ls, float amt, float age, float hy, float hp) {
        float t = age / 20f, br = s(t * 1.6f);
        m.rot("bracoD", -0.45f, 0.15f, 0); m.rot("anteD", -1.05f, 0, 0);
        m.rot("bracoE", -0.15f, 0, 0); m.rot("anteE", -0.5f, 0, 0);
        m.rot("corpo", 0.08f + br * 0.015f, 0, 0);
        m.rot("cabeca", -0.1f - br * 0.02f + hp * DEG * 0.5f, hy * DEG * 0.6f, 0);
        m.rot("fitas", s(t * 1.3f) * 0.06f, 0, s(t * 0.9f) * 0.05f);
        m.move("corpo", 0, br * 0.25f, 0);
        m.rot("punhoD", 0.2f, 0, 0);
        float w = ls * 0.45f, sw = s(w) * amt;
        m.rot("coxaD", sw * 0.45f, 0, 0); m.rot("coxaE", -sw * 0.45f, 0, 0);
        m.rot("canelaD", Math.max(0, -sw) * 0.4f, 0, 0); m.rot("canelaE", Math.max(0, sw) * 0.4f, 0, 0);
        m.rot("corpo", 0, sw * 0.08f, sw * 0.09f);
        m.move("corpo", 0, -Math.abs(s(w)) * 0.9f * amt, 0);
        m.rot("bracoE", -sw * 0.35f, 0, 0);
        m.rot("fitas", (-0.15f - Math.abs(sw) * 0.2f) * amt, 0, sw * 0.12f);
        float a = e.attackProgress(age, 1.9f);
        if (a >= 0 && e.attackKind == 0) {
            // levanta e ruge, corta de cima, segura o impacto, volta
            float up, down;
            if (a < 0.42f) { up = sm(0, 0.42f, a); down = 0; }
            else if (a < 0.52f) { up = 1; down = sm(0.42f, 0.52f, a); }
            else if (a < 0.72f) { up = 1; down = 1; }
            else { float k = sm(0.72f, 1, a); up = 1 - k; down = 1 - k; }
            float lift = up * (1 - down), hit = down * up;
            m.rot("bracoD", -2.3f * lift + 1.1f * hit, 0, -0.25f * lift);
            m.rot("anteD", 0.6f * lift + 0.75f * hit, 0, 0);
            m.rot("corpo", -0.22f * lift + 0.32f * hit, 0.25f * lift - 0.2f * hit, 0);
            m.rot("cabeca", -0.25f * lift + 0.15f * hit, 0, 0);
            m.rot("mandibula", 0.75f * lift + 0.3f * hit, 0, 0);
            m.rot("bracoE", -0.6f * lift, 0, 0.3f * lift);
            m.move("corpo", 0, 1.6f * hit, 0);
            m.rot("coxaD", -0.25f * hit, 0, 0.1f * hit); m.rot("coxaE", 0.2f * hit, 0, -0.1f * hit);
        }
        a = e.attackProgress(age, 1.2f);
        if (a >= 0 && e.attackKind == 1) {
            // rugido: cabeça pra cima, boca escancarada, braços abertos
            float k = a < 0.2f ? sm(0, 0.2f, a) : a < 0.75f ? 1 : 1 - sm(0.75f, 1, a);
            m.rot("cabeca", -0.5f * k, 0, s(age * 1.7f) * 0.05f * k);
            m.rot("mandibula", 0.9f * k, 0, 0);
            m.rot("corpo", -0.15f * k, 0, 0);
            m.rot("bracoD", 0, 0, 0.5f * k); m.rot("bracoE", -0.3f * k, 0, -0.6f * k);
        }
    }

    // ---------- Bora ----------
    public static void bora(BlockyModel<Bora> m, Bora e, float ls, float amt, float age, float hy, float hp) {
        float t = age / 20f, br = s(t * 1.4f);
        m.rot("bracoD", -0.35f, 0.2f, 0); m.rot("anteD", -0.85f, 0, 0); m.rot("lanca", 0.25f, 0, 0);
        m.rot("bracoE", -0.1f, 0, 0); m.rot("anteE", -0.35f, 0, 0);
        m.rot("corpo", br * 0.02f, 0, 0);
        m.move("corpo", 0, br * 0.2f, 0);
        m.rot("cabeca", -br * 0.02f + hp * DEG * 0.6f, hy * DEG * 0.7f, 0);
        m.rot("rabo", s(t * 1.1f) * 0.08f, 0, s(t * 0.8f) * 0.06f);
        m.rot("tanga", s(t * 1.2f) * 0.04f, 0, 0);
        float w = ls * 0.6662f, sw = s(w) * amt;
        m.rot("pernaD", sw * 0.75f, 0, 0); m.rot("pernaE", -sw * 0.75f, 0, 0);
        m.rot("bracoE", -sw * 0.5f, 0, 0);
        m.rot("corpo", 0.05f * amt, -sw * 0.08f, 0);
        m.move("corpo", 0, -Math.abs(s(w)) * 0.6f * amt, 0);
        m.rot("tanga", -(0.2f + Math.abs(sw) * 0.25f) * amt, 0, 0);
        m.rot("rabo", 0.3f * amt, 0, -sw * 0.15f);
        float a = e.attackProgress(age, 1.5f);
        if (a >= 0) {
            // puxa a lança, estoca de uma vez, segura, volta
            float pull = a < 0.35f ? sm(0, 0.35f, a) : a < 0.43f ? 1 - sm(0.35f, 0.43f, a) : 0;
            float hit = a < 0.35f ? 0 : a < 0.43f ? sm(0.35f, 0.43f, a) : a < 0.62f ? 1 : 1 - sm(0.62f, 1, a);
            m.rot("bracoD", 0.5f * pull - 1.1f * hit, -0.3f * pull + 0.1f * hit, 0);
            m.rot("anteD", 0.4f * pull + 0.85f * hit, 0, 0);
            m.rot("lanca", -0.1f * pull + 1.2f * hit, 0, 0);
            m.rot("corpo", 0.18f * hit - 0.05f * pull, 0.35f * pull - 0.3f * hit, 0);
            m.rot("pernaD", -0.55f * hit, 0, 0); m.rot("pernaE", 0.35f * hit, 0, 0);
            m.move("corpo", 0, 1.2f * hit, -2.5f * hit);
            m.rot("bracoE", 0.4f * hit, 0, 0.2f * hit);
            m.rot("cabeca", -0.15f * hit, 0.25f * hit, 0);
        }
    }

    // ---------- Androide 8 ----------
    public static void androide(BlockyModel<Androide8> m, Androide8 e, float ls, float amt, float age, float hy, float hp) {
        float t = age / 20f, br = s(t * 1.1f);
        m.rot("anteD", -0.25f, 0, 0); m.rot("anteE", -0.25f, 0, 0);
        m.rot("corpo", 0.05f + br * 0.025f, 0, 0);
        m.move("corpo", 0, br * 0.35f, 0);
        m.rot("bracoD", -br * 0.04f, 0, br * 0.03f); m.rot("bracoE", -br * 0.04f, 0, -br * 0.03f);
        m.rot("cabeca", -0.05f + hp * DEG * 0.5f, hy * DEG * 0.6f, s(t * 0.5f) * 0.04f);
        float w = ls * 0.45f, sw = s(w) * amt;
        m.rot("pernaD", sw * 0.45f, 0, 0); m.rot("pernaE", -sw * 0.45f, 0, 0);
        m.rot("bracoD", -sw * 0.35f, 0, 0); m.rot("bracoE", sw * 0.35f, 0, 0);
        m.rot("corpo", 0.04f * amt, sw * 0.07f, -sw * 0.07f);
        m.move("corpo", 0, -Math.abs(s(w)) * 1.1f * amt, 0);
        float a = e.attackProgress(age, 2.0f);
        if (a >= 0) {
            float up = a < 0.45f ? sm(0, 0.45f, a) : a < 0.53f ? 1 - sm(0.45f, 0.53f, a) : 0;
            float hit = a < 0.45f ? 0 : a < 0.53f ? sm(0.45f, 0.53f, a) : a < 0.75f ? 1 : 1 - sm(0.75f, 1, a);
            m.rot("bracoD", -2.9f * up - 1.25f * hit, 0, -0.45f * up - 0.32f * hit);
            m.rot("bracoE", -2.9f * up - 1.25f * hit, 0, 0.45f * up + 0.32f * hit);
            m.rot("anteD", -0.4f * up + 0.2f * hit, 0, 0); m.rot("anteE", -0.4f * up + 0.2f * hit, 0, 0);
            m.rot("corpo", -0.22f * up + 0.55f * hit, 0, 0);
            m.rot("cabeca", 0.2f * up - 0.35f * hit, 0, 0);
            m.move("corpo", 0, 2f * hit, 0);
            m.rot("pernaD", -0.25f * hit, 0, 0.12f * hit); m.rot("pernaE", -0.25f * hit, 0, -0.12f * hit);
        }
    }

    // ---------- Namekiano ----------
    public static void namek(BlockyModel<Namekiano> m, Namekiano e, float ls, float amt, float age, float hy, float hp) {
        float shift = (e.variant() * 0.618f) % 1f;
        float t = age / 20f, br = s(t * 1.5f + shift * 6);
        boolean elder = e.elder();
        m.part("bracoD").yScale = 1;
        m.part("anteD").yScale = 1;
        m.rot("corpo", br * 0.015f, 0, 0);
        m.move("corpo", 0, br * 0.18f, 0);
        m.rot("cabeca", (elder ? 0.1f : -0.03f) + hp * DEG * 0.6f, hy * DEG * 0.7f, 0);
        m.rot("anteD_a", s(t * 1.7f - 0.6f) * 0.07f, 0, s(t * 1.3f) * 0.05f);
        m.rot("anteE_a", s(t * 1.5f - 0.2f) * 0.07f, 0, -s(t * 1.1f + 1) * 0.05f);
        m.rot("anteD_b", s(t * 1.7f - 1.3f) * 0.12f, 0, 0);
        m.rot("anteE_b", s(t * 1.5f - 0.9f) * 0.12f, 0, 0);
        if (elder) {
            m.rot("bracoD", -0.2f, 0, 0); m.rot("anteD", -0.6f, 0, 0); m.rot("bracoE", -0.2f, 0, 0); m.rot("anteE", -0.6f, 0, 0);
        }
        float w = ls * (elder ? 0.5f : 0.6662f), sw = s(w) * amt;
        m.rot("pernaD", sw * 0.7f, 0, 0); m.rot("pernaE", -sw * 0.7f, 0, 0);
        m.rot("bracoD", -sw * 0.55f, 0, 0); m.rot("bracoE", sw * 0.55f, 0, 0);
        m.move("corpo", 0, -Math.abs(s(w)) * 0.4f * amt, 0);
        m.rot("anteD_b", -Math.abs(sw) * 0.2f, 0, 0); m.rot("anteE_b", -Math.abs(sw) * 0.2f, 0, 0);
        m.rot("abaD", -(Math.abs(sw) * 0.15f + 0.05f) * amt, 0, 0); m.rot("abaE", -(Math.abs(sw) * 0.15f + 0.05f) * amt, 0, 0);
        m.rot("abaT", Math.abs(sw) * 0.15f, 0, 0);
        float a = e.attackProgress(age, 1.6f);
        if (a >= 0) {
            // o braço que estica
            float load = a < 0.25f ? sm(0, 0.25f, a) : a < 0.38f ? 1 - sm(0.25f, 0.38f, a) : 0;
            float shot = a < 0.25f ? 0 : a < 0.38f ? sm(0.25f, 0.38f, a) : a < 0.55f ? 1 : 1 - sm(0.55f, 0.9f, a);
            m.rot("bracoD", 0.5f * load - 1.6f * shot, 0.2f * load, 0);
            m.rot("anteD", -0.8f * load, 0, 0);
            m.rot("corpo", 0.1f * shot, 0.4f * load - 0.35f * shot, 0);
            m.rot("bracoE", -0.3f * shot, 0, 0.3f * shot);
            m.rot("cabeca", 0, -0.3f * shot, 0);
            m.part("bracoD").yScale = 1 + 0.6f * shot;
            m.part("anteD").yScale = 1 + 3.2f * shot;
            m.rot("pernaD", -0.3f * shot, 0, 0); m.rot("pernaE", 0.25f * shot, 0, 0);
        }
    }

    // ---------- Peixe gigante ----------
    public static void peixe(BlockyModel<PeixeGigante> m, PeixeGigante e, float ls, float amt, float age, float hy, float hp) {
        float t = age / 20f;
        float fast = Math.min(1f, (float) e.getDeltaMovement().length() * 4f);
        float f = 2 + fast * 2.6f, amp = 1 + fast * 0.5f, w = t * f;
        m.rot("corpo", 0, s(w + 0.8f) * 0.05f * amp, 0);
        m.rot("cabeca", 0, -s(w + 1.2f) * 0.04f * amp, 0);
        m.rot("cauda1", 0, s(w) * 0.16f * amp, 0);
        m.rot("cauda2", 0, s(w - 0.9f) * 0.3f * amp, 0);
        m.rot("caudal", 0, s(w - 1.8f) * 0.4f * amp, 0);
        m.rot("peitD", 0, s(t * 2.4f) * 0.15f, s(t * 2.4f) * 0.25f);
        m.rot("peitE", 0, -s(t * 2.4f + 0.5f) * 0.15f, -s(t * 2.4f + 0.5f) * 0.25f);
        m.rot("mandibula", (s(t * 1.1f) * 0.5f + 0.5f) * 0.12f, 0, 0);
        m.rot("bigodeD", s(t * 1.6f) * 0.15f, 0, s(t * 1.2f) * 0.1f); m.rot("bigodeD2", s(t * 1.6f - 0.7f) * 0.25f, 0, 0);
        m.rot("bigodeE", s(t * 1.4f + 1) * 0.15f, 0, -s(t * 1.1f) * 0.1f); m.rot("bigodeE2", s(t * 1.4f + 0.3f) * 0.25f, 0, 0);
        // nariz segue a direção do movimento (sobe no salto, desce na queda)
        m.rot("corpo", -hp * DEG, 0, 0);
        float a = e.attackProgress(age, 2.0f);
        if (a >= 0) {
            float arc = Mth.sin(Mth.PI * sm(0.05f, 0.85f, a));
            float open = a < 0.4f ? sm(0.1f, 0.4f, a) : a < 0.5f ? 1 - sm(0.44f, 0.5f, a) : 0;
            m.rot("mandibula", 0.85f * open, 0, 0);
            m.rot("cabeca", -0.18f * open, 0, 0);
            m.rot("cauda1", 0.3f * arc, s(age * 0.45f) * 0.12f, 0); m.rot("cauda2", 0.35f * arc, 0, 0); m.rot("caudal", 0.2f * arc, 0, 0);
            m.rot("peitD", 0, 0, -0.6f * arc); m.rot("peitE", 0, 0, 0.6f * arc);
        }
        if (!e.isInWater()) {
            // fora d'água se debate
            m.rot("corpo", 0, 0, s(age * 0.9f) * 0.25f);
            m.rot("cauda1", 0, s(age * 0.9f) * 0.35f, 0);
        }
    }
}
