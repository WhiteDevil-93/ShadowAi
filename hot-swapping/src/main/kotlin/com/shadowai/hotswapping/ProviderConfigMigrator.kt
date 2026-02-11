package com.shadowai.hotswapping

/**
 * Migrates provider config snapshots between versions.
 */
class ProviderConfigMigrator(
    private val currentVersion: Int = 1
) {
    /**
     * Migrates a snapshot to the latest schema version.
     */
    fun migrate(snapshot: ProviderConfigSnapshot): ProviderConfigSnapshot {
        if (snapshot.version >= currentVersion) {
            return snapshot.copy(updatedAt = System.currentTimeMillis())
        }

        var migrated = snapshot
        if (snapshot.version < 1) {
            migrated = migrated.copy(version = 1)
        }

        return migrated.copy(version = currentVersion, updatedAt = System.currentTimeMillis())
    }
}
