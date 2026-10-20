package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Body-only diagnostic brackets around selected direct writes; fingerprint required, no schema changes. */
public final class FinanceMutationTransformer {
    private static final Map<String, String> HASHES = Map.of(
            "best.ah", "9e12714ef123afe00636edd25a64334451ba272a9d702b5ce63c656cd4411e6f",
            "best.C", "e208455170e565d83ab0733a79ba2c10e4addfb9a07b583d6b06d46a460eded4");
    private static final Set<String> LEDGER = Set.of("dK", "dL", "dM", "dN", "dO", "dP", "dQ", "dR", "dS", "dT", "dU", "dV", "dW", "dX");
    private FinanceMutationTransformer() { }
    public static byte[] transform(String name, byte[] original) throws Exception {
        if (Runtime.version().feature() != 17 && Runtime.version().feature() != 21) throw new IOException("Financial mutation probe requires Java17/21");
        if (!Objects.equals(HASHES.get(name), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original))))
            throw new IOException("Financial mutation fingerprint changed; refused");
        int[] counts = new int[3];
        byte[] result = transformBytes(original, "best/ah", "best/C", counts);
        if (name.equals("best.ah") && (counts[0] != 10 || counts[1] != 4 || counts[2] != 0)
                || name.equals("best.C") && (counts[0] != 0 || counts[1] != 0 || counts[2] != 28))
            throw new IOException("Direct financial field-write site inventory changed; refused");
        return result;
    }
    static byte[] transformFixture(byte[] original) {
        String own = new ClassReader(original).getClassName();
        return transformBytes(original, own, own, new int[3]);
    }
    private static byte[] transformBytes(byte[] original, String clubOwner, String ledgerOwner, int[] counts) {
        var reader = new ClassReader(original); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM8, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitFieldInsn(int op, String owner, String field, String desc) {
                        boolean watched = op == Opcodes.PUTFIELD && (owner.equals(clubOwner) && (field.equals("nb") && desc.equals("J") || field.equals("nx") && desc.startsWith("L"))
                                || owner.equals(ledgerOwner) && LEDGER.contains(field) && (desc.equals("J") || desc.equals("I")));
                        if (!watched) { super.visitFieldInsn(op, owner, field, desc); return; }
                        if (owner.equals(clubOwner) && field.equals("nb")) counts[0]++;
                        else if (owner.equals(clubOwner) && field.equals("nx")) counts[1]++;
                        else counts[2]++;
                        // Argument-free callbacks leave receiver/value (int, long or reference) untouched.
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeMutationStarting", "()V", false);
                        super.visitFieldInsn(op, owner, field, desc);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "br/vbrasfoot/HookProbe", "financeMutationFinished", "()V", false);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
