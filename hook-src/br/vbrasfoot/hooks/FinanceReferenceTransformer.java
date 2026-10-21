package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import jdk.internal.org.objectweb.asm.*;

/** Body-only observation of exact object/serializer arguments after original Kryo calls return. */
public final class FinanceReferenceTransformer {
    private FinanceReferenceTransformer() { }
    public static byte[] transform(byte[] original) throws Exception {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Reference probe requires Java17/21");
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)).equals("817f50e574b98a9d1d97427202a31243038e58a7dab47d0c29a24c150f8f7e20"))
            throw new IOException("ObjectField fingerprint changed; refused");
        int[] counts = new int[3];
        var reader = new ClassReader(original); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("write") || !descriptor.equals("(Lcom/esotericsoftware/kryo/io/Output;Ljava/lang/Object;)V")) return delegate;
                return new MethodVisitor(Opcodes.ASM8, delegate) {
                    private void callback(String route, boolean nullValue) {
                        super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitVarInsn(Opcodes.ALOAD, nullValue ? 1 : 8);
                        if (nullValue) { super.visitInsn(Opcodes.ACONST_NULL); super.visitInsn(Opcodes.ACONST_NULL); }
                        else { super.visitVarInsn(Opcodes.ALOAD, 9); super.visitVarInsn(Opcodes.ALOAD, 10); }
                         super.visitLdcInsn(route);
                         if (nullValue) super.visitInsn(Opcodes.ACONST_NULL); else super.visitVarInsn(Opcodes.ALOAD, 11);
                         super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeReferenceEmitted",
                                 "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V", false);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("com/esotericsoftware/kryo/Kryo")
                                && (method.equals("writeObject") || method.equals("writeObjectOrNull"))
                                && desc.equals("(Lcom/esotericsoftware/kryo/io/Output;Ljava/lang/Object;Lcom/esotericsoftware/kryo/Serializer;)V")) {
                            counts[method.equals("writeObject") ? 0 : 1]++;
                            // Preserve actual stack arguments, not a second field read or a reconstructed serializer.
                             super.visitVarInsn(Opcodes.ASTORE, 10); super.visitVarInsn(Opcodes.ASTORE, 9); super.visitVarInsn(Opcodes.ASTORE, 8);
                             super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, 2); super.visitVarInsn(Opcodes.ALOAD, 8); super.visitVarInsn(Opcodes.ALOAD, 9);
                             super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeReferenceCalling",
                                     "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                             super.visitVarInsn(Opcodes.ASTORE, 11);
                            super.visitVarInsn(Opcodes.ALOAD, 8); super.visitVarInsn(Opcodes.ALOAD, 9); super.visitVarInsn(Opcodes.ALOAD, 10);
                            super.visitMethodInsn(opcode, owner, method, desc, itf); callback(method, false); return;
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, itf);
                    }
                    @Override public void visitInsn(int opcode) {
                        // Fingerprinted first RETURN is exclusively the dynamic-null writeClass path.
                        if (opcode == Opcodes.RETURN && counts[2]++ == 0) callback("writeClass(null)", true);
                        super.visitInsn(opcode);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        if (counts[0] != 2 || counts[1] != 1 || counts[2] != 2) throw new IOException("ObjectField write/return inventory changed; refused");
        return KryoFinanceMutationTransformer.addToVerified("com.esotericsoftware.kryo.serializers.ObjectField", writer.toByteArray());
    }
}
