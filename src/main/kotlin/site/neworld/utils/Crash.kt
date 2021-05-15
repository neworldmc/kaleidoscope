package site.neworld.utils

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * This function is for unrecoverable errors derived from [exception].
 * Once this function is called, a stacktrace will be logged to the configured
 * logger and printed to the stdout at the same time.
 * The program will hang or halt depending on the configuration
 */
fun bugCheck(exception: Throwable): Nothing {
    val message = """
        Program crashes because of unexpected and unrecoverable error in execution path
        Exception: ${exception.javaClass.canonicalName}
        Message: ${exception.message}
        Localized Message: ${exception.localizedMessage}
        StackTrace: ${StringWriter().also { exception.printStackTrace(PrintWriter(it)) }}
    """.trimIndent()
    println(message)
    exitProcess(-1)
}