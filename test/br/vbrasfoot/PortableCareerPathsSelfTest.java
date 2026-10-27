package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSaveAssociations.*;

/** Owned miniature installations only; no original game or financial effect. */
public final class PortableCareerPathsSelfTest {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    interface Action { void run() throws Exception; }
    private static void refuses(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Unsafe operation accepted"); }
        catch (java.io.IOException expected) { checks++; }
    }
    private static void copy(Path source, Path target) throws Exception {
        try (var stream = Files.walk(source)) {
            for (Path item : stream.toList()) {
                Path destination = target.resolve(source.relativize(item));
                if (Files.isDirectory(item)) Files.createDirectories(destination); else Files.copy(item, destination);
            }
        }
    }
    private static Map<String,String> hashes(Path root) throws Exception {
        var hashes = new TreeMap<String,String>();
        try (var stream = Files.walk(root)) {
            for (Path item : stream.filter(Files::isRegularFile).toList())
                hashes.put(root.relativize(item).toString(), Profile.digest(item));
        }
        return hashes;
    }
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "portable-career-");
        Path old = Files.createDirectory(base.resolve("old PC")), saves = Files.createDirectory(old.resolve("sav"));
        Files.write(saves.resolve("Clube São Paulo.s22"), new byte[]{1,2,3});
        Path data = old.resolve("data"), root = data.resolve("associations");
        var observer = new SaveFileObserver(); var observed = observer.observe(saves, "Clube São Paulo.s22");
        var legacy = new CareerSaveAssociations(root);
        var raw = CareerSaveAssociations.class.getDeclaredMethod("createNew", SaveFileObserver.Observation.class, Optional.class, Confirmation.class);
        raw.setAccessible(true);
        for (int i=0; i<3; i++) raw.invoke(legacy, observed, Optional.empty(), Confirmation.CREATE_NEW_CAREER);
        var group = legacy.consolidate(observed);
        UUID sponsor = UUID.randomUUID();
        var activations = new SponsorActivationStore(new CareerSidecarStore(data.resolve("sponsor-activations")), group.primary());
        var request = SponsorActivationStoreSelfTest.request(group.primary(), sponsor, observed.revision());
        var claim = activations.claim(sponsor, "a".repeat(64), request, CareerSidecarStore.EMPTY);
        var applied = activations.complete(claim.lease(), claim.snapshot().token(), SponsorActivationStore.State.MEMORY_APPLIED);
        Map<String,String> original = hashes(old);
        Path second = base.resolve("outro usuário - Cópia"); copy(old, second);
        var portable = new CareerSaveAssociations(second.resolve("data/associations"), second.resolve("sav"));
        check(portable.group(second.resolve("sav/Clube São Paulo.s22")).members().isEmpty(), "legacy path is not guessed from a matching filename/hash");
        portable.enablePortable(saves);
        var relocated = portable.group(second.resolve("sav/Clube São Paulo.s22"));
        check(relocated.primary().equals(group.primary()) && relocated.members().size()==3, "explicit relocation retains primary and every historical alias");
        for (var member : group.members()) {
            var current = portable.inspect(member.handle());
            check(current.token().equals(member.token()) && current.save().equals(member.save()), "association bytes, revision and token are unchanged");
            check(current.directory().equals(second.resolve("sav")), "legacy path resolves inside current installation");
        }
        check(original.equals(hashes(old)), "original installation remains untouched and may still exist");
        for (var entry : original.entrySet()) check(entry.getValue().equals(hashes(second).get(entry.getKey())), "copied historical file preserved: " + entry.getKey());
        var restored = new SponsorActivationStore(new CareerSidecarStore(second.resolve("data/sponsor-activations")), group.primary());
        check(restored.read().equals(applied), "applied sponsor tombstone survives relocation exactly");
        refuses(() -> restored.claim(sponsor, "a".repeat(64), request, applied.token()));
        refuses(() -> portable.enablePortable(base.resolve("unrelated")));
        Files.move(old, base.resolve("old PC no longer available"));
        check(!Files.exists(saves), "old machine directory is unavailable for subsequent moves");
        Path third = base.resolve("third PC"); copy(second, third);
        var thirdStore = new CareerSaveAssociations(third.resolve("data/associations"), third.resolve("sav"));
        check(thirdStore.group(third.resolve("sav/Clube São Paulo.s22")).primary().equals(group.primary()), "second move needs no old PC path or migration rewrite");
        Files.write(third.resolve("sav/new.s22"), new byte[]{4,5});
        var fresh = thirdStore.create(observer.observe(third.resolve("sav"), "new.s22"), Confirmation.CREATE_NEW_CAREER);
        thirdStore.consolidate(observer.observe(third.resolve("sav"), "new.s22"));
        Path fourth = base.resolve("fourth PC"); copy(third, fourth);
        var fourthStore = new CareerSaveAssociations(fourth.resolve("data/associations"), fourth.resolve("sav"));
        check(fourthStore.group(fourth.resolve("sav/new.s22")).primary().equals(fresh.handle()), "new format association and identity group are relative");
        var revised = observer.observe(fourth.resolve("sav"), "Clube São Paulo.s22");
        var primary = fourthStore.inspect(group.primary());
        Files.write(fourth.resolve("sav/Clube São Paulo.s22"), new byte[]{9});
        revised = observer.observe(fourth.resolve("sav"), "Clube São Paulo.s22");
        fourthStore.acceptRevision(primary.handle(), primary.token(), revised, Confirmation.ACCEPT_CHANGED_REVISION);
        Path fifth = base.resolve("fifth PC"); copy(fourth, fifth);
        var fifthStore = new CareerSaveAssociations(fifth.resolve("data/associations"), fifth.resolve("sav"));
        check(fifthStore.group(fifth.resolve("sav/Clube São Paulo.s22")).members().size()==3, "relative primary update does not strand frozen legacy aliases");
        Files.writeString(fifth.resolve("data/associations/.identity-registry/portable-root.properties"), "format=999");
        refuses(() -> fifthStore.inventory());
        System.out.println("PASS: " + checks + " portable installation/history/replay checks; owned fixtures: " + base);
    }
}
