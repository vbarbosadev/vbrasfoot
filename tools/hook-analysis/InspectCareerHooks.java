import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipFile;

/** Read-only inventory/fingerprints of local class bytes; never loads game classes. */
public final class InspectCareerHooks {
    private static final String GAME_SHA = "2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c";

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void attributes(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        for (int i = 0; i < count; i++) { input.readUnsignedShort(); input.skipNBytes(Integer.toUnsignedLong(input.readInt())); }
    }

    private static boolean selected(String name, String descriptor) {
        return ((name.equals("ae") || name.equals("ad")) && descriptor.equals("(Ljava/lang/String;)V"))
                || (name.equals("c") && descriptor.equals("(Ljava/lang/String;Z)Z"))
                || (name.equals("vK") && descriptor.equals("()V"))
                || (name.equals("a") && descriptor.equals("(Lbest/f;)V"))
                || (name.equals("<init>") && descriptor.equals("()V"));
    }

    public static void main(String[] args) throws Exception {
        Path executable = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output)) throw new IOException("Output must be new");
        byte[] executableBytes = Files.readAllBytes(executable);
        if (!GAME_SHA.equals(hash(executableBytes))) throw new IOException("Unknown game identity: refused before class inspection");
        byte[] bytes;
        try (ZipFile zip = new ZipFile(executable.toFile())) {
            var entry = zip.getEntry("c/a.class");
            if (entry == null || entry.getSize() < 0 || entry.getSize() > 1024 * 1024) throw new IOException("Career I/O class missing/oversized");
            try (var stream = zip.getInputStream(entry)) { bytes = stream.readAllBytes(); }
        }
        Properties report = new Properties();
        report.setProperty("executable.sha256", GAME_SHA);
        report.setProperty("class.entry", "c/a.class");
        report.setProperty("class.sha256", hash(bytes));
        report.setProperty("coverage", "Static identities/signatures/Code fingerprints only; no game execution or hooks");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0xcafebabe) throw new IOException("Not a classfile");
            input.readUnsignedShort();
            report.setProperty("class.major", Integer.toString(input.readUnsignedShort()));
            String[] utf8 = new String[input.readUnsignedShort()];
            for (int i = 1; i < utf8.length; i++) {
                switch (input.readUnsignedByte()) {
                    case 1 -> utf8[i] = input.readUTF();
                    case 3, 4 -> input.skipNBytes(4);
                    case 5, 6 -> { input.skipNBytes(8); i++; }
                    case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unknown constant pool tag");
                }
            }
            input.skipNBytes(6);
            input.skipNBytes(input.readUnsignedShort() * 2L);
            int fields = input.readUnsignedShort();
            for (int i = 0; i < fields; i++) { input.skipNBytes(6); attributes(input); }
            int methods = input.readUnsignedShort();
            int selected = 0;
            for (int i = 0; i < methods; i++) {
                int access = input.readUnsignedShort();
                String name = utf8[input.readUnsignedShort()], descriptor = utf8[input.readUnsignedShort()];
                int count = input.readUnsignedShort();
                for (int j = 0; j < count; j++) {
                    String attribute = utf8[input.readUnsignedShort()];
                    int length = input.readInt();
                    if (length < 0 || length > bytes.length) throw new IOException("Invalid method attribute length");
                    byte[] data = input.readNBytes(length);
                    if (data.length != length) throw new IOException("Truncated classfile");
                    if (!attribute.equals("Code") || !selected(name, descriptor)) continue;
                    String key = "method." + name + descriptor;
                    report.setProperty(key + ".access", Integer.toString(access));
                    report.setProperty(key + ".codeAttribute.sha256", hash(data));
                    try (DataInputStream code = new DataInputStream(new ByteArrayInputStream(data))) {
                        report.setProperty(key + ".maxStack", Integer.toString(code.readUnsignedShort()));
                        report.setProperty(key + ".maxLocals", Integer.toString(code.readUnsignedShort()));
                        int codeLength = code.readInt();
                        if (codeLength < 0 || codeLength > data.length) throw new IOException("Invalid Code length");
                        byte[] instructions = code.readNBytes(codeLength);
                        if (instructions.length != codeLength) throw new IOException("Truncated Code");
                        report.setProperty(key + ".instructions.sha256", hash(instructions));
                        report.setProperty(key + ".instructions.length", Integer.toString(codeLength));
                        report.setProperty(key + ".exceptionHandlers", Integer.toString(code.readUnsignedShort()));
                    }
                    selected++;
                }
            }
            if (selected != 6) throw new IOException("Expected six exact career-method candidates, got " + selected);
            report.setProperty("methods.selected", Integer.toString(selected));
        }
        if (!GAME_SHA.equals(hash(Files.readAllBytes(executable)))) throw new IOException("Input changed during inspection");
        try (var writer = Files.newBufferedWriter(output, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            report.store(writer, "Brasfoot22-23 static hook candidates; not runtime corroboration");
        }
        report.stringPropertyNames().stream().sorted().forEach(key -> System.out.println(key + "=" + report.getProperty(key)));
    }
}
