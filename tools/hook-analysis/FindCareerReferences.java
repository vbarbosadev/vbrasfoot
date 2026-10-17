import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Exact JVM invoke/putstatic references, not string guesses or decompilation. */
public final class FindCareerReferences {
    private static final String GAME_SHA = "2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c";
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void attributes(DataInputStream in) throws IOException {
        for (int count = in.readUnsignedShort(); count > 0; count--) { in.readUnsignedShort(); in.skipNBytes(Integer.toUnsignedLong(in.readInt())); }
    }
    private static int integer(byte[] b, int p) { return ((b[p] & 255) << 24) | ((b[p+1] & 255) << 16) | ((b[p+2] & 255) << 8) | (b[p+3] & 255); }
    private static int length(byte[] b, int p) throws IOException {
        int op = b[p] & 255;
        if (op == 170 || op == 171) {
            int data = p + 1 + ((4 - ((p + 1) & 3)) & 3);
            if (data + 8 > b.length) throw new IOException("Truncated switch");
            long size;
            if (op == 170) {
                if (data + 12 > b.length) throw new IOException("Truncated tableswitch");
                long count = (long)integer(b, data + 8) - integer(b, data + 4) + 1;
                if (count < 0) throw new IOException("Invalid tableswitch");
                size = data - p + 12L + 4L * count;
            } else {
                int count = integer(b, data + 4);
                if (count < 0) throw new IOException("Invalid lookupswitch");
                size = data - p + 8L + 8L * count;
            }
            if (size > b.length - p) throw new IOException("Switch exceeds Code");
            return (int)size;
        }
        if (op == 196) {
            if (p + 1 >= b.length) throw new IOException("Truncated wide");
            return (b[p+1] & 255) == 132 ? 6 : 4;
        }
        if (op == 185 || op == 186 || op == 200 || op == 201) return 5;
        if (op == 197) return 4;
        if (op == 17 || op == 19 || op == 20 || op == 132 || (op >= 153 && op <= 168)
                || (op >= 178 && op <= 184) || op == 187 || op == 189 || op == 192 || op == 193 || op == 198 || op == 199) return 3;
        if (op == 16 || op == 18 || (op >= 21 && op <= 25) || (op >= 54 && op <= 58) || op == 169 || op == 188) return 2;
        if (op > 201) throw new IOException("Reserved opcode");
        return 1;
    }

    static List<String> inspect(byte[] bytes, Set<String> selectedTargets) throws Exception {
        List<String> result = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 0xcafebabe) throw new IOException("Invalid classfile");
            in.skipNBytes(4);
            Object[] pool = new Object[in.readUnsignedShort()];
            for (int i = 1; i < pool.length; i++) {
                switch (in.readUnsignedByte()) {
                    case 1 -> pool[i] = in.readUTF();
                    case 7 -> pool[i] = in.readUnsignedShort();
                    case 9, 10, 11, 12 -> pool[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()};
                    case 3, 4, 17, 18 -> in.skipNBytes(4);
                    case 5, 6 -> { in.skipNBytes(8); i++; }
                    case 8, 16, 19, 20 -> in.skipNBytes(2);
                    case 15 -> in.skipNBytes(3);
                    default -> throw new IOException("Unknown pool tag");
                }
            }
            in.readUnsignedShort();
            String owner = (String)pool[(Integer)pool[in.readUnsignedShort()]];
            in.readUnsignedShort();
            in.skipNBytes(2L * in.readUnsignedShort());
            for (int fields = in.readUnsignedShort(); fields > 0; fields--) { in.skipNBytes(6); attributes(in); }
            for (int methods = in.readUnsignedShort(); methods > 0; methods--) {
                in.readUnsignedShort();
                String method = (String)pool[in.readUnsignedShort()], descriptor = (String)pool[in.readUnsignedShort()];
                for (int attrs = in.readUnsignedShort(); attrs > 0; attrs--) {
                    String name = (String)pool[in.readUnsignedShort()];
                    int size = in.readInt();
                    if (size < 0 || size > bytes.length) throw new IOException("Invalid attribute length");
                    byte[] attribute = in.readNBytes(size);
                    if (attribute.length != size) throw new IOException("Truncated classfile");
                    if (!name.equals("Code")) continue;
                    try (DataInputStream code = new DataInputStream(new ByteArrayInputStream(attribute))) {
                        code.skipNBytes(4);
                        int codeLength = code.readInt();
                        if (codeLength < 0 || codeLength > attribute.length) throw new IOException("Invalid Code length");
                        byte[] instructions = code.readNBytes(codeLength);
                        if (instructions.length != codeLength) throw new IOException("Truncated Code");
                        int previous = -1;
                        for (int p = 0; p < instructions.length;) {
                            int op = instructions[p] & 255, step = length(instructions, p);
                            if (p + step > instructions.length) throw new IOException("Truncated instruction");
                            if (op == 179 || (op >= 182 && op <= 185)) {
                                int index = ((instructions[p+1] & 255) << 8) | (instructions[p+2] & 255);
                                int[] ref = (int[])pool[index], signature = (int[])pool[ref[1]];
                                String targetOwner = (String)pool[(Integer)pool[ref[0]]];
                                String targetName = (String)pool[signature[0]], targetDescriptor = (String)pool[signature[1]];
                                boolean career = targetOwner.equals("c/a") && ((op == 179 && (targetName.equals("SR") || targetName.equals("afQ")))
                                        || (op != 179 && (targetName.equals("c") || targetName.equals("ae") || targetName.equals("a") || targetName.equals("<init>"))));
                                boolean window = targetOwner.equals("best/n") && ((op == 179 && targetName.equals("cC"))
                                        || (op != 179 && targetName.equals("a") && targetDescriptor.equals("(Ljavax/swing/JFrame;)V")));
                                boolean close = op != 179 && ((targetName.equals("dispose") && targetDescriptor.equals("()V"))
                                        || (targetOwner.equals("java/lang/System") && targetName.equals("exit") && targetDescriptor.equals("(I)V")));
                                 if (career || window || close || selectedTargets.contains(targetOwner + "." + targetName + targetDescriptor)) {
                                    result.add((op == 179 ? "putstatic" : "invoke") + "\t" + targetOwner + "." + targetName + targetDescriptor + "\t" + owner + "." + method + descriptor
                                            + "\t" + p + "\tpreviousOpcode=" + previous + "\tclass.sha256=" + hash(bytes) + "\tcode.sha256=" + hash(attribute));
                                }
                            }
                            previous = op; p += step;
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path executable = Path.of(args[0]).toRealPath(), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IOException("Output must be new");
        if (!hash(Files.readAllBytes(executable)).equals(GAME_SHA)) throw new IOException("Unknown executable");
        int classes = 0;
        Set<String> selectedTargets = new LinkedHashSet<>();
        for (int i = 2; i < args.length; i++) {
            String target = args[i];
            if (!target.matches("[A-Za-z0-9_$/]+\\.[A-Za-z0-9_$<>]+\\(.*\\).+")) throw new IOException("Use exact owner.member(descriptor): " + target);
            selectedTargets.add(target);
        }
        List<String> findings = new ArrayList<>();
        try (ZipFile zip = new ZipFile(executable.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                if (entry.getSize() < 0 || entry.getSize() > 1024 * 1024) throw new IOException("Oversized class member");
                try (var stream = zip.getInputStream(entry)) { findings.addAll(inspect(stream.readAllBytes(), selectedTargets)); }
                classes++;
            }
        }
        if (!hash(Files.readAllBytes(executable)).equals(GAME_SHA)) throw new IOException("Input changed");
        Collections.sort(findings);
        try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) {
            writer.write("# executable.sha256=" + GAME_SHA + "; classes=" + classes + "; static references only; no runtime corroboration\n");
            writer.write("# selectedTargets=" + String.join(",", selectedTargets) + "\n");
            for (String finding : findings) { writer.write(finding); writer.newLine(); }
        }
        System.out.println("classes=" + classes + " references=" + findings.size());
        findings.forEach(System.out::println);
    }
}
