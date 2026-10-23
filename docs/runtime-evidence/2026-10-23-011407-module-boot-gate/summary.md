# Module boot gate — preflight complete; artifact selection pending

No module installation, removal, reboot, production APK change, permission mutation, AppOps mutation or diagnostic binding has occurred. Both users 0 and 17 were RUNNING_UNLOCKED. Root access was available.

The installed companion is alpha.27/code 51. Its APK signature verifies (RSA 2048; signer SHA-256 17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553). Installed module files match the alpha.27 module ZIP embedded in that installed APK exactly. The repository candidate is alpha.28/code 52 and differs from the APK embedded ZIP. Neither artifact is being substituted silently.

APK SHA-256: df37f86da7ea617fcd1aad65504b23c95aaad56a1f9726ca3270846f8165cffc

Embedded alpha.27 ZIP SHA-256: 53649be46759c3b8ba461d122a20d214727f310ceb34ee9fa296a4a2e7454b3f

Repository alpha.28 ZIP SHA-256: 9e7e3dad93c1650f70919371f493d115b18cc39dd4f7fddc6f890f2efb2c8298

Read-only evidence: package metadata, APK/signature, both module ZIPs, installed module per-file hashes, stable permission/AppOps modes, binding index/store hashes, published-body and publisher-share hashes. Exact ADB commands and results are recorded in commands.json. Host signature command: C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat verify --verbose --print-certs installed-companion.apk.

Boot load, bound captures, module removal, post-removal restoration and final audit are NOT RUN. Awaiting owner selection of repository alpha.28 candidate versus the installed APK embedded alpha.27 module. No verification or platform verdict claimed. Nothing staged, committed, tagged or released.
