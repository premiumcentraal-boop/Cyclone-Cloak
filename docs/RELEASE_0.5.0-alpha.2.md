# Cyclone Cloak 0.5.0-alpha.2

- Publish rooted profile state through Magisk's global mount namespace and stop
  reporting success when any copy, permission, or replacement step fails.
- Report rooted publish failures on bindings so connector readiness is not
  mistaken for a working Zygisk profile route.
- Read protected profile state through the Zygisk root companion during app
  specialization, instead of assuming the Zygote process can read `/data/adb`.
