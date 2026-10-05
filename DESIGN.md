---
name: Boardwalk
description: Compact native Android reading with independently accessible media.
colors:
  light-primary: "#31694A"
  light-primary-container: "#D7EDD8"
  light-on-primary-container: "#133922"
  light-secondary: "#536454"
  light-secondary-container: "#E0E9DC"
  light-background: "#FAFBF6"
  light-surface: "#FAFBF6"
  light-surface-container: "#F0F3EB"
  light-surface-container-low: "#F4F6F0"
  light-surface-container-highest: "#E5E9E0"
  light-on-surface: "#1A211B"
  light-on-surface-variant: "#4D574D"
  light-outline-variant: "#D3DAD0"
  dark-primary: "#A0D4AB"
  dark-on-primary: "#07391C"
  dark-primary-container: "#254D32"
  dark-on-primary-container: "#D2EDD6"
  dark-secondary: "#B5CBB5"
  dark-secondary-container: "#354638"
  dark-background: "#111712"
  dark-surface: "#111712"
  dark-surface-container: "#1D241E"
  dark-surface-container-low: "#192019"
  dark-surface-container-highest: "#303A30"
  dark-on-surface: "#E1E9DE"
  dark-on-surface-variant: "#BDC9BB"
  dark-outline-variant: "#404C40"
  light-on-primary: "#FFFFFF"
  viewer-background: "#000000"
  viewer-foreground: "#FFFFFF"
  viewer-muted: "#C9CFC9"
  viewer-error: "#FFDAD6"
rounded:
  thumbnail: "12dp"
  attachment-badge: "4dp"
spacing:
  badge-inset: "4dp"
  inline-gap: "6dp"
  compact-gap: "8dp"
  content-gap: "12dp"
  feed-gap: "14dp"
  page-inset: "20dp"
  section-top: "24dp"
  empty-horizontal: "32dp"
  empty-vertical: "48dp"
components:
  media-thumbnail:
    backgroundColor: "{colors.light-surface-container-highest}"
    rounded: "{rounded.thumbnail}"
    size: "88dp"
  media-thumbnail-dark:
    backgroundColor: "{colors.dark-surface-container-highest}"
    rounded: "{rounded.thumbnail}"
    size: "88dp"
  thread-row:
    padding: "14dp 12dp 14dp 20dp"
  bookmark-action:
    size: "48dp"
  fullscreen-viewer:
    backgroundColor: "{colors.viewer-background}"
    textColor: "{colors.viewer-foreground}"
---

# Design System: Boardwalk

## Overview

**Creative North Star: "Compact reading, full-screen media"**

Boardwalk uses a quiet green native Android interface around dense, readable conversations. Small images share the feed with short text previews; media gets its own black, full-screen surface when opened. This describes the implemented direction, not a new brand identity. Boardwalk remains a provisional name.

The implementation uses Jetpack Compose Material 3 with explicit light and dark color schemes. Preserve native controls and accessibility semantics. Dimensions below are Android density-independent units (`dp`); text uses inherited, scalable Material typography (`sp`), not CSS pixels.

**Key Characteristics:**
- Compact, flat conversation rows.
- Separate thumbnail and discussion actions.
- Green accents, restrained neutral surfaces, and explicit theme choices.
- Black media viewing with pinch, double-tap, and paging controls.

## Colors

The palette pairs green accents with pale green-white surfaces in light mode and near-black green surfaces in dark mode. Frontmatter records explicit source colors; framework defaults remain framework-owned.

### Primary

The light and dark `primary` roles color interaction cues, reply icons, bookmarks, section labels, quote links, and quoted lines. `on-primary` and the primary container roles support native Material controls.

### Secondary

The `secondary` and `secondary-container` roles support the native Material scheme, including selected control treatments. Use theme roles rather than hard-coded light colors when extending a screen.

### Neutral

`background` and `surface` share a value within each theme. Container roles provide tonal separation; `surface-container-highest` backs thumbnails and loading placeholders. `on-surface-variant` holds supporting text. Feed dividers use `outline-variant` at 55% opacity. Attachment badges use the theme surface at 94% opacity.

The full-screen viewer deliberately uses its own black background and white controls in both themes, with muted metadata and a pale error message. Material error containers used elsewhere inherit library defaults; this app does not define custom error tokens.

## Typography

`BoardwalkTheme` installs unmodified Material 3 `Typography()`. There is no bundled display family, custom font scale, or numeric `sp` override in the UI source. Preserve the library's native font and font-scaling behavior; do not invent independent typography tokens.

- **Headline small:** directory introduction and thread subject.
- **Title large:** empty-state title.
- **Title medium:** board titles and viewer board heading.
- **Title small:** catalog titles and section labels.
- **Body medium:** thread comments, explanatory copy, and settings results.
- **Body small:** compact previews, filenames, and secondary connection copy.
- **Label large:** post author.
- **Label medium:** top-bar subtitle, reply totals, and viewer position.
- **Label small:** Home board-and-age labels, reply/image counts, post IDs, timestamps, and attachment badges.

Catalog titles and previews each truncate after two lines. Post authors truncate after one line. A compact feed does not justify fixed pixel fonts or removing Android font scaling.

## Layout

The app uses a single scrolling content column, centered within an explicit maximum width of 760dp. At a window width of 700dp or greater, navigation becomes a rail; smaller windows use a bottom bar except inside a thread. Scaffold and system-bar insets remain part of the layout. Settings also apply keyboard insets.

Catalog rows use 20dp leading and 12dp trailing padding, 14dp vertical padding, and a 14dp gap between children. The thumbnail is 88dp square, discussion text takes remaining width, and the bookmark action has a 48dp box. Rows without media do not reserve an empty thumbnail column. Thread posts use 20dp padding and 12dp vertical spacing. Search uses 20dp horizontal and 8dp vertical exterior padding. Filter chips use an 8dp gap.

Home, the catalog, thread, and board directory use lazy lists. Saved route state preserves browsing context; thread position is also recorded locally. Opening media overlays the reader instead of replacing its scrolling content.

## Elevation & Depth

Custom feed rows are flat and separated by inset dividers; no custom shadow system is defined. Thumbnail placeholders and Material container roles provide tonal depth. Native Material controls, the navigation surfaces, and quote dialogs retain their library defaults. Do not document those defaults as app-specific shadow tokens.

## Shapes

Thumbnails and their loading placeholders use the same 12dp rounded shape. Small attachment badges use a 4dp radius. Search explicitly selects `MaterialTheme.shapes.large`; other outlined fields and native controls inherit their Material shapes. No custom global shape theme is installed.

## Components

### Thread row and media thumbnail

A thumbnail is an independent button that opens media; the text column separately opens the discussion. The bookmark is a third action. Thumbnail images crop to fill their square. Spoiler images show an icon and label until explicitly opened. WEBM, GIF, and PDF attachments carry a small bottom-right format badge. Loading and broken-image states retain thumbnail geometry.

### Home timeline

Home combines favorite-board threads using the same compact `ThreadRow` geometry and independent actions. Above each title, a primary-colored label-small line shows the board and relative age, truncates to one line, and leaves 5dp below it. Active order uses last-modified age; Newest uses creation age. Native Active/Newest filter chips use the existing 8dp gap. A headline-small introduction and body-medium board count or loading progress precede the feed. Partial failures retain available threads and use an error-container message with Retry; an empty favorites list offers Choose boards.

### Buttons and fields

Native `Button`, `OutlinedButton`, `TextButton`, and `IconButton` supply interaction, disabled, focus, and press treatment. They are used for retry, connection save/test, external links, bookmarks, and navigation. No app-specific hover animation is defined.

Search is a single-line `OutlinedTextField` with a search icon and conditional clear action. Connection fields use native outlined inputs and appropriate keyboard types; the password has an explicit reveal control. Connection controls disable while testing. Do not substitute web controls or claim custom focus-ring tokens.

### Chips, switches, and navigation

Native `FilterChip` selects Home Active/Newest ordering, catalog ordering, Nested/Chronological reply layout, or System/Light/Dark appearance. Native switches control the board directory filter and proxy use. Home, Boards, Saved, and Settings are the four navigation destinations. Material selected states communicate current selection. The app bar shows route context, a back action when applicable, and refresh/bookmark actions.

### Reader and quote dialog

Comments use body-medium typography with primary-colored links and quote lines. Text spoilers require an explicit reveal action. Tapping an in-thread quote opens an `AlertDialog`; its body scrolls within a 420dp maximum height. Quote media can open the same full-screen viewer.

Thread reading offers Nested and Chronological chips with the existing 20dp horizontal inset and 8dp gap. Nested replies add 12dp leading indentation per depth level, capped at 36dp beyond the base 20dp inset; deeper conversations do not keep narrowing. Reply-reference text buttons wrap in a flow row with 4dp gaps and open the quote dialog. A parent with descendants offers a counted Show/Hide replies text button in Nested mode. Chronological mode removes nesting indentation and collapse controls.

### Saved discussions and local backup

Saved rows distinguish “Saving discussion…”, “Available offline · discussion only”, and “Bookmark only · discussion not downloaded”. A missing offline copy offers Download discussion, with errors shown using the native error color. Open offline discussions display saved age and explain that refresh fetches current posts and media loads separately. Keep discussion availability distinct from attachment downloads.

Settings places Local backup between Appearance and Connection, reusing a 20dp horizontal inset, 8dp vertical gaps, and body-medium/body-small copy. A filled button chooses or changes a local folder; an outlined button imports a backup; a conditional text button stops updating it. Busy state disables these controls and displays a linear progress indicator. Restore uses a native confirmation dialog with Restore and Cancel actions and states what will be replaced.

The backup copy describes two latest completed timestamped JSON files in the chosen local folder, updated while the app is used. It explicitly says there is no cloud backup or account, and that media files and proxy passwords are separate. File selection uses native local-only document pickers; surface missing-picker and operation results inline.

### Full-screen media viewer

The viewer respects system-bar insets and uses light system icons against black. Images fit the available viewport initially. Pinch zoom clamps to 1–5×; double-tap toggles between 1× and 3× around the tap. Pan is bounded, and horizontal paging is disabled while zoomed so gestures do not compete. Moving off a page resets its zoom. Arrow buttons offer an alternative to swiping. Back returns to the underlying reading position.

A white download icon in the viewer header is labeled “Save attachment to device” and opens the native local-only save picker for the current attachment. Save status and picker failures use body-small copy above the bottom controls. This action is separate from saving discussion text or backing up app data.

Only the active video page owns its player; video starts paused, pauses when the app stops, and releases on disposal. PDFs open in the browser. Loading indicators and retry states appear on the media surface. The reader beneath the viewer is removed from accessibility semantics while covered.

### Loading, empty, and error states

Initial loading uses five static placeholder rows with the same thumbnail size and shape as content. Refreshing existing content adds a linear progress indicator. Errors with existing content use a Material error-container banner and Retry action. Empty or initial failure states use centered title/body copy and an optional action; no decorative illustration is defined.

## Do's and Don'ts

### Do:
- **Do** preserve separate thumbnail, discussion, and bookmark targets.
- **Do** use theme roles and verify both light and dark appearance.
- **Do** retain native dp geometry, scalable typography, system insets, and accessible action labels.
- **Do** preserve list position when closing full-screen media.

### Don't:
- **Don't** turn the compact conversation feed into oversized image cards.
- **Don't** page the gallery while a user is panning a zoomed image.
- **Don't** invent web hover treatments, fixed pixel typography, or custom shadows absent from the native app.
- **Don't** imply official 4chan branding or treat the provisional app name as an approved identity.
