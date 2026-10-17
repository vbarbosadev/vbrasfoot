package br.exemplo.consulta;

import br.vbrasfoot.api.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Headless tests against a public SDK fake; no game or loader required. */
public final class ReadOnlyModTest {
    private static int checks;
    private static void check(boolean condition) { if (!condition) throw new AssertionError("template check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        var scope = new GameAccess.CareerScope("template-fixture", 1);
        var reference = new GameAccess.ClubRef(scope, "club");
        AtomicInteger queries = new AtomicInteger();
        GameAccess fake = new GameAccess() {
            public Snapshot snapshot() { throw new AssertionError("Must not copy full career"); }
            public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                check(limit == 50 && offset == 0); queries.incrementAndGet();
                return new ScopedClubPage(true, scope, List.of(new ClubView(reference, new ClubSummary(0, 0, "Clube de teste", true, 1))), 0, 1);
            }
            public ScopedPlayerPage scopedPlayers(ClubRef club, int offset, int limit) {
                check(club.equals(reference) && limit == 20); queries.incrementAndGet();
                return new ScopedPlayerPage(scope, List.of(new Player(1, "Jogador de teste", 22, 4, 50, 100)), 0, 1);
            }
        };
        String report = ReadOnlyMod.report(fake);
        check(report.contains("Clube de teste") && report.contains("Jogador de teste") && queries.get() == 2);
        GameAccess unloaded = new GameAccess() {
            public Snapshot snapshot() { throw new AssertionError(); }
            public ScopedClubPage scopedClubs(String filter, int offset, int limit) { return new ScopedClubPage(false, scope, List.of(), offset, 0); }
        };
        check(ReadOnlyMod.report(unloaded).contains("Carregue uma carreira"));
        GameAccess stale = new GameAccess() {
            public Snapshot snapshot() { throw new AssertionError(); }
            public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                throw new GameQueryException(GameQueryException.Reason.STALE_SCOPE, "fixture");
            }
        };
        check(ReadOnlyMod.report(stale).contains("STALE_SCOPE"));
        AtomicInteger registered = new AtomicInteger(), closed = new AtomicInteger();
        ModContext context = new ModContext() {
            public GameAccess game() { return fake; }
            public Logger logger() { return Logger.getLogger("template-fixture"); }
            public void registerAction(String id, String label, Runnable action) { throw new AssertionError("Use scoped registration"); }
            public AutoCloseable registerScopedAction(String id, String label, Runnable action) { registered.incrementAndGet(); return closed::incrementAndGet; }
            public byte[] readData(String file) { throw new AssertionError("No data permission used"); }
            public void writeData(String file, byte[] bytes) { throw new AssertionError("No data permission used"); }
        };
        var mod = new ReadOnlyMod(); mod.initialize(context);
        check(registered.get() == 1); mod.shutdown(); mod.shutdown(); check(closed.get() == 1);
        System.out.println("PASS: " + checks + " standalone template checks; public SDK only.");
    }
}
