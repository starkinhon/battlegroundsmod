package com.starkinhon.soulsdev.client;

import com.starkinhon.soulsdev.SoulsDev;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

// Câmera de teste: põe a visão onde o roteiro mandar SEM trocar de entidade (o jogador continua mandando posição e mira
// pro servidor normal). Quem posiciona é o mixin CameraDevMixin, no fim do Camera.setup, e ele marca a câmera como
// "solta" pra o próprio jogador aparecer.
//
// Modos (todos menos fixa olham pro peito do jogador e seguem ele):
//   lado/frente/tras <dist> [altura]  - do lado direito / na frente / atrás, girado pelo rumo do jogador NA HORA do comando
//   cima <dist>                       - de cima pra baixo
//   duelo <dist> [altura]             - de lado, no meio entre você e o mob/boss mais perto (cabem os dois)
//   ponto x y z                       - parada nesse ponto, olhando pro jogador
//   fixa x y z yaw pitch              - parada, olhando pra onde mandar
//   congelar                          - fica onde está agora (vira fixa)
//   jogador                           - volta ao normal
public final class CameraDev {
    public record Pose(Vec3 pos, float yaw, float pitch) {
    }

    private enum Modo {
        LADO, FRENTE, TRAS, CIMA, PONTO, FIXA, DUELO
    }

    private static Modo modo;
    private static float rumo;
    private static double dist;
    private static double altura;
    private static Vec3 ponto = Vec3.ZERO;
    private static float fixaYaw;
    private static float fixaPitch;
    private static Pose ultima;

    private CameraDev() {
    }

    public static boolean ativa() {
        return modo != null && Minecraft.getInstance().player != null;
    }

    public static void relativa(String nome, double distancia, double alt) {
        Player player = Minecraft.getInstance().player;
        modo = switch (nome) {
            case "lado" -> Modo.LADO;
            case "frente" -> Modo.FRENTE;
            case "tras" -> Modo.TRAS;
            case "cima" -> Modo.CIMA;
            case "duelo" -> Modo.DUELO;
            default -> throw new IllegalArgumentException("câmera desconhecida: " + nome);
        };
        rumo = player == null ? 0 : player.getYRot();
        dist = distancia;
        altura = alt;
        SoulsDev.log("camera " + nome + " dist=" + distancia + " altura=" + alt + " rumo=" + rumo);
    }

    public static void ponto(Vec3 pos) {
        modo = Modo.PONTO;
        ponto = pos;
        SoulsDev.log("camera ponto " + pos);
    }

    public static void fixa(Vec3 pos, float yaw, float pitch) {
        modo = Modo.FIXA;
        ponto = pos;
        fixaYaw = yaw;
        fixaPitch = pitch;
        SoulsDev.log("camera fixa " + pos + " yaw=" + yaw + " pitch=" + pitch);
    }

    public static void congelar() {
        if (ultima != null) {
            fixa(ultima.pos(), ultima.yaw(), ultima.pitch());
        }
    }

    public static void desligar() {
        modo = null;
        ultima = null;
        SoulsDev.log("camera jogador");
    }

    private static Pose olhando(Vec3 de, Vec3 para) {
        Vec3 d = para.subtract(de);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG));
        return new Pose(de, yaw, pitch);
    }

    // Chamado pelo mixin a cada quadro. null = câmera normal.
    public static Pose pose(float partialTick) {
        Player player = Minecraft.getInstance().player;
        if (modo == null || player == null) {
            return null;
        }
        Vec3 pe = player.getPosition(partialTick);
        Vec3 peito = pe.add(0, player.getBbHeight() * 0.6, 0);
        float r = rumo * Mth.DEG_TO_RAD;
        Vec3 frente = new Vec3(-Mth.sin(r), 0, Mth.cos(r));
        Vec3 direita = new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
        Pose pose = switch (modo) {
            case LADO -> olhando(peito.add(direita.scale(dist)).add(0, altura, 0), peito);
            case FRENTE -> olhando(peito.add(frente.scale(dist)).add(0, altura, 0), peito);
            case TRAS -> olhando(peito.subtract(frente.scale(dist)).add(0, altura, 0), peito);
            // De cima, com a frente do jogador (no rumo do comando) pra cima na tela.
            case CIMA -> new Pose(peito.add(0, dist, 0), rumo, 90f);
            case PONTO -> olhando(ponto, peito);
            case FIXA -> new Pose(ponto, fixaYaw, fixaPitch);
            case DUELO -> duelo(player, peito, partialTick);
        };
        ultima = pose;
        return pose;
    }

    // De lado, olhando pro meio entre o jogador e o mob/boss mais perto, longe o bastante pra caber os dois.
    private static Pose duelo(Player player, Vec3 peito, float partialTick) {
        net.minecraft.world.entity.LivingEntity alvo = Mira.maisPerto();
        if (alvo == null) {
            return olhando(peito.add(new Vec3(-Mth.cos(rumo * Mth.DEG_TO_RAD), 0, -Mth.sin(rumo * Mth.DEG_TO_RAD)).scale(dist)), peito);
        }
        Vec3 outro = alvo.getPosition(partialTick).add(0, alvo.getBbHeight() * 0.6, 0);
        Vec3 meio = peito.add(outro).scale(0.5);
        Vec3 linha = outro.subtract(peito);
        Vec3 lado = new Vec3(-linha.z, 0, linha.x);
        lado = lado.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : lado.normalize();
        double longe = Math.max(dist, linha.length() * 0.9 + 3);
        return olhando(meio.add(lado.scale(longe)).add(0, altura, 0), meio);
    }

    // Sem a mão flutuando na frente da câmera de teste.
    @SubscribeEvent
    public static void mao(RenderHandEvent event) {
        if (ativa()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void saiu(ClientPlayerNetworkEvent.LoggingOut event) {
        modo = null;
        ultima = null;
    }
}
