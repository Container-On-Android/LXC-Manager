package io.dreamconnected.coa.lxcmanager.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import io.dreamconnected.coa.lxcmanager.R

class ScreenMask(private val context: Context) {
    private var dialog: AlertDialog? = null
    private val dialogMap = mutableMapOf<String, AlertDialog>()
    private var shellClient = ShellClient.instance ?: throw IllegalStateException("ShellClient not initialized")
    private val paddingInDp = 20
    private val density = context.resources.displayMetrics.density
    private val paddingInPx = (paddingInDp * density).toInt()

    fun show() {
        if (dialog == null) {
            val progressBar = ProgressBar(context)
            dialog = MaterialAlertDialogBuilder(context)
                .setView(progressBar)
                .setTitle("Loading")
                .setCancelable(false)
                .create()
        }
        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    fun showInputDialog(context: Context, title: String, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
        val inputField = TextInputEditText(context).apply {
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 0, 50, 0)
            addView(inputField)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val inputText = inputField.text.toString()
                onConfirm(inputText)
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancel()
            }
            .show()
    }

    fun showUniqueTextDialog(context: Context, tag: String, content: String) {
        val existingDialog = dialogMap[tag]

        if (existingDialog != null) {
            val textView = existingDialog.findViewById<MaterialTextView>(R.id.dynamic_text_view)
            textView?.text = content
            existingDialog.show()
        } else {
            val textView = MaterialTextView(context).apply {
                id = R.id.dynamic_text_view
                text = content
            }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(paddingInPx, 0, paddingInPx, 0)
                addView(textView)
            }

            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(tag)
                .setView(layout)
                .setCancelable(false)
                .create()

            dialogMap[tag] = dialog
            dialog.show()
        }
    }

    fun dismissUniqueTextDialog(tag: String) {
        val existingDialog = dialogMap[tag]
        existingDialog?.dismiss()
        dialogMap.remove(tag)
    }

    fun dismissUniqueTextDialog(tag: String, delayInSeconds: Long) {
        val existingDialog = dialogMap[tag]
        if (existingDialog != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                existingDialog.dismiss()
                dialogMap.remove(tag)
            }, delayInSeconds * 1000)
        }
    }

    fun showTemplateSelectionDialog(context: Context, templates: List<LxcTemplates>) {
        val templateNames = templates.map { it.name }

        MaterialAlertDialogBuilder(context)
            .setTitle("选择模板")
            .setItems(templateNames.toTypedArray()) { _, which ->
                val selectedTemplate = templates[which]
                val inputLayoutContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(paddingInPx, 0, paddingInPx, 0)
                }

                val inputFields = mutableListOf<TextInputEditText>()
                selectedTemplate.fields.forEach { field ->
                    val inputLayout = TextInputLayout(context)
                    val editText = TextInputEditText(context).apply {
                        hint = field
                    }
                    inputLayout.addView(editText)
                    inputLayoutContainer.addView(inputLayout)
                    inputFields.add(editText)
                }

                val nameInputLayout = TextInputLayout(context)
                val nameEditText = TextInputEditText(context).apply {
                    hint = "name"
                }
                nameInputLayout.addView(nameEditText)
                inputLayoutContainer.addView(nameInputLayout)

                MaterialAlertDialogBuilder(context)
                    .setTitle("${selectedTemplate.name} 填写")
                    .setView(inputLayoutContainer)
                    .setPositiveButton("确认") { dialog, _ ->
                        val commandParts = mutableListOf<String>()

                        val containerName = nameEditText.text.toString()
                        if (containerName.isEmpty()) {
                            Toast.makeText(context, "容器名不能为空！", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        commandParts.add("--name $containerName")
                        commandParts.add("--template ${selectedTemplate.name}")
                        commandParts.add("--")

                        selectedTemplate.fields.forEachIndexed { index, field ->
                            val inputText = inputFields[index].text.toString()
                            if (inputText.isNotEmpty()) {
                                commandParts.add("--$field $inputText")
                            }
                        }

                        val command = commandParts.joinToString(" ")
                        println("Generated Command: $command")

                        shellClient.execCommand("lxc-create $command", 5, object : ShellClient.CommandOutputListener {
                            override fun onOutput(output: String?) {
                                output?.let {
                                    showUniqueTextDialog(context,"Create",output)
                                }
                            }
                            override fun onCommandComplete(code: String?) {
                                code?.let {
                                    if (it.contains("EXITCODE 0")) {
                                        Toast.makeText(context, "OK", Toast.LENGTH_LONG).show()
                                        dismissUniqueTextDialog("Create")
                                    } else {
                                        dismissUniqueTextDialog("Create", 5)
                                    }
                                }

                            }
                        })
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun showDebugDialog(context: Context, title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                onConfirm()
            }
            .show()
    }
}