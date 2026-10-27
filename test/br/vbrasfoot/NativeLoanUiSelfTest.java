package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;

/** Own widgets and preference bytes only; does not initialize native game classes or authorize an effect. */
public final class NativeLoanUiSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private interface Checked { void run() throws Exception; }
    private static void refused(Checked action, String label) throws Exception {
        try { action.run(); throw new AssertionError(label); }
        catch (java.io.IOException expected) { checks++; }
    }
    private static CareerSaveAssociations.Association association(Path source, CareerSaveAssociations.Handle handle) {
        return new CareerSaveAssociations.Association(handle, "a".repeat(64), 1, source.getParent(), source.getFileName().toString(),
                "file", "modified", "created", new CareerSidecarStore.SaveRevision("b".repeat(64), 12), Optional.empty(),
                CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var oldEffect = new AtomicInteger(); var modEffect = new AtomicInteger(); var available = new AtomicBoolean(true);
            JButton button = new JButton("Native loan"); button.setToolTipText("Original");
            button.addActionListener(event -> oldEffect.incrementAndGet());
            var binding = new NativeLoanButtonBinding(button, available::get, modEffect::incrementAndGet);
            check(button.getText().equals("Empréstimos"), "one unified caption without novo/mod suffix");
            button.setText("Emprestimo (novo)"); binding.update();
            check(button.getText().equals("Empréstimos"), "native selection refresh cannot restore old caption");
            check(button.getActionListeners().length == 1, "one callback only, no stacked native effect");
            var callback = button.getActionListeners()[0];
            button.doClick(); check(modEffect.get() == 1 && oldEffect.get() == 0, "replaced native handler never runs beside mod");
            available.set(false); binding.update(); button.doClick();
            check(!button.isEnabled() && modEffect.get() == 1, "busy/unavailable selection cannot invoke callback");
            available.set(true); binding.update();
            check(button.isEnabled(), "new valid selection enables query entry");
            binding.close(); binding.close(); callback.actionPerformed(null);
            check(button.getText().equals("Native loan") && button.getToolTipText().equals("Original"), "only owned presentation restored");
            check(button.getActionListeners().length == 1 && modEffect.get() == 1, "late callback inert and close idempotent");
            button.doClick(); check(oldEffect.get() == 1 && modEffect.get() == 1, "original callback restored exactly once");
        });
        Path root = Files.createTempDirectory(Files.createDirectories(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode")), "native-loan-ui-");
        var preference = root.resolve("selection.properties");
        var selections = new NativeLoanAssociation(root.resolve("metadata"), preference, null);
        var source = root.resolve("sav/one.s22"); var other = root.resolve("sav/two.s22");
        var handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        var differentHandle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        var known = association(source, handle);
        check(NativeLoanAssociation.automatic(source, Optional.empty(), List.of()).isEmpty(), "first use permits metadata-only creation without prompts");
        var four = List.of(association(other, differentHandle), known,
                association(root.resolve("sav/three.s22"), new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID())),
                association(root.resolve("sav/four.s22"), new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID())));
        check(NativeLoanAssociation.automatic(source, Optional.empty(), four).orElseThrow().equals(handle), "unique exact loaded path automatically chosen among four associations");
        check(NativeLoanAssociation.automatic(source, Optional.of(handle), four).orElseThrow().equals(handle), "valid remembered exact-path selection honored");
        var duplicate = association(source, differentHandle);
        refused(() -> NativeLoanAssociation.automatic(source, Optional.empty(), List.of(known, duplicate)), "duplicate exact path must not be arbitrarily chosen");
        refused(() -> NativeLoanAssociation.automatic(source, Optional.of(handle), List.of(known, duplicate)), "remembered selection cannot hide duplicate exact path");
        refused(() -> NativeLoanAssociation.automatic(source, Optional.of(differentHandle), List.of(known)), "stale remembered mapping refused");
        refused(() -> NativeLoanAssociation.automatic(source, Optional.of(handle), List.of()), "missing remembered association refused, not recreated");
        refused(() -> NativeLoanAssociation.automatic(other, Optional.empty(), List.of(known)), "renamed/copied equal-byte revision never creates empty history");
        check(NativeLoanPresentation.refused().equals("Emprestimo recusado."), "insufficient data has concise refusal");
        check(NativeLoanPresentation.failure(new NativeLoanAssociation.Failure(NativeLoanAssociation.Reason.AMBIGUOUS, "Technical UUID source hash"))
                .equals("Recusado: existem historicos conflitantes para este save. Nenhum registro foi alterado."), "ambiguous association has safe concise refusal, not ID prompt or hidden empty binding");
        var request = new LoanRequest(handle.career(), handle.binding(), known.save(), "loan", "one",
                new FinanceRequest.Club(0, 10, "Origem"), new FinanceRequest.Club(1, 11, "Destino"), 0,
                List.of(new br.vbrasfoot.api.GameAccess.Player(1, "Jogador", 22, 2, 50, 1000)), List.of(), 0);
        String confirmation = NativeLoanPresentation.confirmation(request);
        check(confirmation.equals("Emprestimo aceito.\nJogador\nOrigem -> Destino\nSem taxa.\nPrazo: 366 dias.\nConfirmar emprestimo?"),
                "single payment authorization contains only player, clubs, fee and duration");
        check(!confirmation.contains(handle.career().toString()) && !confirmation.contains(known.save().sha256())
                && !confirmation.contains("HEAD"), "technical authorization tokens never shown in confirmation");
        check(NativeLoanPresentation.result(CareerCommandDispatcher.Kind.EXECUTED, 75).equals(
                "Emprestimo realizado. Taxa paga: 75.\nSalve pelo menu original."), "success reports fee once without technical internals");
        check(NativeLoanPresentation.result(CareerCommandDispatcher.Kind.UNCERTAIN, 75).contains("Nao repita")
                && NativeLoanPresentation.uncertain().contains("parcial"), "uncertain effect explicitly warns against repetition and partial charge");
        for (var kind : List.of(CareerCommandDispatcher.Kind.NO_EFFECT, CareerCommandDispatcher.Kind.DENIED, CareerCommandDispatcher.Kind.RECORDED_ONLY))
            check(NativeLoanPresentation.result(kind, 75).equals(NativeLoanPresentation.refused()), "nonexecution is not reported as success: " + kind);
        check(selections.selected(source).isEmpty() && !Files.exists(preference), "read of no preference does not create identity");
        selections.remember(source, handle);
        check(selections.selected(source).orElseThrow().equals(handle), "explicit selection reused only at exact path");
        check(selections.selected(other).isEmpty(), "different save path never inherits identity by name/hash");
        Files.writeString(preference, "format=9\nforeign=data\n"); byte[] foreign = Files.readAllBytes(preference);
        try { selections.remember(source, handle); throw new AssertionError("Invalid preference must be preserved"); }
        catch (java.io.IOException expected) { check(Arrays.equals(foreign, Files.readAllBytes(preference)), "unknown preference format never overwritten"); }
        System.out.println("PASS: " + checks + " native-button lifecycle/automatic exact-path/presentation checks; no game: " + root);
    }
}
