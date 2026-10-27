package br.vbrasfoot;

import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

public final class LoanRosterRefreshSelfTest {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        try { LoanRosterRefresh.update(new JTable(), new DefaultTableModel(), null, () -> {}); throw new AssertionError("off EDT allowed"); }
        catch (IllegalStateException expected) { checks++; }
        CareerBridge.onEdt(() -> {
            Object selected = new Object(), added = new Object(); var rows = new ArrayList<>(List.of(selected));
            AbstractTableModel model = new AbstractTableModel() {
                public int getRowCount() { return rows.size(); } public int getColumnCount() { return 1; }
                public Object getValueAt(int row, int column) { return rows.get(row); }
            };
            JTable table = new JTable(model); var sorter = new TableRowSorter<>(model); table.setRowSorter(sorter);
            table.setRowSelectionInterval(0, 0); JLabel counter = new JLabel("1/35");
            rows.add(added);
            LoanRosterRefresh.update(table, model, selected, () -> counter.setText(rows.size() + "/35"));
            check(sorter.getViewRowCount() == 2 && table.convertRowIndexToView(1) >= 0, "sorter rebuilt after insertion");
            check(table.getValueAt(table.getSelectedRow(), 0) == selected && counter.getText().equals("2/35"), "selection and count refreshed");
            rows.remove(selected);
            LoanRosterRefresh.update(table, model, selected, () -> counter.setText(rows.size() + "/35"));
            check(sorter.getViewRowCount() == 1 && table.getValueAt(table.getSelectedRow(), 0) == added, "removed selected player falls back safely");
            sorter.setComparator(0, (a, b) -> a == b ? 0 : a == added ? -1 : 1);
            sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
            rows.add(selected);
            LoanRosterRefresh.update(table, model, selected, () -> {});
            check(table.getValueAt(table.getSelectedRow(), 0) == selected && table.convertRowIndexToModel(0) == 0,
                    "active sort keys and exact selection survive refresh");
            rows.clear(); LoanRosterRefresh.update(table, model, added, () -> counter.setText("0/35"));
            check(table.getSelectedRow() == -1 && sorter.getViewRowCount() == 0 && counter.getText().equals("0/35"), "empty roster clears selection");
            check(LoanRosterRefresh.refresh(null) == LoanRosterRefresh.Status.NOT_VISIBLE, "absent UI no operation");
            return null;
        });
        System.out.println("PASS: " + checks + " roster refresh checks; synthetic live-list model, no game progression.");
    }
}
