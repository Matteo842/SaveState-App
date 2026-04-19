package com.savestate.app.security

/**
 * Anti-piracy gate verified at app startup.
 *
 * The real implementation lives in a gitignored sibling package
 * (`security.secure`) and is loaded reflectively by [LicenseGuardLoader];
 * builds without that package fall back to the stub which always returns
 * [LicenseStatus.Ok].
 */
interface LicenseGuard {
    suspend fun verify(): LicenseStatus
}

sealed class LicenseStatus {
    object Ok : LicenseStatus()
    data class Blocked(val reason: BlockReason, val message: String) : LicenseStatus()
}

enum class BlockReason {
    NOT_FROM_PLAY_STORE,
    INTEGRITY_FAILED,
    LICENSE_INVALID,
    NETWORK_ERROR,
    UNKNOWN
}
