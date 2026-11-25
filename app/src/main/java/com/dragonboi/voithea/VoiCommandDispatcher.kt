
package com.dragonboi.voithea

class VoiCommandDispatcher(
    private val controller: VoiController
) {

    fun execute(
        command: String,
        value: String = ""
    ): Boolean {

        return when (command.lowercase()) {

            "set_expression" -> {
                val expression = runCatching {
                    VoiExpression.valueOf(value.uppercase())
                }.getOrNull() ?: return false

                controller.changeExpression(expression)
                true
            }

            "look_at" -> {
                val direction = runCatching {
                    VoiDirection.valueOf(value.uppercase())
                }.getOrNull() ?: return false

                controller.lookAt(direction)
                true
            }

            "play_animation" -> {
                val animation = runCatching {
                    VoiAnimation.valueOf(value.uppercase())
                }.getOrNull() ?: return false

                controller.play(animation)
                true
            }

            "reset_voi" -> {
                controller.reset()
                true
            }

            else -> false
        }
    }
}
