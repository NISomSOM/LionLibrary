---
name: LionLibrary — Nuvio UI reference
description: Observed Nuvio 0.5.8-beta reference with a separate LionLibrary implementation record.
colors:
  reference-accent: "#FB8C00"
  reference-accent-strong: "#EF6C00"
  reference-background: "#000000"
  reference-surface: "#1E1A1A"
  reference-input: "#24201A"
  reference-text: "#F5F7F8"
  reference-text-secondary: "#B8BEC5"
  reference-text-muted: "#969CA3"
  reference-text-inverse: "#111111"
  reference-outline: "#252A2A"
  reference-focus: "#FFA726"
  reference-danger: "#E36A8A"
typography:
  page:
    fontFamily: "JetBrains Sans"
    fontSize: "38sp"
    fontWeight: 700
    lineHeight: "42sp"
    letterSpacing: "-1.2sp"
  headline:
    fontFamily: "JetBrains Sans"
    fontSize: "26sp"
    fontWeight: 600
    lineHeight: "30sp"
    letterSpacing: "-0.8sp"
  shelf-title:
    fontFamily: "JetBrains Sans"
    fontSize: "18sp"
    fontWeight: 600
    lineHeight: "24sp"
  detail-section:
    fontFamily: "JetBrains Sans"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "24sp"
  title:
    fontFamily: "JetBrains Sans"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "20sp"
  hero-action:
    fontFamily: "JetBrains Sans"
    fontSize: "16sp"
    fontWeight: 700
    lineHeight: "20sp"
  body:
    fontFamily: "JetBrains Sans"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "22sp"
  caption:
    fontFamily: "JetBrains Sans"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  action:
    fontFamily: "JetBrains Sans"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "18sp"
  section-label:
    fontFamily: "JetBrains Sans"
    fontSize: "12sp"
    fontWeight: 600
    lineHeight: "14sp"
    letterSpacing: "0.8sp"
rounded:
  poster: "12dp"
  control: "12dp"
  button: "16dp"
  library-chip: "18dp"
  surface: "24dp"
  modal: "28dp"
  pill: "999dp"
spacing:
  tight: "4dp"
  caption: "6dp"
  control: "8dp"
  header-to-shelf: "10dp"
  list: "12dp"
  rail: "14dp"
  screen: "16dp"
  detail-screen: "18dp"
  modal: "20dp"
  section: "24dp"
components:
  hero-action:
    backgroundColor: "{colors.reference-text}"
    textColor: "{colors.reference-background}"
    rounded: "{rounded.pill}"
    padding: "12dp 28dp"
    typography: "{typography.hero-action}"
  input:
    backgroundColor: "{colors.reference-input}"
    textColor: "{colors.reference-text}"
    rounded: "{rounded.control}"
    typography: "{typography.body}"
  dropdown:
    backgroundColor: "{colors.reference-surface}"
    textColor: "{colors.reference-text}"
    rounded: "{rounded.control}"
    padding: "8dp 12dp"
    typography: "{typography.action}"
  poster:
    rounded: "{rounded.poster}"
    width: "126dp"
  detail-action:
    backgroundColor: "{colors.reference-text}"
    textColor: "{colors.reference-background}"
    rounded: "{rounded.pill}"
    height: "52dp"
  settings-row:
    backgroundColor: "{colors.reference-surface}"
    textColor: "{colors.reference-text}"
    padding: "14dp 16dp"
    typography: "{typography.body}"
---

# LionLibrary design reference

## Overview

**Purpose:** use Nuvio's visual hierarchy, consistent components and measured spacing
to polish LionLibrary. This file preserves the inspected Nuvio reference and records
our implementation separately. The frontmatter describes **Nuvio**, not the active
LionLibrary theme. The implementation record below and the adaptation tables define
our deliberately different choices.

**Creative North Star: "A cinematic local library."** Artwork carries the atmosphere;
controls make browsing and playback clear. Use one coherent type scale, measured
gaps, a restrained orange accent, and dark surfaces. Make the next useful action
easy to find without surrounding every piece of metadata with another card.

### LionLibrary implementation - 2026-10-11

Home, Library, Settings and the Details header now use the reference's
hierarchy and spacing through original Compose components. LionLibrary keeps its
orange identity and existing local-library workflows. Validation and device review
are recorded in [UI polish](docs/UI_POLISH.md) and the subsequent
[interaction fixes](docs/UI_REFINEMENTS.md) and [follow-up feedback fixes](docs/UI_FEEDBACK_FIXES.md) and [player visual polish](docs/PLAYER_UI_POLISH.md). Library now owns the search workflow;
the observed Nuvio Search section remains reference material.

| Active LionLibrary decision | Implementation |
| --- | --- |
| Identity | Orange #FF5722; background #0B0D10, surface #171A20, outline #232831, warm selection #36221B |
| Font | Bundled, licensed Inter 400/500/600/700; system text scaling is respected |
| Page headings | 38/42 sp, bold, tracking -1.2 sp |
| Shelf headings | 18/24 sp, semibold; full category heading remains a navigation action |
| Reading / captions | Body 15/22 sp; poster title 14/18 sp with up to two lines; standard small labels 12 sp; compact resume metadata/status 10 sp per the requested reference |
| Rhythm | 16 dp page gutter, 18 dp Details gutter, 12 dp item gap, 24 dp shelf separation, 6 dp caption gap |
| Posters | 126 dp shelf width, 2:3 artwork, 12 dp artwork corners; caption/year are not rounded or clipped; 16 dp bookmark glyph in a 24 dp circular badge; standard touch target |
| Grids | Adaptive minimum 104 dp; widen cells above 130% text scale, with 12 dp row/column gaps |
| Home hero | 58% screen height bounded to 360-600 dp, with extra space for large text; extends under status bar; clipped crossfade/parallax layers and live-offset indicators; metadata measures available width and omits whole genres to remain one line |
| Resume | 247 dp, 16:9; episode code/title/episode-title caption, top-right timing badge and inset orange progress; tap resumes, long press/accessibility actions open options; caption moves below the image above 140% text scale |
| Navigation | Centered translucent Material capsule over page content, capped at 280 ? 64 dp for normal text; Home/Library/Settings, orange selection, current-tab taps do not navigate, persistent whole labels (two rows above 140% text scale); trailing scroll inset preserves final items and navigation yields to the keyboard |
| Settings | Same key, two folders, playback overrides, scan, history and About; quieter group labels and folder rows |
| Details | White primary action plus independent orange watchlist state with 16 dp glyph and 48 dp target; Play for unstarted shows, Next Episode after any episode completes, Resume/Replay when appropriate; opaque toolbar only after the primary action passes it |
| Player footer | Smaller outlined translucent pill; +90s/Subs/Audio retained; 12/16 sp labels, 16 dp icons and 48 dp touch targets; elapsed/total badges sit beneath opposite ends of the full-width seek bar |
| Motion | 150 ms quick feedback, 220 ms general screen/carousel motion; 240 ms media entrance fade after 70 ms navigation delay; Details also fades actual loaded content for 240 ms, without restarting on ordinary state updates; easing (0.2, 0, 0, 1); swipe offsets read in graphics layers/indicator draw |
| Autoplay | Eight-second decorative rotation only while resumed; stops after dragging or action, with touch exploration or reduced motion |

The episode list and its actions keep their existing logic and layout structure.
Only the shared typography and formatting affect that area. No Nuvio source,
artwork, font files, accounts, discovery feeds, settings menus or image-fetch paths
were imported. Existing cached artwork rendering and database/scanning/playback
contracts remain in place.

### Evidence and confidence

Inspected on **2026-10-11**, using the user's connected Vivo V2515 phone:

| Item | Verified value |
| --- | --- |
| App | `com.nuvio.app`, version **0.5.8-beta**, version code **140** |
| Display | 1216 × 2640 physical pixels; effective density **528 dpi**, or **3.3 px/dp** |
| Logical width | Approximately **368.5 dp**; raw screen height approximately 800 dp before insets |
| System font scale | **1.15×**; Nuvio's inspected theme overrides its composition to **1×** |
| Selected appearance | **Amber**, **AMOLED Black on**, **Adaptive** navigation, **Glass glow on** |
| Poster settings | **Balanced**, width **126 dp**, configured height **189 dp**, corners **12 dp** |
| Original Library layout | Horizontal shelves; temporarily inspected the vertical grid and restored shelves |
| Source snapshot | Release tag `0.5.8-beta`, commit `1ae5066a4dd5026228625e40082eacc3ada68436` |

Evidence labels used in this document:

- **Observed:** seen in phone screenshots or exercised without invoking media changes.
- **Source:** read in the matching release. A declared token is not proof that every
  component uses it; component overrides are recorded separately.
- **Proposed:** a LionLibrary adaptation or acceptance requirement, not a claim about Nuvio.

The repository's current default branch differs from this release. Use the pinned
[release source](https://github.com/NuvioMedia/NuvioMobile/tree/1ae5066a4dd5026228625e40082eacc3ada68436),
not screenshots or constants from another version.

### Coverage and review artifacts

| Area | Phone states inspected | Further evidence |
| --- | --- | --- |
| Home | Hero, pagination, Continue Watching, collection/category shelves, scrolled/collapsed and expanded navigation | Hero sizing, poster and progress components in source |
| Search | Idle Discover, empty catalog, input focus and keyboard, loading skeletons, movie/series results, recent query, type selection sheet | Debounce and offline/error paths in source |
| Library | Saved shelves, vertical grid, sort sheet, poster long-press actions | Chip, grid and sorting source |
| Settings | Root top/lower groups; Layout theme/display/Home/detail entries; Home Layout; poster preview; Playback switches; navigation preview/options | Shared rows, secret input, group, responsive layout source |
| Details | Loaded movie and series heroes, resume actions, expanded secondary actions, metadata, long text, production, cast, trailers, extra information, collection and scrolled header | Description expansion, missing artwork, tablet layout and action source |

The **episode list and season layout are excluded** from the proposed redesign.
Service/account sign-in, purchases, every integration subpage, every custom theme,
offline failures and tablet rendering were not exercised on the phone. Relevant
shared component/error/responsive behavior is documented from source. This is
coverage of the requested surfaces and their principal UI patterns, not exhaustive
testing of every possible Nuvio configuration.

There are **33 screenshots** plus sanitized UI hierarchies in the ignored local
`build/nuvio-review/` directory. The [screenshot gallery](build/nuvio-review/index.html)
labels each captured state. The [evidence manifest](build/nuvio-review/manifest.json)
records the source snapshot, font metadata, screenshot hashes and capture dimensions.
System status widgets and the right-edge Vivo sidebar appear in captures; they are
phone chrome, not Nuvio design. Artwork and watch activity are reference content,
not LionLibrary assets to distribute.

### What carries across to LionLibrary

| Reference pattern | LionLibrary adaptation |
| --- | --- |
| Artwork-led hero, clear title and one prominent action | Home hero remains **View details**; only playable Details/resume actions say Play or Resume |
| Consistent rounded posters and restrained captions | One title treatment per card; preserve accessible names and local-artwork fallbacks |
| Continue Watching | Preserve **Jump Back In**, resume positions and existing overflow actions; adopt the compact landscape card hierarchy |
| Saved/Cloud and remote catalogs | Preserve the local Library, watchlist and existing filters; no cloud/source tabs or streaming integrations |
| Profile destination opening Settings | Keep **Settings**, with existing Home, Library, Search, Settings destinations; no new profile/account model |
| Amber theme and pure-black background | Keep LionLibrary orange and its established dark palette; reference colors are comparison values |
| JetBrains Sans and tight, consistent type roles | Record the exact reference face; adopt its hierarchy with one bundled, properly licensed family across every Material role |
| Extensive Layout and integration preferences | Adopt shared row, input, switch and group styling; preserve LionLibrary's simpler options |
| Metadata/cast/trailer/production content | Restyle metadata we actually have; new data sections are optional later features, not part of a cosmetic change |
| Details episodes | Keep LionLibrary's existing episode list and episode actions unchanged |

Preserve exactly **Movies and Shows** folder roots. Anime movies in Movies remain
movies and appear in Movies and Anime. Preserve offline browsing, playable raw files,
local IDs, history, watchlist, subtitles, engine selection, existing filtering and
non-destructive migrations. Do not add onboarding, manual match or Health screens.
These boundaries come from the user's decisions and [AGENT.md](References/AGENT.md).

## Colors

### Inspected reference palette

Nuvio's selected Amber palette uses a warm orange against neutral/warm near-black
surfaces. AMOLED changes the page background to pure black; it does not turn every
input, sheet and settings group black. The exact reference values are in frontmatter.

| Role | Use in the inspected UI | Evidence |
| --- | --- | --- |
| `reference-accent` | Selected destination, checks, switches, progress, small action labels | `ThemeColors.Amber`, phone |
| `reference-accent-strong` | Secondary accent variant | Palette source; not a second universal CTA color |
| `reference-background` | Main pages, opaque sticky headers and Details body | AMOLED selection, theme source |
| `reference-surface` | Settings groups, dropdowns and sheet body | Palette and components |
| `reference-input` | Filled dark search field | `NuvioInputField` |
| `reference-text` | Titles, body, white hero/Details buttons | Tokens; hero uses on-background as its container |
| `reference-text-secondary` | Secondary text token | Source; actual Settings descriptions use muted instead |
| `reference-text-muted` | Supporting labels, year, description, inactive icons | Components and phone |
| `reference-outline` | Thin field/divider structure | Theme and tokens |
| `reference-focus` | Focused field border and accent focus treatment | Amber palette, phone |
| `reference-danger` | Destructive poster action | Theme, poster action preview |

Nuvio uses accent at **15%** for selection, **12%** for pressed/icon tiles, **8%**
for hover/soft selection, a **56% black** modal scrim, and disabled opacity **38%**.
These are source values. Composite colors depend on the surface underneath them.

### LionLibrary color mapping — proposed

| Semantic role | Existing LionLibrary value | Decision |
| --- | --- | --- |
| Primary accent | `#FF5722` | Preserve orange identity; use for selection, progress and intentional actions |
| Background | `#0B0D10` | Preserve established near-black; do not add an AMOLED setting during polish |
| Surface | `#171A20` | Settings groups, sheets and raised controls |
| Surface variant | `#232831` | Inputs, unselected chips and compact supporting containers |
| Primary text | `#F5F7F8` | Titles, primary content and cinematic white buttons |
| Muted text | `#A1A8B2` | Supporting content with verified contrast |
| Inverse text | `#111111` | Text/icons on white actions; use a tested dark on-accent for small orange controls |

**The Contrast Rule.** Verify the final composited colors, including scrims and
translucent selection fills. Normal text needs 4.5:1; large text and essential
non-text controls need 3:1. Small white text on orange is not automatically readable.
Prefer a white cinematic primary action with dark text, or a dark on-orange label
where contrast supports it. Do not lower subtitle/placeholder opacity until it fails.

## Typography

### Exact reference family and assets

The matching release loads **JetBrains Sans** from bundled regular, semibold and
bold TTFs. Font metadata independently confirms the family; it is not an inference
from a screenshot and it is not JetBrains Mono.

| Resource | Font metadata | Intended weight |
| --- | --- | --- |
| `jetbrains_sans_regular.ttf` | JetBrains Sans / Regular | 400 |
| `jetbrains_sans_semibold.ttf` | Typographic family JetBrains Sans / SemiBold | 600 |
| `jetbrains_sans_bold.ttf` | JetBrains Sans / Bold | 700 |

The font names, downloaded hashes and metadata are in the local evidence manifest.
The inspected font files carry a copyright notice for Philipp Nurullin. This review
did not establish a standalone redistribution license for these Sans files; a
JetBrains **Mono** license is not evidence about **Sans**. Exact visual reuse of the
face requires resolving that asset's provenance during implementation. LionLibrary
already bundles **Inter 400/500/600/700** with its license and can implement this
hierarchy immediately without changing its font assets.

**Reference detail:** `Theme.kt` customizes only eight Material typography roles.
Other roles inherit Material defaults. The tokens also declare additional type
styles, but declaring a token does not make every Material role use that family.
Hero fallback titles, small poster dates and some action labels reference roles
outside those eight overrides. Do not assume every visible glyph is JetBrains Sans.
For LionLibrary, explicitly map **all 15 Material roles** to the selected family.

### Type hierarchy — source values

| Role/pattern | Size / line height | Weight / tracking | Main use |
| --- | --- | --- | --- |
| Page display | 38 / 42 sp | 700, −1.2 sp | Search, Library, Settings and phone Settings subpage headers |
| Headline | 26 / 30 sp | 600, −0.8 sp | Material headlineLarge; tablet Settings header |
| Shelf title | 18 / 24 sp | 600 | Continue Watching and catalog/category shelf headings |
| Details section | 20 sp, inherited 24 sp line height | 600 | Cast, Production, Trailers, Movie Details; 22 sp at local width ≥720 dp |
| TitleMedium | 16 / 20 sp | 600 | Metadata values, generic primary button |
| BodyLarge | 15 / 22 sp | 400; Settings title overrides to 500 | Input, settings rows, sheet options |
| BodyMedium | 14 / 20 sp | 400 | Poster title, descriptions and secondary copy |
| Details synopsis | 14 / 22 sp | 400 | Expandable description, initially three lines |
| LabelLarge | 14 / 18 sp | 600 | Hero metadata and dropdown labels |
| LabelMedium | 12 / 14 sp | 600, +0.8 sp; section label overrides to 700 | Settings section labels |
| Small text tokens | 11/14, 12/15, 13/18 sp | Source-defined variants | Badges, custom styles; not proof of every role's actual use |
| Android expanded nav label | 13 / 16 sp; compact variant 12 / 14 sp | Active 700, inactive 400 | Inspected Android floating bar; its TextStyle has no explicit family and inherits composition text styling |
| Shared fallback nav label | 11 / 14 sp | Selected 600, unselected 400 | Shared fallback only; not the Android phone's actual label size |

The source's wider type-token ladder is **11, 12, 13, 14, 15, 16, 18, 22, 26, 28,
32, 38, 48 sp**. Do not use all those sizes on one screen. Hero title logos are images
with their own lettering; their font cannot be derived from the app typeface.

### Text and copy reference

Use sentence/title case for customer actions. Reserve uppercase, tracked text for
Settings group labels. Content names, synopsis text, ratings and release dates are
data, not permanent UI copy.

| Context | Nuvio labels/pattern | LionLibrary wording/behavior |
| --- | --- | --- |
| Destinations | Home, Search, Library, Profile | Home, Library, Settings; standalone Search removed at the user's request |
| Hero | View Details | View details, opening Details |
| Resume shelf | Continue Watching, remaining minutes, season/episode code | Jump Back In; retain existing resume and episode semantics |
| Search | Search; movie/show prompt; Recent Searches; Discover | Library query field searches the local catalog; no separate Search destination |
| Discovery selectors | Movies, Series, Anime | Existing local Movies, TV shows, Anime filters; no remote catalog/provider selector |
| Library | Saved, Cloud; Recently added; title sort directions | Existing All/watchlist/filter/sort semantics, without new cloud tabs |
| Details | Play, Resume, episode-specific Resume; More actions | Reflect actual availability and resume state; never imply a file exists when it does not |
| Description | Show More / Show Less | Expand/collapse synopsis accessibly, using localized resources |
| Metadata | Runtime, certification, origin, original language | Render only supported available fields; keep existing TMDB/Letterboxd actions |
| Settings | Short title plus one concrete explanatory sentence | Retain current key/folders/playback/scan/history/About options |
| No results | Short empty-state title plus an explanation | Distinguish no local content, no matches, active filters, missing access and failed metadata |

**The Scalable Type Rule.** Match the hierarchy at normal scale, using sp and
content-driven heights. Nuvio forces `fontScale = 1f`; LionLibrary must preserve
system font scaling. At 200%, titles can wrap, metadata groups can flow and actions
can grow. Never shrink text or clip a label to preserve a screenshot's geometry.

## Elevation

### Surfaces and layers

Most content is flat: artwork, captions and whitespace do the work. Settings uses
tonal groups with a fine border rather than heavy shadows. Nuvio source elevation
tokens are **0, 2, 8 and 12 dp**; the scrolled Details header explicitly uses **4 dp**.
Collection cards specify 2 dp at rest. Individual optional depth effects are not a
universal card shadow rule.

The phone's floating navigation has translucent glass and an enabled soft glow.
This is intentional reference chrome, not a reason to blur all cards. The Android
release has a platform glass implementation; the shared fallback uses a 24 dp blur
and a dark `#1C1C1E` fill at 55% with blur or 82% without it. Do not treat those
shared fallback values as measured Android shader output.

| Layer | Reference token | LionLibrary purpose |
| --- | --- | --- |
| Base | 0 | Page and artwork |
| Sticky header | 2 | Consume/cover scrolled content so taps do not reach hidden rows |
| Navigation | 4 | Bottom destinations, above content but below modal decisions |
| Sheet | 8 | Sort/filter/track selections |
| Dialog | 10 | Destructive confirmation |
| Player overlay | 12 | Existing player controls; the active footer adaptation is recorded above |
| Toast | 16 | Transient status source token; prefer existing Material snackbar feedback in LionLibrary |

These are relative z-order roles, not Android elevation measurements.

### Motion reference and adaptation

| Pattern | Source timing/behavior | LionLibrary application |
| --- | --- | --- |
| Fast / standard | 150 / 220 ms; `(0.2, 0, 0, 1)` easing | Press, selection, small content changes |
| Sheet enter / exit | 300 / 250 ms token values | Standard Material sheet transitions with safe insets |
| Slow / cinematic | 400 / 700 ms tokens | Reserved for meaningful transitions, not every row |
| Home carousel | Next page after 8 seconds; waits for active scrolling to end; activity-aware | Pause while touched, backgrounded, or with reduced motion; no mid-frame title clipping |
| Details overflow actions | 240 ms reveal; primary action contracts as icons appear | Preserve usable button width; use an overflow sheet if large text needs space |
| Library layout icon | 140 ms crossfade | Optional if an alternate layout is later adopted |
| Poster opening | Dedicated motion components | Use stable artwork and IDs; motion must not delay navigation or blank the image |

Honor Android's animation preferences. Do not reproduce optional disintegration,
autoplay trailer audio or elastic glass gestures as part of this polish pass.

## Components

### Common layout and spacing

**Source:** standard screen gutters **16 dp**, Details phone gutters **18 dp**,
screen top spacing **10 dp** plus status/platform insets, list gap **12 dp**, section
gap **24 dp**, rail gap token **14 dp**, control gap **8 dp**, compact padding **14 dp**,
surface padding **18 dp**, and dialog/sheet padding tokens **20 dp**.

Home shelf gutters scale to **24 dp at 768**, **28 dp at 1024**, and **32 dp at 1440 dp**.
Hero content has its own larger gutters. Avoid making every screen an identical
stack of 24 dp gaps: distinguish text-to-caption, heading-to-content and section-to-section.

**Observed measurement cross-check:** Library/Search card starts at x=53 px,
approximately 16 dp. A shelf poster spans 416 px, approximately 126 dp. The next
poster starts 33 px later, approximately 10 dp. Continue Watching spans 816 px,
approximately 247.3 dp. These agree with source values. Measure original 1216 px
captures, not chat thumbnails; do not divide sp by density when font scaling differs.

**Proposed Android guarantees:** minimum **48 × 48 dp** interactive targets; reserve
space below lists for the full navigation bar plus gesture inset and at least 16 dp
clearance. Smaller visual icons are fine inside those targets. Do not copy fixed
36/40 dp source hit boxes when they are below that minimum. Keep system Back,
keyboard insets, cutouts, TalkBack labels and RTL behavior.

### Navigation — observed and source

- Four destinations inside a centered floating pill. Selected icon and label use
  accent; selected surface uses a translucent accent fill. Inactive icons are muted.
- The selected phone uses **Adaptive** mode. Downward scrolling collapses labels
  and narrows the bar; upward scrolling expands it. Both states were captured.
- Android source: track height **48 dp collapsed**, **64 dp expanded** in normal
  sizing; compact expanded variant 56 dp. Horizontal margin animates between
  **58 dp collapsed** and **28 dp expanded**; maximum bar width **400 dp**.
- Android icon is **28 dp**, or **24 dp** in compact sizing. Expanded labels are
  **13/16 sp**, bold in the active layer, regular in the inactive layer; compact
  labels are **12/14 sp**. The bar has 4 dp internal padding. Bottom clearance is
  the system navigation inset plus **14 dp** (6 dp platform spacing plus 8 dp).
- Shared scroll source uses a **60 px** accumulated scroll threshold, not 60 dp.
  It does not consume list scrolling. Expanded label/fill changes animate.
- Reference icons: filled house, magnifier, stacked library container, circular
  profile initial. Do not replace Settings with a profile initial in LionLibrary.

**Proposed:** first adopt coherent icon size, active styling and safe placement
through Material navigation. A floating bar can be evaluated as a later visual
variant without adding a Settings preference. Keep labels available to customers;
avoid adaptive hiding at large font sizes or when it reduces accessibility. Use a
navigation rail for wide windows. Details retains system Back rather than forcing
the bottom bar onto its hero.

### Screen headers

- Search, Library and Settings use the page-display role; 16 dp side gutters.
- Header sits on an opaque background and becomes sticky with the screen's
  context controls. Search keeps its field with the header.
- Settings subpages show a back arrow aligned with the large page title. Long
  titles wrap; the captured poster-style title uses two lines.
- Library puts layout/download icons at the trailing edge. These are reference
  features, not a request to add downloads to LionLibrary.

**Proposed:** create a shared header with optional leading Back and trailing slots.
Use size-aware title roles rather than independently styled titles per screen.
Retain our existing destinations and actions, and make scrolled backgrounds opaque.

### Home

**Observed composition:** edge-to-edge hero → Continue Watching → named horizontal
collection/category shelves. Artwork extends under the status bar; text and controls
are readable above a dark bottom fade. Content previews extend toward the next card
so the horizontal scroll affordance is clear.

**Hero source geometry:**

| Element | Reference value |
| --- | --- |
| Phone content | 24 dp horizontal, 16 dp vertical padding; centered; max content width 480 dp |
| Logo | 62% of content width; fit inside 2.6:1 area; text fallback at most two lines |
| Logo → metadata | 12 dp |
| Metadata | Media type, first genre, release value; 8 dp gaps; 4 dp separator dot |
| Metadata → action | 14 dp |
| Main action | White pill, 40 dp radius; label padding 28 dp horizontal / 12 dp vertical |
| Action → pagination | 12 dp on phone |
| Pagination | 8 dp height; dot width 8 dp; active width up to 32 dp; 8 dp gaps |
| Hero bottom fade | 220 dp phone; image scrims increase toward page background |
| Outer bottom corners | 28 dp source override |
| Phone height | Uses 82% viewport with 360–760 dp bounds; reserves space for the following resume section when present; 1.16×width fallback |

Tablet hero source becomes left-aligned at ≥600 dp. Its height uses width factors
0.58 / 0.46 / 0.42 at 600 / 840 / 1200 dp, clamped to 320–380 / 340–420 / 360–440 dp.
This is source-only responsive evidence, not a tablet screenshot review.

**Jump Back In adaptation:** reference landscape card is approximately
**247 × 140 dp**, computed as `126 × (180/110) × 1.2`, with 1.77:1 aspect ratio.
At this size it uses 12 dp corners, 10 dp inner padding, 14 sp title, 10 sp episode
metadata/badge, 7 dp badge inset, and a 3 dp progress bar inset 9 dp from the sides.
The remaining-time badge sits top-right; series code, title and episode name form
a bottom-left hierarchy over a scrim. Captured movie cards use title plus progress.
The source's 10 sp microcopy is a reference measurement; use at least a readable
12 sp supporting role for LionLibrary when retaining the same information.

Keep LionLibrary's resume tap behavior and overflow menu. Do not duplicate its full
title below a card that already carries the title unless it is a deliberate different
card variant. Limit the resume shelf to a useful portion of the page; leave the next
section discoverable. Hero transitions must preserve a centered, unclipped logo and
never show fallback text behind a successfully loaded transparent logo.

**Shelves and category cards:** header 18/24 sp; header-to-row gap 10 dp; shelf gap
between items 10 dp; start/end gutters 16 dp. Reference category collections include
landscape, square and portrait artwork cards with a caption 6 dp below. They are
Nuvio catalog groupings, not additional SAF folder roots. For LionLibrary, use existing
Movies, TV shows, Anime and genre destinations with optional local-artwork montages;
do not require new remote discovery data to render them.

### Poster and grid cards

- Shelf width **126 dp**, configured preview height **189 dp**, corners **12 dp**.
  Actual shelf rendering uses aspect ratio **0.675**, so height is about **186.7 dp**;
  the preview's nominal 2:3 height is not the exact rendered shelf formula.
- Poster image crops within its shape. Logo images use Fit; never stretch a logo.
- Shelf caption: bodyMedium 14 sp, one line with ellipsis, followed by muted release
  caption; 6 dp vertical item gaps. Grid captions can use a different two-line block.
- Vertical grid uses **12 dp** column gaps and three columns on this phone.
  Source grid breakpoints: 4 at 840, 5 at 1000, 6 at 1200, 7 at 1400 dp.
- Grid tiles fill available width rather than retaining shelf width. On this phone
  a three-column tile is approximately 104 dp wide after 16 dp gutters and gaps.
- Tap opens Details. Long press on a saved poster revealed an enlarged poster
  preview, title/date and watched/library actions. No mutation was selected.
- Some captured posters include IMDb/other rating strips baked into supplied artwork.
  They are **not proof of a native multi-rating footer component** to copy.

**Proposed:** keep our adaptive grid and filters. Use consistent shape, image ratio,
baseline and one title location. At 200% text or narrow widths, allow fewer columns
and readable captions instead of copying Nuvio's fixed three-column minimum.
Watched/watchlist marks must have semantics and never change file/history identity.

### Search

**Observed:** large Search heading, dark filled outlined field, then Discover with
type/catalog selectors. An empty remote catalog has a tonal explanation card.
Focus shows recent searches when the query is blank. Typing Superman showed skeleton
poster rows, then movie and series shelves. The keyboard replaces the bottom area;
the floating app bar is hidden in the keyboard captures.

**Source:** 6 dp between header and field; 14 dp below field. Input uses 12 dp
corners, bodyLarge 15 sp, single line, accent cursor/focus border, outlined default
border and a trailing clear control when nonblank. Standard Material field content
padding is used; it is not a custom screenshot-derived 60 dp fixed height. Query
debounce is **350 ms**. Header/field is sticky. Empty/error/offline states are distinct.
Recent rows use a lightweight full-row action plus a separate remove icon, not cards.

**Proposed:** style our local search input and retain its existing query/category
semantics. Replace remote catalog selectors with the already-supported local filters.
Idle content may show local browsing guidance or existing categories; recent searches
are an optional feature requiring an explicit small storage/privacy design later.
Do not show trending/recommendation/loading placeholders if no such local request is
running. Return from Details to the prior query, results and scroll position.

### Library

**Observed:** large heading and actions → source tabs → sort chip → Series shelf.
Alternate grid adds an All types dropdown alongside the sort chip. Saved and Cloud
tabs are not Movies and Shows folder selectors.

Source tab chips use 18 dp radius, 14 dp horizontal / 8 dp vertical padding, 8 dp
gaps, labelMedium typography and a 1 dp accent border at 45%. Selected fill is the
theme's primaryContainer (Amber focus background `#3D2D1A`), with selected semibold
versus normal unselected labels. Generic sort/filter dropdowns use 12 dp
corners, 12 dp horizontal / 8 dp vertical padding, labelLarge, 6 dp text/chevron gap,
and an 18 dp downward chevron.

**Sort sheet observed:** inset modal, handle, title, four full-width choices; selected
choice has a warm fill, stronger text and a trailing check. It dismisses through
Back or outside tap. We inspected and dismissed without selecting a different sort.

**Proposed:** keep all our existing filter dimensions and sort values, including
watchlist, watch status, genre, exact year, rating and discovery date. Present a
compact primary filter row and progressively disclose the rest in the existing
filter surface. Always show active filters and a clear/reset affordance. Preserve
grid scrolling and selected filters on return. Do not add redundant source tabs,
downloads or another folder model to imitate the reference.

### Settings

**Observed root structure:** Account, General, About and Advanced groups. Root rows
have an orange icon tile, a clear title, muted explanatory text and optional trailing
status. Related rows share one tonal container with inset separators. Subpages use
the same header, group labels, switches, fields and action vocabulary.

**Source geometry:** phone group corner **12 dp**, tablet **16 dp**, 0.5 dp subtle
border. Row padding **16 dp horizontal / 14 dp vertical**; icon tile **36 dp** with
12 dp corners and accent at 12%; icon **24 dp** where explicitly sized; tile-to-text
gap **14 dp**. Title bodyLarge 15 sp with medium weight; description bodyMedium
14 sp, 2 dp below title, muted at an additional 92% opacity. Dividers start at
**66 dp** on phone. Tablet row padding 20/16 dp, tile 42 dp and text gap 16 dp.

Switch rows allocate remaining width to title/description, with **4 dp** text gap,
**12 dp** trailing text clearance and a Material switch. Checked track is accent;
unchecked track is outline, thumb muted. Text can wrap and row height follows it.
Chip rows wrap with 8 dp horizontal/vertical gaps. Secret inputs have password
transformation, a labeled visibility icon, error state and keyboard options.

**Proposed LionLibrary grouping, retaining current options:**

| Group | Existing controls to retain | Visual treatment |
| --- | --- | --- |
| Metadata | TMDB key, verification/save state | Labeled masked field, readable helper/error, one action |
| Library folders | Movies and Shows pickers and their current access status | Two consistent rows; shortened visible path with accessible full name/status |
| Playback | Existing VLC and external-player overrides | Shared switch rows; explanatory copy describes consequences |
| Scan | Scan Library, progress, cancellation and results | One prominent action, inline progress/status; no extra repair dashboard |
| History | Clear watch history | Destructive row with existing confirmation; metadata/files unaffected |
| About | Version, credits and notices | Quiet rows/links; correct bundled font attribution |

Do not turn Nuvio's Layout, tracking, content plugins, custom theme, notification,
account or membership options into requirements for LionLibrary. We are documenting
their control patterns, not expanding our Settings feature set.

### Details — movie and series, excluding episode list

**Observed hierarchy:** edge-to-edge backdrop → centered logo → genres → wide
white Play/Resume button and circular overflow → concise metadata → credit/synopsis
→ available supporting sections. Scrolling replaces the hero context with an opaque
header containing Back, compact logo/title and saved action. Main navigation is absent.

| Element | Source phone value / behavior |
| --- | --- |
| Hero | `width × 1.33`, bounded 420–760 dp; on this phone about 490 dp |
| Backdrop | Crop, 1.08 scale; scroll translation at half the scroll offset |
| Bottom fade | 320 dp, transparent through progressively stronger background stops |
| Hero content | 18 dp horizontal, 8 dp bottom padding |
| Logo | 60% content width, Fit, 80 dp height; genre gap 8 dp |
| Genres | Up to three, separated by dots; bodyMedium and muted |
| Primary action | 52 dp phone / 56 dp tablet; white pill with dark icon/text; maximum action-group width 420 / 520 dp |
| Action icon and gap | Play icon 18 dp phone / 20 dp tablet, 8 dp label gap; 12 dp to secondary circles |
| Secondary circles | Same size as primary height; watched/saved states; overflow expands actions and reduces primary width |
| Metadata | 14 dp between year/runtime/certification/rating groups; 12 dp between metadata blocks |
| Synopsis | 14/22 sp; three collapsed lines, expand/collapse when needed |
| Section heading | 20 sp phone, 600; 14 dp to section content |
| Cast | 80 dp round portrait within 92 dp item; 16 dp gap; 14 sp name, 12 sp role; 8 dp vertical gap |
| Trailer | 200 dp wide, 16:9, user poster radius (12 dp here), 12 dp gap; 12 sp title and 10 sp year in phone source |
| Extra info | 10 dp vertical row padding; label left, value right, 16 dp gap; 1 dp subtle divider |
| Scrolled header | 56 dp plus top inset; 16 dp side gutters; logo up to 60% width / 240 dp, 42 dp high; 40 dp visual Back/action controls |

Movie example: Superman, Resume, year/runtime/certification/IMDb, director/writer,
synopsis, Production, Cast, Trailers and Movie Details. Series example: a series
logo, genre line, Resume with season/episode code, release span/runtime/certification,
rating and longer writer/synopsis text. Their content differs; the component language
stays consistent. Nuvio's secondary-action reveal is an inline row, **not** a bottom
sheet; the Library sort/type pickers are bottom sheets.

**Proposed:** preserve our local Play/Resume availability, TMDB/Letterboxd meaning,
watchlist and episode actions. Use a FlowRow or another wrapping layout for atomic
metadata groups; never cut a rating/provider name to force a single line. Show an
opaque toolbar as soon as content can scroll behind it. Make the logo fallback
exclusive with the loaded logo. Render local artwork offline; never substitute
remote-only UI requests for our durable cached paths.

Cast/trailers/production/collection sections document the reference's design. They
are optional future data features and should not be fabricated or added to the
current schema merely for visual similarity. **LionLibrary's season selectors,
episode list, episode rows and episode options are preserved.**

### Sheets, dialogs and states

Nuvio Android's shared modal wrapper draws a **28 dp** rounded inner sheet inset
**10 dp**, with a 1 dp edge, rather than a full-width rectangular body. The handle
is **36 × 4 dp**, top gap 10 dp and bottom gap 4 dp. Dropdown heading has 16 dp
horizontal / 14 dp vertical padding; rows have minimum **56 dp** height, 8 dp
inner padding and selected fill/check. Dropdown lists cap height at 420 dp and
scroll. The wrapper is distinct from the general 24 dp sheet-shape token.

**Proposed:** use themed Material dialogs/sheets. Landscape and large text must show
every choice through expansion or scrolling, respect gesture/IME insets, and allow
Back dismissal. destructive confirmation names its effect and offers Cancel. A
cosmetic pass must not silently select a sort, clear history or mark a title watched.

| State | Reference evidence | LionLibrary acceptance |
| --- | --- | --- |
| Loading | Search skeleton rows and Details initial spinner observed; source shared skeleton shapes | Keep geometry stable; preserve existing art/content during refresh |
| No results | Empty remote catalog observed | Explain local query/filter result; offer an applicable recovery action |
| Offline/error | Source paths inspected; not induced on phone | Separate network metadata errors from locally playable media |
| Missing art/logo | Source fallback paths | Local placeholder/title; success removes fallback permanently until file changes |
| Unidentified local file | LionLibrary requirement, not a Nuvio screen claim | Visible filename and local playback; missing remote metadata is not a blank card |
| Disabled/unavailable | Source controls | Readable state and explanation; no misleading Play action |
| Selection | Sort/type sheets, chips, navigation observed | Semantic selected state plus visible cue; color alone is insufficient |
| Long text | Wrapped Settings description and series credits observed | Grow/wrap text; keep actions reachable at 200% |

## Do's and Don'ts

### Do

- **Do** use this as a measured reference, with source values and proposed changes
  distinguished. Use the pinned release, not a newer branch's unrelated constants.
- **Do** implement one shared spacing/type/shape system, using Material roles and
  the installed Android Compose/MVI/navigation skills when code work begins.
- **Do** preserve orange, two folder roots, offline art, stable IDs and every
  existing scan/playback/history/watchlist contract in AGENT.md.
- **Do** keep the episode list and its controls unchanged during this redesign.
- **Do** test 320/360 dp widths, 1×/1.5×/2× text, long titles, landscape return,
  tablet/rail layout, TalkBack, RTL, keyboard and missing artwork.
- **Do** verify image transitions at rest and mid-swipe; text and logos must never
  flash behind each other or drift outside the centered content area.
- **Do** use a licensed bundled family, and explicitly configure every Material
  type role. Preserve Inter until any replacement's redistribution terms are established.
- **Do** preserve scroll position, search query and filter selection when returning
  from Details; keep saved state in the existing presentation model.
- **Do** capture before/after screenshots on the same phone/density and compare
  typography baselines, image ratios, target sizes and empty/error behavior.

### Don't

- **Don't** treat reference inspection as authorization to implement features now.
- **Don't** introduce Cloud/Profile/accounts, remote discovery, downloads, new
  folder roots, onboarding or Settings options merely because Nuvio has them.
- **Don't** disable font scaling, copy undersized hit targets or preserve a fixed
  grid count when captions become unreadable.
- **Don't** add an extra title below an already-labeled image by accident, stretch
  logos, or render fallback text underneath a loaded transparent logo.
- **Don't** copy rating strips that are part of poster images as if they were a
  native rating component, or show ratings/services our app does not fetch.
- **Don't** let a floating bar, transparent sticky header or keyboard hide final
  rows/actions. Safe clearance is a layout requirement, not a screenshot workaround.
- **Don't** copy all Nuvio fonts/code/assets into the app based solely on public
  repository visibility. The [release repository license](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/LICENSE)
  is GPL-3.0; this reference uses observations and measurements, without importing
  its implementation or artwork into LionLibrary.

### Implementation sequence for the next task — proposed

1. **Shared theme/components:** consolidate spacing, shape, all typography roles,
   header, poster, action, input, chip, Settings row and sheet; use previews for
   loaded/missing/long-text/large-font states. No schema or scanner changes.
2. **Home and navigation:** fix hero geometry and transitions, restyle Jump Back In
   and existing category shelves; preserve destinations and semantics.
3. **Library and Search:** align headers/fields/chips/posters; preserve filters,
   sorting, local results, query state and card actions.
4. **Settings:** restyle existing groups, inputs, switches, scan feedback and
   confirmation; retain the simpler current option set.
5. **Details:** align hero/actions/metadata/synopsis/header around the preserved
   episode list. Do not add remote data features to finish a styling task.
6. **Visual acceptance:** same-device screenshots, meaningful affected UI tests,
   existing behavior regression checks and accessibility review. A theme screenshot
   alone is not evidence that scans, resume and offline artwork still work.

### Source index

All links below target the inspected release commit. Local copies are review-only
under `build/nuvio-review/source/`. No third-party app source was added to `app/`.

| Topic | Primary reference |
| --- | --- |
| Tokens, type/spacing/motion scale | [Tokens.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Tokens.kt) |
| Bundled font, Material mappings, forced scale | [Theme.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Theme.kt) |
| Amber and other color roles | [ThemeColors.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ThemeColors.kt) |
| Headers, fields, buttons | [Components.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Components.kt) |
| Android floating bar | [FloatingNavigationBar.android.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/FloatingNavigationBar.android.kt) |
| Android tab icons/labels | [JellyTabs.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/jelly/JellyTabs.kt) |
| Scroll-responsive navigation and fallback | [NavigationBar.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/NavigationBar.kt) |
| Posters/shelves | [ShelfComponents.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ShelfComponents.kt) |
| Poster defaults | [PosterCardStyleRepository.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterCardStyleRepository.kt) |
| Home hero | [HomeHeroSection.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt) |
| Resume shelf | [HomeContinueWatchingSection.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeContinueWatchingSection.kt) |
| Responsive poster grid | [PosterGrid.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/PosterGrid.kt) |
| Search | [SearchScreen.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/search/SearchScreen.kt) |
| Library | [LibraryScreen.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/library/LibraryScreen.kt) |
| Settings rows and groups | [SettingsComponents.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsComponents.kt) |
| Settings root/layout | [SettingsScreen.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsScreen.kt) |
| Details hero | [DetailHero.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailHero.kt) |
| Details buttons/reveal | [DetailActionButtons.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailActionButtons.kt) |
| Metadata/description | [DetailMetaInfo.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailMetaInfo.kt) |
| Scrolled Details header | [DetailFloatingHeader.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailFloatingHeader.kt) |
| Bottom sheets | [BottomSheet.kt](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/BottomSheet.kt) |
| English UI text | [strings.xml](https://github.com/NuvioMedia/NuvioMobile/blob/1ae5066a4dd5026228625e40082eacc3ada68436/composeApp/src/commonMain/composeResources/values/strings.xml) |

The adjacent `.impeccable/design.json` holds reference motion/breakpoint metadata
and illustrative web previews for inspection tools. Those previews approximate
the measured vocabulary; native source and phone evidence take precedence.
