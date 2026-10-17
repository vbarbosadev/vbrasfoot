package br.vbrasfoot.api;

import java.util.logging.Logger;

public interface ModContext {
    GameAccess game();
    Logger logger();
    /** Actions run on Swing's event dispatch thread. */
    void registerAction(String id, String label, Runnable action);
    /** Registration owned by this mod. Close on any thread to schedule removal on the EDT. */
    default AutoCloseable registerScopedAction(String id, String label, Runnable action) {
        throw new UnsupportedOperationException("Loader anterior a 0.2.0: use minLoaderVersion=0.2.0");
    }
    byte[] readData(String file) throws java.io.IOException;
    void writeData(String file, byte[] bytes) throws java.io.IOException;
}
