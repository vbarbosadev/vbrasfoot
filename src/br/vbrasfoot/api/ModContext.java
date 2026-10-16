package br.vbrasfoot.api;

import java.util.logging.Logger;

public interface ModContext {
    GameAccess game();
    Logger logger();
    /** Actions run on Swing's event dispatch thread. */
    void registerAction(String id, String label, Runnable action);
    byte[] readData(String file) throws java.io.IOException;
    void writeData(String file, byte[] bytes) throws java.io.IOException;
}
