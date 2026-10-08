package com.tapasya.focus

import android.content.Context

data class StudySession(
    val startTime: Long,
    val endTime: Long,
    val blockedPackages: Set<String>
)

data class StudyCompletion(
    val startTime: Long,
    val endTime: Long,
    val totalMinutes: Int
)

/**
 * Synchronous SharedPreferences persistence is deliberate here: the exact endTime must be durable
 * before the foreground timer service is started.
 */
object SessionManager {
    private const val PREFS = "tapasya_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_START = "start_time"
    private const val KEY_END = "end_time"
    private const val KEY_ACTIVE_PACKAGES = "active_packages"
    private const val KEY_SELECTED_PACKAGES = "selected_packages"
    private const val KEY_HAS_SELECTION = "has_selection"
    private const val KEY_LAST_COMPLETION_START = "last_completion_start"
    private const val KEY_LAST_COMPLETION_END = "last_completion_end"
    private const val KEY_LAST_COMPLETION_MINUTES = "last_completion_minutes"
    private const val KEY_UI_LANGUAGE = "ui_language"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ACTIVE, false)

    fun loadActiveSession(context: Context): StudySession? {
        val prefs = preferences(context)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        val startTime = prefs.getLong(KEY_START, 0L)
        val endTime = prefs.getLong(KEY_END, 0L)
        if (startTime <= 0L || endTime <= startTime) return null
        return StudySession(
            startTime = startTime,
            endTime = endTime,
            blockedPackages = prefs.getStringSet(KEY_ACTIVE_PACKAGES, emptySet())
                ?.toSet()
                .orEmpty()
        )
    }

    fun startSession(context: Context, durationMinutes: Int, packages: Set<String>): StudySession {
        require(durationMinutes in 10..180)
        require(packages.isNotEmpty())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMinutes * 60_000L
        val session = StudySession(startTime, endTime, packages.toSet())
        val saved = preferences(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_START, startTime)
            .putLong(KEY_END, endTime)
            .putStringSet(KEY_ACTIVE_PACKAGES, packages.toSet())
            .commit()
        check(saved) { "Unable to persist the study session before starting its service." }
        return session
    }

    /**
     * Completes an expired session once. A null result means the session is still active or has
     * already been completed.
     */
    fun completeIfExpired(context: Context, now: Long = System.currentTimeMillis()): StudyCompletion? {
        val session = loadActiveSession(context) ?: return null
        if (now < session.endTime) return null

        val duration = ((session.endTime - session.startTime) / 60_000L)
            .coerceAtLeast(0L)
            .toInt()
        val completion = StudyCompletion(session.startTime, session.endTime, duration)
        val saved = preferences(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putLong(KEY_LAST_COMPLETION_START, completion.startTime)
            .putLong(KEY_LAST_COMPLETION_END, completion.endTime)
            .putInt(KEY_LAST_COMPLETION_MINUTES, completion.totalMinutes)
            .remove(KEY_START)
            .remove(KEY_END)
            .remove(KEY_ACTIVE_PACKAGES)
            .commit()
        check(saved) { "Unable to persist study session completion." }
        return completion
    }

    fun lastCompletion(context: Context): StudyCompletion? {
        val prefs = preferences(context)
        val endTime = prefs.getLong(KEY_LAST_COMPLETION_END, 0L)
        if (endTime <= 0L) return null
        return StudyCompletion(
            startTime = prefs.getLong(KEY_LAST_COMPLETION_START, 0L),
            endTime = endTime,
            totalMinutes = prefs.getInt(KEY_LAST_COMPLETION_MINUTES, 0)
        )
    }

    fun hasSavedSelection(context: Context): Boolean =
        preferences(context).getBoolean(KEY_HAS_SELECTION, false)

    fun selectedPackages(context: Context): Set<String> =
        preferences(context).getStringSet(KEY_SELECTED_PACKAGES, emptySet())
            ?.toSet()
            .orEmpty()

    fun saveSelectedPackages(context: Context, packages: Set<String>) {
        val saved = preferences(context).edit()
            .putStringSet(KEY_SELECTED_PACKAGES, packages.toSet())
            .putBoolean(KEY_HAS_SELECTION, true)
            .commit()
        check(saved) { "Unable to save selected apps." }
    }

    fun language(context: Context): String? =
        preferences(context).getString(KEY_UI_LANGUAGE, null)

    fun setLanguage(context: Context, language: String) {
        preferences(context).edit().putString(KEY_UI_LANGUAGE, language).apply()
    }
}
