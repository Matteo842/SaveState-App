package com.savestate.app.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-level cache for the license verification result.
 *
 * The verification runs at most once per process (until the user explicitly
 * retries or the previous attempt was blocked). This means transient
 * activity recreations — most notably configuration changes such as
 * rotation — do not re-trigger the verify call and, crucially, do not
 * flash the [com.savestate.app.security.ui.LicenseCheckSplash] every time.
 *
 * The [status] flow is the single source of truth consumed by the UI:
 *   - `null`        → first verification still in flight (initial cold start)
 *   - `Ok`          → cached success, no further work needed this process
 *   - `Blocked`     → cached failure, UI must show the block screen
 */
object LicenseGate {

    private val _status = MutableStateFlow<LicenseStatus?>(null)
    val status: StateFlow<LicenseStatus?> = _status.asStateFlow()

    // Dedicated supervisor scope so an in-flight verify survives the
    // Activity that originally requested it (e.g. rotated mid-check).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var inFlight: Job? = null
    private val lock = Any()

    /**
     * Kick off a verification if there is no cached successful result and
     * no other verify is currently running. Safe to call from any
     * Activity.onCreate — repeated calls are coalesced.
     */
    fun ensureStarted(guard: LicenseGuard) {
        synchronized(lock) {
            if (_status.value is LicenseStatus.Ok) return
            if (inFlight?.isActive == true) return
            inFlight = scope.launch {
                val result = withContext(Dispatchers.IO) { guard.verify() }
                _status.value = result
            }
        }
    }

    /**
     * Force a re-verification, used by the manual retry button on the
     * block screen. Resets [status] to `null` so the UI can show the
     * splash again while the new attempt runs.
     */
    fun retry(guard: LicenseGuard) {
        synchronized(lock) {
            inFlight?.cancel()
            _status.value = null
            inFlight = scope.launch {
                val result = withContext(Dispatchers.IO) { guard.verify() }
                _status.value = result
            }
        }
    }
}
