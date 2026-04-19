package com.savestate.app.security.stub

import android.content.Context
import com.savestate.app.security.LicenseGuard
import com.savestate.app.security.LicenseGuardProvider
import com.savestate.app.security.LicenseStatus

/**
 * No-op provider used when the gitignored real provider is absent.
 *
 * This is the implementation that ships in the public source tree, so
 * anyone cloning the repository ends up with a fully functional build
 * that simply skips the Play Store / licensing checks.
 */
class StubLicenseGuardProvider : LicenseGuardProvider {
    override fun create(context: Context): LicenseGuard = StubLicenseGuard
}

private object StubLicenseGuard : LicenseGuard {
    override suspend fun verify(): LicenseStatus = LicenseStatus.Ok
}
