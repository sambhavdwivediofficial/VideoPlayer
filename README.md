# Video Player

A private, offline-first video player built for a single Android device. No accounts, no backend, no analytics, no casting, no network calls — the app's only job is to find the video already on the device and play it well.

## Why this exists

Most video players on the Play Store carry weight that has nothing to do with playing a video: streaming tie-ins, ad networks, cloud backup prompts, permissions the app doesn't need to function. This project takes the opposite approach — a small, native Android app that does exactly one thing, indexing and playing local video, using nothing but on-device APIs. If a feature would require a server, a login, or an internet permission, it doesn't belong here.

## What it does

- **Library scanning** — Indexes every video visible to `MediaStore` on the device, including its real width, height, duration, and size, with no manual folder configuration required.
- **A library that mirrors the content, not a grid template** — The home screen lays videos out in a Pinterest-style masonry grid, where every tile's size is derived from that video's actual aspect ratio rather than a fixed square or a forced crop. A landscape recording sits wide, a portrait clip sits tall, and the grid finds its own shape column by column instead of imposing one.
- **Full playback engine** — Built on Media3/ExoPlayer, rendered full-screen with the system bars hidden while a video plays, and released cleanly the moment playback is left so nothing keeps running silently in the background.
- **Gesture-first controls** — Swiping horizontally moves between the next and previous video in the library, with the neighbouring video sliding in under the finger and either committing or springing back depending on how far the swipe travels. Double-tapping the left or right edge of the screen seeks ten seconds in that direction with a brief animated indicator; double-tapping the centre toggles play and pause. Dragging vertically on the right half adjusts volume and on the left half adjusts screen brightness, each with its own on-screen readout while the finger is down.
- **A speed picker built to be felt, not just tapped** — Playback speed opens as a ruler the finger can drag across, with the current value shown live above it, alongside fixed presets for a single tap to a common speed.
- **Resume, not restart** — Leaving a video and coming back to it later picks up from the exact position it was left at, rather than starting over.
- **Screen lock** — A single toggle disables every gesture and control except itself, so the screen can be held or pocketed mid-playback without accidental taps changing anything.
- **Aspect ratio control** — A dedicated toggle cycles the video between fitting entirely on screen, filling the screen, and zooming to remove letterboxing, independent of the device's own aspect ratio.
- **Picture-in-Picture** — Playback can be shrunk into a floating window without stopping, for continuing to watch while using another app.

## Design principles

- **Black, and nothing competing with it.** There is no secondary background color anywhere in the app — every screen, from the library grid to the player, sits on true black, so the video itself is always the brightest and most detailed thing on screen.
- **The thumbnail is the video, not a card about it.** Library tiles carry no title, no duration badge, no overlay text of any kind — just the frame itself, edge to edge, because a video library should look like the videos in it.
- **Controls that get out of the way.** Playback controls fade out automatically a few seconds after the last touch and reappear on a single tap, so the default state of watching something is an unobstructed screen, not a permanent overlay.
- **Every gesture has one job.** Horizontal movement changes the video, vertical movement on either half adjusts one property, and taps are zoned by position rather than overloaded — nothing on screen has to guess what a touch was trying to do.

## How it's put together

The app keeps three concerns separate. A repository layer talks to `MediaStore` directly and hands back plain data — it has no awareness of Compose, gestures, or the player. A single ExoPlayer instance lives inside a ViewModel and is exposed as state — current video, playback position, duration, speed, and queue position — through `StateFlow`s, so the player survives configuration changes like rotation without losing its place or restarting. The screens themselves hold no playback logic; they render whatever state they're given and forward gestures and taps back up as intent.

Thumbnails are decoded once, cached to both memory and disk, and the first screen's worth is warmed in the background as soon as the library finishes scanning — so returning to the app shows a populated, responsive grid immediately rather than one that fills in tile by tile.

## Built with

- Kotlin, targeting current Android APIs (minimum SDK 26)
- Jetpack Compose for the entire UI — no XML layouts
- Media3 (ExoPlayer) for playback
- Coil, with video frame decoding and a dedicated memory and disk cache, for thumbnails
- Android's native `MediaStore` for library scanning — no third-party indexing

## Current limitations

- The library is a single flat grid; there's no folder-based or album-style browsing yet.
- Subtitle and caption files are not detected or rendered.
- There's no in-app search or filtering — the full library is always shown.
- Favorites and custom playlists aren't implemented.

These are deliberate scoping choices for the current version, not oversights, and can be extended later without restructuring what's already built.
