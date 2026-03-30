package eu.ottop.yamlauncher.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Centralized logging utility for YAM Launcher.
 * Provides file-based logging with automatic log rotation and export functionality.
 *
 * Features:
 * - Thread-safe singleton implementation
 * - Automatic log rotation when file exceeds 5MB or 5000 entries
 * - File-based persistence in app's private storage
 * - Export functionality for sharing logs
 *
 * Usage:
 * val logger = Logger.getInstance(context)
 * logger.d("Tag", "Debug message")
 * logger.i("Tag", "Info message")
 * logger.w("Tag", "Warning message")
 * logger.e("Tag", "Error message", throwable)
 */
class Logger private constructor(private val context: Context) {

    companion object {
        private const val TAG = "YAMLogger"
        private const val LOG_FILE_NAME = "yam_launcher.log"
        private const val MAX_LOG_FILE_SIZE = 1024 * 1024 * 5L // 5MB - maximum size before rotation
        private const val MAX_LOG_ENTRIES = 5000 // Maximum log entries before rotation

        // Volatile ensures thread-safe singleton initialization
        @Volatile
        private var instance: Logger? = null

        /**
         * Gets the singleton Logger instance.
         * Uses double-checked locking for thread safety.
         *
         * @param context Application context (will use applicationContext internally)
         * @return The singleton Logger instance
         */
        fun getInstance(context: Context): Logger {
            return instance ?: synchronized(this) {
                instance ?: Logger(context.applicationContext).also { instance = it }
            }
        }
    }

    // Single-threaded executor ensures all log writes are serialized
    private val logExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Date format for log timestamps - includes milliseconds for precise ordering
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // Counter for log entries to track rotation threshold
    private var logEntryCount = 0

    // Lazy initialization ensures file is created only when first accessed
    private val logFile: File by lazy {
        File(context.filesDir, LOG_FILE_NAME)
    }

    init {
        initializeLogFile()
    }

    /**
     * Initializes the log file if it doesn't exist.
     * Creates the file and writes the header with system information.
     */
    private fun initializeLogFile() {
        if (!logFile.exists()) {
            logFile.createNewFile()
            writeHeader()
        }
    }

    /**
     * Writes the log file header with app and device information.
     * Useful for debugging as it provides context about the environment.
     */
    private fun writeHeader() {
        val header = buildString {
            appendLine("========================================")
            appendLine("YAM Launcher Log File")
            appendLine("Started: ${dateFormat.format(Date())}")
            appendLine("App Version: ${getAppVersion()}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("========================================")
            appendLine()
        }
        writeToFile(header)
    }

    /**
     * Retrieves the app version name from the package manager.
     *
     * @return Version name string or "unknown" if retrieval fails
     */
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Logs a debug message.
     * Debug logs are visible in Logcat but not written to file by default.
     *
     * @param tag Source identifier (typically class name)
     * @param message The message to log
     */
    fun d(tag: String, message: String) {
        log("DEBUG", tag, message)
        Log.d(tag, message)
    }

    /**
     * Logs an info message.
     * Used for general operational information.
     *
     * @param tag Source identifier (typically class name)
     * @param message The message to log
     */
    fun i(tag: String, message: String) {
        log("INFO", tag, message)
        Log.i(tag, message)
    }

    /**
     * Logs a warning message.
     * Indicates potentially harmful situations but the app can continue.
     *
     * @param tag Source identifier (typically class name)
     * @param message The warning message
     * @param throwable Optional exception causing the warning
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log("WARN", tag, message, throwable)
        Log.w(tag, message, throwable)
    }

    /**
     * Logs an error message.
     * Indicates serious problems that need attention.
     *
     * @param tag Source identifier (typically class name)
     * @param message The error message
     * @param throwable Optional exception causing the error
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
        Log.e(tag, message, throwable)
    }

    /**
     * Logs an exception with full stack trace.
     * Includes exception class name, message, and complete stack trace.
     *
     * @param tag Source identifier (typically class name)
     * @param message Context message describing what was happening
     * @param throwable The exception to log
     */
    fun exception(tag: String, message: String, throwable: Throwable) {
        val stackTrace = Log.getStackTraceString(throwable)
        val fullMessage = "$message\n${throwable.javaClass.name}: ${throwable.message}\n$stackTrace"
        log("EXCEPTION", tag, fullMessage)
        Log.e(tag, message, throwable)
    }

    /**
     * Core logging method that handles file writing and rotation.
     * Executes on a background thread via logExecutor.
     *
     * @param level Log level string (DEBUG, INFO, WARN, ERROR, EXCEPTION)
     * @param tag Source identifier
     * @param message The log message
     * @param throwable Optional exception to include
     */
    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        logExecutor.execute {
            try {
                // Check if rotation is needed before writing
                if (shouldRotate()) {
                    rotateLogFile()
                }

                val timestamp = dateFormat.format(Date())
                val logLine = if (throwable != null) {
                    val stackTrace = Log.getStackTraceString(throwable)
                    "[$timestamp] $level/$tag: $message\n${stackTrace}\n"
                } else {
                    "[$timestamp] $level/$tag: $message\n"
                }

                writeToFile(logLine)
                logEntryCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log entry", e)
            }
        }
    }

    /**
     * Determines if log file rotation should occur.
     * Rotation happens when file exceeds size or entry limit.
     *
     * @return true if rotation is needed
     */
    private fun shouldRotate(): Boolean {
        return logFile.length() > MAX_LOG_FILE_SIZE || logEntryCount > MAX_LOG_ENTRIES
    }

    /**
     * Rotates the log file to prevent unlimited growth.
     * Creates a backup of current log and starts a new file.
     * Only keeps one backup file to save storage space.
     */
    private fun rotateLogFile() {
        try {
            // Create backup of current log
            val backupFile = File(context.filesDir, "$LOG_FILE_NAME.old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)

            // Create new log file with header
            logFile.createNewFile()
            logEntryCount = 0
            writeHeader()

            // Delete old backup if it exists (keep only 1 backup)
            val oldBackup = File(context.filesDir, "$LOG_FILE_NAME.old.old")
            if (oldBackup.exists()) {
                oldBackup.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log file", e)
        }
    }

    /**
     * Writes content to the log file.
     * Uses FileWriter in append mode for thread safety.
     *
     * @param content The content to write
     */
    private fun writeToFile(content: String) {
        FileWriter(logFile, true).use { writer ->
            writer.append(content)
        }
    }

    /**
     * Gets the log file for sharing/exporting.
     * Useful for attaching logs to bug reports.
     *
     * @return File object pointing to the log file
     */
    fun getLogFileForExport(): File = logFile

    /**
     * Gets the current log content as a String.
     * Useful for displaying logs in-app or sending via email.
     *
     * @return The complete log content or empty string if file doesn't exist
     */
    fun getLogContent(): String {
        return try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read log file", e)
            ""
        }
    }

    /**
     * Clears the log file.
     * Deletes the current file and creates a fresh one with header.
     * Useful for users who want to start with clean logs.
     */
    fun clearLogs() {
        logExecutor.execute {
            try {
                logFile.delete()
                logFile.createNewFile()
                logEntryCount = 0
                writeHeader()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear log file", e)
            }
        }
    }

    /**
     * Gets the current log file size in bytes.
     * Useful for checking storage usage or determining if rotation is needed.
     *
     * @return File size in bytes
     */
    fun getLogFileSize(): Long = logFile.length()

    /**
     * Shuts down the logger executor service.
     * Call this when the app is terminating to prevent memory leaks.
     */
    fun shutdown() {
        logExecutor.shutdown()
    }
}
