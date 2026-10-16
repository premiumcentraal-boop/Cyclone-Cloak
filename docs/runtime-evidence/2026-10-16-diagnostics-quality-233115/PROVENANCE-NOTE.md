# Evidence provenance clarification — 2026-10-17

The original evidence ZIP is unchanged (19,530 bytes), SHA-256
`d80f3be6c014e611a266bd44f3a96751f86ad2295f3f83928ad356649a52138b`.

`manifest.json.permissions` was collected by flattening all matching entries
from an unscoped `dumpsys package` output. Later Android-user blocks overwrite
earlier values. It is **not a per-user permission attestation**: its false values
cannot establish denial for the measured user 0. Do not infer the complete grant
state from this map. No grants or revocations were issued by this run.

The user-0 reports independently contain the executed reads: Wi-Fi service MAC,
Bluetooth address and advertising ID matched both fixtures; SSID, BSSID, link
speed and Bluetooth name were observed without fixture expectations. There are
zero blocked rows. On-device denial/revocation paths were not tested.

The actual measured diagnostics APK SHA-256 is
`033714e32fb305fd5d5aff5d6448b669077f2ed0feb91ea6860ec89a6b200ad4`.
The installed companion/module were alpha.15/code 39, with installed ARM64 module
file SHA-256
`ffebdf4436876852db5fe3c8a817a351a470137117411f98410f37e3907020cd`.
Neither this evidence nor matching behavior-only source establishes physical
verification of newly packaged alpha.16 APK/module binaries or an external
platform verdict. Scope is Pixel 8 / user 0 / bound A, repeat A, bound B.
