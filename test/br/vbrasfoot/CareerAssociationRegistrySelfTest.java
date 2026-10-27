package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static br.vbrasfoot.CareerSaveAssociations.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Owned synthetic files only. Consolidation never resolves game effects. */
public final class CareerAssociationRegistrySelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    interface Action { void run() throws Exception; }
    private static void refuses(Action action) throws Exception {
        try { action.run(); } catch (java.io.IOException expected) { checks++; return; }
        throw new AssertionError("Expected safe refusal");
    }
    private static Map<Path, String> hashes(Path root) throws Exception {
        var result = new HashMap<Path, String>();
        try (var files = Files.walk(root)) { for (var p : files.filter(Files::isRegularFile).toList())
            if (!p.startsWith(root.resolve(".identity-registry"))) result.put(p, Profile.digest(p)); }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("--worker")) {
            var disk = new CareerSaveAssociations(Path.of(args[1])); var path = Path.of(args[2]);
            var seen = new SaveFileObserver().observe(path.getParent(), path.getFileName().toString());
            for (int retry = 0; retry < 1000; retry++) try { disk.create(seen, Confirmation.CREATE_NEW_CAREER); return; }
            catch (Failure failure) { if (failure.reason() != Reason.BUSY) throw failure; Thread.yield(); }
            throw new AssertionError("Process writer unavailable");
        }
        Path base = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "association-registry-");
        Path saves = Files.createDirectory(base.resolve("sav")); Files.write(saves.resolve("one.s22"), new byte[]{1,2,3});
        var observation = new SaveFileObserver().observe(saves, "one.s22");
        Path root = base.resolve("associations"); var service = new CareerSaveAssociations(root);
        var pool = Executors.newFixedThreadPool(12); var handles = ConcurrentHashMap.<Handle>newKeySet();
        try {
            var jobs = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 24; i++) jobs.add(() -> {
                for (int retry = 0; retry < 200; retry++) {
                    try { handles.add(service.create(observation, Confirmation.CREATE_NEW_CAREER).handle()); return null; }
                    catch (Failure busy) { if (busy.reason() != Reason.BUSY) throw busy; Thread.yield(); }
                }
                throw new AssertionError("Writer never became available");
            });
            for (var job : pool.invokeAll(jobs)) job.get();
        } finally { pool.shutdownNow(); }
        check(handles.size() == 1 && service.inventory().size() == 1, "concurrent creation is idempotent");
        check(new CareerSaveAssociations(root).create(observation, Confirmation.CREATE_NEW_CAREER).handle().equals(handles.iterator().next()), "restart reuses identity");
        var workers = new ArrayList<Process>(); Path multiRoot = base.resolve("multiprocess");
        for (int i = 0; i < 4; i++) workers.add(new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), CareerAssociationRegistrySelfTest.class.getName(), "--worker", multiRoot.toString(), saves.resolve("one.s22").toString()).inheritIO().start());
        for (var worker : workers) check(worker.waitFor(30, TimeUnit.SECONDS) && worker.exitValue() == 0, "cross-process writer completed");
        check(new CareerSaveAssociations(multiRoot).inventory().size() == 1, "cross-process lock permits only one identity");
        // Emulate records produced by the old version, without opening a public duplicate API.
        var rawCreate = CareerSaveAssociations.class.getDeclaredMethod("createNew", SaveFileObserver.Observation.class, Optional.class, Confirmation.class);
        rawCreate.setAccessible(true);
        for (int i = 0; i < 3; i++) rawCreate.invoke(service, observation, Optional.empty(), Confirmation.CREATE_NEW_CAREER);
        refuses(() -> service.create(observation, Confirmation.CREATE_NEW_CAREER));
        refuses(() -> service.group(saves.resolve("one.s22")));
        var before = hashes(root); var group = service.consolidate(observation);
        check(group.members().size() == 4 && group.primary() != null, "four old records become one primary and three historical members");
        check(before.equals(hashes(root)), "all old association bytes preserved");
        check(service.create(observation, Confirmation.CREATE_NEW_CAREER).handle().equals(group.primary()), "new creation cannot bypass consolidation");
        check(new CareerSaveAssociations(root).group(group.source()).equals(group), "durable group survives restart");
        check(service.consolidate(observation).equals(group), "consolidation is idempotent");
        var alias = group.members().stream().filter(a -> !a.handle().equals(group.primary())).findFirst().orElseThrow();
        Files.write(saves.resolve("one.s22"), new byte[]{4,5}); var revised = new SaveFileObserver().observe(saves, "one.s22");
        refuses(() -> service.acceptRevision(alias.handle(), alias.token(), revised, Confirmation.ACCEPT_CHANGED_REVISION));
        var primary = service.inspect(group.primary());
        var changed = service.acceptRevision(primary.handle(), primary.token(), revised, Confirmation.ACCEPT_CHANGED_REVISION);
        check(service.group(group.source()).primary().equals(changed.handle()), "primary evolves without losing historical members");
        check(service.inspect(alias.handle()).token().equals(alias.token()), "alias metadata stays frozen");
        Path aliasPath = root.resolve(alias.handle().career().toString()).resolve(alias.handle().binding().toString());
        Path held = base.resolve("held-alias"); Files.move(aliasPath, held);
        refuses(() -> service.group(group.source())); Files.move(held, aliasPath);
        Files.copy(saves.resolve("one.s22"), saves.resolve("copy.s22")); var copied = new SaveFileObserver().observe(saves, "copy.s22");
        var copy = service.fork(changed.handle(), changed.token(), copied, Confirmation.FORK_COPY_WITH_EMPTY_MOD_DATA);
        check(!copy.handle().equals(changed.handle()), "explicit independent copy has independent identity");
        check(service.fork(changed.handle(), changed.token(), copied, Confirmation.FORK_COPY_WITH_EMPTY_MOD_DATA).handle().equals(copy.handle()), "repeated fork cannot duplicate target");
        var aliasKey = new Key(alias.handle().career(), alias.handle().binding(), ASSOCIATION_OWNER);
        var data = new CareerSidecarStore(root); var view = data.inspectAssociation(aliasKey); var value = view.value().orElseThrow();
        data.write(aliasKey, value.save(), value.schema(), view.token(), value.payload());
        refuses(() -> service.group(group.source()));
        check(Files.readAllBytes(saves.resolve("one.s22")).length == 2, "registry never writes save bytes");
        // Surviving intent must block even if a creation might have committed.
        Path interruptedRoot = base.resolve("interrupted"); var interrupted = new CareerSaveAssociations(interruptedRoot);
        interrupted.create(copied, Confirmation.CREATE_NEW_CAREER);
        Files.writeString(interruptedRoot.resolve(".identity-registry/creation.pending"), "interrupted");
        refuses(() -> interrupted.inventory());
        Path addedRoot = base.resolve("added"); var added = new CareerSaveAssociations(addedRoot);
        for (int i = 0; i < 2; i++) rawCreate.invoke(added, copied, Optional.empty(), Confirmation.CREATE_NEW_CAREER);
        added.consolidate(copied); rawCreate.invoke(added, copied, Optional.empty(), Confirmation.CREATE_NEW_CAREER);
        refuses(() -> added.group(saves.resolve("copy.s22")));
        Path corruptRoot = base.resolve("corrupt"); var corrupt = new CareerSaveAssociations(corruptRoot);
        corrupt.create(copied, Confirmation.CREATE_NEW_CAREER); corrupt.consolidate(copied);
        try (var records = Files.newDirectoryStream(corruptRoot.resolve(".identity-registry"), "*.group")) {
            var record = records.iterator().next(); byte[] bytes = Files.readAllBytes(record); bytes[0] ^= 1; Files.write(record, bytes);
        }
        refuses(() -> corrupt.group(saves.resolve("copy.s22")));
        System.out.println("PASS: " + checks + " unique association/consolidation/frozen history checks; synthetic only: " + base);
    }
}
