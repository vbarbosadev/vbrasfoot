package br.vbrasfoot;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

/** Internal view-only refresh. Never reconstructs a career or invokes progression/save routines. */
final class LoanRosterRefresh {
    enum Status { NOT_VISIBLE, UPDATED, FAILED }
    record Target(JPanel panel, JTable table, AbstractTableModel model, Object selected) { }
    private LoanRosterRefresh() { }
    static Target capture(ClassLoader runtime, Object origin, Object destination) throws Exception {
        edt();
        Object window = runtime.loadClass("best.n").getMethod("db").invoke(null);
        if (!(window instanceof JFrame frame) || !frame.isDisplayable()) return null;
        var targets = new ArrayList<JPanel>(); find(frame.getContentPane(), runtime, targets);
        if (targets.size() != 1) return null;
        JPanel panel = targets.get(0);
        Object club = CareerBridge.call(panel, "qp");
        if (club != origin && club != destination) return null;
        var field = panel.getClass().getDeclaredField("Iy"); field.setAccessible(true);
        JTable table = (JTable) field.get(panel);
        if (table == null || !table.getModel().getClass().getName().equals("b.Q")
                || table.getModel().getClass().getClassLoader() != runtime || !(table.getModel() instanceof AbstractTableModel model))
            throw new IllegalStateException("Original roster model required");
        Object selected = null;
        int row = table.getSelectedRow();
        if (row >= 0) selected = model.getValueAt(table.convertRowIndexToModel(row), 0);
        return new Target(panel, table, model, selected);
    }
    static Status refresh(Target target) {
        edt(); if (target == null) return Status.NOT_VISIBLE;
        if (!target.panel().isShowing() || target.table().getModel() != target.model()) return Status.NOT_VISIBLE;
        try {
            update(target.table(), target.model(), target.selected(), () -> {
                try { CareerBridge.call(target.panel(), "pK"); }
                catch (Exception failure) { throw new IllegalStateException("Roster counter refresh failed", failure); }
            });
            target.panel().revalidate(); target.panel().repaint();
            return Status.UPDATED;
        } catch (RuntimeException failure) { return Status.FAILED; }
    }
    /** Model event rebuilds sorter mappings before any post-mutation view-index access. */
    static void update(JTable table, AbstractTableModel model, Object selected, Runnable counter) {
        edt();
        if (table.getModel() != model) throw new IllegalStateException("Roster model replaced");
        table.clearSelection();
        model.fireTableDataChanged();
        int chosen = -1;
        for (int i = 0; i < model.getRowCount(); i++) if (model.getValueAt(i, 0) == selected) { chosen = i; break; }
        if (chosen < 0 && model.getRowCount() > 0) chosen = 0;
        if (chosen >= 0) {
            int view = table.convertRowIndexToView(chosen);
            if (view >= 0) table.setRowSelectionInterval(view, view);
        }
        counter.run(); table.revalidate(); table.repaint();
    }
    private static void find(Container parent, ClassLoader runtime, List<JPanel> result) {
        if (parent instanceof JPanel panel && panel.isShowing() && panel.getClass().getName().equals("a.eg")
                && panel.getClass().getClassLoader() == runtime) result.add(panel);
        for (Component child : parent.getComponents()) if (child instanceof Container container) find(container, runtime, result);
    }
    private static void edt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Roster refresh requires EDT"); }
}
