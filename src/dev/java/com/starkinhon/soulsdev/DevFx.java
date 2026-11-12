package com.starkinhon.soulsdev;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// /dev fx: faz nascer qualquer efeito das SubAlmas (SubNet, ex. genki = Genkidama, ki_ball, getsuga, death_ball...)
// PARADO na sua frente, reenviando todo tick pelo tempo pedido (os efeitos "every tick" somem sozinhos uns ticks depois
// que param de chegar). ticks 0 = manda uma vez só (pros efeitos que tocam sozinhos, tipo explosões).
// O significado de a, b, c (e dir) muda por efeito: está no comentário de cada constante em SubNet.java.
public final class DevFx {
    private static final Map<String, Integer> TIPOS = new TreeMap<>();
    private static final List<Ativo> ATIVOS = new ArrayList<>();

    private record Ativo(ServerLevel level, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, int[] falta) {
    }

    static {
        for (Field f : SubNet.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (f.getType() == int.class && Modifier.isStatic(m) && Modifier.isFinal(m) && Modifier.isPublic(m)) {
                try {
                    TIPOS.put(f.getName().toLowerCase(), f.getInt(null));
                } catch (IllegalAccessException ignored) {
                    // só públicas
                }
            }
        }
    }

    private DevFx() {
    }

    public static Iterable<String> nomes() {
        return TIPOS.keySet();
    }

    // Nome (genki, ki_ball...) ou número. -1 se não existe.
    public static int tipo(String nome) {
        Integer t = TIPOS.get(nome.toLowerCase());
        if (t != null) {
            return t;
        }
        try {
            return Integer.parseInt(nome);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static int tocar(ServerLevel level, int kind, Vec3 pos, Vec3 dir, float a, float b, float c, int ticks) {
        int id = SubNet.newId();
        SubNet.send(level, kind, id, pos, dir, a, b, c);
        if (ticks > 0) {
            ATIVOS.add(new Ativo(level, kind, id, pos, dir, a, b, c, new int[] {ticks}));
        }
        return id;
    }

    public static int parar() {
        int n = ATIVOS.size();
        ATIVOS.clear();
        return n;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ATIVOS.removeIf(x -> {
            if (--x.falta()[0] <= 0) {
                return true;
            }
            SubNet.send(x.level(), x.kind(), x.id(), x.pos(), x.dir(), x.a(), x.b(), x.c());
            return false;
        });
    }

    @SubscribeEvent
    public static void parou(ServerStoppingEvent event) {
        ATIVOS.clear();
    }
}
