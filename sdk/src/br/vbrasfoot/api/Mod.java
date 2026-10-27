package br.vbrasfoot.api;

public interface Mod {
    void initialize(ModContext context) throws Exception;
    default void shutdown() throws Exception { }
}
