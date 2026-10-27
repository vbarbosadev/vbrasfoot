# Read-only club-toolbar banners — SDK 0.10.0

`ModContext.registerClubBanner` is an opt-in, read-only UI integration for the
original club toolbar. A mod must declare `ui.register` and the
`ui.banner.club` capability. Registration is owned by the mod lifecycle and is
revoked on close/shutdown.

The provider runs on Swing's EDT while the club screen is available and receives
an immutable summary and ephemeral query generation only when exactly one current
human club is identified. It must be fast, non-blocking, and return an image only
for that exact club ID, name and query generation. The loader checks those values,
accepts bounded PNG data (256 KiB,
512x256 pixels), and displays it immediately after the original date label (`a.eg.Eg`).
No original controls are replaced, called, or modified. The provider has no
gameplay-write authority and cannot cause an image to be treated as an active
contract.

This interface is not a sandbox: mod code still runs in-process. The SDK/loader
does not infer financial state from banner presence. Mods should label previews
as such and persist an explicit selection when needed; the loader does not
automatically select a banner from existing mod data.

Automated coverage verifies API bounds, exact club matching, lifecycle cleanup,
and mod-panel layout. It does not establish visual fit in every game resolution;
that remains a manual UI check.
