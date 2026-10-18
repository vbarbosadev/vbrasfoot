package br.vbrasfoot.hooks;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;

/** Diagnostic causal boundaries only: exact scheduler/task methods, no lifecycle promotion. */
public final class AsyncCreationTransformer {
    private static final String BRIDGE = "br/vbrasfoot/HookProbe";
    private static final Map<String, String> HASHES = Map.of(
            "a.cR", "ae233636feed8f34d56d701545b756eb0d0f701aa5a1ee5c7e72679b5905ecca",
            "a.cS", "2eeff34873ae48c8b9abda7e81bc14a3d480598511aee73a898575a4370402fa",
            "a.hk", "79a82b087bf1785e2709e1dbed66a8d692f236f05eee82665be5d43c4273b079",
            "a.ho", "9b65c69adf136e3468c35a05a3a10ba616e32d515ce0253bb399d872821864cf");
    private AsyncCreationTransformer() { }
    public static byte[] transform(String name, byte[] original) throws Exception {
        int java = Runtime.version().feature();
        if (java != 17 && java != 21) throw new IOException("Sonda suporta somente Java17/21");
        if (!HASHES.containsKey(name) || !HASHES.get(name).equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)))
                || !new ClassReader(original).getClassName().equals(name.replace('.', '/'))) throw new IOException("Fingerprint/identidade async incompativel: " + name);
        byte[] input = name.equals("a.cR") ? CreationPathTransformer.transform(name, original) : original;
        boolean scheduler = name.equals("a.cR") || name.equals("a.hk");
        String task = name.equals("a.cR") ? "a/cS" : "a/ho";
        String label = name.equals("a.cR") || name.equals("a.cS") ? "a.cR.timer" : "a.hk.timer";
        return transformFixture(input, scheduler, task, label, true);
    }
    static byte[] transformFixture(byte[] input, boolean scheduler, String task, String label, boolean roots) throws IOException {
        var reader = new ClassReader(input); var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int[] counts = new int[4];
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals(scheduler ? "oR" : "run") || !descriptor.equals("()V")) return method;
                if (access != Opcodes.ACC_PUBLIC) throw new IllegalArgumentException("Unexpected async method access");
                counts[0]++;
                return new MethodVisitor(Opcodes.ASM8, method) {
                    final Label start = new Label(), end = new Label(), handler = new Label();
                    void simple(String called) {
                        super.visitLdcInsn(label); super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, called, "(Ljava/lang/String;)V", false);
                    }
                    void withRoots(String called, boolean created) {
                        if (created) { super.visitInsn(Opcodes.DUP); super.visitLdcInsn(label); super.visitInsn(Opcodes.SWAP); }
                        else { super.visitLdcInsn(label); super.visitVarInsn(Opcodes.ALOAD, 0); }
                        if (roots) {
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "SR", "Lbest/f;");
                            super.visitFieldInsn(Opcodes.GETSTATIC, "c/a", "afQ", "Lbest/ay;");
                        } else { super.visitInsn(Opcodes.ACONST_NULL); super.visitInsn(Opcodes.ACONST_NULL); }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, called, "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false);
                    }
                    public void visitCode() {
                        super.visitCode(); if (scheduler) simple("asyncScheduleStarted"); else withRoots("asyncRunStarted", false);
                        super.visitLabel(start);
                    }
                    public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean iface) {
                        super.visitMethodInsn(opcode, owner, called, desc, iface);
                        if (scheduler && opcode == Opcodes.INVOKESPECIAL && owner.equals(task) && called.equals("<init>")) {
                            counts[2]++; withRoots("asyncTaskCreated", true);
                        }
                        if (scheduler && owner.equals("java/util/Timer") && called.equals("schedule") && desc.equals("(Ljava/util/TimerTask;J)V")) counts[3]++;
                        if (!scheduler && owner.equals("java/util/Timer") && called.equals("cancel") && desc.equals("()V")) counts[2]++;
                        if (!scheduler && owner.equals(roots ? "best/f" : reader.getClassName()) && called.equals(roots ? (label.equals("a.cR.timer") ? "V" : "ax") : "progress") && desc.equals("()V")) counts[3]++;
                    }
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            counts[1]++; if (scheduler) simple("asyncScheduleReturned"); else withRoots("asyncRunReturned", false);
                        }
                        super.visitInsn(opcode);
                    }
                    public void visitMaxs(int stack, int locals) {
                        super.visitLabel(end); super.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                        super.visitLabel(handler);
                        // Retain only this for a run marker; all other locals are irrelevant on failure.
                        if (scheduler) super.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[] {"java/lang/Throwable"});
                        else super.visitFrame(Opcodes.F_NEW, 1, new Object[] {reader.getClassName()}, 1, new Object[] {"java/lang/Throwable"});
                        if (scheduler) simple("asyncScheduleFailed"); else withRoots("asyncRunFailed", false);
                        super.visitInsn(Opcodes.ATHROW); super.visitMaxs(stack, locals);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        for (int count : counts) if (count != 1) throw new IOException("Async method/return/task/schedule sites changed; refused");
        return writer.toByteArray();
    }
}
