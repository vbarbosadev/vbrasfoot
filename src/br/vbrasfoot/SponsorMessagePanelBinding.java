package br.vbrasfoot;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;

/** Transient projection into the native panel's PRIVATE COPY of its messages. Never edits a saved game list. */
final class SponsorMessagePanelBinding implements AutoCloseable {
    private final JPanel host, nativeDetail, cards = new JPanel(new CardLayout());
    private final JTable table;
    private final List<Object> rows;
    private final Class<?> messageType;
    private final TableCellRenderer nativeRenderer;
    private final ListSelectionListener nativeSelection, selection;
    private final Map<Object, SponsorService.Message> projected = new IdentityHashMap<>();
    private final Set<Object> temporary = Collections.newSetFromMap(new IdentityHashMap<>());
    private final JTextArea body = new JTextArea();
    private final JButton receive = new JButton();
    private final JLabel status = new JLabel();
    private final Consumer<SponsorService.Message> onReceive;
    private boolean closed, busy;

    SponsorMessagePanelBinding(JPanel host, JTable table, JPanel nativeDetail, List<Object> rows, Class<?> messageType,
                              ListSelectionListener nativeSelection, Consumer<SponsorService.Message> onReceive) {
        if (!SwingUtilities.isEventDispatchThread() || !(host.getLayout() instanceof GroupLayout layout)
                || !(table.getModel() instanceof AbstractTableModel)) throw new IllegalArgumentException("Tela de mensagens inesperada");
        this.host = host; this.table = table; this.nativeDetail = nativeDetail; this.rows = rows; this.messageType = messageType;
        this.nativeSelection = nativeSelection; this.onReceive = onReceive;
        nativeRenderer = table.getDefaultRenderer(messageType);
        layout.replace(nativeDetail, cards); cards.add(nativeDetail, "native");
        JPanel custom = new JPanel(new BorderLayout(8, 8)); custom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true); body.setName("sponsor.central.body");
        custom.add(new JScrollPane(body), BorderLayout.CENTER);
        var actions = new JPanel(new BorderLayout(4, 4)); actions.add(status, BorderLayout.NORTH); actions.add(receive, BorderLayout.SOUTH);
        receive.setName("sponsor.central.receive"); custom.add(actions, BorderLayout.SOUTH); cards.add(custom, "sponsor");
        receive.addActionListener(e -> { var selected = selected(); if (!busy && selected != null
                && selected.state() == br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT) onReceive.accept(selected); });
        table.getSelectionModel().removeListSelectionListener(nativeSelection);
        selection = e -> {
            if (e.getValueIsAdjusting()) return;
            var selected = selected();
            if (selected == null) {
                ((CardLayout) cards.getLayout()).show(cards, "native"); nativeSelection.valueChanged(e);
            } else show(selected);
        };
        table.getSelectionModel().addListSelectionListener(selection);
        table.setDefaultRenderer(messageType, (t, value, selected, focus, row, column) -> {
            var message = projected.get(value);
            if (message == null) return nativeRenderer.getTableCellRendererComponent(t, value, selected, focus, row, column);
            var renderer = new DefaultTableCellRenderer();
            var component = renderer.getTableCellRendererComponent(t, column == 0 ? message.offer().date()
                    : "Patrocinio: " + message.offer().name(), selected, focus, row, column);
            renderer.putClientProperty("html.disable", Boolean.TRUE);
            renderer.setFont(t.getFont()); return component;
        });
    }
    SponsorService.Message selected() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        row = table.convertRowIndexToModel(row);
        return row < rows.size() ? projected.get(rows.get(row)) : null;
    }
    boolean current(SponsorService.Message message) {
        return !closed && host.isShowing() && selected() == message;
    }
    void busy(boolean value) { busy = value; var selected = selected(); if (selected != null) show(selected); }
    void uncertain() { busy = true; receive.setEnabled(false); status.setText("Resultado exige revisao; nao repetir. Reabra a central para consultar o historico."); }
    void install(List<SponsorService.Message> messages, Function<SponsorService.Message, Object> factory) {
        if (closed) return;
        busy = false;
        var previous = selected();
        rows.removeIf(temporary::contains); temporary.clear(); projected.clear();
        var added = new ArrayList<Object>();
        for (var message : messages) {
            Object existing = rows.stream().filter(row -> NativeSponsorSaveMessage.matches(row, message.offer().sponsor())).findFirst().orElse(null);
            Object value = existing == null ? factory.apply(message) : existing;
            projected.put(value, message);
            if (existing == null) { added.add(value); temporary.add(value); }
        }
        rows.addAll(0, added);
        ((AbstractTableModel)table.getModel()).fireTableDataChanged();
        if (!messages.isEmpty()) {
            var chosen = messages.get(0);
            if (previous != null) for (var message : messages)
                if (message.offer().sponsor().equals(previous.offer().sponsor())) chosen = message;
            for (int i = 0; i < rows.size(); i++) if (projected.get(rows.get(i)) == chosen) { table.setRowSelectionInterval(i, i); break; }
            show(chosen);
        }
        host.revalidate(); host.repaint();
    }
    private void show(SponsorService.Message message) {
        body.setText(message.offer().body()); body.setCaretPosition(0);
        var state = message.state();
        boolean pending = state == br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT;
        status.setText(switch (state) {
            case PENDING_RECEIPT -> "Aguardando recebimento. Ativar/publicar nao credita dinheiro.";
            case APPLIED_IN_MEMORY -> "Patrocinio recebido. Salve pelo jogo; pagamento nao sera repetido.";
            case ENDED -> "Patrocinio encerrado/cancelado. Recebimento bloqueado.";
            default -> "Resultado exige revisao. Nao repetir pagamento.";
        });
        receive.setText(pending ? "Receber patrocinio — " + java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(message.offer().credit())
                : state == br.vbrasfoot.api.SponsorAccess.State.APPLIED_IN_MEMORY ? "Patrocinio recebido" : "Recebimento bloqueado");
        receive.setEnabled(pending && !busy);
        ((CardLayout)cards.getLayout()).show(cards, "sponsor");
    }
    public void close() {
        if (closed) return; closed = true;
        table.getSelectionModel().removeListSelectionListener(selection);
        rows.removeIf(temporary::contains); temporary.clear(); projected.clear();
        table.clearSelection(); table.setDefaultRenderer(messageType, nativeRenderer);
        ((AbstractTableModel)table.getModel()).fireTableDataChanged();
        table.getSelectionModel().addListSelectionListener(nativeSelection);
        cards.remove(nativeDetail); ((GroupLayout)host.getLayout()).replace(cards, nativeDetail);
        host.revalidate(); host.repaint();
    }
}
