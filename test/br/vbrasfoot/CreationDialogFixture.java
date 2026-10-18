package br.vbrasfoot;

/** Owned branch structure only; never opens Swing dialogs or invokes the game. */
public final class CreationDialogFixture {
    public static int choice, seen = 99, accepted, proceeded;
    public static boolean failDialog, failCandidate;
    public void oq() { pl(); }
    public void pl() { int result = confirm(); seen = result; if (result == 0) accepted++; else AB(); }
    public void AB() {
        HookProbe.creationDialog("AB", "CANDIDATE_ENTER", 0, null, null);
        if (failCandidate) throw new IllegalStateException("owned candidate failure"); proceeded++;
    }
    public static int confirm() { if (failDialog) throw new IllegalArgumentException("owned dialog failure"); return choice; }
}
