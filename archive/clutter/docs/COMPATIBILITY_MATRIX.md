# Compatibility Matrix (Authoritative Contract)

This document defines the only allowed toolchain and platform versions.
Any drift must fail CI.

| Component | Version | Notes |
|---------|--------|------|
| Java | 17 | Required for AGP 8+ |
| Gradle | 8.7 | Wrapper-pinned |
| Android Gradle Plugin | 8.5.0 | Android 16 compatible |
| Kotlin | 1.9.24 | AGP compatible |
| compileSdk | 36 | Android 16 |
| targetSdk | 36 | Android 16 |
| minSdk | 24 | Android 7.0 |

## Rules
- No dynamic versions allowed.
- No silent upgrades.
- CI must validate this matrix before any build.
