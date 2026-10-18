import java.io.IOException;

/** Owned records only; no game artifact required. */
public final class MapCareerBoundariesSelfTest {
    private static int checks;
    private static String reference(String operation, String target) {
        return operation + "\t" + target + "\tfixture.Owned.test()V\t1\tpreviousOpcode=3\tclass.sha256=" + "a".repeat(64) + "\tcode.sha256=" + "b".repeat(64);
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("boundary map check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        check(MapCareerBoundaries.role(reference("invoke", "java/lang/System.exit(I)V")).equals("process-exit-not-unload"));
        check(MapCareerBoundaries.role(reference("invoke", "javax/swing/JFrame.dispose()V")).equals("window-close-not-unload"));
        check(MapCareerBoundaries.role(reference("invoke", "java/awt/Graphics.dispose()V")) == null);
        check(MapCareerBoundaries.role(reference("invoke", "javax/swing/JDialog.dispose()V")) == null);
        check(MapCareerBoundaries.role(reference("putstatic", "c/a.SRLbest/f;")).equals("root-write-not-ready-or-unload"));
        check(MapCareerBoundaries.role(reference("putstatic", "c/a.afQLbest/ay;")).equals("root-write-not-ready-or-unload"));
        check(MapCareerBoundaries.role(reference("putfield", "c/a.SRLbest/f;")) == null);
        check(MapCareerBoundaries.role(reference("invoke", "best/f.V()V")).equals("shared-progression-call"));
        check(MapCareerBoundaries.role(reference("invoke", "best/f.V(I)V")) == null);
        check(MapCareerBoundaries.role(reference("invoke", "best/S.aY(I)Ljavax/swing/JPanel;")).equals("panel-switch-not-ready"));
        check(MapCareerBoundaries.role(reference("invoke", "java/util/Timer.schedule(Ljava/util/TimerTask;J)V")).equals("scheduled-path-candidate"));
        check(MapCareerBoundaries.role(reference("invoke", "best/h.mh()V")).equals("creation-entry-candidate"));
        check(MapCareerBoundaries.role(reference("invoke", "c/a.c(Ljava/lang/String;Z)Z")).equals("existing-read-caller"));
        check(MapCareerBoundaries.role(reference("invoke", "c/a.ae(Ljava/lang/String;)V")).equals("save-caller"));
        for (String invalid : new String[] {"truncated", reference("getstatic", "c/a.SRLbest/f;"),
                reference("invoke", "best/f.V()V").replace("class.sha256=", "class.invalid="),
                reference("invoke", "best/f.V()V").replace("previousOpcode=3", "previousOpcode=oops")}) {
            boolean refused = false;
            try { MapCareerBoundaries.role(invalid); } catch (IOException expected) { refused = true; }
            check(refused);
        }
        check(MapCareerBoundaries.TARGETS.contains("a/cR.oR()V") && MapCareerBoundaries.TARGETS.contains("best/f.at()V"));
        check(MapCareerBoundaries.role(reference("invoke", "best/f.ax()V")).equals("shared-progression-call"));
        check(MapCareerBoundaries.role(reference("invoke", "a/ho.<init>(La/hk;)V")).equals("scheduled-path-candidate"));
        System.out.println("PASS: " + checks + " static career-boundary classifier checks (owned records, no runtime lifecycle).");
    }
}
