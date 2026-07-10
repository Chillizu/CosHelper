package com.coshelper.ptt

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.coshelper.audio.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PTTAccessibilityService : AccessibilityService() {

    private var onPress: (() -> Unit)? = null
    private var onRelease: (() -> Unit)? = null
    private var longPressJob: Job? = null
    private var isPttLongPressed = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (longPressJob == null) {
                        longPressJob = scope.launch {
                            delay(300)
                            isPttLongPressed = true
                            onPress?.invoke()
                        }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    longPressJob?.cancel()
                    longPressJob = null
                    if (isPttLongPressed) {
                        isPttLongPressed = false
                        onRelease?.invoke()
                        return true
                    }
                    // Short press: let system handle it
                    return false
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (instance == this) instance = null
    }

    companion object {
        private var instance: PTTAccessibilityService? = null

        fun setCallbacks(press: () -> Unit, release: () -> Unit) {
            instance?.apply {
                onPress = press
                onRelease = release
            }
        }
    }
}
