package br.vbrasfoot.api;

import javax.swing.JComponent;

/** Mod-owned component lifecycle. Created/closed on EDT; never receives original game components. */
public interface ModPanel extends AutoCloseable {
    JComponent component();
    /** Release listeners/timers/model; no blocking I/O or gameplay. May run after mod shutdown. */
    @Override void close();
}
