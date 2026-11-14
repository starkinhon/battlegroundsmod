import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// Swaps single blocks of a structure template (.nbt) in place, keeping everything else byte for byte the same kind.
// Run: java tools/structures/StructFix.java <file.nbt> "x,y,z=minecraft:spruce_slab[type=bottom,waterlogged=false]" ...
public class StructFix {
    record Tag(int type, Object value, int elemType) {
    }

    public static void main(String[] args) throws IOException {
        String file = args[0];
        String rootName;
        Tag root;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            in.readByte();
            rootName = in.readUTF();
            root = read(in, 10);
        }
        Map<String, Tag> top = map(root);
        @SuppressWarnings("unchecked") List<Tag> palette = (List<Tag>) top.get("palette").value();
        @SuppressWarnings("unchecked") List<Tag> blocks = (List<Tag>) top.get("blocks").value();
        for (int a = 1; a < args.length; a++) {
            String[] kv = args[a].split("=", 2);
            String[] p = kv[0].split(",");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            int state = stateIndex(palette, kv[1]);
            boolean found = false;
            for (Tag b : blocks) {
                Map<String, Tag> m = map(b);
                @SuppressWarnings("unchecked") List<Tag> pos = (List<Tag>) m.get("pos").value();
                if ((Integer) pos.get(0).value() == x && (Integer) pos.get(1).value() == y && (Integer) pos.get(2).value() == z) {
                    m.put("state", new Tag(3, state, 0));
                    found = true;
                }
            }
            System.out.println((found ? "set " : "NOT FOUND ") + kv[0] + " -> " + kv[1]);
        }
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            out.writeByte(10);
            out.writeUTF(rootName);
            write(out, root);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Tag> map(Tag t) {
        return (Map<String, Tag>) t.value();
    }

    // "minecraft:name[a=b,c=d]": its palette index, added at the end when new.
    static int stateIndex(List<Tag> palette, String spec) {
        String name = spec.contains("[") ? spec.substring(0, spec.indexOf('[')) : spec;
        Map<String, String> props = new LinkedHashMap<>();
        if (spec.contains("[")) {
            for (String kv : spec.substring(spec.indexOf('[') + 1, spec.length() - 1).split(",")) {
                String[] p = kv.split("=");
                props.put(p[0], p[1]);
            }
        }
        for (int i = 0; i < palette.size(); i++) {
            Map<String, Tag> e = map(palette.get(i));
            if (!name.equals(e.get("Name").value())) {
                continue;
            }
            Map<String, String> have = new LinkedHashMap<>();
            if (e.containsKey("Properties")) {
                map(e.get("Properties")).forEach((k, v) -> have.put(k, (String) v.value()));
            }
            if (have.equals(props)) {
                return i;
            }
        }
        Map<String, Tag> e = new LinkedHashMap<>();
        e.put("Name", new Tag(8, name, 0));
        if (!props.isEmpty()) {
            Map<String, Tag> pm = new LinkedHashMap<>();
            props.forEach((k, v) -> pm.put(k, new Tag(8, v, 0)));
            e.put("Properties", new Tag(10, pm, 0));
        }
        palette.add(new Tag(10, e, 0));
        return palette.size() - 1;
    }

    static Tag read(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case 1 -> new Tag(1, in.readByte(), 0);
            case 2 -> new Tag(2, in.readShort(), 0);
            case 3 -> new Tag(3, in.readInt(), 0);
            case 4 -> new Tag(4, in.readLong(), 0);
            case 5 -> new Tag(5, in.readFloat(), 0);
            case 6 -> new Tag(6, in.readDouble(), 0);
            case 7 -> {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                yield new Tag(7, a, 0);
            }
            case 8 -> new Tag(8, in.readUTF(), 0);
            case 9 -> {
                int t = in.readByte(), n = in.readInt();
                List<Tag> l = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    l.add(read(in, t));
                }
                yield new Tag(9, l, t);
            }
            case 10 -> {
                Map<String, Tag> m = new LinkedHashMap<>();
                while (true) {
                    int t = in.readByte();
                    if (t == 0) {
                        break;
                    }
                    String k = in.readUTF();
                    m.put(k, read(in, t));
                }
                yield new Tag(10, m, 0);
            }
            case 11 -> {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                yield new Tag(11, a, 0);
            }
            case 12 -> {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                yield new Tag(12, a, 0);
            }
            default -> throw new IOException("tag " + type);
        };
    }

    static void write(DataOutputStream out, Tag t) throws IOException {
        switch (t.type()) {
            case 1 -> out.writeByte((Byte) t.value());
            case 2 -> out.writeShort((Short) t.value());
            case 3 -> out.writeInt((Integer) t.value());
            case 4 -> out.writeLong((Long) t.value());
            case 5 -> out.writeFloat((Float) t.value());
            case 6 -> out.writeDouble((Double) t.value());
            case 7 -> {
                byte[] a = (byte[]) t.value();
                out.writeInt(a.length);
                out.write(a);
            }
            case 8 -> out.writeUTF((String) t.value());
            case 9 -> {
                @SuppressWarnings("unchecked") List<Tag> l = (List<Tag>) t.value();
                out.writeByte(t.elemType());
                out.writeInt(l.size());
                for (Tag e : l) {
                    write(out, e);
                }
            }
            case 10 -> {
                for (Map.Entry<String, Tag> e : map(t).entrySet()) {
                    out.writeByte(e.getValue().type());
                    out.writeUTF(e.getKey());
                    write(out, e.getValue());
                }
                out.writeByte(0);
            }
            case 11 -> {
                int[] a = (int[]) t.value();
                out.writeInt(a.length);
                for (int v : a) {
                    out.writeInt(v);
                }
            }
            case 12 -> {
                long[] a = (long[]) t.value();
                out.writeInt(a.length);
                for (long v : a) {
                    out.writeLong(v);
                }
            }
            default -> throw new IOException("tag " + t.type());
        }
    }
}
