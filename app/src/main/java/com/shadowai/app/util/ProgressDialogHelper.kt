package com.shadowai.app.util

import android.app.AlertDialog
import android.content.Context
import android.widget.ProgressBar

object ProgressDialogHelper {
    fun show(context: Context, message: String = "Loading..."): AlertDialog {
        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            setPadding(50, 50, 50, 50)
        }
        
        return AlertDialog.Builder(context)
            .setTitle(message)
            .setView(progressBar)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }
}
