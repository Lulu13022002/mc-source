package dev.lulu.plugin.util

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.slf4j.Logger

class VineflowerLogger(
    private val progressGroup: ProgressGroupLogger,
    private val logger: Logger
) : IFernflowerLogger() {

    override fun writeMessage(message: String, severity: Severity) {
        if (severity.ordinal < Severity.ERROR.ordinal) return

        progressGroup.write(message, true)
        logger.error(message)
    }

    override fun writeMessage(message: String, severity: Severity, t: Throwable) {
        if (severity.ordinal < Severity.ERROR.ordinal) return

        progressGroup.write(message, true)
        logger.error(message, t)
    }

    override fun startReadingClass(className: String) {
        progressGroup.write("Decompiling $className")
    }

    override fun startClass(className: String) {
        progressGroup.write("Decompiling $className")
    }

    override fun startWriteClass(className: String) {
    }

    override fun startMethod(methodName: String) {
    }

    override fun endMethod() {
    }
}
