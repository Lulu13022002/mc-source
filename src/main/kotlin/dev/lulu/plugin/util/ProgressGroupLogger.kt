package dev.lulu.plugin.util

import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory

class ProgressGroupLogger(
    private val initialStatus: String,
    private val factory: ProgressLoggerFactory,
    private val size: Int
) : AutoCloseable {

    private val parent = factory.newOperation(this::class.java).start(initialStatus, "")
    private val loggers = Array(size) {
        factory.newOperation(this::class.java, parent).start(initialStatus, "")
    }

    private var index = 0

    fun write(status: String, failing: Boolean = false) {
        val logger = loggers[index]
        logger.progress(status, failing)
        index = (index + 1) % size
    }

    override fun close() {
        loggers.forEach(ProgressLogger::completed)
        parent.completed()
    }
}
