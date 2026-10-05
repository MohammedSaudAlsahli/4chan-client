# Build scope

The approved UX is a compact discussion feed with independent image taps. The image viewer uses a black canvas, pinch/double-tap zoom, a thread-local pager, and standard Android Back. Browsing state stays mounted behind the viewer.

The user added proxy support during implementation. One configurable HTTP CONNECT proxy must cover the API, thumbnails, original images, GIFs, and videos. It has no direct fallback while enabled. Settings provide optional credentials, encrypted with Android Keystore, and a live connection test. Browser handoffs use the external browser's own connection and are labeled accordingly.

The explicit compact/WhatsApp interaction direction takes precedence over generative design selection. Visual implementation follows native Material 3 with restrained green accents, flat separated rows, and light/dark system themes. No alternative design approval round is needed for the already agreed interaction.
