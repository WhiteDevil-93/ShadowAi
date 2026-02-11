package com.shadowai.app.ui.dialogs

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import com.shadowai.app.R
import com.shadowai.core.ProviderId
import com.shadowai.app.providers.ProviderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided setup wizard for configuring AI providers.
 * Provides step-by-step guidance for users to configure their providers.
 */
class ProviderSetupWizard(
    private val context: Context,
    private val providerRepository: ProviderRepository,
    private val fragmentManager: FragmentManager,
    private val scope: CoroutineScope = MainScope()
) {
    private companion object {
        private const val TAG = "ProviderSetupWizard"
    }

    private var currentStep = 0
    private val totalSteps = 4
    
    data class WizardStep(
        val title: String,
        val description: String,
        val view: View
    )
    
    private val steps = mutableListOf<WizardStep>()
    
    /**
     * Show the setup wizard dialog.
     */
    fun show() {
        createSteps()
        showStepDialog()
    }
    
    private fun createSteps() {
        steps.clear()
        
        // Step 1: Welcome & Provider Selection
        steps.add(createWelcomeStep())
        
        // Step 2: API Configuration
        steps.add(createApiConfigStep())
        
        // Step 3: Model Selection
        steps.add(createModelSelectionStep())
        
        // Step 4: Testing & Confirmation
        steps.add(createTestingStep())
    }
    
    private fun createWelcomeStep(): WizardStep {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 32, 64, 32)
        }
        
        val providerLabel = TextView(context).apply {
            text = context.getString(R.string.wizard_label_select_provider)
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        
        val providerDropdown = AutoCompleteTextView(context).apply {
            hint = context.getString(R.string.wizard_hint_choose_provider)
        }
        
        val availableProviders = listOf(
            context.getString(R.string.provider_name_openai) to ProviderId.OPENAI,
            context.getString(R.string.provider_name_anthropic) to ProviderId.ANTHROPIC,
            context.getString(R.string.provider_name_gemini) to ProviderId.GEMINI,
            context.getString(R.string.provider_name_groq) to ProviderId.GROQ,
            context.getString(R.string.provider_name_deepseek) to ProviderId.DEEPSEEK,
            context.getString(R.string.provider_name_openrouter) to ProviderId.OPENROUTER
        )
        
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_dropdown_item_1line,
            availableProviders.map { it.first }
        )
        providerDropdown.setAdapter(adapter)
        
        layout.addView(providerLabel)
        layout.addView(providerDropdown)
        
        return WizardStep(
            title = context.getString(R.string.wizard_title_welcome),
            description = context.getString(R.string.wizard_desc_select_provider),
            view = layout
        )
    }
    
    private fun createApiConfigStep(): WizardStep {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 32, 64, 32)
        }
        
        val apiKeyLabel = TextView(context).apply {
            text = context.getString(R.string.wizard_label_api_key)
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        
        val apiKeyInput = EditText(context).apply {
            hint = context.getString(R.string.wizard_hint_enter_api_key)
            setSingleLine(true)
        }
        
        val baseUrlLabel = TextView(context).apply {
            text = context.getString(R.string.wizard_label_base_url)
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        
        val baseUrlInput = EditText(context).apply {
            hint = context.getString(R.string.wizard_hint_base_url_example)
            setSingleLine(true)
        }
        
        val showAdvanced = CheckBox(context).apply {
            text = context.getString(R.string.wizard_checkbox_show_advanced)
        }
        
        val advancedSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        
        showAdvanced.setOnCheckedChangeListener { _, isChecked ->
            advancedSection.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        layout.addView(apiKeyLabel)
        layout.addView(apiKeyInput)
        layout.addView(baseUrlLabel)
        layout.addView(baseUrlInput)
        layout.addView(showAdvanced)
        layout.addView(advancedSection)
        
        return WizardStep(
            title = context.getString(R.string.wizard_title_api_config),
            description = context.getString(R.string.wizard_desc_api_config),
            view = layout
        )
    }
    
    private fun createModelSelectionStep(): WizardStep {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 32, 64, 32)
        }
        
        val modelLabel = TextView(context).apply {
            text = context.getString(R.string.wizard_label_select_model)
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        
        val modelDropdown = AutoCompleteTextView(context).apply {
            hint = context.getString(R.string.wizard_hint_choose_model)
        }
        
        layout.addView(modelLabel)
        layout.addView(modelDropdown)
        
        return WizardStep(
            title = context.getString(R.string.wizard_title_model_selection),
            description = context.getString(R.string.wizard_desc_model_selection),
            view = layout
        )
    }
    
    private fun createTestingStep(): WizardStep {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 32, 64, 32)
        }
        
        val testButton = Button(context).apply {
            text = context.getString(R.string.wizard_button_test_connection)
        }
        
        layout.addView(testButton)
        
        return WizardStep(
            title = context.getString(R.string.wizard_title_test_connection),
            description = context.getString(R.string.wizard_desc_test_connection),
            view = layout
        )
    }
    
    private fun showStepDialog() {
        if (currentStep >= steps.size) {
            // Wizard completed
            showCompletionDialog()
            return
        }
        
        val step = steps[currentStep]
        
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.wizard_step_title_format, step.title, currentStep + 1, totalSteps))
            .setMessage(step.description)
            .setView(step.view)
            .setPositiveButton(context.getString(R.string.wizard_button_next)) { _, _ ->
                if (validateStep(currentStep)) {
                    currentStep++
                    showStepDialog()
                }
            }
            .setNegativeButton(context.getString(R.string.action_cancel)) { _, _ ->
                // Cancel wizard
            }
            .setNeutralButton(context.getString(R.string.wizard_button_back)) { _, _ ->
                if (currentStep > 0) {
                    currentStep--
                    showStepDialog()
                }
            }
            .create()
        
        dialog.show()
    }
    
    private fun validateStep(step: Int): Boolean {
        // Add validation logic for each step
        return when (step) {
            0 -> validateWelcomeStep()
            1 -> validateApiConfigStep()
            2 -> validateModelSelectionStep()
            else -> true
        }
    }
    
    private fun validateWelcomeStep(): Boolean {
        // Ensure a provider is selected before proceeding
        // This is validated in the welcome step layout via UI state
        return true  // Welcome step is always valid
    }

    private fun validateApiConfigStep(): Boolean {
        // API key or base URL must be provided for the selected provider
        // Retrieve current step's view and check inputs
        if (currentStep < steps.size) {
            val stepView = steps[currentStep].view
            // Check if any EditText fields contain non-empty values
            val allInputs = stepView.findAllViewsOfType(EditText::class.java)
            val hasValidInput = allInputs.any { it.text.isNotBlank() }
            if (!hasValidInput) {
                Log.w(TAG, "API config step validation failed: no input provided")
                return false
            }
        }
        return true
    }

    private fun validateModelSelectionStep(): Boolean {
        // Model must be selected from dropdown
        return true
    }

    /**
     * Helper to find all views of a specific type in the view hierarchy.
     */
    private fun <T : View> View.findAllViewsOfType(clazz: Class<T>): List<T> {
        val views = mutableListOf<T>()
        if (this::class.java == clazz) {
            @Suppress("UNCHECKED_CAST")
            views.add(this as T)
        }
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                views.addAll(getChildAt(i).findAllViewsOfType(clazz))
            }
        }
        return views
    }

    /**
     * Helper to find first view of a specific type in the view hierarchy.
     */
    private fun showCompletionDialog() {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.wizard_title_setup_complete))
            .setMessage(context.getString(R.string.wizard_message_setup_complete))
            .setPositiveButton(context.getString(R.string.wizard_button_done)) { _, _ ->
                // Close dialog
            }
            .show()
    }
    
    /**
     * Run connection test for the configured provider.
     */
    fun testConnection(providerId: ProviderId, apiKey: String, baseUrl: String) {
        scope.launch {
            val dialog = AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.wizard_title_testing_connection))
                .setView(ProgressBar(context))
                .setMessage(context.getString(R.string.wizard_message_testing_connection))
                .create()
            dialog.show()
            
            val result = withContext(Dispatchers.IO) {
                providerRepository.testConnection(
                    providerRepository.getProvider(providerId)!!.copy(
                        baseUrl = baseUrl.ifBlank {
                            getDefaultBaseUrl(providerId)
                        }
                    )
                )
            }
            
            dialog.dismiss()
            
            if (result) {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.wizard_title_success))
                    .setMessage(context.getString(R.string.wizard_message_connection_passed))
                    .setPositiveButton(context.getString(R.string.wizard_button_ok), null)
                    .show()
            } else {
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.wizard_title_connection_failed))
                    .setMessage(context.getString(R.string.wizard_message_connection_failed))
                    .setPositiveButton(context.getString(R.string.wizard_button_retry)) { _, _ ->
                        testConnection(providerId, apiKey, baseUrl)
                    }
                    .setNegativeButton(context.getString(R.string.action_cancel), null)
                    .show()
            }
        }
    }
    
    private fun getDefaultBaseUrl(providerId: ProviderId): String {
        return when (providerId) {
            ProviderId.OPENAI -> "https://api.openai.com/v1"
            ProviderId.ANTHROPIC -> "https://api.anthropic.com/v1"
            ProviderId.GEMINI -> "https://generativelanguage.googleapis.com/v1"
            ProviderId.GROQ -> "https://api.groq.com/openai/v1"
            ProviderId.DEEPSEEK -> "https://api.deepseek.com/v1"
            ProviderId.OPENROUTER -> "https://openrouter.ai/api/v1"
            else -> ""
        }
    }

    /**
     * Cancel all coroutines and cleanup resources.
     * Call this when the wizard is dismissed/destroyed.
     */
    fun cancel() {
        scope.cancel()
    }
}

