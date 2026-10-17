package br.vbrasfoot.api;

import java.awt.Color;
import java.awt.Font;
import java.awt.Component;
import java.awt.Container;

/** Copied styling from local controls, not original resources or mutable game components. */
public record UiTheme(Font font, Color background, Color foreground, Color controlBackground, Color controlForeground) {
    public UiTheme {
        java.util.Objects.requireNonNull(font); java.util.Objects.requireNonNull(background); java.util.Objects.requireNonNull(foreground);
        java.util.Objects.requireNonNull(controlBackground); java.util.Objects.requireNonNull(controlForeground);
    }
    /** Apply on EDT to mod-owned components only. This is a palette, not every original widget/asset. */
    public void apply(Component component) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Tema exige EDT");
        component.setFont(font);
        boolean control = component instanceof javax.swing.AbstractButton;
        component.setBackground(control ? controlBackground : background);
        component.setForeground(control ? controlForeground : foreground);
        if (component instanceof Container container) for (Component child : container.getComponents()) apply(child);
    }
}
