package com.tapasya.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.tapasya.focus.databinding.ActivityAppSelectionBinding
import com.tapasya.focus.databinding.RowInstalledAppBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppSelectionBinding
    private var allApps: List<InstalledLaunchableApp> = emptyList()
    private val selectedPackages = mutableSetOf<String>()
    private var appListReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SessionManager.isActive(this)) {
            finish()
            return
        }

        binding = ActivityAppSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        selectedPackages += SessionManager.selectedPackages(this)
        binding.searchInput.doAfterTextChanged { renderApps(it?.toString().orEmpty()) }
        binding.saveSelectionButton.setOnClickListener {
            SessionManager.saveSelectedPackages(this, selectedPackages)
            setResult(RESULT_OK)
            finish()
        }

        updateCount()
        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val loadedApps = withContext(Dispatchers.IO) {
                InstalledAppsRepository.loadLaunchableApps(applicationContext)
            }
            allApps = loadedApps
            if (!SessionManager.hasSavedSelection(this@AppSelectionActivity)) {
                selectedPackages += InstalledAppsRepository.defaultSuggestedSelection(loadedApps)
            }
            appListReady = true
            binding.loadingText.visibility = View.GONE
            renderApps(binding.searchInput.text?.toString().orEmpty())
        }
    }

    private fun renderApps(query: String) {
        if (!appListReady || !::binding.isInitialized) return
        val normalizedQuery = query.trim().lowercase()
        val visibleApps = allApps.filter { app ->
            normalizedQuery.isEmpty() ||
                app.label.lowercase().contains(normalizedQuery) ||
                app.packageName.lowercase().contains(normalizedQuery)
        }

        binding.appList.removeAllViews()
        binding.emptyText.visibility = if (visibleApps.isEmpty()) View.VISIBLE else View.GONE
        visibleApps.forEach { app ->
            val rowBinding = RowInstalledAppBinding.inflate(
                LayoutInflater.from(this),
                binding.appList,
                false
            )
            rowBinding.appName.text = app.label
            rowBinding.appCheckbox.setOnCheckedChangeListener(null)
            rowBinding.appCheckbox.isChecked = app.packageName in selectedPackages
            try {
                rowBinding.appIcon.setImageDrawable(packageManager.getApplicationIcon(app.packageName))
            } catch (_: Exception) {
                rowBinding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            rowBinding.appCheckbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedPackages.add(app.packageName)
                else selectedPackages.remove(app.packageName)
                SessionManager.saveSelectedPackages(this, selectedPackages)
                updateCount()
            }
            rowBinding.root.setOnClickListener {
                rowBinding.appCheckbox.isChecked = !rowBinding.appCheckbox.isChecked
            }
            binding.appList.addView(rowBinding.root)
        }
    }

    private fun updateCount() {
        if (!::binding.isInitialized) return
        binding.selectedCount.text = getString(R.string.picker_count, selectedPackages.size)
    }

    override fun onBackPressed() {
        // Changes are persisted on each checkbox action; Back never starts or cancels a session.
        super.onBackPressed()
    }
}
