package com.starkinhon.battlegroundsmod.combat;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// Static state of the moves that PowerEvents.onServerStopped did not clear. In single player the game keeps running
// between worlds, so those maps carried the old world over: timed states stored as the old world's game time (a SubAlma
// state set at tick 2,000,000 stayed "on" in a newer world until it got there), and the old entities and levels themselves
// (memory held for good).
public final class ServerReset {
    private ServerReset() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerReset::onStopped);
    }

    private static void onStopped(ServerStoppedEvent event) {
        com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.sub.nr.Shikamaru.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.sub.nr.Gaara.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.hashirama.Hashirama.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.freezaboss.FreezaTotal.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.sub.nr.Kakashi.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.sub.nr.Naruto.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.sub.nr.Sasuke.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.sub.db.DbMobs.resetStatics();
        com.starkinhon.battlegroundsmod.animal.dbmob.DbMobMoves.resetStatics();
        com.starkinhon.battlegroundsmod.animal.jjmob.JjMob.resetJj();
        com.starkinhon.battlegroundsmod.power.powers.gojo.Azul.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.shigaraki.Decay.resetStatics();
        com.starkinhon.battlegroundsmod.power.powers.shigaraki.Esfarelar.resetStatics();
    }
}
