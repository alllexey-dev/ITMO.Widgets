# Changelog

Reference documents in `docs/` describe the current state; this file records
what changed and when. Dates are commit dates on `itmo-widgets-v2.1`.

## Unreleased (2.1-SNAPSHOT)

### 2026-09-16
- Profile: notifications and version rows removed (both remain in settings);
  compact GitHub and Telegram buttons with logos above sign-out, Telegram via
  `tg://` deep link with a web fallback.

- Split compact and full schedule-widget settings into independent pages, previews
  and preference keys, preserving existing choices. Early switching is compact-only;
  past/tomorrow controls are full-only; each has separate teacher visibility. The
  full list no longer removes an ongoing lesson because compact switched early.
- Another user's sport no longer waits for the merged sport schedule; it resolves
  confirmed lessons against the raw catalog, so it loads before the sport tab
  was ever opened in the process.
- FCM restored: messaging service, token sync, `sport` and `friends` channels,
  serialized WorkManager processing, sport auto-sign booking from pushes,
  friendship notifications that open the profile. Delivery is guarded by the
  recipient ISU and a signed-in session.
- Documentation restructured: tracked `AGENTS.md`, `docs/README.md` index,
  per-feature documents, decision records, English everywhere except README.

### 2026-09-15
- Social layer: `SocialRepository`, friends and requests screen, people search
  through MyITMO with Backend lookup, public profiles with schedule and sport
  entry points, another user's schedule and sport screens.
- Profile tab: study group, friends card with request badge, privacy,
  notifications and version rows.
- Friend picker aligned with the design language: quiet rows, container
  selection, `Друзья / Все` toggle, profile on long-press.
- BARS overlay on the recordbook behind a chip, using MyItmoApi 1.8.0.
- Resource and naming clean-up: one file per resource, semantic colours, uniform
  layout and view ids, one voice in user-facing strings.
- Real sport venues kept out of the building filter categories.

### 2026-09-08 — 2026-09-09
- Sharing audiences (`Все / Друзья / Никто`) with viewer capabilities from Core.
- Schedule timeline markers aligned; pending sport rows in the own schedule and
  both schedule widgets behind a default-off setting.
- Unified design pass: shared card styles, state styles, refresh helper,
  preserved schedule range and scroll on refresh.
- Sport session cards and details redesigned; collapsing score card.

### 2026-09-07
- Recordbook redesigned with live sport progress for physical education.
- Contextual overlay navigation stack; profile and settings with live widget
  previews; authentication and sign-out.

### 2026-07 — 2026-08
- QR widget and schedule widgets on the refactored architecture.
- Sport sign double-tap guard; friend schedule disabled without services.
