package com.shadowai.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowai.diagnostics.ErrorCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * View model for diagnostics reporting.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    val collector: ErrorCollector
) : ViewModel() {

    private val _exportPath = MutableStateFlow<String?>(null)
    val exportPath: StateFlow<String?> = _exportPath.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    /**
     * Exports the report to a local file.
     */
    fun exportReport(context: Context, report: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val exportDir = File(context.cacheDir, "diagnostics")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                val file = File(exportDir, "diagnostics_report_${System.currentTimeMillis()}.txt")
                file.writeText(report)
                _exportPath.value = file.absolutePath
                _exportError.value = null
            }.onFailure {
                _exportError.value = it.message
            }
        }
    }

    /**
     * Clears the export state.
     */
    fun clearExportState() {
        _exportPath.value = null
        _exportError.value = null
    }
}
