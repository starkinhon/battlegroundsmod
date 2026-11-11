package com.starkinhon.battlegroundsmod.power;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.power.powers.DestructionGod;
import com.starkinhon.battlegroundsmod.power.powers.Fighter;
import com.starkinhon.battlegroundsmod.power.powers.Manipulator;
import com.starkinhon.battlegroundsmod.power.powers.MoonDemon;
import com.starkinhon.battlegroundsmod.power.powers.OnePunch;
import com.starkinhon.battlegroundsmod.power.powers.RubberBoy;
import com.starkinhon.battlegroundsmod.power.powers.Strongest;
import com.starkinhon.battlegroundsmod.power.powers.Sukuna;
import com.starkinhon.battlegroundsmod.power.powers.UchihaGhost;
import com.starkinhon.battlegroundsmod.power.powers.sub.Byakuya;
import com.starkinhon.battlegroundsmod.power.powers.sub.Rukia;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Freeza;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Goku;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Kame;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Kuririn;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.Gaara;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.Ino;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.Kakashi;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.Naruto;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.Sasuke;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.Shikamaru;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Piccolo;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Vegeta;

// Every selectable power, in the order shown in the power book. The Divine Souls (the bosses' powers) are on the book's
// first tab, the SubAlmas (Low Souls: characters from each boss's anime) on the second.
public final class Powers {
    private static final Map<String, Power> BY_ID = new LinkedHashMap<>();
    private static final List<Power> DIVINE = new ArrayList<>();
    private static final List<Power> SUB = new ArrayList<>();
    // Powers only mobs use (the Fighters' base combat): found by get, but not selectable and without a soul crystal.
    private static final Map<String, Power> HIDDEN = new LinkedHashMap<>();

    public static final Power UCHIHA_GHOST = register(UchihaGhost.create());
    public static final Power MANIPULATOR = register(Manipulator.create());
    public static final Power RUBBER_BOY = register(RubberBoy.create());
    public static final Power DESTRUCTION_GOD = register(DestructionGod.create());
    public static final Power ONE_PUNCH = register(OnePunch.create());
    public static final Power STRONGEST = register(Strongest.create());
    public static final Power MOON_DEMON = register(MoonDemon.create());
    public static final Power SUKUNA = register(Sukuna.create());

    // SubAlmas (Bleach). The provisional Sukuna, Ichigo and Urahara were taken out for now (2026-11-07).
    public static final Power BYAKUYA = registerSub(Byakuya.create());
    public static final Power RUKIA = registerSub(Rukia.create());

    // SubAlmas (Dragon Ball), tiers 6 to 8: nobody above the bosses.
    public static final Power GOKU = registerSub(Goku.create());
    public static final Power VEGETA = registerSub(Vegeta.create());
    public static final Power FREEZA = registerSub(Freeza.create());
    public static final Power PICCOLO = registerSub(Piccolo.create());
    public static final Power KAME = registerSub(Kame.create());
    public static final Power KURIRIN = registerSub(Kuririn.create());

    // SubAlmas (Naruto), tiers 6 to 8.
    public static final Power NARUTO = registerSub(Naruto.create());
    public static final Power SASUKE = registerSub(Sasuke.create());
    public static final Power KAKASHI = registerSub(Kakashi.create());
    public static final Power GAARA = registerSub(Gaara.create());
    public static final Power SHIKAMARU = registerSub(Shikamaru.create());
    public static final Power INO = registerSub(Ino.create());

    // SubAlmas (Jujutsu), tiers 6 to 8.
    public static final Power ITADORI = registerSub(com.starkinhon.battlegroundsmod.power.powers.sub.jj.Itadori.create());
    public static final Power CHOSO = registerSub(com.starkinhon.battlegroundsmod.power.powers.sub.jj.Choso.create());
    public static final Power NANAMI = registerSub(com.starkinhon.battlegroundsmod.power.powers.sub.jj.Nanami.create());
    public static final Power NOBARA = registerSub(com.starkinhon.battlegroundsmod.power.powers.sub.jj.Nobara.create());
    public static final Power TODO = registerSub(com.starkinhon.battlegroundsmod.power.powers.sub.jj.Todo.create());
    public static final Power INUMAKI = registerSub(com.starkinhon.battlegroundsmod.power.powers.sub.jj.Inumaki.create());
    public static final Power MIWA = registerSub(com.starkinhon.battlegroundsmod.power.powers.sub.jj.Miwa.create());

    // Mob only.
    public static final Power FIGHTER = registerHidden(Fighter.create());
    // The Dragon World's enemies, two moves each (DbMobs).
    public static final Power DB_SAIBAMAN = registerHidden(com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs.saibaman());
    public static final Power DB_BANDIT = registerHidden(com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs.bandit());
    public static final Power DB_PIRATE = registerHidden(com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs.pirate());
    public static final Power DB_RED_RIBBON = registerHidden(com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs.redRibbon());
    public static final Power DB_YETI = registerHidden(com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs.yeti());
    public static final Power DB_FREEZA_SOLDIER = registerHidden(com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs.freezaSoldier());

    private Powers() {
    }

    private static Power register(Power power) {
        BY_ID.put(power.id(), power);
        DIVINE.add(power);
        return power;
    }

    private static Power registerSub(Power power) {
        BY_ID.put(power.id(), power);
        SUB.add(power);
        return power;
    }

    private static Power registerHidden(Power power) {
        HIDDEN.put(power.id(), power);
        return power;
    }

    public static Power get(String id) {
        Power power = BY_ID.get(id);
        return power != null || id == null ? power : HIDDEN.get(id);
    }

    public static Collection<Power> all() {
        return BY_ID.values();
    }

    public static List<Power> divine() {
        return DIVINE;
    }

    public static List<Power> sub() {
        return SUB;
    }

    public static boolean isSub(String id) {
        Power power = BY_ID.get(id);
        return power != null && SUB.contains(power);
    }
}
