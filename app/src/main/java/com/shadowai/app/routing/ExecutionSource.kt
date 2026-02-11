package com.shadowai.app.routing

/**
 * Defines the physical location where a task is executed.
 * This is a capability descriptor, not a routing instruction.
 */
enum class ExecutionSource {
    /**
     * The task is executed entirely on the device.
     * No data leaves the device boundary.
     */
    LOCAL,

    /**
     * The task is delegated to a remote server.
     * Data leaves the device boundary.
     */
    CLOUD
}
