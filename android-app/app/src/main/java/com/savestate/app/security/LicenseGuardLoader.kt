package com.savestate.app.security

import android.content.Context
import android.util.Log
import com.savestate.app.BuildConfig
import com.savestate.app.security.stub.StubLicenseGuardProvider

/**
 * Resolves the active [LicenseGuard] at runtime.
 *
 * The fully qualified name of the real provider is looked up reflectively
 * so the public source tree never references the (gitignored) `secure`
 * package. If the class is missing — as is the case for any GitHub clone —
 * the loader silently falls back to the no-op [StubLicenseGuardProvider].
 *
 * In **debug builds** ([BuildConfig.BYPASS_LICENSE] == true) the real
 * provider is never consulted, making sideloaded test builds always pass
 * the license check without Play Store involvement.
 */
object LicenseGuardLoader {

    private const val TAG = "LicenseGuardLoader"
    private const val REAL_PROVIDER_FQCN =
        "com.savestate.app.security.secure.RealLicenseGuardProvider"

    fun load(context: Context): LicenseGuard {
        // Debug / CI builds: skip licensing entirely so we can sideload and
        // test without going through Play Store review each time.
        if (BuildConfig.BYPASS_LICENSE) {
            Log.d(TAG, "BYPASS_LICENSE=true – using stub (debug build)")
            return StubLicenseGuardProvider().create(context.applicationContext)
        }

        val real = tryLoadRealProvider()
        return (real ?: StubLicenseGuardProvider()).create(context.applicationContext)
    }

    private fun tryLoadRealProvider(): LicenseGuardProvider? {
        return try {
            val cls = Class.forName(REAL_PROVIDER_FQCN)
            val instance = cls.getDeclaredConstructor().newInstance()
            instance as? LicenseGuardProvider
        } catch (e: ClassNotFoundException) {
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Real license provider failed to load, using stub", t)
            null
        }
    }
}
