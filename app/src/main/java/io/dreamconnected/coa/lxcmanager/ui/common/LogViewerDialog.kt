package io.dreamconnected.coa.lxcmanager.ui.common

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.dreamconnected.coa.lxcmanager.R

object LogViewerDialog {
    
    fun show(context: Context, logs: List<String>, operationName: String) {
        val scrollView = ScrollView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val horizontalScrollView = HorizontalScrollView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val textView = TextView(context).apply {
            text = logs.joinToString("\n")
            textSize = 12f
            setTextColor(context.getColor(R.color.log_text_color))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(48, 32, 48, 32)
        }
        
        horizontalScrollView.addView(textView)
        scrollView.addView(horizontalScrollView)
        
        MaterialAlertDialogBuilder(context)
            .setTitle("LXC Logs: $operationName")
            .setView(scrollView)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}