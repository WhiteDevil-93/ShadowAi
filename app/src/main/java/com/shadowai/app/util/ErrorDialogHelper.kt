package com.shadowai.app.util

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

object ErrorDialogHelper {

    fun showErrorDialog(
        context: Context,
        title: String = "Error",
        message: String,
        onRetry: (() -> Unit)? = null
    ) {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)

        if (onRetry != null) {
            builder.setNegativeButton("Retry") { _, _ -> onRetry() }
        }

        builder.show()
    }

    fun showErrorDialog(
        context: Context,
        @StringRes titleRes: Int = android.R.string.dialog_alert_title,
        @StringRes messageRes: Int,
        onRetry: (() -> Unit)? = null
    ) {
        showErrorDialog(
            context,
            context.getString(titleRes),
            context.getString(messageRes),
            onRetry
        )
    }

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun showSuccessToast(context: Context, message: String) {
        showToast(context, message, Toast.LENGTH_SHORT)
    }
}