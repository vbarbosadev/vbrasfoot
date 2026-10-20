package br.vbrasfoot;

import java.util.*;

/** Per-operation read-only reconciliation report. Byte correlation is NEVER causal proof of effect inclusion. */
final class FinanceSaveAssessment {
    enum SaveRelation { NO_WRITER_EVIDENCE, DIFFERENT_DESTINATION, DIFFERENT_BYTES, EXACT_WRITTEN_BYTES_RELOADED }
    enum Inclusion { NO_EFFECT_CONFIRMED, UNPROVEN, CONFLICT }
    record Operation(String key, Optional<UUID> operationId, FinanceContractReview.Status contract,
                     Optional<CareerCommandJournal.State> receipt, Inclusion inclusion) { }
    record Report(CareerSidecarStore.Key owner, CareerSidecarStore.SaveRevision loaded,
                  CareerSidecarStore.SaveRevision contractAnchor, CareerSidecarStore.SaveRevision journalAnchor,
                  SaveRelation relation, List<Operation> operations) {
        Report { operations = List.copyOf(operations); }
        boolean blocksContext() {
            return !loaded.equals(contractAnchor) || !loaded.equals(journalAnchor)
                    || operations.stream().anyMatch(operation -> operation.inclusion() != Inclusion.NO_EFFECT_CONFIRMED);
        }
    }
    private FinanceSaveAssessment() { }
    static Report assess(CareerSidecarStore.Key owner, FinanceRequestStore.Recovery requests,
                         CareerCommandJournal.Recovery journal, LoadedSaveVerifier.Verified loaded,
                         Optional<WrittenSaveVerifier.Verified> written) {
        Objects.requireNonNull(owner); Objects.requireNonNull(requests); Objects.requireNonNull(journal);
        Objects.requireNonNull(loaded); Objects.requireNonNull(written);
        if (!requests.observedSave().equals(loaded.bytes()) || !journal.observedSave().equals(loaded.bytes()))
            throw new IllegalArgumentException("Report observations must refer to the same decoded revision");
        for (var request : requests.snapshot().requests()) {
            if (!request.career().equals(owner.career()) || !request.binding().equals(owner.binding()) || !request.owner().equals(owner.mod()))
                throw new IllegalArgumentException("Report contract scope mismatch");
        }
        SaveRelation relation = written.map(value -> !value.destination().equals(loaded.source()) ? SaveRelation.DIFFERENT_DESTINATION
                : !value.bytes().equals(loaded.bytes()) ? SaveRelation.DIFFERENT_BYTES : SaveRelation.EXACT_WRITTEN_BYTES_RELOADED)
                .orElse(SaveRelation.NO_WRITER_EVIDENCE);
        var receipts = new HashMap<String, CareerCommandJournal.Receipt>();
        for (var receipt : journal.snapshot().receipts()) {
            if (receipts.put(receipt.intent().key(), receipt) != null) throw new IllegalArgumentException("Duplicate receipt key");
        }
        var result = new ArrayList<Operation>();
        for (var link : FinanceContractReview.compare(requests.snapshot(), journal.snapshot())) {
            var receipt = receipts.get(link.key());
            Inclusion inclusion = link.status() == FinanceContractReview.Status.PAYLOAD_CONFLICT ? Inclusion.CONFLICT
                    : receipt != null && receipt.state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED ? Inclusion.NO_EFFECT_CONFIRMED : Inclusion.UNPROVEN;
            result.add(new Operation(link.key(), Optional.ofNullable(receipt).map(CareerCommandJournal.Receipt::operationId), link.status(),
                    Optional.ofNullable(receipt).map(CareerCommandJournal.Receipt::state), inclusion));
        }
        return new Report(owner, loaded.bytes(), requests.recordedSave(), journal.recordedSave(), relation, result);
    }
}
