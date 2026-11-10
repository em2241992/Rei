# Rei — Calm, Zero-Truncation UI/UX Refactoring Plan

Refactor Rei's visual layout and navigation scaffold to eliminate text truncation (`...`), remove redundant nested containers, and reclaim over **180dp of vertical screen space** so the **1 Thing Now Hero Card** and **Action Deck / Second Brain** tasks have generous room to breathe while preserving 100% of the existing on-device AI, SQLite FTS4, and 6-Step ADHD Executive Assistant protocols.

## User Review & Critical Decisions

> [!IMPORTANT]
> Both interaction choices from Phase 1 have been locked into this plan:

- **Confirmed Decision 1 — Inline Slide-Up Helper Tray on Capture Focus**: Tapping into the single-row Floating Quick-Capture input field smoothly slides up a compact inline helper tray directly above the bar (`❓ Clarify`, `🎙️ Presets`, `@VIP / #Topic` quick-insert chips, and `⌨️ Full Editor`) so you can enrich thoughts without a modal interruption.
- **Confirmed Decision 2 — Flat Unified Task Card List Filtered by Chip**: Removing the nested collapsible Q1–Q4 accordion boxes in favor of a **flat, unified vertical list of clean task cards** directly below the horizontal filter chip bar (`All`, `🔥 Q1`, `🛡️ Q2`, `⏳ Q3`, `🅿️ Q4`, `⏸️ Paused`, `⚠️ Stalled`), with subtle tonal elevation and clear quadrant badges on each card.

---

## 1. Overview & Core Concept

- **What It Does**: Streamlines the entire executive workspace by collapsing multi-row headers, tall energy cards, redundant summary boxes, and double-row input bars into single-line, high-signal Material 3 controls.
- **Target Audience / Persona**: An executive with Inattentive ADHD who needs instant visual clarity, zero clipped labels, and frictionless access to their **1 Thing Now** micro-step without visual noise or neon border fatigue.
- **Key Value**: Reclaims ~35% of viewport height previously consumed by chrome and eliminates every truncated button/tab label (`"✨ Or"`, `"Comms &"`, `"Deep Architect.."`).

---

## 2. User Experience & Visual Design

### Key User Flows & The 6 Layout Improvements

1. **Single-Row Consolidated Top Header**:
   - Removes the second utility pill row and the long `"100% Local • Zero Manual Formatting"` subtitle.
   - **Left**: Compact Rei neural emblem + `"Rei"` title + tappable status pill (`🟢 Nano` or `🔵 Sim`) that opens the AI Status & Diagnostics / Mode Switch sheet.
   - **Right**: Compact `"↺ Where Was I?"` pill button, Theme toggle icon, and an Overflow/Settings icon button (`⋮` / `⚙️`) housing **Learned VIP Rules**, **Backup / Restore / Start Fresh**, and **AICore Self-Test Inspector**.
   - Safely padded below the S25 Ultra camera cutout using `WindowInsets.statusBars.union(WindowInsets.displayCutout)`.

2. **Slim `<56dp` Segmented "Focus My Energy" Bar**:
   - Replaces the tall 2-line energy cards with a single-line Material 3 segmented pill bar:
     `[ ⚡ Sharp ]   [ 🟡 Steady ]   [ 🔋 Fried ]`
   - Places `"Learned Rhythm: Steady"` as a subtle, right-aligned micro-label directly above the bar so the entire section stays under `52dp` total height.

3. **Horizontal Filter Chips + Compact View Toggle (Replaces Executive Snapshot Card)**:
   - Deletes the bulky `"EXECUTIVE SNAPSHOT"` box and its 6 crowded sub-chips.
   - Replaces it with a single row containing a compact **`Tactical` / `Gantt`** segmented toggle on the left and horizontally scrollable filter chips on the right:
     `All (6)` • `🔥 Q1 (2)` • `🛡️ Q2 (2)` • `⏳ Q3 (1)` • `🅿️ Q4 (1)` • `⏸️ Paused (1)` • `⚠️ Stalled (2)` • `✅ Done`
   - Selecting any chip filters the **flat, unified task card list** immediately below.

4. **Spacious "1 THING NOW" Hero Card (16dp Padding & Zero Truncation)**:
   - Positioned as the unmistakable visual centerpiece at the top of the workspace with `16.dp` internal padding, calm tonal container contrast, and full multi-line text rendering (no `...` clipping on the task title or `<2m` starter step).
   - Displays the checkable `<2m Physical Starter Step` with its `⏱️ Time to complete` pill, followed by a clean horizontal row of 1-tap action chips when expanded/tapped:
     `Shrink It` • `Pause (Breadcrumb)` • `Draft It` • `99% Check` • `⚡ Prompts`

5. **Single-Row Floating Quick-Capture Bar + Inline Slide-Up Helper Tray**:
   - **Default State (Single Row)**:
     - **Left**: Voice Mic icon button (`🎤`).
     - **Center**: Clean pill input field (`"Dump a thought or task..."`).
     - **Right**: Compact `"Park"` (`🅿️`) pill and `"✨ Organize"` primary pill button with full, unclipped labels.
   - **Focused / Active State (Inline Slide-Up Tray)**:
     - When the user focuses the input field (or types text), an inline tray smoothly slides up directly above the input row with full-label helper chips: `❓ Clarify`, `🎙️ Presets`, `⌨️ Expand`, and 1-tap `@Corrie`, `@Debbie`, `@Wayne`, `#Epic SBO` entity pills.

6. **4-Tab Bottom Navigation Bar & Calm Tonal Surfaces**:
   - Renames the 4 bottom navigation tabs to crisp, single-word labels that never clip on any screen width:
     **`Rei`** • **`Brain`** • **`Prep`** • **`Studio`**
   - Replaces heavy `1.dp` neon borders on nested task and knowledge cards with Material 3 tonal surface hierarchy (`surfaceContainerLow` for list cards, `surfaceContainerHigh` for the Hero card and elevated bars), reserving subtle accent borders strictly for active selection or urgent `🔴 Q1` / `⚠️ Stalled` alerts.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Flat Unified Task List vs. Nested Quadrant Accordions**
  - *Chosen Approach*: Render tasks in a flat, unified vertical list filtered by the horizontal chip bar (`All`, `🔥 Q1`, `🛡️ Q2`, `⏸️ Paused`, `⚠️ Stalled`), with each card displaying its own compact quadrant pill (`🔴 Q1`, `🛡️ Q2`, `⏳ Q3`, `🅿️ Q4`) and 1-tap priority switcher when expanded.
  - *Why*: Eliminates double-nested cards (cards inside quadrant cards) which caused horizontal crowding, heavy borders, and text truncation.
- **Decision 2: Inline Slide-Up Capture Tray vs. Always-Visible 2nd Row**
  - *Chosen Approach*: Keep the bottom capture bar to a single sleek row at rest, and animate the helper chip tray (`Clarify`, `Presets`, `@VIP` tags) inline above the bar only when the input is focused or non-empty.
  - *Why*: Saves `44dp` of permanent vertical screen real estate while keeping 1-tap access to `Clarify` and `Presets` the moment you start capturing a thought.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Hierarchy Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         ReiExecutiveHubApp Scaffold                       │
├───────────────────────────────────────────────────────────────────────────┤
│ TOP BAR: Single-Row ReiExecutiveTopBar                                    │
│  ├─ Left: Emblem + "Rei" + [🟢 Nano / 🔵 Sim] Status Pill (Opens Sheet)   │
│  └─ Right: [↺ Where Was I?] Pill + Theme Toggle + [⚙️/⋮] Settings Menu    │
├───────────────────────────────────────────────────────────────────────────┤
│ WORKSPACE: ReiCommandTab (LazyColumn, Generous Spacing, Tonal Surfaces)   │
│  ├─ 1. Slim EnergyBandwidthSelectorBar (<56dp)                            │
│  │      └─ Micro-header ("Learned Rhythm") + [⚡ Sharp | 🟡 Steady | 🔋 Fried]│
│  ├─ 2. Unified View & Filter Bar                                          │
│  │      ├─ Segmented Toggle: [Tactical | Gantt]                           │
│  │      └─ Scrollable Chips: [All] [🔥 Q1] [🛡️ Q2] [⏸️ Paused] [⚠️ Stalled]│
│  ├─ 3. HeroOneThingNowCard (16dp Padding, Zero Truncation)                │
│  │      ├─ Full Task Title + Entity Pills + ⏱️ Time Estimate              │
│  │      ├─ Checkable <2m Physical Starter Step                            │
│  │      └─ Action Row: [Shrink It] [Pause (Breadcrumb)] [Draft It] [99%]  │
│  └─ 4. Flat Unified Task List (surfaceContainerLow Tonal Cards)           │
│         └─ Compact/Expandable Task Cards + 1-Tap ⚡ Prompt & Edit Icons   │
├───────────────────────────────────────────────────────────────────────────┤
│ BOTTOM DOCK (With .imePadding() & Auto-Hide Nav When Keyboard Open)       │
│  ├─ Animated Inline Helper Tray (Visible when Input Focused / Non-Empty)  │
│  │      └─ [❓ Clarify] [🎙️ Presets] [⌨️ Full Editor] [@Corrie] [#Epic]   │
│  ├─ Single-Row Floating Quick-Capture Bar                                 │
│  │      └─ [🎤 Mic] + [ "Dump a thought or task..." ] + [Park] [✨ Organize]│
│  └─ 4-Tab NavigationBar: [⚡ Rei]  [🧠 Brain]  [📅 Prep]  [💬 Studio]      │
└───────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping
- **Top Bar Status Pill & Settings Menu**:
  - Tapping `🟢 Nano` / `🔵 Sim` opens the AI Status & Diagnostics sheet (with 1-tap mode switch and S25U Self-Test Inspector).
  - Tapping the top-right Settings/Overflow icon opens a clean M3 `DropdownMenu` with **⚙️ Learned & VIP Rules**, **💾 Backup, Restore & Start Fresh**, **⚡ Run Prompt on Any Task**, and **🔬 AICore Self-Test Inspector**.
- **Filter Chip State**:
  - Local state `selectedTaskFilter` (`ALL`, `Q1`, `Q2`, `Q3`, `Q4`, `PAUSED`, `STALLED`, `DONE`) filters the flat task list instantaneously without mutating the underlying SQLite table.
- **Capture Focus Tracking**:
  - `onFocusChanged` on the quick-capture text field toggles `isCaptureFocused`, smoothly revealing the inline helper tray with `AnimatedVisibility` when focused or when `text.isNotBlank()`.
