package com.tapasya.focus

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.core.os.LocaleListCompat
import com.google.android.material.button.MaterialButton
import com.tapasya.focus.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var summaryLoadInProgress = false

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Notifications are optional for launching an Android foreground service. The ongoing
            // service notification is still supplied; Android may show it only in Task Manager.
            startSessionIfValid()
        }

    private val countdown = object : Runnable {
        override fun run() {
            val session = SessionManager.loadActiveSession(this@MainActivity)
            if (session == null) {
                refreshScreen()
                return
            }

            val remaining = session.endTime - System.currentTimeMillis()
            if (remaining <= 0L) {
                SessionManager.completeIfExpired(this@MainActivity)
                refreshScreen()
                return
            }

            binding.countdownText.text = NotificationHelper.formatRemaining(remaining)
            mainHandler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedLanguage = SessionManager.language(this)
        if (savedLanguage == null) {
            SessionManager.setLanguage(this, "hi")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))
            recreate()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureControls()
        initializeSuggestedApps()
        refreshScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            refreshScreen()
            updateSelectedAppsSummary()
        }
    }

    override fun onPause() {
        mainHandler.removeCallbacks(countdown)
        super.onPause()
    }

    private fun configureControls() {
        binding.languageButton.setOnClickListener { toggleLanguage() }
        binding.accessibilitySettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.selectAppsButton.setOnClickListener {
            if (!SessionManager.isActive(this)) {
                startActivity(Intent(this, AppSelectionActivity::class.java))
            }
        }
        binding.startButton.setOnClickListener { onStartTapped() }

        val presetButtons = listOf(
            binding.duration25 to 25,
            binding.duration45 to 45,
            binding.duration60 to 60,
            binding.duration90 to 90,
            binding.duration120 to 120
        )
        presetButtons.forEach { (button, _) ->
            button.setOnClickListener {
                binding.customDurationInput.text?.clear()
                if (button.id == binding.duration90.id || button.id == binding.duration120.id) {
                    binding.durationGroup.clearChecked()
                    binding.durationGroupSecondRow.check(button.id)
                } else {
                    binding.durationGroupSecondRow.clearChecked()
                    binding.durationGroup.check(button.id)
                }
                clearError()
            }
        }
        binding.customDurationInput.inputType = InputType.TYPE_CLASS_NUMBER
        binding.customDurationInput.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                binding.durationGroup.clearChecked()
                binding.durationGroupSecondRow.clearChecked()
            }
            clearError()
        }
    }

    private fun initializeSuggestedApps() {
        if (SessionManager.hasSavedSelection(this)) return
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                InstalledAppsRepository.loadLaunchableApps(applicationContext)
            }
            if (!SessionManager.hasSavedSelection(this@MainActivity)) {
                SessionManager.saveSelectedPackages(
                    this@MainActivity,
                    InstalledAppsRepository.defaultSuggestedSelection(apps)
                )
            }
            updateSelectedAppsSummary(apps)
        }
    }

    private fun updateSelectedAppsSummary(
        suppliedApps: List<InstalledLaunchableApp>? = null
    ) {
        if (summaryLoadInProgress || !::binding.isInitialized) return
        val selected = SessionManager.selectedPackages(this)
        if (selected.isEmpty()) {
            binding.selectedAppsSummary.text = getString(R.string.apps_none)
            return
        }

        if (suppliedApps != null) {
            setSelectedSummary(selected, suppliedApps)
            return
        }

        summaryLoadInProgress = true
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                InstalledAppsRepository.loadLaunchableApps(applicationContext)
            }
            summaryLoadInProgress = false
            if (::binding.isInitialized) {
                setSelectedSummary(SessionManager.selectedPackages(this@MainActivity), apps)
            }
        }
    }

    private fun setSelectedSummary(
        selectedPackages: Set<String>,
        apps: List<InstalledLaunchableApp>
    ) {
        val namesByPackage = apps.associate { it.packageName to it.label }
        val names = selectedPackages.sortedBy { namesByPackage[it] ?: it }
            .map { namesByPackage[it] ?: it }
        val displayNames = names.take(3).joinToString(", ")
        val additional = if (names.size > 3) " +${names.size - 3}" else ""
        binding.selectedAppsSummary.text =
            "${getString(R.string.apps_selected_count, names.size)} · $displayNames$additional"
    }

    private fun onStartTapped() {
        if (SessionManager.isActive(this)) {
            refreshScreen()
            return
        }
        if (durationMinutes() == null) {
            showError(getString(R.string.validation_duration))
            return
        }
        if (SessionManager.selectedPackages(this).isEmpty()) {
            showError(getString(R.string.validation_apps))
            return
        }
        if (!isTapasyaAccessibilityEnabled()) {
            showError(getString(R.string.validation_accessibility))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startSessionIfValid()
    }

    private fun startSessionIfValid() {
        if (SessionManager.isActive(this)) return
        val duration = durationMinutes()
        if (duration == null) {
            showError(getString(R.string.validation_duration))
            return
        }
        val packages = SessionManager.selectedPackages(this)
        if (packages.isEmpty()) {
            showError(getString(R.string.validation_apps))
            return
        }
        if (!isTapasyaAccessibilityEnabled()) {
            showError(getString(R.string.validation_accessibility))
            return
        }

        SessionManager.startSession(this, duration, packages)
        clearError()
        try {
            val serviceIntent = Intent(this, StudyTimerForegroundService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (_: IllegalStateException) {
            // The exact persisted deadline is retained; the accessibility service and the next
            // app launch will try to restore the foreground timer without extending the session.
        } catch (_: SecurityException) {
            // Keep the strict deadline; Android can restrict service starts on some device builds.
        }
        refreshScreen()
    }

    private fun durationMinutes(): Int? {
        val custom = binding.customDurationInput.text?.toString()?.trim().orEmpty()
        if (custom.isNotEmpty()) {
            val parsed = custom.toIntOrNull() ?: return null
            return parsed.takeIf { it in 10..180 }
        }

        val selectedId = when {
            binding.durationGroup.checkedButtonId != View.NO_ID ->
                binding.durationGroup.checkedButtonId
            binding.durationGroupSecondRow.checkedButtonId != View.NO_ID ->
                binding.durationGroupSecondRow.checkedButtonId
            else -> View.NO_ID
        }
        return when (selectedId) {
            binding.duration25.id -> 25
            binding.duration45.id -> 45
            binding.duration60.id -> 60
            binding.duration90.id -> 90
            binding.duration120.id -> 120
            else -> null
        }
    }

    private fun isTapasyaAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!enabled) return false

        val expected = ComponentName(this, TapasyaAccessibilityService::class.java)
            .flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun refreshScreen() {
        if (!::binding.isInitialized) return
        SessionManager.completeIfExpired(this)
        val session = SessionManager.loadActiveSession(this)
        val active = session != null

        binding.activeSection.visibility = if (active) View.VISIBLE else View.GONE
        binding.durationCard.visibility = if (active) View.GONE else View.VISIBLE
        binding.appsCard.visibility = if (active) View.GONE else View.VISIBLE
        binding.accessibilityCard.visibility = if (active) View.GONE else View.VISIBLE
        binding.startButton.visibility = if (active) View.GONE else View.VISIBLE
        binding.notificationPermissionNote.visibility = if (active) View.GONE else View.VISIBLE
        binding.languageButton.visibility = if (active) View.GONE else View.VISIBLE
        binding.completionSection.visibility =
            if (!active && SessionManager.lastCompletion(this) != null) View.VISIBLE else View.GONE

        SessionManager.lastCompletion(this)?.let { completion ->
            binding.completionTotal.text =
                getString(R.string.completion_total, completion.totalMinutes)
        }

        val accessibilityEnabled = isTapasyaAccessibilityEnabled()
        binding.accessibilityTitle.text = getString(
            if (accessibilityEnabled) R.string.accessibility_enabled
            else R.string.accessibility_title
        )
        if (active && session != null) {
            val remaining = session.endTime - System.currentTimeMillis()
            if (remaining <= 0L) {
                SessionManager.completeIfExpired(this)
                refreshScreen()
                return
            }
            binding.countdownText.text = NotificationHelper.formatRemaining(remaining)
            mainHandler.removeCallbacks(countdown)
            mainHandler.post(countdown)
            ensureForegroundTimer()
        } else {
            mainHandler.removeCallbacks(countdown)
            updateSelectedAppsSummary()
        }
    }

    private fun ensureForegroundTimer() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, StudyTimerForegroundService::class.java)
            )
        } catch (_: IllegalStateException) {
            // No deadline changes are made; the service will be retried when the app is reopened.
        } catch (_: SecurityException) {
            // Accessibility remains active and all countdown calculations still use the saved endTime.
        }
    }

    private fun toggleLanguage() {
        if (SessionManager.isActive(this)) return
        val next = if (SessionManager.language(this) == "en") "hi" else "en"
        SessionManager.setLanguage(this, next)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun clearError() {
        binding.errorText.visibility = View.GONE
    }
}
