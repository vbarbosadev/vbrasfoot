import java.io.IOException;
import java.util.*;

public final class MapGameplayBoundariesSelfTest {
    private static int checks;
    private static String reference(String operation, String target) {
        return operation + "\t" + target + "\tfixture.Owned.test()V\t1\tpreviousOpcode=3\tclass.sha256=" + "a".repeat(64) + "\tcode.sha256=" + "b".repeat(64);
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("gameplay map check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        for (String target : MapGameplayBoundaries.TARGETS) {
            if (target.startsWith("field:")) continue;
            check(MapGameplayBoundaries.role(reference("invoke", target)) != null);
            check(MapGameplayBoundaries.role(reference("putstatic", target)) == null);
        }
        check(MapGameplayBoundaries.role(reference("putfield", "best/ah.nbJ")).equals("balance-write-not-complete-financial-command"));
        check(MapGameplayBoundaries.role(reference("putstatic", "best/ah.nbJ")) == null);
        check(MapGameplayBoundaries.role(reference("invoke", "best/ah.v(JI)V")) == null);
        check(MapGameplayBoundaries.role(reference("invoke", "best/ah.e(J)V")).equals("raw-balance-setter-not-bookkeeping-command"));
        for (String unrelated : List.of("c/a.ae(Ljava/lang/String;)V", "java/lang/System.exit(I)V", "javax/swing/JDialog.dispose()V"))
            check(MapGameplayBoundaries.role(reference("invoke", unrelated)) == null);
        for (String malformed : List.of("short", reference("getfield", "best/ah.nbJ"), reference("invoke", "best/ah.v(II)V").replace("code.sha256=", "invalid="))) {
            boolean refused = false; try { MapGameplayBoundaries.role(malformed); } catch (IOException expected) { refused = true; } check(refused);
        }
        System.out.println("PASS: " + checks + " gameplay candidate classifier checks; owned records, no game operations.");
    }
}
