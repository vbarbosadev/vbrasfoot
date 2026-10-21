package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Selected original CachedField setter sites only. Does not instrument JDK reflection/Unsafe globally. */
public final class KryoFinanceMutationTransformer {
    private static final String PREFIX = "com.esotericsoftware.kryo.serializers.";
    private static final Map<String, String> HASHES = Map.ofEntries(
        Map.entry("UnsafeCacheFields$UnsafeIntField", "eb98fe842bb3c2eff3be5795c7176861357ef67c637935f81911a80ff9d7b0db"),
        Map.entry("UnsafeCacheFields$UnsafeLongField", "9154d0a3ae45dca3bc7653f513da1cf5afc48550993b3e83023dcaaf8ae8e4c9"),
        Map.entry("AsmCacheFields$AsmIntField", "1289a7a51961cb9f8a57b901c0e3542f6e28fc32c93b9182927905283f5c6a59"),
        Map.entry("AsmCacheFields$AsmLongField", "3921cda131f47bc30e9d91ba706dc14052539418376f5e411fddd8a76a7b76e8"),
        Map.entry("ObjectField$ObjectIntField", "b16959daf336c2e4da2ed1fb5cf5d6df363fc2da78686e0699b5af64f87cbc8b"),
        Map.entry("ObjectField$ObjectLongField", "ed146c941f8b39624568fd8a99f8d08469ab8d7139c5ef97abe51672dd6e3454"),
        Map.entry("ObjectField", "817f50e574b98a9d1d97427202a31243038e58a7dab47d0c29a24c150f8f7e20"),
        Map.entry("UnsafeCacheFields$UnsafeObjectField", "6b371e91aff4b2237500251b4661a4f6dba4705313252a935052af581e0287f7"),
        Map.entry("AsmCacheFields$AsmObjectField", "8d0d356a82afbb0ecf386dc2a068b878094bf0569a9573b01c4125d8d7786df5"));
    private KryoFinanceMutationTransformer() { }
    public static byte[] transform(String name, byte[] original) throws Exception {
        String simple = name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : "";
        if (!Objects.equals(HASHES.get(simple), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original))))
            throw new IOException("Kryo mutation fingerprint changed; refused");
        return addToVerified(name, original);
    }
    /** Only called by fingerprint-checked original emission transformers, or transform above. */
    static byte[] addToVerified(String name, byte[] input) throws IOException {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Kryo mutation probe requires Java17/21");
        if (!name.startsWith(PREFIX) || !HASHES.containsKey(name.substring(PREFIX.length()))) throw new IOException("Unknown cached-field class");
        var reader = new ClassReader(input); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); int[] counts = new int[3];
        if (!reader.getClassName().equals(name.replace('.', '/'))) throw new IOException("Cached-field identity mismatch");
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                var delegate = super.visitMethod(access, method, desc, signature, exceptions);
                int kind = method.equals("read") && desc.equals("(Lcom/esotericsoftware/kryo/io/Input;Ljava/lang/Object;)V") ? 0
                        : method.equals("copy") && desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)V") ? 1
                        : method.equals("setField") && desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)V") ? 2 : -1;
                if (kind < 0) return delegate;
                return new MethodVisitor(Opcodes.ASM8, delegate) {
                    @Override public void visitMethodInsn(int op, String owner, String method, String descriptor, boolean itf) {
                        boolean setter = op == Opcodes.INVOKEVIRTUAL && (
                            owner.equals("sun/misc/Unsafe") && (method.equals("putInt") && descriptor.equals("(Ljava/lang/Object;JI)V")
                                || method.equals("putLong") && descriptor.equals("(Ljava/lang/Object;JJ)V") || method.equals("putObject") && descriptor.equals("(Ljava/lang/Object;JLjava/lang/Object;)V"))
                            || owner.equals("java/lang/reflect/Field") && (method.equals("setInt") && descriptor.equals("(Ljava/lang/Object;I)V")
                                || method.equals("setLong") && descriptor.equals("(Ljava/lang/Object;J)V") || method.equals("set") && descriptor.equals("(Ljava/lang/Object;Ljava/lang/Object;)V"))
                            || owner.equals("com/esotericsoftware/reflectasm/FieldAccess") && (method.equals("setInt") && descriptor.equals("(Ljava/lang/Object;II)V")
                                || method.equals("setLong") && descriptor.equals("(Ljava/lang/Object;IJ)V") || method.equals("set") && descriptor.equals("(Ljava/lang/Object;ILjava/lang/Object;)V")));
                        if (!setter) { super.visitMethodInsn(op, owner, method, descriptor, itf); return; }
                        counts[kind]++;
                        // Target is arg2 for read/copy, arg1 for setField. Actual setter operands remain untouched.
                        super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, kind == 2 ? 1 : 2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeKryoMutationStarting", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                        super.visitVarInsn(Opcodes.ISTORE, 12);
                        super.visitMethodInsn(op, owner, method, descriptor, itf);
                        super.visitVarInsn(Opcodes.ILOAD, 12);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeKryoMutationFinished", "(Z)V", false);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        boolean numeric = name.endsWith("IntField") || name.endsWith("LongField");
        if (counts[0] != (numeric ? 2 : 0) || counts[1] != 1 || counts[2] != (numeric ? 0 : 1)) throw new IOException("Kryo field setter inventory changed; refused");
        return writer.toByteArray();
    }
}
