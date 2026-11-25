
package com.dragonboi.voithea

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

private val VoiBlack = Color.Black

private val VoiColors = listOf(
    Color(0xFFB0F3FF),
    Color(0xFF66D8FF),
    Color(0xFF26A9F4),
    Color(0xFF1685DD)
)

fun DrawScope.drawVoiEye(
    center: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    expression: VoiExpression,
    side: Float,
    openAmount: Float = 1f
) {

    val opening = openAmount.coerceIn(0.02f, 1.2f)

    // Expressions can change the actual eye geometry.
    // We are no longer forcing every emotion into a square.

    val widthScale = when (expression) {
        VoiExpression.HAPPY -> 0.92f
        VoiExpression.SURPRISED -> 0.88f
        VoiExpression.CONFUSED -> 0.96f
        VoiExpression.LISTENING -> 0.84f
        VoiExpression.SLEEPY -> 1.05f
        VoiExpression.SLEEPING -> 0.95f
        VoiExpression.ANGRY -> 0.96f
        VoiExpression.FOCUSED -> 0.92f
        VoiExpression.LOVE -> 0.95f
        else -> 1f
    }

    val heightScale = when (expression) {
        VoiExpression.HAPPY -> 0.82f
        VoiExpression.SURPRISED -> 1.16f
        VoiExpression.CONFUSED ->
            if (side < 0f) 0.94f else 0.78f

        VoiExpression.LISTENING -> 0.92f
        VoiExpression.SLEEPY -> 0.72f
        VoiExpression.SLEEPING -> 0.08f
        VoiExpression.ANGRY -> 0.86f
        VoiExpression.FOCUSED -> 0.72f
        VoiExpression.LOVE -> 0.95f
        else -> 1f
    }

    val width = eyeWidth * widthScale
    val height = eyeHeight * heightScale * opening

    val left = center.x - width / 2f
    val top = center.y - height / 2f
    val right = left + width
    val bottom = top + height

    val radius = minOf(width, height) * 0.29f

    // Special expressions have their own drawing routines.
    when (expression) {

        // ---------------------------------
        // HAPPY: Cute curved closed eyes
        // ---------------------------------
        VoiExpression.HAPPY -> {

            val happyEye = Path().apply {
                moveTo(left, center.y + height * 0.10f)

                quadraticTo(
                    center.x,
                    center.y - height * 0.78f,
                    right,
                    center.y + height * 0.10f
                )
            }

            drawPath(
                path = happyEye,
                color = VoiColors[1],
                style = Stroke(
                    width = height * 0.30f,
                    cap = StrokeCap.Round
                )
            )

            return
        }

        // ---------------------------------
        // SLEEPING: Gentle closed eyelids
        // ---------------------------------
        VoiExpression.SLEEPING -> {

            val sleepingEye = Path().apply {
                moveTo(left, center.y)

                quadraticTo(
                    center.x,
                    center.y + height * 1.5f,
                    right,
                    center.y
                )
            }

            drawPath(
                path = sleepingEye,
                color = VoiColors[1],
                style = Stroke(
                    width = eyeHeight * 0.075f,
                    cap = StrokeCap.Round
                )
            )

            return
        }

        else -> Unit
    }

    // ---------------------------------
    // Base eye geometry
    // ---------------------------------

    val eyeTopLeft = Offset(left, top)
    val eyeSize = Size(width, height)

    // Subtle glow.
    drawRoundRect(
        color = Color(0x2435BFFF),
        topLeft = Offset(left - 9f, top - 9f),
        size = Size(width + 18f, height + 18f),
        cornerRadius = CornerRadius(radius + 9f)
    )

    // Different silhouette for different emotions.
    when (expression) {

        // Large rounded circular eyes.
        VoiExpression.SURPRISED -> {
            drawOval(
                brush = Brush.verticalGradient(
                    colors = VoiColors,
                    startY = top,
                    endY = bottom
                ),
                topLeft = eyeTopLeft,
                size = eyeSize
            )
        }

        // Softer oval eyes for listening.
        VoiExpression.LISTENING -> {
            drawOval(
                brush = Brush.verticalGradient(
                    colors = VoiColors,
                    startY = top,
                    endY = bottom
                ),
                topLeft = eyeTopLeft,
                size = eyeSize
            )
        }

        // Slightly asymmetric ovals for confusion.
        VoiExpression.CONFUSED -> {
            drawOval(
                brush = Brush.verticalGradient(
                    colors = VoiColors,
                    startY = top,
                    endY = bottom
                ),
                topLeft = eyeTopLeft,
                size = eyeSize
            )
        }

        // Circular eyes for love.
        VoiExpression.LOVE -> {
            drawOval(
                brush = Brush.verticalGradient(
                    colors = VoiColors,
                    startY = top,
                    endY = bottom
                ),
                topLeft = eyeTopLeft,
                size = eyeSize
            )
        }

        // Default rounded-square eye.
        else -> {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = VoiColors,
                    startY = top,
                    endY = bottom
                ),
                topLeft = eyeTopLeft,
                size = eyeSize,
                cornerRadius = CornerRadius(radius)
            )
        }
    }

    // ---------------------------------
    // Expression-specific eyelids
    // ---------------------------------

    when (expression) {

        // ANGRY: Slanted upper eyelid.
        VoiExpression.ANGRY -> {

            val angryLid = Path().apply {
                moveTo(left, top)
                lineTo(right, top)

                lineTo(
                    right,
                    top + height * 0.20f
                )

                lineTo(
                    left,
                    top + height *
                            if (side < 0f) 0.48f else 0.20f
                )

                close()
            }

            drawPath(
                path = angryLid,
                color = VoiBlack
            )
        }

        // SLEEPY: Half-covered eye.
        VoiExpression.SLEEPY -> {

            val sleepyLid = Path().apply {
                moveTo(left, top)
                lineTo(right, top)

                lineTo(
                    right,
                    top + height * 0.48f
                )

                quadraticTo(
                    center.x,
                    top + height * 0.30f,
                    left,
                    top + height * 0.48f
                )

                close()
            }

            drawPath(
                path = sleepyLid,
                color = VoiBlack
            )
        }

        // FOCUSED: Narrow eye opening.
        VoiExpression.FOCUSED -> {

            drawRect(
                color = VoiBlack,
                topLeft = Offset(left, top),
                size = Size(width, height * 0.30f)
            )
        }

        // SAD: Soft upper eyelid.
        VoiExpression.SAD -> {

            val sadLid = Path().apply {
                moveTo(left, top)
                lineTo(right, top)

                lineTo(
                    right,
                    top + height * 0.32f
                )

                quadraticTo(
                    center.x,
                    top + height * 0.05f,
                    left,
                    top + height * 0.20f
                )

                close()
            }

            drawPath(
                path = sadLid,
                color = VoiBlack
            )
        }

        else -> Unit
    }
}
