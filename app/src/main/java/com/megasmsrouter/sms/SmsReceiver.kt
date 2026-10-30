package com.megasmsrouter.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.megasmsrouter.MsrApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Lightweight: parse intent, hand over to the processor, finish. No network here. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as MsrApp
        if (!app.prefs.isForwardingEnabled()) return // paused / not yet enabled: do nothing, keep nothing

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.map { RawSmsPart(it.originatingAddress, it.messageBody, it.timestampMillis) }
            .orEmpty()
        val messages = SmsAssembler.assemble(parts)
        if (messages.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) { messages.forEach { app.processor.process(it) } }
            } catch (e: Exception) {
                // Never log SMS content; the exception class name is enough.
                Log.w("MSR", "SMS processing failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}
