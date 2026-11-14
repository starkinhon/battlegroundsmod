import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

// Loose block checker for the structure templates (.nbt): lists every block that needs something under it (doors,
// pressure plates, carpets, flowers, rails, standing torches and signs, beds...) with nothing there, and hanging lanterns
// with nothing above. Run: java tools/structures/LooseBlocks.java <file.nbt>...
public class LooseBlocks {
    record Block(int x, int y, int z, String name, Map<String, String> props) {
    }

    public static void main(String[] args) throws IOException {
        int bad = 0;
        for (String file : args) {
            Map<String, Object> root;
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
                in.readByte();
                in.readUTF();
                root = compound(in);
            }
            List<Block> palette = new ArrayList<>();
            List<?> pal = (List<?>) root.get("palette");
            Map<String, Block> at = new HashMap<>();
            List<Block> blocks = new ArrayList<>();
            for (Object o : (List<?>) root.get("blocks")) {
                @SuppressWarnings("unchecked") Map<String, Object> b = (Map<String, Object>) o;
                int[] pos = (int[]) b.get("pos");
                @SuppressWarnings("unchecked") Map<String, Object> st = (Map<String, Object>) pal.get((Integer) b.get("state"));
                Map<String, String> props = new HashMap<>();
                if (st.get("Properties") instanceof Map<?, ?> p) {
                    p.forEach((k, v) -> props.put((String) k, (String) v));
                }
                Block blk = new Block(pos[0], pos[1], pos[2], ((String) st.get("Name")).replace("minecraft:", ""), props);
                blocks.add(blk);
                at.put(pos[0] + "," + pos[1] + "," + pos[2], blk);
            }
            for (Block b : blocks) {
                String why = loose(b, at);
                if (why != null) {
                    System.out.println(file + ": " + b.name() + b.props() + " at " + b.x() + " " + b.y() + " " + b.z() + " -> " + why);
                    bad++;
                }
            }
        }
        System.out.println(bad + " loose");
    }

    static boolean empty(Map<String, Block> at, int x, int y, int z) {
        Block b = at.get(x + "," + y + "," + z);
        return b == null || b.name().equals("air") || b.name().equals("structure_void") || b.name().equals("cave_air");
    }

    static boolean needsFloor(Block b) {
        String n = b.name();
        if (n.endsWith("_door")) {
            return "lower".equals(b.props().get("half"));
        }
        if (n.endsWith("_wall_torch") || n.endsWith("_wall_sign") || n.endsWith("_wall_banner") || n.contains("wall_head")) {
            return false;
        }
        if (n.equals("lantern") || n.equals("soul_lantern")) {
            return !"true".equals(b.props().get("hanging"));
        }
        if (n.endsWith("_button") || n.equals("lever")) {
            return "floor".equals(b.props().get("face"));
        }
        return n.endsWith("_pressure_plate") || n.endsWith("_carpet") || n.equals("torch") || n.equals("soul_torch")
                || n.endsWith("_sign") || n.endsWith("_banner") || n.endsWith("rail") || n.endsWith("_bed") || n.equals("flower_pot")
                || n.startsWith("potted_") || n.endsWith("_sapling") || n.equals("short_grass") || n.equals("fern") || n.endsWith("_tulip")
                || n.equals("dandelion") || n.equals("poppy") || n.equals("snow") || n.equals("redstone_wire") || n.endsWith("candle")
                || n.equals("repeater") || n.equals("comparator") || n.equals("bamboo") || n.equals("sugar_cane") || n.equals("cactus");
    }

    static String loose(Block b, Map<String, Block> at) {
        if (b.name().endsWith("_door") && "upper".equals(b.props().get("half"))) {
            Block below = at.get(b.x() + "," + (b.y() - 1) + "," + b.z());
            return below == null || !below.name().equals(b.name()) ? "upper door half with no lower half" : null;
        }
        if (b.name().endsWith("_door") && "lower".equals(b.props().get("half"))) {
            Block above = at.get(b.x() + "," + (b.y() + 1) + "," + b.z());
            if (above == null || !above.name().equals(b.name())) {
                return "lower door half with no upper half";
            }
        }
        if ((b.name().equals("lantern") || b.name().equals("soul_lantern")) && "true".equals(b.props().get("hanging"))
                && empty(at, b.x(), b.y() + 1, b.z())) {
            return "hanging with nothing above";
        }
        if (System.getenv("DUMP") != null && b.name().matches(System.getenv("DUMP"))) {
            Block u = at.get(b.x() + "," + (b.y() - 1) + "," + b.z());
            return "below: " + (u == null ? "none" : u.name() + u.props());
        }
        if (needsFloor(b) && empty(at, b.x(), b.y() - 1, b.z())) {
            return "nothing under it";
        }
        return null;
    }

    // ---- NBT ----
    static Object tag(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                yield a;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int t = in.readByte(), n = in.readInt();
                List<Object> l = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    l.add(tag(in, t));
                }
                if (t == 3) {
                    int[] a = new int[n];
                    for (int i = 0; i < n; i++) {
                        a[i] = (Integer) l.get(i);
                    }
                    yield a;
                }
                yield l;
            }
            case 10 -> compound(in);
            case 11 -> {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                yield a;
            }
            case 12 -> {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                yield a;
            }
            default -> throw new IOException("tag " + type);
        };
    }

    static Map<String, Object> compound(DataInputStream in) throws IOException {
        Map<String, Object> m = new HashMap<>();
        while (true) {
            int t = in.readByte();
            if (t == 0) {
                return m;
            }
            String k = in.readUTF();
            m.put(k, tag(in, t));
        }
    }
}
