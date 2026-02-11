package com.shadowai.app.db

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migration definitions.
 * 
 * IMPORTANT: When modifying the database schema, you MUST:
 * 1. Increment the database version in ShadowDatabase
 * 2. Create a new Migration object below
 * 3. Add the migration to the database builder
 * 4. Test the migration thoroughly before release
 * 
 * NEVER rely on fallbackToDestructiveMigration() in production - it will delete user data!
 */
object ShadowMigrations {

    private const val TAG = "ShadowMigrations"

    /**
     * Migration from version 1 to 2.
     * 
     * Changes: (template - update when implementing)
     * - Added new_column to existing_table
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 1 -> 2")
            
            // Example migration - UPDATE THIS when schema changes
            // database.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            
            Log.i(TAG, "Completed migration 1 -> 2")
        }
    }

    /**
     * Migration from version 2 to 3.
     * 
     * Changes:
     * - Added memory table columns and ledger table
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 2 -> 3")
            
            MigrationHelpers.addColumnIfNotExists(database, "memory", "layer", "TEXT", "'WORKING'")
            MigrationHelpers.addColumnIfNotExists(database, "memory", "confidence", "REAL", "1.0")
            MigrationHelpers.addColumnIfNotExists(database, "memory", "lastUpdated", "INTEGER", "0")
            
            Log.i(TAG, "Completed migration 2 -> 3")
        }
    }

    /**
     * Migration from version 3 to 4.
     * 
     * Changes:
     * - Added ledger_entries table
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 3 -> 4")
            
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `ledger_entries` (
                    `id` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `result` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `taskId` TEXT,
                    `provider` TEXT,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            
            Log.i(TAG, "Completed migration 3 -> 4")
        }
    }

    /**
     * Migration from version 4 to 5.
     * 
     * Changes:
     * - Added tasks table
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 4 -> 5")
            
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `tasks` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `input` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            
            Log.i(TAG, "Completed migration 4 -> 5")
        }
    }

    /**
     * Migration from version 5 to 6.
     * 
     * Changes:
     * - Added agent_failures table
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 5 -> 6")
            
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `agent_failures` (
                    `id` TEXT NOT NULL,
                    `taskId` TEXT NOT NULL,
                    `errorMessage` TEXT NOT NULL,
                    `errorCategory` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `providerId` TEXT,
                    `modelId` TEXT,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            
            Log.i(TAG, "Completed migration 5 -> 6")
        }
    }

    /**
     * Migration from version 6 to 7.
     * 
     * Changes:
     * - Added feedback table
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 6 -> 7")
            
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `feedback` (
                    `id` TEXT NOT NULL,
                    `messageId` TEXT NOT NULL,
                    `rating` INTEGER NOT NULL,
                    `comment` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            
            Log.i(TAG, "Completed migration 6 -> 7")
        }
    }

    /**
     * Migration from version 7 to 8.
     * 
     * Changes:
     * - Added indices for performance
     * - Added modelName and executionSource columns to messages
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 7 -> 8")
            
            // Add columns to messages for richer metadata
            MigrationHelpers.addColumnIfNotExists(database, "messages", "modelName", "TEXT")
            MigrationHelpers.addColumnIfNotExists(database, "messages", "executionSource", "TEXT")
            MigrationHelpers.addColumnIfNotExists(database, "messages", "error", "TEXT")
            MigrationHelpers.addColumnIfNotExists(database, "messages", "imageUri", "TEXT")
            
            // Add performance indices
            MigrationHelpers.createIndexIfNotExists(database, "idx_messages_timestamp", "messages", "timestamp")
            MigrationHelpers.createIndexIfNotExists(database, "idx_memory_layer", "memory", "layer")
            MigrationHelpers.createIndexIfNotExists(database, "idx_memory_lastUpdated", "memory", "lastUpdated")
            MigrationHelpers.createIndexIfNotExists(database, "idx_ledger_timestamp", "ledger_entries", "timestamp")
            MigrationHelpers.createIndexIfNotExists(database, "idx_tasks_state", "tasks", "state")
            MigrationHelpers.createIndexIfNotExists(database, "idx_failures_timestamp", "agent_failures", "timestamp")
            MigrationHelpers.createIndexIfNotExists(database, "idx_feedback_messageId", "feedback", "messageId")
            
            Log.i(TAG, "Completed migration 7 -> 8")
        }
    }

    /**
     * Migration from version 8 to 9.
     * 
     * Changes:
     * - Added model_paths table for persisting discovered model locations
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Starting migration 8 -> 9")
            
            // Create model_paths table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `model_paths` (
                    `path` TEXT NOT NULL,
                    `modelId` TEXT NOT NULL,
                    `lastDiscovered` INTEGER NOT NULL,
                    `valid` INTEGER NOT NULL,
                    PRIMARY KEY(`path`)
                )
            """)
            
            // Add index for valid column for faster filtering
            MigrationHelpers.createIndexIfNotExists(database, "idx_model_paths_valid", "model_paths", "valid")
            
            Log.i(TAG, "Completed migration 8 -> 9")
        }
    }

    /**
     * Get all migrations for the database builder.
     * 
     * Usage:
     * ```kotlin
     * Room.databaseBuilder(context, ShadowDatabase::class.java, "shadow_db")
     *     .addMigrations(*ShadowMigrations.getAllMigrations())
     *     .build()
     * ```
     */
    fun getAllMigrations(): Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9
    )

    /**
     * Validate that all migrations are properly defined.
     * Call this in debug builds to catch missing migrations early.
     */
    fun validateMigrations(currentVersion: Int) {
        val migrations = getAllMigrations()
        
        // Check for gaps in migration chain
        var expected = 1
        for (migration in migrations.sortedBy { it.startVersion }) {
            if (migration.startVersion != expected) {
                Log.e(TAG, "Migration gap detected! Expected $expected but found ${migration.startVersion}")
            }
            expected = migration.endVersion
        }

        // Check that we can migrate to current version
        if (expected < currentVersion && migrations.isNotEmpty()) {
            Log.w(TAG, "Missing migrations to reach version $currentVersion (have migrations up to $expected)")
        }

        Log.d(TAG, "Migration validation complete. ${migrations.size} migrations defined.")
    }
}

/**
 * Helper class for creating common migration operations safely.
 */
object MigrationHelpers {

    /**
     * Safely add a column if it doesn't exist.
     * SQLite doesn't support IF NOT EXISTS for ALTER TABLE, so we check first.
     */
    fun addColumnIfNotExists(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String,
        defaultValue: String? = null
    ) {
        val cursor = database.query("PRAGMA table_info($tableName)")
        val columnNames = mutableListOf<String>()
        
        while (cursor.moveToNext()) {
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex >= 0) {
                columnNames.add(cursor.getString(nameIndex))
            }
        }
        cursor.close()

        if (columnName !in columnNames) {
            val sql = buildString {
                append("ALTER TABLE $tableName ADD COLUMN $columnName $columnType")
                if (defaultValue != null) {
                    append(" DEFAULT $defaultValue")
                }
            }
            database.execSQL(sql)
            Log.d("MigrationHelpers", "Added column $columnName to $tableName")
        } else {
            Log.d("MigrationHelpers", "Column $columnName already exists in $tableName")
        }
    }

    /**
     * Safely create an index if it doesn't exist.
     */
    fun createIndexIfNotExists(
        database: SupportSQLiteDatabase,
        indexName: String,
        tableName: String,
        vararg columns: String
    ) {
        val columnList = columns.joinToString(", ")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS $indexName ON $tableName ($columnList)"
        )
        Log.d("MigrationHelpers", "Created index $indexName on $tableName")
    }

    /**
     * Rename a table safely using SQLite's ALTER TABLE.
     */
    fun renameTable(
        database: SupportSQLiteDatabase,
        oldName: String,
        newName: String
    ) {
        database.execSQL("ALTER TABLE $oldName RENAME TO $newName")
        Log.d("MigrationHelpers", "Renamed table $oldName to $newName")
    }

    /**
     * Create a table copy-based migration for complex schema changes.
     * This is needed when SQLite's ALTER TABLE is insufficient.
     * 
     * @param database The database
     * @param tableName The table to migrate
     * @param newSchema The CREATE TABLE statement for the new schema
     * @param columnMapping Mapping of old column names to new column names (or expressions)
     */
    fun recreateTable(
        database: SupportSQLiteDatabase,
        tableName: String,
        newSchema: String,
        columnMapping: Map<String, String>
    ) {
        val tempTable = "${tableName}_temp"
        
        // Create temp table with new schema
        val tempSchema = newSchema.replace(tableName, tempTable)
        database.execSQL(tempSchema)
        
        // Copy data from old to temp
        val columns = columnMapping.entries.joinToString(", ") { (old, new) ->
            if (old == new) old else "$old AS $new"
        }
        val newColumns = columnMapping.values.joinToString(", ")
        database.execSQL("INSERT INTO $tempTable ($newColumns) SELECT $columns FROM $tableName")
        
        // Drop old table
        database.execSQL("DROP TABLE $tableName")
        
        // Rename temp to original name
        database.execSQL("ALTER TABLE $tempTable RENAME TO $tableName")
        
        Log.d("MigrationHelpers", "Recreated table $tableName with new schema")
    }
}
