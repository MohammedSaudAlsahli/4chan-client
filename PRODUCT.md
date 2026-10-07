# Boardwalk

<!-- impeccable:product-schema 1 -->

## Platform
android

## Users and purpose
People browsing real 4chan boards, threads, and media on Android phones. The user explicitly chose a compact feed with images that open like WhatsApp attachments.

## Confirmed interaction
Small thumbnails alongside thread previews. Tapping the image opens a full-screen viewer. Pinch and double-tap zoom. Swipe between images in the same thread. Back restores the exact browsing position. Tapping discussion text opens the thread.

## Stack
Implementation choice: Kotlin and Jetpack Compose, native Android. This was selected by the implementer; it is not a user-specified framework.

## Constraints
Use real content through the read-only public API. Posting opens the original website. Honor upstream request limits and conditional requests. Disclose the content source and link to it. No official affiliation or reuse of 4chan branding. Boardwalk is a provisional name, not an approved final brand.

## First build scope
Boards, catalog, thread reader, full-screen images and video, local bookmarks and favorite boards, theme settings, loading/error states. No backend accounts or analytics.

## Home timeline (0.2.0)

Home is the starting screen and combines threads from favorite boards. Active uses the API last-modified timestamp; Newest uses creation time rather than board-local post numbers. Board labels identify the source. Existing image gestures, bookmarks, visibility controls, and proxy routing apply. Failed board refreshes retain prior content with a retry notice.

## Local reading and recovery (0.3.0)

Default favorites are news and biz on fresh installs. Nested replies remain switchable to chronological order. Bookmarks download text discussion snapshots; attachments are separate user-selected local files. Local-only backup folders hold the two latest completed JSON files; after reinstall or device change the user imports the newest file. No cloud backup or account. Proxy credentials are excluded. Existing explicit favorite choices are not overwritten.

## Conversation navigation (0.4.0)

Nested discussion uses visible parent connectors. Unquoted replies attach to the opening post. Board and thread links stay in the native reader and preserve Back history. Cross-board quoted posts appear inline with their source text and image when available. Home and board catalogs use a shared Hot, New, and Latest reply filter. Each thread row keeps its opener and offers a separate latest-reply preview.

## Reader polish and distribution (0.5.0)

Home begins with Popular Threads ranked from a sample of public board catalogs, then shows the user's favorite-board feed. It does not mirror the website's private Popular ranking. Boards use the website's named categories. Home, board catalogs, and threads support pull-to-refresh. The media viewer closes with a downward swipe at normal zoom. Nested replies use outlined cards and continuous branch connectors patterned after the user's reference image. The repository is GPL-3.0-only and includes F-Droid metadata, screenshots, icon assets, a GitHub build workflow, and release guidance.
