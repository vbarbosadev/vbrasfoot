import java.util.Set;

/** Synthetic classfile coverage only, never executes the inspected fixture. */
public final class FindCareerReferencesSelfTest {
    private static int checks;
    public static final class Target {
        public static void selected() { throw new AssertionError("Target must never run"); }
        public static void selected(int ignored) { throw new AssertionError("Target must never run"); }
    }
    public static final class Fixture {
        static { if (true) throw new AssertionError("Fixture must never initialize"); }
        public void references(int value) {
            switch (value) { case 0 -> Target.selected(); case 5 -> Target.selected(1); default -> { } }
        }
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        byte[] fixture;
        try (var stream = FindCareerReferencesSelfTest.class.getResourceAsStream("/FindCareerReferencesSelfTest$Fixture.class")) {
            fixture = stream.readAllBytes();
        }
        String target = "FindCareerReferencesSelfTest$Target.selected";
        check(FindCareerReferences.inspect(fixture, Set.of()).isEmpty(), "default selector excludes arbitrary methods");
        var noArgs = FindCareerReferences.inspect(fixture, Set.of(target + "()V"));
        check(noArgs.size() == 1 && noArgs.get(0).contains(target + "()V"), "exact no-arg descriptor");
        var integer = FindCareerReferences.inspect(fixture, Set.of(target + "(I)V"));
        check(integer.size() == 1 && integer.get(0).contains(target + "(I)V"), "overload selection");
        check(FindCareerReferences.inspect(fixture, Set.of(target + "()V", target + "(I)V")).size() == 2, "multiple targets");
        check(FindCareerReferences.inspect(fixture, Set.of(target + "()I")).isEmpty(), "return descriptor exact");
        check(noArgs.get(0).contains("class.sha256=") && noArgs.get(0).contains("code.sha256="), "source fingerprints retained");
        boolean rejected = false;
        try { FindCareerReferences.inspect(new byte[] {0, 0, 0, 0}, Set.of()); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "invalid classfile refused");
        System.out.println("PASS: " + checks + " exact static reference checks; no fixture initialization/runtime evidence.");
    }
}
