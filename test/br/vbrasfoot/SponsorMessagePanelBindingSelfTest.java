package br.vbrasfoot;

import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

public final class SponsorMessagePanelBindingSelfTest {
    private static int checks;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private static JButton button(java.awt.Container root) {
        for (var c : root.getComponents()) {
            if (c instanceof JButton b && "sponsor.central.receive".equals(b.getName())) return b;
            if (c instanceof java.awt.Container child) { var found = button(child); if (found != null) return found; }
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var nativeRows = new ArrayList<Object>(List.of("Native message"));
            var model = new AbstractTableModel() {
                public int getRowCount() { return nativeRows.size(); }
                public int getColumnCount() { return 2; }
                public Object getValueAt(int row, int col) { return nativeRows.get(row); }
                public Class<?> getColumnClass(int col) { return String.class; }
            };
            var table = new JTable(model); var detail = new JPanel(); detail.add(new JLabel("Native body")); var host = new JPanel();
            var layout = new GroupLayout(host); host.setLayout(layout);
            layout.setHorizontalGroup(layout.createSequentialGroup().addComponent(table).addComponent(detail));
            layout.setVerticalGroup(layout.createParallelGroup().addComponent(table).addComponent(detail));
            var nativeEvents = new AtomicInteger(); ListSelectionListener listener = e -> nativeEvents.incrementAndGet();
            table.getSelectionModel().addListSelectionListener(listener);
            var scope = new br.vbrasfoot.api.CareerDataAccess.Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), 1);
            var offer = new SponsorOfferStore.Offer(UUID.randomUUID(), -1, "Clube", "Sponsor", 1000, "", "Ola {clube}", "02/01/2022", false);
            var clicked = new AtomicInteger(); var reference = new AtomicReference<SponsorMessagePanelBinding>();
            var binding = new SponsorMessagePanelBinding(host, table, detail, nativeRows, String.class, listener,
                    e -> { clicked.incrementAndGet(); reference.get().busy(true); }); reference.set(binding);
            var pending = new SponsorService.Message(scope, offer, br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT);
            binding.install(List.of(pending), ignored -> new String("Owned projection"));
            check(table.getRowCount() == 2 && binding.selected() == pending, "one projected row beside native message");
            var receive = button(host); check(receive.isEnabled(), "pending message offers receive button");
            receive.doClick(); receive.doClick(); check(clicked.get() == 1, "busy receipt button cannot fire twice");
            table.setRowSelectionInterval(1,1); check(nativeEvents.get() > 0, "native row delegates original selection callback");
            table.setRowSelectionInterval(0,0); check(!receive.isEnabled(), "switching selection does not clear receipt lock");
            for (var state : br.vbrasfoot.api.SponsorAccess.State.values()) {
                if (state == br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT) continue;
                binding.install(List.of(new SponsorService.Message(scope, offer, state)), ignored -> new String("Owned projection"));
                check(!receive.isEnabled() && table.getRowCount() == 2, "non-pending state cannot receive and refresh never duplicates: " + state);
            }
            binding.install(List.of(pending), ignored -> new String("Owned projection")); binding.uncertain();
            table.setRowSelectionInterval(1,1); table.setRowSelectionInterval(0,0);
            check(!receive.isEnabled(), "uncertain receipt remains disabled across row switches");
            binding.close(); binding.close();
            check(nativeRows.equals(List.of("Native message")) && detail.getParent() == host, "dispose restores native detail/table without touching saved messages");
            check(Arrays.asList(((DefaultListSelectionModel)table.getSelectionModel()).getListSelectionListeners()).contains(listener), "dispose restores original selection listener");
        });
        System.out.println("PASS: " + checks + " native-message projection/button lifecycle checks; synthetic, no game writes.");
    }
}
