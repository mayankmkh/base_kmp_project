package dev.mayankmkh.basekmpproject.foundation.runtime

/** The platform handle every storage and platform module is opened with. One instance per app. */
public expect class PlatformContext {
    /** Stable application identifier; names files, Keychain services and web storage keys. */
    public val applicationId: String
}
