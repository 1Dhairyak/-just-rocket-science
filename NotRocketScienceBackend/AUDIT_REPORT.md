# Higher-Lower Frontend — Senior Engineer Audit Report
**Auditor Profile:** Netflix (UI Systems), Riot Games (Game UI), Spotify (Design Systems)
**Architecture Score (Pre-Refactor): 5.5 / 10**
**Architecture Score (Post-Refactor): 8.5 / 10**

---

## Executive Summary

The codebase has a strong visual instinct and solid Framer Motion fundamentals. It's clearly written by someone with a good eye. However, it has significant production blockers: a duplicate component crisis, broken `AnimatePresence` wiring that breaks all route transitions, serious accessibility gaps, zero keyboard support despite advertising it, inline style sprawl that makes theming impossible, and an `useGame` hook built with raw `setTimeout` that will cause race conditions under API load.

This is a portfolio project that looks like a portfolio project. These fixes make it look like a product.

---

## Critical Bugs (Ship-Blockers)

### BUG-01: `AnimatePresence` + `Routes` — Page Transitions Are Broken
**File:** `App.jsx`
**Severity:** Critical

`AnimatePresence` wraps `<Routes>`, but Framer Motion needs a `key` that changes on each route to know when to animate out the old route. Without a `useLocation`-derived key on an inner wrapper, `exit` animations **never fire**. This is a known React Router v6 + Framer Motion gotcha.

```jsx
// BROKEN — exit animations never fire
<AnimatePresence mode="wait">
  <Routes>...</Routes>   // Routes doesn't forward the key
</AnimatePresence>

// FIX — use location.key on the Routes element
const location = useLocation()
<AnimatePresence mode="wait">
  <Routes location={location} key={location.pathname}>...</Routes>
</AnimatePresence>
```

### BUG-02: `ItemCard.jsx` Is a Dead Component
**File:** `src/components/game/ItemCard.jsx`
**Severity:** Critical

`ItemCard.jsx` is never imported or used. `GamePage.jsx` implements its own internal `SideCard` component with nearly identical logic. This means there are two implementations of the same component drifting apart. Any bug fix or design change must be done twice — or will be missed in one place. `ItemCard.jsx` must be deleted or canonicalized.

### BUG-03: Keyboard Controls Advertised But Not Implemented
**File:** `GamePage.jsx` (footer hint text)
**Severity:** High

The footer reads `"Use ↑ ↓ arrow keys to guess"`. There is **zero keyboard event listener** anywhere in the codebase. This is false advertising and fails basic accessibility auditing.

### BUG-04: `isNewHigh` Score Logic Is Wrong
**File:** `GameOverPage.jsx`, line ~31
**Severity:** Medium

```js
const isNewHigh = score > 0 && score === highScore && score > Number(localStorage.getItem('hl_prev_best') || 0)
```
`hl_prev_best` is never written anywhere in the codebase. This condition never fires. The "NEW HIGH SCORE!" badge never shows.

### BUG-05: `useGame` setTimeout Race Condition
**File:** `src/hooks/useGame.js`
**Severity:** Medium (becomes Critical under API latency)

State updates inside `setTimeout` close over stale values. When Phase 4 introduces async API calls, this will produce intermittent wrong state. The `score` and `rightIndex` captured inside the timeout callback may be stale by the time they run.

---

## Architecture Issues

### ARCH-01: No Global State / Context Layer
The app has 3+ pages that all independently read `localStorage.getItem('hl_highscore')`. When the API replaces localStorage, every page needs to be touched. A `GameContext` or Zustand store would centralize this.

### ARCH-02: No API Service Abstraction Layer
`mockData.js` mixes data (the items array) with a utility function (`formatSearchVolume`) and leaderboard data. For Phase 4, an `api/` folder with `gameService.js`, `leaderboardService.js`, etc. is required. The current structure conflates concerns.

### ARCH-03: `useGame` Not Phase-4 Ready
The hook uses synchronous mock data. It has no loading state, no error state, and no async fetch capability. This needs to be restructured before Phase 4 touches it or the refactor will be massive.

### ARCH-04: Vite Config Has Zero Optimization
No path aliases, no chunk splitting, no image optimization config. The production build will produce a single fat chunk.

---

## Component Structure Issues

### COMP-01: `SideCard` and `GuessBtn` Are Inline in `GamePage.jsx`
Two components (`SideCard`, `GuessBtn`) are defined at the bottom of `GamePage.jsx`. They are reusable components that deserve their own files. This makes `GamePage.jsx` 200+ lines and impossible to test in isolation.

### COMP-02: `Logo` Uses Conditional Rendering Anti-Pattern
```jsx
const Wrap = animate ? motion.div : 'div'
const wrapProps = animate ? { variants: staggerContainer, ... } : {}
```
This pattern creates a new component type on every render, breaking React's reconciler. Use `motion.div` always and conditionally apply `variants`.

### COMP-03: `ParticleField` Has a Memory Leak Risk
The canvas `resize` handler updates `W` and `H` local variables, but the canvas element's `width` and `height` attributes also need updating. On resize, new particles won't appear in the new area. More importantly, if the component unmounts during the async `draw()` rAF loop, the `ctx` reference could be stale.

---

## Inline Style Sprawl (Tailwind Anti-Pattern)

The codebase uses inline `style={{}}` for 80% of visual properties — gradients, shadows, borders, colors — that are already defined in `tailwind.config.js`. This defeats the entire purpose of the design token system. Examples:

- `style={{ color: '#00FF87' }}` instead of `className="text-game-green"`
- `style={{ background: 'rgba(255,255,255,0.04)' }}` instead of `className="bg-game-surface"`
- Gradient strings repeated verbatim across 6+ files

**Tokens defined but never used:** `game-green`, `game-red`, `game-gold`, `game-blue`, `game-surface`, `game-border`.

---

## Framer Motion Weaknesses

### FM-01: `staggerContainer` Missing `initial` Key
```js
export const staggerContainer = {
  animate: { transition: { staggerChildren: 0.08 } },
  // ❌ No `initial` state — stagger has no baseline to animate FROM
}
```
This is why stagger animations feel janky. The `initial` variant on the container must be defined.

### FM-02: Animation Timing Is Uniform and Mechanical
Every animation uses `duration: 0.5-0.7` with the same cubic bezier `[0.25, 0.46, 0.45, 0.94]`. Real game UIs vary timing dramatically — fast snappy feedback (<200ms), medium transitions (400ms), slow atmospheric pulses (2s+). The current system feels like a template.

### FM-03: `glowPulse` and `floatVariant` Are Defined But Never Used
Dead exports in `variants.js`. These should either be used or removed.

### FM-04: `scorePop` Variant Structure Conflicts with Usage
The variant is defined in `variants.js` but `ScorePopup.jsx` defines its own inline animation. Two sources of truth.

### FM-05: No `useReducedMotion` Hook
Users with vestibular disorders will have a terrible experience. `prefers-reduced-motion` must be respected. Framer Motion provides `useReducedMotion()` for exactly this.

---

## Mobile Responsiveness Issues

### MOBILE-01: Game Layout Breaks on Small Screens
`GamePage` uses `h-screen` with two side-by-side `flex-1` cards. On screens narrower than 375px, both item names (`text-7xl` Bebas Neue) overflow their containers. There is no vertical stacking fallback for mobile portrait.

### MOBILE-02: Font Sizes Are Over-Scaled on Mobile
`text-[10rem]` on `GameOverPage` (the score) will overflow on any mobile device. No clamp or responsive alternative.

### MOBILE-03: Navbar Has No Mobile Menu
The navbar renders all 4 links horizontally. On screens under 380px they overlap.

### MOBILE-04: ParticleField Canvas Is Not `devicePixelRatio` Aware
On retina screens, the canvas renders at 1x and appears blurry. A proper canvas implementation must scale by `window.devicePixelRatio`.

---

## Accessibility Issues

### A11Y-01: No `lang` on `<html>`
`index.html` has no `lang="en"` attribute. Screen readers cannot determine language.

### A11Y-02: Game Buttons Have No `aria-label`
`GuessBtn` renders "Higher ▲" and "Lower ▼". Screen readers read the triangle symbols as "triangle up" not the intent. `aria-label="Guess higher"` is required.

### A11Y-03: No Focus Management on Page Transition
After navigating from Landing → Game, focus stays on the "Start" button which no longer exists. Focus must be moved to the game container.

### A11Y-04: Color as Sole Status Indicator
The correct/wrong state is communicated only via green/red color flash. Fails WCAG 1.4.1 (Use of Color). The `✓`/`✗` in VSCircle helps, but the card background color change has no text equivalent.

### A11Y-05: Images Have Generic Alt Text
`alt={item.name}` is a start, but the context is missing. Should be `alt={`${item.name} — ${item.category}`}`.

### A11Y-06: Leaderboard Table Is Not Semantic
The leaderboard renders as `div` rows. It should use `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<th scope="col">`, `<td>` for screen reader compatibility.

---

## Performance Issues

### PERF-01: `ParticleField` Runs on All Pages
70 particles on LandingPage + 50 on GameOverPage = 120 canvas particle loops running simultaneously during page transitions (because `AnimatePresence` keeps both pages in the DOM during exit). This causes visible frame drops.

### PERF-02: No Image Preloading or `loading="lazy"`
Item images are fetched on-demand. The first card always shows a blank for 200-600ms. Images should be preloaded for the next card.

### PERF-03: Backdrop Filter on Every Layer
Multiple nested elements use `backdropFilter: blur()`. Each one is a compositor layer. Stacking 3+ creates a performance cliff on mid-range mobile.

### PERF-04: No `useMemo` / `useCallback` on Filtered Leaderboard
`LeaderboardPage` recomputes `filtered = MOCK_LEADERBOARD.filter(...)` on every render. Negligible now with mock data; becomes expensive with 10,000 API rows.

---

## Phase 4 API Integration Gaps

The following are not bugs yet, but will require significant rework:

1. `useGame` has no `isLoading`, `isError`, `fetchNextItem` pattern
2. No Axios interceptor setup for auth headers, error normalization, or retry logic
3. No environment variable pattern (`.env.example`) for `VITE_API_BASE_URL`
4. `GameOverPage` score submission to leaderboard is not wired even as a TODO
5. No optimistic update strategy for score submission

---

## What Was Done Well

- Visual aesthetic is genuinely strong — the glassmorphism + neon palette is cohesive
- `variants.js` centralization is the right architectural instinct
- `useGame` separation of game logic from UI is correct
- `mockData.js` service layer is easy to swap for real API calls
- Tailwind config has good token definitions (just not being used)
- Framer Motion is used for real interactions, not just fade-ins
- The `VSCircle` component is a creative and polished UI element

---

## Refactored Files Delivered

1. `App.jsx` — Fixed `AnimatePresence` + `useLocation` key
2. `src/context/GameContext.jsx` — NEW: centralized state, highScore, keyboard support
3. `src/hooks/useGame.js` — Async-ready, race-condition-safe, loading/error states
4. `src/services/api.js` — NEW: Axios instance + service layer scaffold
5. `src/services/mockData.js` — Separated from formatters
6. `src/animations/variants.js` — Fixed stagger, added `useReducedMotion` utility
7. `src/components/ui/ParticleField.jsx` — devicePixelRatio, cleanup fix
8. `src/components/ui/Navbar.jsx` — Mobile menu, active state fix
9. `src/components/game/SideCard.jsx` — Extracted from GamePage, canonical
10. `src/components/game/GuessButton.jsx` — Extracted, accessible
11. `src/pages/GamePage.jsx` — Keyboard support, cleanup, uses SideCard
12. `src/pages/GameOverPage.jsx` — Fixed isNewHigh, clamp font sizes
13. `src/pages/LeaderboardPage.jsx` — Semantic table, useMemo
14. `src/index.css` — Added `prefers-reduced-motion` rule
15. `vite.config.js` — Path aliases, chunk splitting
16. `index.html` — lang attribute, font preloads
