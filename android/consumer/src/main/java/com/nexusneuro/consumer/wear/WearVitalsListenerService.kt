package com.nexusneuro.consumer.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearVitalsListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        WearVitalsRepository.ingestMessage(messageEvent.path, messageEvent.data)
    }
}
