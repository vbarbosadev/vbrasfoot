package br.vbrasfoot.api;

/** Loader lifecycle, not a sandbox security state. */
public enum ModState {
    DISCOVERED, VALIDATED, INITIALIZED, ACTIVE, FAILED, DISABLED, STOPPED
}
