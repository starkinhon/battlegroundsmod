package com.starkinhon.battlegroundsmod.skill;

// Fear ("MEDO"), the effect of the boss's final ult: lentidao and pulo take that fraction off speed and jump height
// (a full block is still reachable, barely). On the client the screen shakes by tremor and a fast heartbeat plays
// every batimentoTicks. Read from souls_skills/padrao.medo.json.
public record MedoSpec(String skill, String nome, double lentidao, double pulo, float tremor, int batimentoTicks, float volumeBatimento,
        float pitchBatimento) {

    public static MedoSpec get() {
        return SkillSpecs.get("padrao.medo", MedoSpec.class, false);
    }
}
