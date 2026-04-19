package com.savestate.app.security

import android.content.Context

/**
 * Factory for [LicenseGuard]. Implemented twice:
 *  - `security.stub.StubLicenseGuardProvider` (committed, no-op)
 *  - `security.secure.RealLicenseGuardProvider` (gitignored, real checks)
 *
 * Implementations MUST have a public no-arg constructor so that
 * [LicenseGuardLoader] can instantiate them via reflection.
 */
interface LicenseGuardProvider {
    fun create(context: Context): LicenseGuard
}
