
package com.dragonboi.voithea

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class VoiController {

    var expression by mutableStateOf(VoiExpression.IDLE)
        private set

    var direction by mutableStateOf(VoiDirection.CENTER)
        private set

    var animation by mutableStateOf(VoiAnimation.NONE)
        private set

    var actionId by mutableStateOf(0)
        private set

    // Renamed from setExpression to avoid JVM setter clash.
    fun changeExpression(value: VoiExpression) {
        expression = value
    }

    fun lookAt(value: VoiDirection) {
        direction = value
    }

    fun reset() {
        expression = VoiExpression.IDLE
        direction = VoiDirection.CENTER
        animation = VoiAnimation.NONE
        actionId++
    }

    fun play(value: VoiAnimation) {
        animation = value
        actionId++
    }

    fun happy() = changeExpression(VoiExpression.HAPPY)
    fun sad() = changeExpression(VoiExpression.SAD)
    fun angry() = changeExpression(VoiExpression.ANGRY)
    fun surprised() = changeExpression(VoiExpression.SURPRISED)
    fun confused() = changeExpression(VoiExpression.CONFUSED)
    fun thinking() = changeExpression(VoiExpression.THINKING)
    fun listening() = changeExpression(VoiExpression.LISTENING)
    fun sleepy() = changeExpression(VoiExpression.SLEEPY)
    fun sleep() = changeExpression(VoiExpression.SLEEPING)

    fun wakeUp() = play(VoiAnimation.WAKE_UP)
    fun blink() = play(VoiAnimation.BLINK)
    fun wink() = play(VoiAnimation.WINK)
    fun laugh() = play(VoiAnimation.LAUGH)

    fun lookLeft() = lookAt(VoiDirection.LEFT)
    fun lookRight() = lookAt(VoiDirection.RIGHT)
    fun lookUp() = lookAt(VoiDirection.UP)
    fun lookDown() = lookAt(VoiDirection.DOWN)
}
