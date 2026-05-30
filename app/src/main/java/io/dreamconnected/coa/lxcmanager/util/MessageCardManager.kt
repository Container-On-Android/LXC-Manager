package io.dreamconnected.coa.lxcmanager.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import io.dreamconnected.coa.lxcmanager.R
import java.lang.ref.WeakReference
import java.util.UUID

class MessageCardManager private constructor() {

    private val marginTop = (16 * Resources.getSystem().displayMetrics.density).toInt()
    private val messageCards = mutableMapOf<String, MaterialCardView>()
    private var containerRef: WeakReference<ViewGroup>? = null

    data class Message(
        val type: String,
        val title: String,
        val body: String,
        val action: String? = null,
        val actionClickListener: ((String) -> Unit)? = null
    )

    companion object {

        @Volatile
        private var instance: MessageCardManager? = null

        fun getInstance(): MessageCardManager {
            return instance ?: synchronized(this) {
                instance ?: MessageCardManager().also { instance = it }
            }
        }

        fun attachContainer(container: ViewGroup) {
            val instance = getInstance()
            instance.containerRef = WeakReference(container)
        }

        fun addMessage(
            type: String,
            title: String,
            body: String,
            action: String? = null,
            actionClickListener: ((String) -> Unit)? = null
        ): String? {
            val instance = getInstance()
            val container = instance.containerRef?.get() ?: return null
            val messageId = UUID.randomUUID().toString()
            val context = container.context
            
            val clickListener = actionClickListener?.let {
                View.OnClickListener { it(messageId) }
            }
            
            val cardView = createMessageCard(context, messageId, type, title, body, action, clickListener)
            addCardToContainer(container, cardView)
            instance.messageCards[messageId] = cardView
            
            return messageId
        }

        fun addMessage(container: ViewGroup, message: Message): String {
            val instance = getInstance()
            val messageId = UUID.randomUUID().toString()
            val context = container.context
            
            val clickListener = message.actionClickListener?.let {
                View.OnClickListener { it(messageId) }
            }
            
            val cardView = createMessageCard(context, messageId, message.type, message.title, message.body, message.action, clickListener)
            addCardToContainer(container, cardView)
            instance.messageCards[messageId] = cardView
            
            return messageId
        }

        fun removeMessage(messageId: String) {
            val instance = getInstance()
            val cardView = instance.messageCards.remove(messageId)
            cardView?.let {
                instance.containerRef?.get()?.removeView(it)
            }
        }

        fun removeAllMessages() {
            val instance = getInstance()
            instance.messageCards.clear()
            instance.containerRef?.get()?.removeAllViews()
        }

        private fun addCardToContainer(container: ViewGroup, cardView: MaterialCardView) {
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.topMargin = getInstance().marginTop
            cardView.layoutParams = layoutParams
            container.addView(cardView)
        }

        @SuppressLint("InflateParams")
        private fun createMessageCard(
            context: Context,
            messageId: String,
            type: String,
            title: String,
            body: String,
            action: String?,
            actionClickListener: View.OnClickListener?
        ): MaterialCardView {
            val cardView = LayoutInflater.from(context).inflate(R.layout.item_message_card, null, false) as MaterialCardView
            
            cardView.tag = messageId

            val titleView = cardView.findViewById<MaterialTextView>(R.id.message_title)
            val bodyView = cardView.findViewById<MaterialTextView>(R.id.message_body)
            val actionButton = cardView.findViewById<MaterialButton>(R.id.message_action)
            val actionContainer = cardView.findViewById<LinearLayout>(R.id.action_container)

            titleView.text = title
            bodyView.text = body
            titleView.setTextColor(context.getColor(R.color.md_theme_background))
            bodyView.setTextColor(context.getColor(R.color.md_theme_background))

            val backgroundColor = when (type.lowercase()) {
                "warn", "warning" -> context.getColor(R.color.warning_container)
                "error" -> context.getColor(R.color.error_container)
                else -> context.getColor(R.color.primary_container)
            }

            cardView.setCardBackgroundColor(backgroundColor)
            cardView.outlineSpotShadowColor = backgroundColor
            cardView.outlineAmbientShadowColor = backgroundColor

            if (action.isNullOrEmpty()) {
                actionContainer.visibility = View.GONE
            } else {
                actionButton.text = action
                actionButton.setOnClickListener(actionClickListener)
            }

            return cardView
        }
    }
}
