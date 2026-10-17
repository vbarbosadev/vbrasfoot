package br.vbrasfoot.api;

import java.awt.Color;
import java.awt.Font;
import java.awt.Component;
import java.awt.Container;

/** Copied styling from local controls, not original resources or mutable game components. */
public record UiTheme(Font font, Color background, Color foreground, Color controlBackground, Color controlForeground) {
    /** Original, asset-free styling roles. Requires SDK/loader 0.7.0. */
    public enum Role { BODY, HEADER, TITLE, SECTION, PRIMARY_BUTTON, SECONDARY_BUTTON, INPUT, TABLE, STATUS }
    private static final String ROLE_PROPERTY = "vbrasfoot.ui.role";
    private static final Color DARK = new Color(18, 18, 18), GREEN = new Color(36, 91, 45),
            YELLOW = new Color(244, 193, 26), LINE = new Color(215, 220, 216);
    public UiTheme {
        java.util.Objects.requireNonNull(font); java.util.Objects.requireNonNull(background); java.util.Objects.requireNonNull(foreground);
        java.util.Objects.requireNonNull(controlBackground); java.util.Objects.requireNonNull(controlForeground);
    }
    /** Apply base defaults without overwriting custom colors/fonts; explicit roles survive reapplication. */
    public void apply(Component component) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Tema exige EDT");
        applyTree(component, background, foreground);
    }
    /** Style a mod-owned component; never use this on original game controls. EDT only. */
    public <T extends javax.swing.JComponent> T style(T component, Role role) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Tema exige EDT");
        component.putClientProperty(ROLE_PROPERTY, java.util.Objects.requireNonNull(role));
        applyRole(component, role);
        return component;
    }
    private void applyTree(Component component, Color inheritedBackground, Color inheritedForeground) {
        Object role = component instanceof javax.swing.JComponent widget ? widget.getClientProperty(ROLE_PROPERTY) : null;
        if (role instanceof Role selected) applyRole((javax.swing.JComponent) component, selected);
        else {
            if (component.getFont() == null || component.getFont() instanceof javax.swing.plaf.UIResource) component.setFont(font);
            boolean control = component instanceof javax.swing.AbstractButton;
            if (component.getBackground() == null || component.getBackground() instanceof javax.swing.plaf.UIResource) component.setBackground(control ? controlBackground : inheritedBackground);
            if (component.getForeground() == null || component.getForeground() instanceof javax.swing.plaf.UIResource) component.setForeground(control ? controlForeground : inheritedForeground);
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) applyTree(child, component.getBackground(), component.getForeground());
    }
    private void applyRole(javax.swing.JComponent component, Role role) {
        component.setFont(font.deriveFont(Font.PLAIN, 12f));
        switch (role) {
            case BODY, INPUT -> { component.setBackground(Color.WHITE); component.setForeground(DARK); }
            case HEADER -> {
                component.setBackground(DARK); component.setForeground(Color.WHITE);
                component.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 3, 0, GREEN), javax.swing.BorderFactory.createEmptyBorder(10, 14, 10, 14)));
            }
            case TITLE -> { component.setBackground(DARK); component.setForeground(Color.WHITE); component.setFont(font.deriveFont(Font.BOLD, 18f)); }
            case SECTION -> {
                component.setBackground(GREEN); component.setForeground(Color.WHITE); component.setFont(font.deriveFont(Font.BOLD, 12f));
                component.setBorder(javax.swing.BorderFactory.createEmptyBorder(7, 10, 7, 10));
            }
            case PRIMARY_BUTTON, SECONDARY_BUTTON -> {
                component.setBackground(role == Role.PRIMARY_BUTTON ? GREEN : DARK); component.setForeground(Color.WHITE);
                component.setFont(font.deriveFont(Font.BOLD, 12f)); component.setOpaque(true);
                component.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(role == Role.PRIMARY_BUTTON ? GREEN.darker() : LINE), javax.swing.BorderFactory.createEmptyBorder(7, 12, 7, 12)));
                if (component instanceof javax.swing.AbstractButton button) { button.setContentAreaFilled(true); button.setFocusPainted(true); }
            }
            case TABLE -> {
                component.setBackground(Color.WHITE); component.setForeground(DARK);
                if (component instanceof javax.swing.JTable table) {
                    table.setRowHeight(Math.max(23, table.getFontMetrics(table.getFont()).getHeight() + 6));
                    table.setGridColor(LINE); table.setSelectionBackground(YELLOW); table.setSelectionForeground(DARK);
                    table.setShowHorizontalLines(true); table.setShowVerticalLines(false);
                    table.getTableHeader().setBackground(new Color(237, 239, 237)); table.getTableHeader().setForeground(DARK);
                    table.getTableHeader().setFont(font.deriveFont(Font.BOLD, 12f));
                    table.setFillsViewportHeight(true);
                }
            }
            case STATUS -> { component.setBackground(DARK); component.setForeground(YELLOW); component.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 12, 4, 12)); }
        }
    }
}
