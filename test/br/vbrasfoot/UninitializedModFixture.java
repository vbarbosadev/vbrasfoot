package br.vbrasfoot;

import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;

/** If validation initializes code, the owned child fails immediately. */
public final class UninitializedModFixture implements Mod {
    static { if (System.nanoTime() != 0) throw new AssertionError("Mod initializer MUST NOT run in validation"); }
    public UninitializedModFixture() { throw new AssertionError("Mod constructor MUST NOT run in validation"); }
    public void initialize(ModContext context) { throw new AssertionError("Mod callback MUST NOT run in validation"); }
}
