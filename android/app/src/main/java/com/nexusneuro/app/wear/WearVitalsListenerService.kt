package com.nexusneuro.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives /nexus/vitals messages from the watch even when the phone UI is backgrounded.
 */
class WearVitalsListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        WearVitalsRepository.ingestMessage(messageEvent.path, messageEvent.data)
    }
}
