package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;

/** Internal offline identity maintenance. Never opens the game or adjudicates save effects. */
final class CareerAssociationConsolidationTool {
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !args[0].equals("--consolidate-identity-only"))
            throw new IllegalArgumentException("Expected --consolidate-identity-only <metadata-root> <save-directory> <save-name>");
        Path metadata = Path.of(args[1]).toRealPath(), saves = Path.of(args[2]).toRealPath();
        var observation = new SaveFileObserver().observe(saves, args[3]);
        var associations = new CareerSaveAssociations(metadata.resolve("associations"), saves);
        var group = associations.consolidate(observation);
        System.out.println("Identity consolidated: active=1 preservedMembers=" + group.members().size() + " primary=" + group.primary());
        for (var member : group.members()) {
            System.out.println("MEMBER " + member.handle() + " primary=" + member.handle().equals(group.primary()));
            var owners = new TreeSet<String>();
            for (String name : List.of("journals", "finance-requests", "finance-execution-witnesses", "loan-requests", "loan-continuation")) {
                var inventory = new CareerSidecarStore(metadata.resolve(name)).inventoryBinding(member.handle().career(), member.handle().binding());
                System.out.println("  " + name + "=" + inventory.tokens());
                if (name.equals("loan-requests")) owners.addAll(inventory.tokens().keySet());
            }
            for (String owner : owners) {
                var store = new LoanRequestStore(new CareerSidecarStore(metadata.resolve("loan-requests")),
                        new CareerSidecarStore.Key(member.handle().career(), member.handle().binding(), owner));
                var snapshot = store.inspectRecordedRecovery(observation.revision()).snapshot();
                for (var request : snapshot.requests()) System.out.println("  LOAN " + request.player().name() + "=" + snapshot.state(request.key()));
            }
        }
        System.out.println("IDENTITY ONLY: no transfer, fee, save write, reanchoring or historical effect resolution performed.");
    }
}
