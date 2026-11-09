package com.starkinhon.battlegroundsmod.power.powers.duel;

import java.util.ArrayList;
import java.util.List;

// The rules of the Duelo de Expansões minigames, the same on the server (DomainDuel: the bots, the score, the result)
// and on the clients (client/fx/DuelScene: the HUD, the keys), ported from the preview Stark approved
// (forja/duelo-expansoes, v2, 2026-11-09 06:46: "esse minigame é viciante"). One of three is drawn for each duel:
// - Sequência: 10 keys out of W A S D Q E, one at a time, the same for both; the window shrinks from 0.8 s to 0.45 s
//   and only the first press counts.
// - Cabo de guerra: 5 s alternating A and D as fast as they can; each beat pushes the line between the two skies.
// - Refinamento: 5 rounds of a pointer bouncing back and forth; Space in the zone is 1 point, in its middle 2; the zone
//   shrinks and the pointer speeds up.
// Same score at the end is a tie (Stark 05:51). Every time below is in ticks since the duel began (the moment the
// Santuário starts to sprout in the tunnel).
public final class DuelRules {
    public static final int SEQ = 0, CABO = 1, REF = 2;
    public static final String[] NAMES = {"Sequência", "Cabo de guerra", "Refinamento"};
    public static final char[] KEYSET = {'W', 'A', 'S', 'D', 'Q', 'E'};

    // The Santuário sprouts, the title, the HUD with its countdown, the first key.
    public static final int RISE_END = 34;
    public static final int TITLE = 36;
    public static final int MG = 44;
    public static final int K0 = 62;
    // From the game's end: the result on screen this long before the winner's domain takes over.
    public static final int RESULT = 24;

    public static final int[] WIN = {16, 15, 14, 13, 12, 12, 11, 10, 10, 9};
    public static final int GAP = 4;
    public static final int CABO_LEN = 100;
    public static final int RW = 34, RGAP = 8;
    public static final int[] RPER = {26, 22, 18, 15, 12};
    public static final double[] RHW = {0.15, 0.13, 0.11, 0.09, 0.075};

    // Bots, with human reactions and mistakes (never perfect): accuracy, reaction ticks, beats per second, chance of a 2
    // and of a 1 in the Refinamento. Mobs play "normal", bosses "difícil".
    public record Bot(double acc, double react, double rate, double p2, double p1) {
    }

    public static final Bot NORMAL = new Bot(0.75, 6.2, 8, 0.3, 0.45);
    public static final Bot HARD = new Bot(0.88, 5.2, 9.5, 0.45, 0.4);

    // One scoring move: at tick, on index (key, beat or round), worth value points.
    public record Move(double tick, int index, int value) {
    }

    public record Round(double center, double half, double phase, int period) {
    }

    private DuelRules() {
    }

    public static int game(int seed) {
        return Math.floorMod(seed, 3);
    }

    public static int slotStart(int i) {
        int s = K0;
        for (int k = 0; k < i; k++) {
            s += WIN[k] + GAP;
        }
        return s;
    }

    public static int slotEnd(int i) {
        return slotStart(i) + WIN[i];
    }

    public static int roundStart(int i) {
        return K0 + i * (RW + RGAP);
    }

    public static int roundEnd(int i) {
        return roundStart(i) + RW;
    }

    // When the playing is over, and when the result is done showing (the winner's domain takes over then).
    public static int gameEnd(int game) {
        int end = game == SEQ ? slotEnd(9) : game == CABO ? K0 + CABO_LEN : roundEnd(4);
        return end + 6;
    }

    public static int resultEnd(int game) {
        return gameEnd(game) + RESULT;
    }

    // The preview's generator (a Lehmer stream), so a seed gives the same keys and rounds everywhere.
    public static final class Rng {
        private long s;

        public Rng(long seed) {
            long v = Math.floorMod(seed * 9301 + 49297, 233280L);
            s = v == 0 ? 1 : v;
        }

        public double next() {
            s = (s * 16807) % 2147483647L;
            return (s - 1) / 2147483646.0;
        }
    }

    public static char[] sequence(int seed) {
        Rng r = new Rng((long) seed * 7 + 3);
        char[] q = new char[10];
        for (int i = 0; i < 10; i++) {
            char k;
            do {
                k = KEYSET[(int) Math.floor(r.next() * 6)];
            } while (i > 0 && k == q[i - 1] && r.next() < 0.7);
            q[i] = k;
        }
        return q;
    }

    public static Round round(int seed, int i) {
        Rng r = new Rng((long) seed * 13 + i * 7L + 5);
        return new Round(0.2 + 0.6 * r.next(), RHW[i], r.next(), RPER[i]);
    }

    private static double tri(double x) {
        return 1 - Math.abs(2 * (x - Math.floor(x)) - 1);
    }

    private static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double smooth(double a, double b, double x) {
        double u = clamp((x - a) / (b - a), 0, 1);
        return u * u * (3 - 2 * u);
    }

    // Where the Refinamento's pointer is (0..1) in round i at tick t.
    public static double pointer(Round round, int i, double t) {
        double s = roundStart(i), e = roundEnd(i);
        return tri((clamp(t, s, e) - s) / round.period() + round.phase());
    }

    public static int points(Round round, int i, double t) {
        double d = Math.abs(pointer(round, i, t) - round.center());
        return d <= round.half() * 0.3 ? 2 : d <= round.half() ? 1 : 0;
    }

    // The slot whose window is open at tick t, or -1.
    public static int slotAt(double t) {
        for (int i = 0; i < 10; i++) {
            if (t >= slotStart(i) && t < slotEnd(i)) {
                return i;
            }
        }
        return -1;
    }

    public static int roundAt(double t) {
        for (int i = 0; i < 5; i++) {
            if (t >= roundStart(i) && t < roundEnd(i)) {
                return i;
            }
        }
        return -1;
    }

    // What a bot does in this duel (the preview's botRec): its scoring moves, in order. side: 0 Gojo, 1 Sukuna.
    public static List<Move> bot(int game, int seed, int side, Bot b) {
        Rng r = new Rng((long) seed * 31 + (side == 0 ? 11 : 23));
        List<Move> out = new ArrayList<>();
        if (game == SEQ) {
            char[] seq = sequence(seed);
            for (int i = 0; i < 10; i++) {
                double react = b.react() + (r.next() - 0.5) * 3 + (i > 6 ? 0.6 : 0);
                boolean ok = r.next() < b.acc();
                boolean late = !ok && r.next() < 0.4;
                if (late) {
                    continue;
                }
                int s = slotStart(i), e = slotEnd(i);
                out.add(new Move(s + clamp(react, 3, e - s - 0.5), i, ok ? 1 : 0));
            }
            return out;
        }
        if (game == CABO) {
            double rate = b.rate() + (r.next() - 0.5) * 1.6;
            double t = K0 + 4 + r.next() * 4;
            int n = 0;
            while (t < K0 + CABO_LEN) {
                out.add(new Move(t, n++, 1));
                double tired = 1 + 0.25 * smooth(K0 + 60, K0 + CABO_LEN, t);
                t += 20 / rate * (0.7 + 0.6 * r.next()) * tired;
            }
            return out;
        }
        for (int i = 0; i < 5; i++) {
            Round round = round(seed, i);
            double x = r.next();
            int want = x < b.p2() ? 2 : x < b.p2() + b.p1() ? 1 : 0;
            double hit = -1;
            for (double t = roundStart(i) + 5 + r.next() * 3; t < roundEnd(i); t += 0.25) {
                int p = points(round, i, t);
                if (p == want && (want > 0 || Math.abs(pointer(round, i, t) - round.center()) < round.half() * 2.2)) {
                    if (r.next() < 0.5) {
                        hit = t;
                        break;
                    }
                }
            }
            if (hit >= 0) {
                out.add(new Move(hit, i, points(round, i, hit)));
            }
        }
        return out;
    }

    // How hard a move pushes the line between the skies (the preview's events: a key 1, a beat 0.28, a round its points).
    public static double push(int game, int value) {
        return game == CABO ? 0.28 : value;
    }
}
