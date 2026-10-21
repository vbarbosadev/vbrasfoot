package br.vbrasfoot.hooks;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;

/** Offline bounded bytecode inventory. A call site is NOT runtime execution or proof of target aliasing. */
public final class MutationSurfaceInventory {
    public enum Kind { DIRECT_FINANCE_WRITE, REFLECTION_WRITE, UNSAFE_WRITE, FIELD_ACCESS_WRITE,
        REFLECTIVE_DISPATCH, METHOD_HANDLE, DYNAMIC_CODE, NATIVE_METHOD, SERIALIZER_METHOD_CANDIDATE }
    public record Site(String member, String method, String descriptor, int instruction, Kind kind, String target) { }
    public record Report(String sha256, int classes, long instructions, List<Site> sites) {
        public Report { sites = List.copyOf(sites); }
        public boolean universalMutationCoverage() { return false; }
    }
    private static final int MAX_CLASSES = 20000, MAX_CLASS_BYTES = 2 * 1024 * 1024, MAX_SITES = 8192;
    private static final long MAX_BYTES = 256L * 1024 * 1024, MAX_INSTRUCTIONS = 8_000_000;
    private static final Set<String> LEDGER = Set.of("dK", "dL", "dM", "dN", "dO", "dP", "dQ", "dR", "dS", "dT", "dU", "dV", "dW", "dX");
    private MutationSurfaceInventory() { }
    public static Report scan(Path artifact) throws Exception {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IOException("Offline inventory must run off EDT");
        if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(artifact)) throw new IOException("Plain archive required");
        String before = digest(artifact); var sites = new ArrayList<Site>(); long[] instructions = {0}, bytes = {0}; int[] classes = {0};
        try (var zip = new ZipFile(artifact.toFile())) {
            var entries = new ArrayList<ZipEntry>(); var iterator = zip.entries();
            while (iterator.hasMoreElements()) { if (entries.size() >= 50000) throw new IOException("Archive entry budget exceeded"); entries.add(iterator.nextElement()); }
            entries.sort(Comparator.comparing(ZipEntry::getName));
            var names = new HashSet<String>();
            for (var entry : entries) {
                if (!names.add(entry.getName())) throw new IOException("Duplicate archive member");
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                if (++classes[0] > MAX_CLASSES) throw new IOException("Class budget exceeded");
                byte[] code;
                try (var input = zip.getInputStream(entry)) { code = input.readNBytes(MAX_CLASS_BYTES + 1); }
                bytes[0] += code.length;
                if (code.length > MAX_CLASS_BYTES || bytes[0] > MAX_BYTES) throw new IOException("Class byte budget exceeded");
                try { inspect(entry.getName(), code, sites, instructions); }
                catch (RuntimeException invalid) { throw new IOException("Inventory refused at member " + entry.getName() + "; no partial report", invalid); }
            }
        }
        if (!before.equals(digest(artifact))) throw new IOException("Artifact changed during inventory");
        return new Report(before, classes[0], instructions[0], sites);
    }
    private static void inspect(String member, byte[] code, List<Site> sites, long[] total) {
        var reader = new ClassReader(code);
        reader.accept(new ClassVisitor(Opcodes.ASM8) {
            private String parent;
            @Override public void visit(int version, int access, String name, String signature, String superclass, String[] interfaces) { parent = superclass; }
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_NATIVE) != 0) add(sites, new Site(member, method, desc, -1, Kind.NATIVE_METHOD, reader.getClassName()));
                // Immediate subclass only: deeper custom inheritance remains an explicit analysis gap.
                if ((method.equals("read") || method.equals("write")) && ("com/esotericsoftware/kryo/Serializer".equals(parent)
                        || "com/esotericsoftware/kryo/serializers/FieldSerializer".equals(parent)))
                    add(sites, new Site(member, method, desc, -1, Kind.SERIALIZER_METHOD_CANDIDATE, parent));
                return new MethodVisitor(Opcodes.ASM8) {
                    int index;
                    private void tick() { index++; if (++total[0] > MAX_INSTRUCTIONS) throw new IllegalArgumentException("Instruction budget exceeded"); }
                    private void site(Kind kind, String target) { add(sites, new Site(member, method, desc, index - 1, kind, target)); }
                    @Override public void visitInsn(int opcode) { tick(); }
                    @Override public void visitIntInsn(int opcode, int value) { tick(); }
                    @Override public void visitVarInsn(int opcode, int variable) { tick(); }
                    @Override public void visitTypeInsn(int opcode, String type) { tick(); }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String type) {
                        tick();
                        if (opcode == Opcodes.PUTFIELD && (owner.equals("best/ah") && (field.equals("nb") && type.equals("J") || field.equals("nx") && type.equals("Lbest/C;"))
                                || owner.equals("best/C") && LEDGER.contains(field) && (type.equals("J") || type.equals("I"))))
                            site(Kind.DIRECT_FINANCE_WRITE, owner + "." + field + ":" + type);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                        tick(); Kind kind = null;
                        if (owner.equals("java/lang/reflect/Field") && name.startsWith("set") && !name.equals("setAccessible")) kind = Kind.REFLECTION_WRITE;
                        else if ((owner.equals("sun/misc/Unsafe") || owner.equals("jdk/internal/misc/Unsafe"))
                                && (name.startsWith("put") || name.startsWith("compareAnd") || name.startsWith("weakCompareAnd") || name.startsWith("getAnd")
                                || name.equals("copyMemory") || name.equals("setMemory"))) kind = Kind.UNSAFE_WRITE;
                        else if (owner.equals("com/esotericsoftware/reflectasm/FieldAccess") && name.startsWith("set")) kind = Kind.FIELD_ACCESS_WRITE;
                        else if (owner.equals("java/lang/reflect/Method") && name.equals("invoke") || owner.equals("java/lang/reflect/Constructor") && name.equals("newInstance")) kind = Kind.REFLECTIVE_DISPATCH;
                        else if (owner.startsWith("java/lang/invoke/")) kind = Kind.METHOD_HANDLE;
                        else if (name.contains("defineClass") || name.equals("defineHiddenClass") || owner.equals("java/lang/System") && (name.equals("load") || name.equals("loadLibrary"))
                                || owner.equals("java/lang/Class") && name.equals("forName")) kind = Kind.DYNAMIC_CODE;
                        if (kind != null) site(kind, owner + "." + name + descriptor);
                    }
                    @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) { tick(); site(Kind.DYNAMIC_CODE, bootstrap.getOwner() + "." + bootstrap.getName() + ":" + name + descriptor); }
                    @Override public void visitJumpInsn(int opcode, Label label) { tick(); }
                    @Override public void visitLdcInsn(Object value) { tick(); }
                    @Override public void visitIincInsn(int variable, int increment) { tick(); }
                    @Override public void visitTableSwitchInsn(int min, int max, Label fallback, Label... labels) { tick(); }
                    @Override public void visitLookupSwitchInsn(Label fallback, int[] keys, Label[] labels) { tick(); }
                    @Override public void visitMultiANewArrayInsn(String descriptor, int dimensions) { tick(); }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    private static void add(List<Site> sites, Site value) { if (sites.size() >= MAX_SITES) throw new IllegalArgumentException("Site budget exceeded"); sites.add(value); }
    private static String digest(Path path) throws Exception {
        var hash = MessageDigest.getInstance("SHA-256"); try (var input = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) != -1) hash.update(buffer, 0, n); }
        return HexFormat.of().formatHex(hash.digest());
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply one explicit original archive; nested libraries must be scanned separately");
        var result = scan(Path.of(args[0]));
        System.out.println("sha256=" + result.sha256() + " classes=" + result.classes() + " instructions=" + result.instructions());
        for (var kind : Kind.values()) System.out.println(kind + "=" + result.sites().stream().filter(site -> site.kind() == kind).count());
        for (var site : result.sites()) System.out.println(site.kind() + " " + site.member() + " " + site.method() + site.descriptor() + " instruction=" + site.instruction() + " -> " + site.target());
        System.out.println("Static call sites only; no target execution/alias analysis, nested archives, deep serializer inheritance or universal mutation coverage.");
    }
}
