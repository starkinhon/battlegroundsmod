package com.starkinhon.battlegroundsmod.npc;

// What a Shibuya pedestrian looks like (Stark approved the preview 2026-11-14: forja/jujutsu-pedestres/peds.js, port
// line by line). The whole look comes out of one int: the type of person in the top byte, the draw's seed in the rest,
// so the server sends a single number and the client builds the same person (PedestreSkins paints the 64x64 skin, the
// renderer adds the extra pieces). Kept apart from the walking so the metro's passengers can use the same people.
// Nine types (no children: Stark 2026-11-14), each with its own palettes and every color nudged a little (vary), so
// two salarymen in navy suits never have the same navy.
public final class PedestreLook {
    public static final int SALARYMAN = 0, OL = 1, SEIFUKU = 2, GAKURAN = 3, STREET = 4, TURISTA = 5, IDOSO = 6,
            OBRA = 7, GYARU = 8, TYPES = 9;
    private static final int[] WEIGHT = {17, 11, 9, 7, 14, 8, 3, 4, 5};
    private static final double[] NIGHT = {1.5, 1.1, 0.4, 0.4, 1.4, 0.9, 0.25, 0.5, 1.3};

    // ------------------------------------------------------------ the draw (mulberry32, as in the preview)
    public static final class Rand {
        private int s;

        public Rand(int seed) {
            s = seed == 0 ? 1 : seed;
        }

        public double next() {
            s += 0x6D2B79F5;
            int t = s;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }

        public double range(double a, double b) {
            return a + (b - a) * next();
        }

        public int integer(int a, int b) {
            return a + (int) Math.floor(next() * (b - a + 1));
        }

        public boolean chance(double p) {
            return next() < p;
        }

        public String pick(String... arr) {
            return arr[(int) Math.floor(next() * arr.length)];
        }
    }

    public static double[] hex(String h) {
        return new double[] {Integer.parseInt(h.substring(1, 3), 16), Integer.parseInt(h.substring(3, 5), 16),
                Integer.parseInt(h.substring(5, 7), 16)};
    }

    public static double[] mul(double[] c, double k) {
        return new double[] {c[0] * k, c[1] * k, c[2] * k};
    }

    public static double[] mix(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static double[] vary(Rand r, String h, double k) {
        double[] c = hex(h);
        double f = 1 + (r.next() - 0.5) * k * 2;
        double t = (r.next() - 0.5) * k * 40;
        return new double[] {c[0] * f + t, c[1] * f, c[2] * f - t};
    }

    private static double[] vary(Rand r, String h) {
        return vary(r, h, 0.08);
    }

    private static final String[] SKIN_JP = {"#f3d6bd", "#eccbad", "#e6c2a2", "#dfb894", "#f6dfcc", "#d6a982"};
    private static final String[] SKIN_ALL = {"#f3d6bd", "#eccbad", "#e6c2a2", "#dfb894", "#f6dfcc", "#d6a982", "#c99872",
            "#b07a54", "#8d5a3c", "#6b4430", "#f4dccb"};
    private static final String[] HAIR_DARK = {"#151210", "#1c1814", "#231c16", "#2e231a", "#3a2a1e"};
    private static final String[] HAIR_DYED = {"#6a4428", "#8a5a32", "#b98a4e", "#c8963a", "#7a2e3a", "#4a3a5e", "#c87a5a", "#a0602a"};
    private static final String[] SUIT = {"#1d2330", "#262a33", "#2f3540", "#3a3d44", "#1c1c20", "#4a4c52", "#2a3346", "#3a3428"};
    private static final String[] TIE = {"#7a1f2a", "#1f3a6a", "#3a3a3a", "#7a6a2a", "#2a5a4a", "#5a2a5a", "#a03a2a", "#2a4a8a"};
    private static final String[] SHIRT = {"#f2f2ee", "#e6eef6", "#f4efe4", "#dde8f2", "#eef2ea"};
    private static final String[] BLOUSE = {"#f4f0ea", "#f2e2e6", "#e2ecf4", "#efe6d2", "#d8e4d6", "#f6f6f6", "#e8d8ea"};
    private static final String[] OL_BOTTOM = {"#23252c", "#3a3a40", "#5a4a40", "#2a3446", "#6a6a70", "#8a7a68", "#c8bca8"};
    private static final String[] HOODIE = {"#2b2d31", "#585c62", "#9aa0a6", "#e8e4dc", "#3a4a5e", "#5e3a3a", "#3e5a46", "#c8b090",
            "#e07a3a", "#6a4aa0", "#1e5aa0", "#a8c8a0", "#d84a5a", "#1a1a1c"};
    private static final String[] JEANS = {"#3a5a8a", "#2c4468", "#5a7aa8", "#1e2a40", "#46648e"};
    private static final String[] CASUAL_PANTS = {"#1c1d20", "#a89878", "#7a6a50", "#5a5e44", "#4a4e3c", "#6a6c70", "#2a2a30", "#d8d0c0"};
    private static final String[] VIVID = {"#e8423c", "#ff8a1e", "#ffd23a", "#38b86a", "#2fb6e6", "#3a6cff", "#ff4fb0", "#8a4aff", "#f4f1ea"};
    private static final String[] TEE = {"#f2f2f0", "#1c1c1e", "#e8423c", "#2f6db3", "#ffd23a", "#7ac8a0", "#f4b8c8", "#a8a8b0", "#3a6a3a", "#e8c8a0"};
    private static final String[] SNEAKER = {"#f2f2f0", "#e8e6e0", "#1c1c1e", "#c8c4bc", "#2a3446"};

    private static String[] head(String[] a, int n) {
        return java.util.Arrays.copyOf(a, n);
    }

    private static String[] concat(String[] a, String... b) {
        String[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ------------------------------------------------------------ the sheet
    public static final class Piece {
        public String kind;
        public double[] c, c2;
        public boolean flag; // cap: turned back

        Piece(String kind, double[] c) {
            this.kind = kind;
            this.c = c;
        }
    }

    public static final class Top {
        public String kind;
        public double[] c, shirt, tie, collar, stripe, scarf, button, print, band, jacket;
        public boolean open, inner, big, polo, tuck;

        Top(String kind, double[] c) {
            this.kind = kind;
            this.c = c;
        }
    }

    public final int look, type;
    public boolean female, glasses, mask, blush, sunglasses, lashes, elder, towel, watch, strap, camera;
    public double hunch;
    public double[] skin, hairC, headphones;
    public String hairStyle, sleeve;
    public Top top;
    public Piece bottom, legwear, shoes, held, back, hat;
    public double speed;

    private PedestreLook(int look) {
        this.look = look;
        this.type = Math.floorMod(look >>> 24, TYPES);
        Rand r = new Rand(look * 0x9E3779B1 ^ 0x51ED27);
        switch (type) {
            case SALARYMAN -> salaryman(r);
            case OL -> ol(r);
            case SEIFUKU -> seifuku(r);
            case GAKURAN -> gakuran(r);
            case STREET -> street(r);
            case TURISTA -> turista(r);
            case IDOSO -> idoso(r);
            case OBRA -> obra(r);
            default -> gyaru(r);
        }
    }

    private static final java.util.Map<Integer, PedestreLook> CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Integer, PedestreLook> e) {
                    return size() > 512;
                }
            });

    public static PedestreLook of(int look) {
        return CACHE.computeIfAbsent(look, PedestreLook::new);
    }

    public static int make(int type, int seed) {
        return (type << 24) | (seed & 0xFFFFFF);
    }

    public static int pickType(java.util.Random r, boolean night) {
        double tot = 0;
        for (int i = 0; i < TYPES; i++) {
            tot += WEIGHT[i] * (night ? NIGHT[i] : 1);
        }
        double x = r.nextDouble() * tot;
        for (int i = 0; i < TYPES; i++) {
            x -= WEIGHT[i] * (night ? NIGHT[i] : 1);
            if (x <= 0) {
                return i;
            }
        }
        return SALARYMAN;
    }

    public boolean phone() {
        return held != null && held.kind.equals("phone");
    }

    public boolean skirt() {
        return switch (bottom.kind) {
            case "pencil", "pleated", "mini", "longskirt" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------ the types
    private void salaryman(Rand r) {
        String suit = r.pick(SUIT);
        female = false;
        skin = hex(r.pick(SKIN_JP));
        hairC = hex(r.pick(HAIR_DARK));
        hairStyle = r.pick("part", "part", "short", "short", "slick", "buzz");
        glasses = r.chance(0.35);
        mask = r.chance(0.18);
        top = new Top("suit", vary(r, suit, 0.05));
        top.shirt = vary(r, r.pick(SHIRT), 0.02);
        top.tie = vary(r, r.pick(TIE));
        top.open = r.chance(0.15);
        sleeve = "long";
        watch = r.chance(0.5);
        bottom = new Piece("pants", vary(r, suit, 0.05));
        shoes = new Piece("leather", vary(r, r.pick("#141414", "#1e1a16", "#3a2418"), 0.05));
        held = r.chance(0.7) ? new Piece("briefcase", vary(r, r.pick("#1a1a1a", "#2a1e16", "#3a2a1c", "#202630"), 0.06))
                : r.chance(0.5) ? new Piece("phone", null) : null;
        speed = r.range(1.35, 1.65);
    }

    private void ol(Rand r) {
        String bot = r.pick(OL_BOTTOM);
        boolean blazer = r.chance(0.6);
        female = true;
        skin = hex(r.pick(SKIN_JP));
        hairC = hex(r.chance(0.8) ? r.pick(HAIR_DARK) : r.pick(head(HAIR_DYED, 3)));
        hairStyle = r.pick("bob", "long", "pony", "bun", "long");
        glasses = r.chance(0.15);
        mask = r.chance(0.2);
        blush = r.chance(0.4);
        if (blazer) {
            top = new Top("blazer", vary(r, r.chance(0.5) ? bot : r.pick("#2a2c34", "#e8e0d4", "#8a7a68", "#3a3a40"), 0.05));
            top.shirt = vary(r, r.pick(BLOUSE), 0.03);
        } else {
            top = new Top("blouse", vary(r, r.pick(BLOUSE), 0.03));
            top.tuck = true;
        }
        sleeve = blazer ? "long" : r.pick("long", "short");
        bottom = r.chance(0.7) ? new Piece("pencil", vary(r, bot, 0.05)) : new Piece("pants", vary(r, bot, 0.05));
        legwear = new Piece("stocking", hex(r.pick("#d8b8a0", "#c8a088", "#2a2224")));
        shoes = new Piece("heel", vary(r, r.pick("#141414", "#2a1e1a", "#c8a888", "#5a2a2a"), 0.05));
        held = r.chance(0.55) ? new Piece("handbag", vary(r, r.pick("#1a1a1a", "#5a3a2a", "#c8a080", "#8a2a2a", "#e8e0d0"), 0.06))
                : r.chance(0.5) ? new Piece("phone", null) : null;
        speed = r.range(1.3, 1.55);
    }

    private void seifuku(Rand r) {
        boolean summer = r.chance(0.4);
        double[] navy = vary(r, r.pick("#1e2440", "#1a1e30", "#242a44"), 0.04);
        female = true;
        skin = hex(r.pick(SKIN_JP));
        hairC = hex(r.chance(0.85) ? r.pick(HAIR_DARK) : r.pick(head(HAIR_DYED, 2)));
        hairStyle = r.pick("long", "pony", "twin", "bob", "long");
        blush = r.chance(0.5);
        mask = r.chance(0.1);
        top = new Top("sailor", summer ? vary(r, "#f4f4f0", 0.02) : navy);
        top.collar = navy;
        top.stripe = hex("#f2f2ee");
        top.scarf = vary(r, r.pick("#c82a2a", "#a01e2e", "#1e2a5a", "#e8a8b8"), 0.04);
        sleeve = summer ? "short" : "long";
        bottom = new Piece("pleated", navy);
        legwear = r.chance(0.7) ? new Piece("kneesock", r.chance(0.6) ? navy : hex("#f0f0ec")) : new Piece("lowsock", hex("#f0f0ec"));
        shoes = new Piece("loafer", vary(r, "#3a2418", 0.06));
        held = r.chance(0.7) ? new Piece("schoolbag", vary(r, r.pick("#1a1a1c", "#2a2418", "#1e2440"), 0.05)) : new Piece("phone", null);
        speed = r.range(1.4, 1.7);
    }

    private void gakuran(Rand r) {
        female = false;
        skin = hex(r.pick(SKIN_JP));
        hairC = hex(r.chance(0.85) ? r.pick(HAIR_DARK) : r.pick(head(HAIR_DYED, 3)));
        hairStyle = r.pick("short", "messy", "messy", "buzz", "part");
        glasses = r.chance(0.2);
        top = new Top("gakuran", vary(r, r.pick("#121418", "#161a22", "#1a1a1e"), 0.03));
        top.button = hex("#d8b450");
        sleeve = "long";
        bottom = new Piece("pants", vary(r, "#141619", 0.03));
        shoes = new Piece("sneaker", vary(r, r.pick(SNEAKER), 0.03));
        shoes.c2 = vary(r, r.pick(VIVID));
        held = r.chance(0.6) ? new Piece("schoolbag", vary(r, "#1a1a1c", 0.05)) : null;
        back = r.chance(0.3) ? new Piece("backpack", vary(r, r.pick("#1a1a1c", "#2a3446", "#5a5e44"), 0.05)) : null;
        headphones = r.chance(0.25) ? vary(r, r.pick("#1a1a1a", "#f0f0f0", "#c82a2a")) : null;
        speed = r.range(1.45, 1.75);
    }

    private void street(Rand r) {
        female = r.chance(0.45);
        double[] hood = vary(r, r.pick(HOODIE), 0.05);
        skin = hex(r.pick(SKIN_JP));
        hairC = hex(r.chance(0.55) ? r.pick(HAIR_DARK) : r.pick(HAIR_DYED));
        hairStyle = female ? r.pick("long", "bob", "pony", "wolf") : r.pick("messy", "wolf", "short", "part");
        mask = r.chance(0.3);
        blush = female && r.chance(0.4);
        if (r.chance(0.65)) {
            top = new Top("hoodie", hood);
            top.print = r.chance(0.5) ? vary(r, r.pick(VIVID)) : null;
            top.inner = r.chance(0.5);
        } else {
            top = new Top("tee", vary(r, r.pick(TEE), 0.04));
            top.print = r.chance(0.7) ? vary(r, r.pick(VIVID)) : null;
            top.big = true;
        }
        sleeve = "long";
        bottom = r.chance(0.5) ? new Piece("jeans", vary(r, r.pick(JEANS), 0.05)) : new Piece("cargo", vary(r, r.pick(CASUAL_PANTS), 0.05));
        shoes = new Piece("sneaker", vary(r, r.pick(SNEAKER), 0.03));
        shoes.c2 = vary(r, r.pick(VIVID));
        if (r.chance(0.35)) {
            hat = new Piece("cap", vary(r, r.pick("#1a1a1c", "#f2f2ee", "#2a3446", "#5a5e44", "#c82a2a", "#e8d8b8")));
            hat.flag = r.chance(0.3);
        }
        headphones = r.chance(0.3) ? vary(r, r.pick("#1a1a1a", "#f0f0f0", "#d8c8b0", "#3a6cff")) : null;
        held = r.chance(0.4) ? new Piece("phone", null) : null;
        back = r.chance(0.25) ? new Piece("backpack", vary(r, r.pick("#1a1a1c", "#e8e4dc", "#3a4a5e", "#c8b090"), 0.05)) : null;
        speed = r.range(1.35, 1.7);
    }

    private void turista(Rand r) {
        female = r.chance(0.5);
        boolean blond = r.chance(0.35);
        skin = hex(r.pick(SKIN_ALL));
        hairC = hex(blond ? r.pick("#d8a848", "#c89a5a", "#e0b860", "#8a5a32") : r.pick(concat(HAIR_DARK, "#4a3423", "#6a4428")));
        hairStyle = female ? r.pick("long", "pony", "bob") : r.pick("short", "messy", "buzz");
        glasses = r.chance(0.3);
        sunglasses = r.chance(0.25);
        blush = r.chance(0.5);
        top = new Top("tee", vary(r, r.pick(TEE), 0.04));
        top.print = vary(r, r.pick(VIVID));
        sleeve = "short";
        bottom = r.chance(0.6) ? new Piece("shorts", vary(r, r.pick("#a89878", "#7a6a50", "#3a5a8a", "#5a5e44", "#2a2a30"), 0.05))
                : new Piece("jeans", vary(r, r.pick(JEANS), 0.05));
        legwear = new Piece("lowsock", hex("#f0f0ec"));
        shoes = new Piece("sneaker", vary(r, r.pick(SNEAKER), 0.03));
        shoes.c2 = vary(r, r.pick(VIVID));
        hat = r.chance(0.5) ? new Piece("bucket", vary(r, r.pick("#e8dcc0", "#a89878", "#3a4a5e", "#f2f2ee"))) : null;
        back = new Piece("backpack", vary(r, r.pick("#c82a2a", "#2a3446", "#e07a3a", "#3e5a46", "#1a1a1c"), 0.05));
        camera = r.chance(0.6);
        strap = true;
        held = r.chance(0.3) ? new Piece("phone", null) : null;
        speed = r.range(1.0, 1.3);
    }

    private void idoso(Rand r) {
        // every elder quite unlike the next: white, grey or dyed hair (brown, black, the purple of Japanese ladies),
        // cardigan, quilted vest, floral blouse or blazer, sun hat, mask, cane or handbag
        female = r.chance(0.5);
        hairC = hex(female ? r.pick("#c8c4be", "#a8a4a0", "#8a8682", "#4a3428", "#5a3a5e", "#6a4a6a", "#2a2020", "#3a2a22")
                : r.pick("#c8c4be", "#a8a4a0", "#8a8682", "#e0ded8", "#5a5654", "#2a2622"));
        String topKind = female ? r.pick("cardigan", "quilt", "floral", "floral", "cardigan") : r.pick("cardigan", "quilt", "blazer", "polo");
        String[] warm = {"#a89070", "#7a6a58", "#5a4a3e", "#8a8478", "#6a5a6a", "#9a7a6a", "#4a5a4a", "#3a4a5e", "#8a3a3a", "#c8a8b8", "#a8b8c8", "#d8c8a0"};
        switch (topKind) {
            case "floral" -> {
                top = new Top("floral", vary(r, r.pick("#e8d8e0", "#d8e0e8", "#f0e8d0", "#5a4a6a", "#3a4a5a", "#e8c8c0"), 0.04));
                top.print = vary(r, r.pick("#c84a6a", "#e8a040", "#6a8ac8", "#8a5aa0", "#f2f2ee", "#5a9a6a"));
            }
            case "quilt" -> {
                top = new Top("quilt", vary(r, r.pick("#3a4a5e", "#6a3a3a", "#4a5a4a", "#8a7a68", "#2a2a30", "#a85a6a", "#5a4a6a"), 0.05));
                top.shirt = vary(r, r.pick("#f2f0ea", "#d8d0c0", "#e8e0e8", "#c8d0d8", "#8a8478"), 0.03);
            }
            case "blazer" -> {
                top = new Top("blazer", vary(r, r.pick("#5a5040", "#4a4a50", "#6a5a48", "#3a3a44"), 0.05));
                top.shirt = vary(r, r.pick("#f2f0ea", "#d8e0e8"), 0.03);
            }
            case "polo" -> {
                top = new Top("tee", vary(r, r.pick("#d8d0b8", "#a8b8c8", "#c8a8a8", "#8a9a7a", "#f2f2ee"), 0.04));
                top.polo = true;
                top.tuck = true;
            }
            default -> {
                top = new Top("cardigan", vary(r, r.pick(warm), 0.05));
                top.shirt = vary(r, r.pick("#f2f0ea", "#d8d0c0", "#e8e0e8"), 0.03);
            }
        }
        skin = hex(r.pick(SKIN_JP));
        hairStyle = female ? r.pick("perm", "bun", "bob", "short", "perm") : r.pick("thin", "thin", "short", "buzz", "part");
        glasses = r.chance(0.55);
        mask = r.chance(0.35);
        elder = true;
        hunch = r.range(0.05, 0.24);
        sleeve = topKind.equals("polo") ? "short" : "long";
        bottom = female && r.chance(0.6) ? new Piece("longskirt", vary(r, r.pick("#4a4a50", "#5a4a40", "#3a3a44", "#7a6a5a", "#6a4a5a", "#2a3446"), 0.05))
                : new Piece("pants", vary(r, r.pick("#5a5a5e", "#6a6050", "#3a3a40", "#8a8478", "#a89878", "#2a2a30"), 0.05));
        legwear = new Piece("stocking", hex("#c8a890"));
        shoes = new Piece(r.chance(0.3) ? "sneaker" : "leather", vary(r, r.pick("#2a2420", "#4a3a2e", "#1a1a1a", "#e8e4dc"), 0.05));
        shoes.c2 = vary(r, r.pick("#8a8a8a", "#6a8ac8", "#c84a6a"));
        held = r.chance(0.4) ? new Piece("cane", vary(r, r.pick("#5a3a22", "#2a2a2a", "#8a6a4a"), 0.08))
                : r.chance(0.6) ? new Piece("handbag", vary(r, r.pick("#4a3a2e", "#2a2a2a", "#7a5a4a", "#5a4a6a", "#a89070"), 0.05)) : null;
        hat = r.chance(female ? 0.4 : 0.35) ? new Piece("bucket", vary(r, female ? r.pick("#e8dcc0", "#c8b8d0", "#f2f2ee", "#a8b8a0", "#5a4a6a")
                : r.pick("#a89878", "#5a5e44", "#4a4a50", "#e8dcc0"))) : null;
        speed = r.range(0.7, 1.0);
    }

    private void obra(Rand r) {
        female = false;
        skin = hex(r.pick(SKIN_JP));
        hairC = hex(r.pick(HAIR_DARK));
        hairStyle = r.pick("buzz", "short");
        towel = r.chance(0.5);
        mask = r.chance(0.2);
        top = new Top("vest", vary(r, r.pick("#ff8a1e", "#e8f02a", "#ff6a1a"), 0.04));
        top.shirt = vary(r, r.pick("#2a3446", "#5a5e44", "#3a3a40", "#6a5a44"), 0.05);
        top.band = hex("#d8dce0");
        sleeve = "long";
        bottom = new Piece("tobi", vary(r, r.pick("#2a3446", "#5a5e44", "#3a3a40", "#6a5a44", "#4a4a5a"), 0.05));
        shoes = new Piece("boot", vary(r, r.pick("#1a1a1a", "#2a2a2e"), 0.05));
        hat = new Piece("helmet", vary(r, r.pick("#f2f2ee", "#f2c81e", "#f2f2ee", "#3a6cff"), 0.03));
        held = r.chance(0.3) ? new Piece("phone", null) : null;
        speed = r.range(1.2, 1.45);
    }

    private void gyaru(Rand r) {
        female = true;
        skin = hex(r.pick("#e8b890", "#d8a070", "#c88a5a", "#f0d0b0"));
        hairC = hex(r.chance(0.08) ? "#f0e4c8" : r.pick("#d8a848", "#c89a5a", "#e0b050", "#e8a8b8", "#b98a4e", "#a8683a", "#d87a4a"));
        hairStyle = r.pick("long", "twin", "long", "wolf");
        blush = true;
        lashes = true;
        sunglasses = r.chance(0.2);
        top = new Top("crop", vary(r, r.pick("#ff4fb0", "#f2f2ee", "#1a1a1c", "#ffd23a", "#8a4aff", "#ff8ab0", "#2fd6e6"), 0.04));
        top.jacket = r.chance(0.5) ? vary(r, r.pick("#4a6aa0", "#f2f2ee", "#1a1a1c", "#e8a8b8")) : null;
        sleeve = "long";
        bottom = new Piece("mini", vary(r, r.pick("#1a1a1c", "#f2f2ee", "#3a5a8a", "#c82a5a", "#e8d8b8"), 0.05));
        legwear = new Piece(r.chance(0.5) ? "loosesock" : "skin", hex("#f2f2ee"));
        shoes = new Piece("platform", vary(r, r.pick("#1a1a1a", "#f2f2ee", "#c82a5a", "#e8d8b8"), 0.04));
        held = r.chance(0.65) ? new Piece("phone", null) : new Piece("handbag", vary(r, r.pick("#ff4fb0", "#f2f2ee", "#1a1a1a", "#e8c040"), 0.04));
        speed = r.range(1.25, 1.5);
    }
}
