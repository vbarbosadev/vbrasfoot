package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import jdk.internal.org.objectweb.asm.*;

/** Original scalar reference markers and completed serializer bodies. No replacement resolver or byte changes. */
public final class KryoReferenceTableTransformer {
    private KryoReferenceTableTransformer() { }
    public static byte[] transform(byte[] original) throws Exception {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Reference table probe requires Java17/21");
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)).equals("98e2fa40c12678b7f66faee59ed7c634d9af3e310ae067ed2da67a313115bac5"))
            throw new IOException("Original Kryo fingerprint changed; refused");
        int[] counts = new int[4]; var reader = new ClassReader(original); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                var delegate = super.visitMethod(access, method, desc, signature, exceptions);
                boolean reference = method.equals("writeReferenceOrNull") && desc.equals("(Lcom/esotericsoftware/kryo/io/Output;Ljava/lang/Object;Z)Z");
                boolean write = method.equals("writeObject") || method.equals("writeObjectOrNull") || method.equals("writeClassAndObject");
                boolean reset = method.equals("reset") && desc.equals("()V");
                if (!reference && !write && !reset) return delegate;
                return new MethodVisitor(Opcodes.ASM8, delegate) {
                    @Override public void visitCode() {
                        super.visitCode();
                        if (reset) { counts[2]++; super.visitVarInsn(Opcodes.ALOAD, 0); super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeReferenceTableReset", "(Ljava/lang/Object;)V", false); }
                    }
                    @Override public void visitMethodInsn(int op, String owner, String name, String descriptor, boolean itf) {
                        if (reference && op == Opcodes.INVOKEVIRTUAL && owner.equals("com/esotericsoftware/kryo/io/Output") && name.equals("writeVarInt") && descriptor.equals("(IZ)I")) {
                            int site = counts[0]++;
                            // null/NO_REF sites are not identities of a non-null ledger; only existing/new are recorded.
                            if (site >= 2) {
                                super.visitVarInsn(Opcodes.ISTORE, 10); super.visitVarInsn(Opcodes.ISTORE, 8); super.visitVarInsn(Opcodes.ASTORE, 9);
                                super.visitVarInsn(Opcodes.ALOAD, 9); super.visitVarInsn(Opcodes.ILOAD, 8); super.visitVarInsn(Opcodes.ILOAD, 10);
                                super.visitMethodInsn(op, owner, name, descriptor, itf);
                                super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, 9); super.visitVarInsn(Opcodes.ALOAD, 2);
                                super.visitLdcInsn(site); super.visitVarInsn(Opcodes.ILOAD, 4); super.visitVarInsn(Opcodes.ILOAD, 8);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeReferenceMarker", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;III)V", false); return;
                            }
                        }
                        if (write && op == Opcodes.INVOKEVIRTUAL && owner.equals("com/esotericsoftware/kryo/Serializer") && name.equals("write")
                                && descriptor.equals("(Lcom/esotericsoftware/kryo/Kryo;Lcom/esotericsoftware/kryo/io/Output;Ljava/lang/Object;)V")) {
                            counts[1]++;
                            super.visitVarInsn(Opcodes.ASTORE, 17); super.visitVarInsn(Opcodes.ASTORE, 16); super.visitVarInsn(Opcodes.ASTORE, 15); super.visitVarInsn(Opcodes.ASTORE, 14);
                            for (int local = 14; local <= 17; local++) super.visitVarInsn(Opcodes.ALOAD, local);
                            super.visitMethodInsn(op, owner, name, descriptor, itf);
                            super.visitVarInsn(Opcodes.ALOAD, 15); super.visitVarInsn(Opcodes.ALOAD, 16); super.visitVarInsn(Opcodes.ALOAD, 17); super.visitVarInsn(Opcodes.ALOAD, 14);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeLedgerContentReturned", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false); return;
                        }
                        super.visitMethodInsn(op, owner, name, descriptor, itf);
                    }
                    @Override public void visitInsn(int op) {
                        if (reset && op == Opcodes.RETURN) {
                            counts[3]++; super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeReferenceTableResetCompleted", "(Ljava/lang/Object;)V", false);
                        }
                        super.visitInsn(op);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        if (counts[0] != 4 || counts[1] != 5 || counts[2] != 1 || counts[3] != 1) throw new IOException("Original Kryo marker/body/reset inventory changed; refused");
        return writer.toByteArray();
    }
}
