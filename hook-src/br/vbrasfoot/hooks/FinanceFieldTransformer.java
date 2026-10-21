package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Numeric emission callbacks plus fingerprint-verified selected read/copy setters; no schema changes. */
public final class FinanceFieldTransformer {
    private static final String PREFIX = "com.esotericsoftware.kryo.serializers.";
    private static final Map<String, String> HASHES = Map.of(
        "UnsafeCacheFields$UnsafeIntField", "eb98fe842bb3c2eff3be5795c7176861357ef67c637935f81911a80ff9d7b0db",
        "UnsafeCacheFields$UnsafeLongField", "9154d0a3ae45dca3bc7653f513da1cf5afc48550993b3e83023dcaaf8ae8e4c9",
        "AsmCacheFields$AsmIntField", "1289a7a51961cb9f8a57b901c0e3542f6e28fc32c93b9182927905283f5c6a59",
        "AsmCacheFields$AsmLongField", "3921cda131f47bc30e9d91ba706dc14052539418376f5e411fddd8a76a7b76e8",
        "ObjectField$ObjectIntField", "b16959daf336c2e4da2ed1fb5cf5d6df363fc2da78686e0699b5af64f87cbc8b",
        "ObjectField$ObjectLongField", "ed146c941f8b39624568fd8a99f8d08469ab8d7139c5ef97abe51672dd6e3454");
    private FinanceFieldTransformer() { }
    public static byte[] transform(String name, byte[] original) throws Exception {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Field emission probe requires Java17/21");
        String simple = name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : "";
        if (!Objects.equals(HASHES.get(simple), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original))))
            throw new IOException("Numeric cached-field fingerprint changed; refused");
        boolean wide = simple.endsWith("LongField");
        int[] calls = new int[2];
        var reader = new ClassReader(original); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                var delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                if (!method.equals("write") || !descriptor.equals("(Lcom/esotericsoftware/kryo/io/Output;Ljava/lang/Object;)V")) return delegate;
                return new MethodVisitor(Opcodes.ASM8, delegate) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        boolean variable = desc.equals(wide ? "(JZ)I" : "(IZ)I");
                        boolean fixed = desc.equals(wide ? "(J)V" : "(I)V");
                        if (opcode != Opcodes.INVOKEVIRTUAL || !owner.equals("com/esotericsoftware/kryo/io/Output")
                                || !method.equals(wide ? "writeLong" : "writeInt") || !(variable || fixed)) {
                            super.visitMethodInsn(opcode, owner, method, desc, itf); return;
                        }
                        calls[variable ? 0 : 1]++;
                        // Scratch locals are live only inside this straight-line instruction replacement.
                        // No new branch/handler/frame and no change to the original call/return value.
                        if (variable) super.visitVarInsn(Opcodes.ISTORE, 12);
                        super.visitVarInsn(wide ? Opcodes.LSTORE : Opcodes.ISTORE, 10);
                        super.visitVarInsn(Opcodes.ASTORE, 9);
                        super.visitVarInsn(Opcodes.ALOAD, 9);
                        super.visitVarInsn(wide ? Opcodes.LLOAD : Opcodes.ILOAD, 10);
                        if (variable) super.visitVarInsn(Opcodes.ILOAD, 12);
                        super.visitMethodInsn(opcode, owner, method, desc, itf);
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitVarInsn(Opcodes.ALOAD, 9);
                        super.visitVarInsn(wide ? Opcodes.LLOAD : Opcodes.ILOAD, 10);
                        if (!wide) super.visitInsn(Opcodes.I2L);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeFieldEmitted",
                                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;J)V", false);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        if (calls[0] != 1 || calls[1] != 1) throw new IOException("Numeric Output call inventory changed; refused");
        return KryoFinanceMutationTransformer.addToVerified(name, writer.toByteArray());
    }
}
