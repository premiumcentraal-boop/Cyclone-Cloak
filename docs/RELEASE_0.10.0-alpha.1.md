# Cyclone Cloak 0.10.0-alpha.1

A reorganised app that makes adding phones easy.

- **Four tabs:** Phones, Identities, Profiles and Health, instead of one long page.
- **Add any phone:**
  - **Import** a real phone's `build.prop` or `adb shell getprop` output. Cloak reads the model,
    build, carrier, network, density, language and time zone, then asks only for what a dump can't
    tell (screen size and refresh rate).
  - **Build a phone** from scratch. Pick the Android version and the SDK level follows; the build
    fingerprint is composed from its parts.
  - **Clone** a built-in phone and change what you need.
  - **Share** a phone as a file, and import phone files from others.
- **The full coherence rules in the app.** The same rules as the desktop forge (fingerprint vs. model
  vs. build, release vs. SDK, patch level, carrier codes, screen, locale) check imports and the
  builder, with each problem shown on its field. Before, an import was only checked for a name, a
  fingerprint format and an SDK level.
- **Identities:** see every value, rename, delete (blocked while bound), bind to a profile, search
  long lists.
- **Reliability:**
  - Every action runs off the main thread, one at a time.
  - Identity files are written atomically, and one damaged file no longer hides every other identity.
  - The built-in phones are one shared file for the app and the forge, with test cases both must
    answer the same.
  - CI now runs the app's unit tests, including every screen rendered on the JVM.

Update both the app and the module (Root Doctor → Check & repair, then restart). Not yet run on a
physical phone.
