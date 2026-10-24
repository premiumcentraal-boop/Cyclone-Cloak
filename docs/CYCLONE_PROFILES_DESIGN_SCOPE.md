# Cyclone profiles: the full design and mechanics scope

What Cyclone's profile feature is, screen by screen and step by step: how profiles are picked, made, switched,
removed, and how Cyclone raises the phone's account (user) limit on rooted phones.

**Source:** Cyclone `claude/cyclone-v5-handoff-review-9qrs40` at `1ad78382` (5.0.0-alpha.155.dev1, code 309). Note that
`main` and Cyclone's `claude/upbeat-pasteur-npf7fv` are still at the V4 baseline and contain only the first ~200-line
version of this feature. Everything below is read from the current code; the plans it came from are
`Cyclone V5 plan/40-profiles-workhorse.md` and `Cyclone V5 plan/57-hardened-profiles.md`. Where the code differs from
the plan, the code is described and the difference is noted.

**Hardware status:** per Cyclone's own docs, most of plan 57 (switcher, room, per-type unlock up to 100) is
**UNVERIFIED on a physical phone**.

---

## 1. What a profile is

- A **Cyclone profile is a real Android user** (a full secondary user since 4.2.7, not a work profile). Root creates
  it, never the model.
- Its Android name is `Cyclone_<16 hex>` (random). That name is how Cyclone proves a user is one it made
  (`ProfileRecovery.validOwned`). The friendly label ("Profile B", "Work") lives only in Cyclone's registry
  (`ProfileRegistryStore`), together with an emoji and a colour.
- Apps are added with `cmd package install-existing --user N <pkg>`: the APK is shared, but **each profile has its own
  app data**, so the same app has its own login in every profile.
- The main user is **Main** ("Profile A" in the registry when unnamed). Profiles are made, removed and resized only
  from Main.

## 2. Where profiles appear (the surfaces)

| Surface | File | What it is |
|---|---|---|
| Home profile slider | `ui/v32/HomeR6.kt` (`ProfileSlider`) | A bank-account-style pager, one profile per page |
| Profiles tab | `ui/v32/CycloneProfilesPage.kt` | The grid of profile faces, Working now, Room, Shared apps, In every profile, Recently deleted, Debug report, connector entries |
| Profile detail | same file (`ProfileDetail429`) | Big face, one primary action, the apps grid, Name & look, Details, Remove |
| Details (ids) | same file (`ProfileIdentityDetails429`) | Copyable Cyclone id, Android user id, parent id, Cloak profile ids |
| Shared apps / app detail | same file | Every app that lives in more than one profile |
| Recently deleted | same file (`DeletedPage`) | The trash, Restore, Delete now / Delete all, Backups |
| In every profile | `ui/ProfileCornerstonesUi.kt` | Cornerstone apps added to each profile, and what each profile had at the last switch |
| New profile setup | `ui/ProfileSetup429.kt` | Full-screen 3-step flow: Name → Choose apps → Review, then Creating, then Ready |
| Problem panel | `ui/ProfileProblemUi.kt` (`ProfileProblemPanel`) | The error card with fix buttons and Details |
| Room card + Room sheet | same file (`ProfileRoomCard`, `RoomSheet`) | **The account-limit increaser** |
| Debug report sheet | same file (`ProfileDebugButtons`) | Share / Save / Copy |
| Rescue screen | `ui/ProfileRescueActivity.kt` | "Get back to Main" when a profile's Cyclone isn't set up |
| Rescue bar | `ui/ProfileRescueBar.kt` | "You're in X · Back to Main" strip inside a non-Main profile |
| Open request | `ui/ProfileOpenRequestActivity.kt` | A connector (Cloak) asks to open a profile; the owner taps Open / Not now |
| Glass (PC) | `apps/glass/src/pages/profilesBar.ts` | Profile strip per phone, Switch from the PC, per-profile app manager. Create/delete stay on the phone |

## 3. The design system ("calm glass", R6)

Every profile screen is built from one small kit, `ui/v32/ProfileGlassKit.kt`, so they read as one product with Home:
**big words, few words, one clear action.**

### 3.1 Background and tokens

- **Backdrop:** `AskCalmField`, a still vertical gradient (never animates), five stops:
  `#2B3BF2` → `#1B28C9` → `#111A86` → `#0C1150` → `#090C2B`, a soft light from the top, faint violet on the right.
  Glow `#5A6CFF`. Glass panels on it smoke at 0.40, chrome at 0.58. Round actions use a white veil `#26FFFFFF`.
- **Ink:** `Ink #F5FFFFFF`, `Muted #A8FFFFFF`, `Faint #70FFFFFF`, `Hairline #2EFFFFFF`.
- **State colours:** Done/In use `#34C759`, Waiting/Setting up/Full `#FFB340`, Working `#64D2FF`,
  Destructive `#FF6B5E`, Cloak pill `#BF5AF2`.
- **Sheet gradient:** `#1D2678` → `#111754` → `#0B0F38`, rows inside a sheet are a `white 8%` veil (no glass).
- **Profile colours** (the look picker): `#3E7BFA` (default), `#7B61FF`, `#30B85A`, `#FF9F0A`, `#FF6B5E`, `#32ADE6`,
  `#E056FD`, `#8E8E93`.
- **Icon tiles** (Settings-style square tiles, radius 30% of size): Shared apps `#5E5CE6`, In every profile `#30B85A`,
  Recently deleted `#8E8E93`, Debug report `#FF9F0A`, Name & look `#3E7BFA`, Storage `#8E8E93`, One restart
  `#FF9F0A`, Undo anytime `#30B85A`, Android's switcher `#32ADE6`.
- Shapes are Kyant "continuous" capsules and rounded rectangles; glass groups radius 22 dp, sheets 32 dp top corners.

### 3.2 Components

| Component | Rule |
|---|---|
| `ProfileButton` | A capsule whose label is **one line, never wrapped**. PRIMARY = solid white with dark ink `#0B1040` (the one thing to do); SECONDARY = white 14% veil + 22% rim; QUIET = words only; DESTRUCTIVE = red words on a 10% veil. Heights 50 dp (36 compact, 44 quiet). Disabled fades to 45%. |
| `ProfileAvatar` | A gradient circle in the profile's colour (lerped 28% to white at the top-left), the emoji or the **initial**. `ring` = a 2 dp white ring for the profile in use; `dimmed` = 35% black for not-ready/trashed. |
| `profileInitial` | "Profile C" → **C**, "Work" → W: skips the shared word "Profile". |
| `ProfileTitle` | 34 sp bold title, 16 sp muted subtitle, optional trailing control. |
| `ProfileSectionLabel` | Muted 15 sp label with an optional action word ("Edit", "Add"). |
| `ProfileGroup` / `ProfileRow` / `ProfileRowDivider` | Settings-style grouped glass list: rows ≥ 56 dp, leading tile, one-line title, muted subtitle, value, chevron; hairlines indented 60 dp. |
| `ProfileStatus` | A 7 dp dot + one word ("In use", "Working", "Needs you"). **Never a pill that can be squeezed.** |
| `ProfileNotice` | One calm line: spinner (WORKING), red mark on a red 16% fill (PROBLEM), or info mark (INFO). |
| `ProfileRoomMeter` | How full the phone is: **one segment per place when the limit is ≤ 12**, a filled capsule above that; amber when full; an empty track when the limit is unknown (never a guess). |
| `ProfileSheet` | Bottom sheet: 45% black dim, grab handle, centred 24 sp title, scrolls when tall, tap-dim/Back closes. |
| `ProfileConfirmSheet` | Title, one or two lines of consequence, the action in its colour (red when destructive), Cancel. |
| `ProfileTextField` | Glass capsule; `large` = 26 sp centred (a profile's name), else a search line with a leading icon. |
| `ProfileAppStack` | Up to 4 overlapping round app icons, then "+N". |
| `ProfileCalmPage` | Full-screen page outside the app shell (setup, rescue) that draws the calm blue itself so glass blurs the same. |

## 4. Selecting a profile

### 4.1 Home slider

- One page per profile, centred like an account balance:
  - small line: **"Profile · In use" / "Profile · Working" / "Profile · Setting up" / "Profile · Ready"**;
  - the name at **44 sp bold**;
  - up to four overlapping app icons and "N apps" (Main says "Your main profile").
- Order: the one in use, then working ones, then Main, then by name. Never empty (falls back to "This phone").
- Connector entries (e.g. Cloak's) come **after** real profiles, marked "From <connector>", and never pose as profiles.
- Page dots in a soft capsule; the current dot is an 18 dp bar. A tap opens the Profiles tab (or the connector's screen).

### 4.2 Profiles tab (home)

Top to bottom:
1. **Header:** "Profiles", subtitle **"You're in <label>"** (or "One phone, many spaces"), and a round **+** chip
   (New profile), the one primary action.
2. Pending requests (`CyclonePendingRequests`).
3. The **problem panel** if the last setup failed (§9).
4. Busy/error notices.
5. **The grid:** profile faces in rows of **3**; a 70 dp avatar in an 82 dp box, ringed for the one you're in, dimmed if
   not ready; name; then one state word: "Setting up" (amber dot), "Working" (blue dot), "In use" (green dot), or
   "N apps". The **last tile is "New"**: an outlined circle with +.
   Sort: current first, then Main, then active, then by name.
6. **Carry note** (one quiet line with a sync icon): what the last switch brought, or "What you learned here goes with
   your next switch." when the last carry out didn't arrive.
7. **Working now:** rows "App · Profile" with the task's subtitle (or "Waiting for its turn") and a state pip
   (Working / Needs you).
8. **Room card** (only for the owner on Main): §8.
9. A group: **Shared apps** (count), **In every profile**, **Recently deleted** (count, only when non-empty),
   **Debug report**.
10. **From your connectors:** entries approved connectors add, with their icon, subtitle and "Needs you"/"Off" state.

Trashed profiles are hidden everywhere except Recently deleted.

### 4.3 Profile detail

- Back row; a **104 dp** avatar (ringed if current); the name at 32 sp; a line with the state dot, "· N apps", and
  Cloak's pill only when Cloak is in play (**Rooted**, **Rooted · check**, **Rooted · not working**).
- **One primary button**, chosen by state:
  - not ready → **Finish setup**;
  - the one you're in → **Ask Cyclone here** (sparkle icon);
  - otherwise → **Switch to <label>** (swap icon).
- Progress/error notices, and the live task if it's in this profile.
- **Apps** (with **Edit**, not on Main): a 4-column icon grid; tapping an app opens it in that profile
  (`SessionKernel.switchWorkspace`). Empty: "All your phone's apps live here." (Main) or "No apps yet. Tap Edit to add
  some."
- A group: **Name & look**, **Details**, **Remove profile** (red; never for Main or the profile you're in).

### 4.4 Details

Copyable rows: Cyclone profile ID, Android user ID, Parent Android user ID, and per-app Cloak profile IDs. A note:
"Cyclone never reads Android ID, IMEI or Device serial number. From Cloak it gets only the profile reference…"

### 4.5 Edit profile sheet (Name & look)

Title "Edit profile"; the 88 dp face updating as you type; a large name field (40 chars); an emoji field (16 chars,
optional); a row of eight colour dots (white 2.5 dp ring on the chosen one); **Save** (primary) and **Cancel**.

## 5. Making a profile (the setup flow)

### 5.1 Starting it

- **+** (header chip or New tile) calls `ProfileSetupRuntime.startNewPlan`: **always a clean plan**.
- If a previous setup was started but not finished, a sheet asks first: **"Finish <label>?"** — "<label> was started
  but isn't finished." — **Finish <label>** / **Start a new one** (discards it) / **Cancel**. (Plan 57 D3: before this,
  + reopened the old journal and a typed name could rename the previous profile.)
- Only from Main: elsewhere step 1 says "New profiles are made from Main." with **Back to Main**.

### 5.2 The screens

A full-screen dialog on the calm blue. Top bar: round **Back** chip, **3 step dots** (current is an 18 dp bar), round
**Close** chip. The primary action sits outside the scroll, above the keyboard, with 76 dp left free under it for the
floating Ask Cyclone orb.

| Step | Title / line | Content | Action |
|---|---|---|---|
| 1 Name | **"Name your profile"** / "Its own apps and sign-ins, one tap away." | A 112 dp live avatar, a large centred name field (40 chars). The name is pre-filled with the first free **"Profile B"…"Profile Z"**, then "Profile 27"… (trashed names count as taken). | **Choose apps** (runs the root check first: "Checking your phone…"; if not rooted: "Cyclone needs root access to create another phone profile. Allow the root request, then try again.") |
| 2 Choose apps | **"Choose apps"** / "Each one starts fresh here." | Search field; an **Essentials — Always included** row with their icon stack; then one continuous list of launcher apps (rounded at its ends), each with a round tick. Counter: "Essentials only" / "N apps + essentials" / red "Choose up to 50 apps." | **Confirm apps** |
| 3 Review | **"Create <name>"** / "N apps, each with its own data" | 96 dp avatar, a 4-column icon grid (12 shown, "+N more"), **Change apps** (quiet). | **Create profile** |
| Creating | **"Creating <name>"** / the live stage line | A 140 dp progress ring around a 110 dp avatar. Stage lines: "Checking what <name> needs…", "Creating <name>…", "Starting <name>…", "Adding <app>…", "Finishing <name>…". | **Pause setup** (quiet) |
| Ready | the name (30 sp) with a round edit button, "Ready"/"Setting up" | **Switch to <name>**; the apps grid (24); a group: Edit apps, New profile, Back to Main. | — |

Names must be unique (case-insensitive): "Another profile already uses that name."

### 5.3 App picking rules (`ProfileAppSelection`, `ProfileCornerstones`)

- **Essentials** (cornerstones) are always included, can't be unticked and **don't count** against the limit:
  Cyclone, the detected root manager (including a hidden/renamed Magisk app), Shizuku; best effort Cyclone Cloak (found
  by its connector id) and up to **12** apps the owner marks in **In every profile**.
- Up to **50** optional apps. The runtime re-discovers essentials itself, so a caller can't drop or forge them.
- Only launcher apps already installed on Main; Cyclone itself is excluded from the list.

### 5.4 What `create()` does (`ProfileSetupRuntime.create`)

All steps run under the workspace mutation lock, through **fixed typed commands only** (`ProfileSetupPlan`; no free
shell text anywhere; every command's tokens are shape-checked). Each step is journalled, so a retry never makes a
duplicate profile and **Pause** stops at the next step boundary.

1. Save the plan (parent user, chosen apps, `ready=false`); generate and save the `Cyclone_<16 hex>` name once.
2. Verify the chosen apps are still installed on Main.
3. **Root check** (`/system/bin/id`), then current user, `cmd user list --all --verbose`, `pm get-max-users`.
4. **Capacity pre-check:** `dumpsys user` type limits; refuse early with `USER_TYPE_LIMIT` / `USER_TYPE_DISABLED`.
5. **Create:** `pm create-user Cyclone_<hex>` (secondary user). Journal the new user id **immediately**, then re-list and
   verify it's exactly the one Cyclone named.
   - If Android says "already exists" but the re-list doesn't find the name, the error is reported as Android's
     refusal, not "already exists" (plan 57 D1: Android's limit sentence contains "already exists").
6. **Start:** `am start-user -w N`, require `RUNNING_UNLOCKED`.
7. **Add apps:** for each (sorted), `install-existing`, then `pm list packages --user N` to prove it's there; register
   a workspace per optional app.
8. **Finish:** `settings --user N put secure user_setup_complete 1` and read it back.
9. **Final proof** (`ProfileReadyVerifier`): user listed with the right name, running and unlocked, every app present,
   workspaces bound to the right user, setup complete, journal agrees. Only then `ready=true`.

Every failure becomes a `ProfileSetupFailure` worded with **the profile's own label** ("Profile C couldn't be added"),
never a hard-coded "Profile B".

## 6. Switching profiles (`openProfile` + `ProfileSwitch`)

A journalled state machine; the last 20 switches are kept for the debug file.

| Stage | What happens |
|---|---|
| PREFLIGHT | No task running and no review pending; root verified; the target is in the registry, not trashed, ready, a secondary user, and Android lists it with Cyclone's exact name. |
| PREPARE (`ProfileBootstrapRuntime.prepare`) | Install Cyclone + cornerstones into the target and enable them; start and require unlocked; share root (Magisk multi-user mode + copy Cyclone's existing grant; nothing new granted); mirror permissions (notifications, mic, calendar, overlay, exact alarms, battery, assistant role, accessibility, notification listener); hand over AI settings + the OpenRouter key **sealed for the target's Keystore key**; prove root from the target's own Cyclone UID. |
| CARRY | Memory, skills, notes and portable settings, sealed (§10). **Best effort: never blocks the switch.** |
| ARM_RETURN | The **dead-man return**: the target's Cyclone is asked to say hello; a root-side `setsid sh -c` timer switches back to the source after **45 s** if no hello arrives. Armed only after the target's Cyclone is listening, so a locked profile can still be unlocked by PIN. |
| SWITCH | `am switch-user N`, then an adaptive wait (150 ms steps, then 300 ms, then 1 s; about 27 s in all) for Android to confirm. |
| CONFIRM | The target's Cyclone writes its hello (disarms the return), tells its connectors `SWITCHED`, and shows a low-priority **Back to Main** notice. A hello later than 18 s is `CONFIRM_LATE`, not a failure. |

- **Any profile to any profile**, and **Back to Main** from anywhere. The full registry travels with every switch, so
  every profile's Cyclone knows every profile.
- Root managers: Magisk 24+ (including the hidden app) grants are shared by command; KernelSU/APatch grants are not
  settable, so the owner gets one guided manual step instead of a silent "switch cancelled".
- Ways back if Cyclone can't switch: the **Rescue screen** ("Get back to Main" — Back to Main / Repair from Main /
  Open Android's switcher / Stay in this profile / Debug report), the **Rescue bar**, and Android's own user switcher
  (Cyclone checks it's on and offers to turn it on, never off).
- PC/Glass switches (`ProfileApps.switchTo`) use the same way back and journal, but carry nothing.
- Connectors (Cloak) can't switch; they can only **ask** (`profiles.open.request.v1`): Cyclone shows "Open <label>?"
  with Open / Not now. One request at a time (2 min), one per 10 s per connector.

## 7. Removing, restoring, deleting

| Action | Behaviour |
|---|---|
| **Remove** (detail → Remove profile) | Sheet "Remove this profile?" — "<label> stops and moves to Recently deleted. It stays there 7 days, untouched: Restore brings it back exactly as it was. After that, Cyclone backs it up automatically and deletes it." Runs `am stop-user`; hides it everywhere but the trash. |
| **Recently deleted** | Title "Recently deleted" / "Kept 7 days, then backed up". Rows: dimmed face, "Deletes in N days / tomorrow / today", **Restore** (compact) and a red bin. **Delete all** at the bottom. Then **Backups**: label, date · size, bin. |
| **Delete now / Delete all / after 7 days** | "Delete for good?" — backs up first (each app's credential and device storage as tar files in Main's Cyclone, smallest first within 70% of free space; the rest listed as left out), then `pm remove-user`, then checks Android removed it. **No backup, no delete.** Backups are kept **30 days**. |
| **Clean up unfinished** | Removes only users Cyclone itself named (`Cyclone_<16 hex>`) that are partial or never journalled ready — never Main, the current user, a ready profile, or any user Cyclone didn't make. |

Guards on all of them: only `Cyclone_` users Cyclone owns; never Main or the current profile; never while a task runs;
only from Main's Cyclone. Restoring a backup into a new profile is not built yet.

## 8. The account-limit increaser ("Room")

This is the feature that lets a rooted phone hold far more profiles than Android allows by default (usually 4).
Files: `runtime/workspaces/ProfileRoom.kt`, `ProfileCapacity.kt`, `ProfileProblem.kt`, `ui/ProfileProblemUi.kt`.

### 8.1 Counting the way Android counts (`ProfileCapacity`)

- Parses `cmd user list --all --verbose`, `pm get-max-users` (including Android 16's "Maximum supported switchable
  users" wording) and the type blocks of `dumpsys user` (`mName`, `mEnabled`, `mMaxAllowed`).
- **Every alive user except guests uses a place:** Main, Cyclone profiles, **Recently deleted ones (still use a
  place)**, unfinished Cyclone users, work profile, Private space, App clones, other users/profiles.
- Verdict: `ROOM`, `TOTAL_LIMIT`, `TYPE_LIMIT` (the phone caps full secondary users), `TYPE_DISABLED`, `UNKNOWN`.
- The room line: "Main · Profile B · Work profile · Old test (Recently deleted, still uses a place)".

### 8.2 Android's two limits, and how each is lifted

| Limit | Where it lives | How Cyclone lifts it |
|---|---|---|
| **Total users** | `fw.max_users` (else `config_multiuserMaximumUsers`), read by `UserManager.getMaxSupportedUsers()` on every call | Set `fw.max_users` live with the root manager's own `resetprop`, and keep it after reboot with a tiny module whose `system.prop` holds `fw.max_users=<n>` |
| **Per user type** (some ROMs, e.g. the owner's Pixel 8 on Android 16 caps full secondary users at 3) | The user type's `maxAllowed` | The module's `system.prop` also carries `ro.debuggable=1` and `debug.user.creation_override=1`, which turn on Android's own `UserManagerService.isCreationOverrideEnabled()` and skip **both** limits. Needs **one restart**. Never set by runtime `resetprop`; only as module lines. |

### 8.3 Root managers

| Manager | Detected by (`ls /data/adb`) | `resetprop` |
|---|---|---|
| Magisk (incl. hidden app) | `magisk` | `magisk resetprop` |
| KernelSU / KernelSU Next | `ksu` | `/data/adb/ksu/bin/resetprop` |
| APatch | `ap` | `/data/adb/ap/bin/resetprop` |
| none | — | not offered |

### 8.4 The module (generated in code, never hand-written)

`/data/adb/modules/cyclone_profiles/`:

```
module.prop
  id=cyclone_profiles
  name=Cyclone profiles
  version=<Cyclone version>
  versionCode=1
  author=Cyclone
  description=Lets this phone hold up to <n> profiles (Android's user limit, fw.max_users).[ unlock note] Added by
              Cyclone at the owner's request; remove it in Cyclone with Restore default.
system.prop
  fw.max_users=<n>
  ro.debuggable=1                      # only when a per-type cap is detected
  debug.user.creation_override=1       # only when a per-type cap is detected
```

Written atomically: Cyclone's own `files/profile-room/cyclone_profiles` → `cp -r` to `cyclone_profiles.new` →
`chown -R 0:0` → `restorecon -R` → `mv` into place → both files **read back byte for byte**. A CI guard checks this is
the only `/data/adb` path in the app and that `resetprop` only ever sets `fw.max_users`.

### 8.5 `allow(n)` step by step

1. Guards: setup not busy; no task or review; root works; **you're on Main**.
2. Detect the manager; refuse if none or no number is left to pick.
3. Record the original `fw.max_users` **once** (so Restore default goes back exactly).
4. `resetprop fw.max_users n`, then read back both `getprop` **and** `pm get-max-users`; both must say `n`, else stop
   ("Root didn't apply the new limit… Nothing else was changed.").
5. Write the module (with the unlock lines if needed). If the module fails, it's removed again and the owner is told
   the limit works only until the next restart.
6. Report `restartNeeded` when the unlock was written.

**Restore default** removes the module, resets the property to the recorded original (or deletes it), reads both back,
and clears Cyclone's notes. Existing profiles stay; only new ones are limited again. If the module had the unlock,
"Restart your phone to finish."

**Apply again:** at start the status compares `getprop` with the module; if a reboot didn't apply it (module disabled
or not loaded), the card says so and the sheet offers **Apply again**.

### 8.6 The choices

- **10, 20, 50 or 100** profiles, **default 20**, only numbers above what's in use, within the allowed range `4..100`.
  (The plan first said 6/8/12/16 with default 8, then 4..16; alpha.125 widened it after the Pixel 8 debug file.)
- Storage is warned, not forbidden: under 3 GB free → "Low: new profiles may not fit".

### 8.7 The screens

**Room card** (Profiles home, owner on Main): a glass row "Room" with "**U of L**" (amber when full), the room meter,
and one line: "Restart your phone to finish making room" / "The raised limit wasn't applied. Tap to apply again" /
"Full · tap to make room" / "Full" / "Raised by Cyclone" / "Android's own limit". Tap opens the Room sheet.

**Room sheet** — title "Room" (or "Make room" when choosing):
- big "**U of L**" (40 sp) with the meter and a status line ("Android's own limit", "Raised by Cyclone",
  "Raised by Cyclone · switched off in Magisk", "Restart your phone to finish");
- **Choosing:** "How many profiles?" and four big number tiles (selected = solid white); a group: **Storage** ("N GB
  free" · "Each profile uses some"), **One restart** ("Turns on Android's debug flag until you undo", only when the
  unlock is needed), **Undo anytime** ("A small Magisk module · Restore default"); **Allow N profiles** (primary) and
  **Not now**;
- **Otherwise:** Apply again (when needed), **Make room / Change limit**, **Android's switcher** row ("Your way back if
  Cyclone can't switch", On / Turn on), and **Restore default** (red) when Cyclone raised it;
- **Done state:** a green check (or amber restart icon) and "This phone now holds up to N profiles." /
  "After a restart, this phone holds up to N profiles.", **Done**.

Confirm text for the plan's confirm sheet (`ProfileProblem.allowText`): "Let this phone hold up to N profiles? It holds
U now. Cyclone sets Android's user limit and keeps it after restarts with a small Magisk module, "Cyclone profiles".
[unlock sentence] Each profile uses storage; you have G GB free. You can undo it in Profiles → Profile room → Restore
default; profiles you have stay."

### 8.8 Safety rules

Owner-only tap (never a Mind, MCP, PC or Glass tool); one property and one module folder, nothing else; every change
read back and recorded in the debug file; fully reversible; it adds Android users for the owner's own profiles and does
not hide root, change device identity or touch other apps.

## 9. When something goes wrong (the problem panel)

One glass card used in setup, on the Profiles home and on the rescue screen:
- a red round "!" and the **headline** in the profile's name; the **reason**; the **action**;
- fix buttons, the first one primary: **Empty Recently deleted**, **Clean up unfinished**, **Make room** (opens the
  Room sheet in choosing mode), **Try again**; **Open Android's users** always last and quiet;
- for root failures: "Open Magisk, KernelSU or APatch and allow Cyclone.";
- a folded **Details**: ✓/✗ checks ("Root works (Magisk)", "Places on the phone: U of L in use", "This kind of user:
  X of Y allowed by the phone's system"), "In use: <room line>", "Android said: <raw words>";
- **Debug report** link.

Headlines by kind (excerpt): "User limit reached" (MAX_USERS_REACHED), "No room for <name>" (USER_TYPE_LIMIT),
"<Name> can't be added on this phone" (USER_TYPE_DISABLED), "<Name> couldn't be added" (PROFILE_CREATION_REJECTED),
"<Name> needs to finish starting" (PROFILE_NOT_UNLOCKED), "An app couldn't be added", "Root needs attention",
"Root check failed".

**Debug report:** "What Cyclone knows about profiles here. No keys, passwords, codes or app data." — **Share** /
**Save** / **Copy** (summary). A zip `cyclone-profile-debug-<time>` with `debug.json` + `summary.txt`: app/device,
root manager, users and limits, registry (ids only), journal, last 200 privileged steps with Android's raw output
(8 KB, redacted), last 20 switches, carry counts, connectors, the error. Secrets are stripped with `looksSecret` plus
fixed patterns; nothing is uploaded by itself.

## 10. What travels between profiles (Cyclone Carry)

- **On every switch** (any direction): people memory (labelled by the profile it came from: "This profile", "From
  Work"), the Brain's verified skills and paths, notes, app-opening evidence, Drive and Visual quality, and the 11
  CARRY settings files (Hands, Modes, Fast Path, planes, setup cards, working indicator, owner notes switch, Automation
  Studio routines and skills, cornerstone marks), Market installs, owner skills, app manuals (≤ 200, 4 MB).
- **Once at setup:** `cyclone_ai` (model, reasoning, access profile, safe mode) and the OpenRouter key, sealed.
- **Never:** keys, tokens, PC pairing, sessions, the vault, chat history, missions, run logs, or anything that looks
  like a secret. A CI guard fails the build when a settings file isn't classified (34 files: 11 CARRY, 16 PER_PROFILE,
  7 NEVER).
- **Sealed:** AES-256-GCM with the key wrapped by RSA-OAEP for the destination's Keystore key; destination and nonce
  bound as associated data.
- **"What each profile has"** (In every profile → At the last switch): "All there", or "N missing" with the names, or
  a Cloak note.

## 11. Cyclone Cloak hooks

- Cloak's approval is carried into new profiles and **re-verified** there (same package, connector id, certificate
  lineage, scopes); an owner revoke in that profile wins.
- Bindings are removed only when the profile is deleted for good or Android's own package list for that profile no
  longer has the app; a user-id change migrates them.
- **Cloak binds every profile except Main** (owner decision 2026-10-09).
- After a verified switch, the target's Cyclone sends `SWITCHED` to its own connectors.

## 12. Why it was designed this way (the decisions)

- **Real Android users, not work profiles:** whole-user switching, separate logins per app, and no work-profile policy
  getting in the way.
- **Fixed commands only:** no free shell, every command shape-checked and CI-guarded; the model never gets root.
- **Safety before power (plan 40 P1 first):** soft delete with a 7-day trash "like a photos app", automatic backups,
  "no backup, no delete".
- **Truthful errors (plan 57):** the owner's "Profile B already exists" while making Profile C came from Android's
  limit sentence containing "already exists"; so limits are checked first, every message names the right profile, the
  room line shows what really uses the places, and every failure offers a debug file.
- **A switcher that always works:** a dead-man return, adaptive waits, a rescue screen and Android's own switcher as
  the last way out.
- **Room as an owner choice, reversible:** one property, one module, read back, Restore default; Android's own
  creation override instead of a phone-specific framework overlay for per-type caps.
- **Calm, few words, one action:** one primary button per screen, one-line labels that never wrap, state as a dot and
  one word, the face (emoji/initial in a colour) as the profile's identity.

## 13. Built vs. not yet

- **Not built:** restoring a backup into a new profile; snapshots/clone; templates; run-in-every-profile; carrying app
  maps/atlas, dictionaries, playbooks; KernelSU/APatch grants by command.
- **Unverified on hardware:** the 50-switch run, the per-type unlock, the one restart and the 100 ceiling.
