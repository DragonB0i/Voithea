
package com.dragonboi.voithea

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun VoiFace(
    controller: VoiController,
    transcript: String = "",
    speechStatus: String = "Tap anywhere to talk to Voi",
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    onFaceClick: () -> Unit = {}
) {

    val blinkAmount = remember { Animatable(1f) }
    val winkAmount = remember { Animatable(1f) }
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    val actionOffset = remember { Animatable(0f) }
    val idleFloat = remember { Animatable(0f) }

    // Listening animation states.
    val listeningPulse = remember { Animatable(0f) }
    val eyebrowLift = remember { Animatable(0f) }

    // Speaking mouth animation.
    val mouthOpen = remember { Animatable(0f) }

    val targetOpening = when (controller.expression) {
        VoiExpression.HAPPY -> 0.78f
        VoiExpression.SAD -> 0.82f
        VoiExpression.ANGRY -> 0.83f
        VoiExpression.SURPRISED -> 1.12f
        VoiExpression.SLEEPY -> 0.60f
        VoiExpression.SLEEPING -> 0.08f
        VoiExpression.FOCUSED -> 0.85f
        VoiExpression.LISTENING -> 0.92f
        else -> 1f
    }

    val eyeOpening by animateFloatAsState(
        targetValue = targetOpening,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 140f
        ),
        label = "EyeOpening"
    )

    val targetGaze = when (controller.direction) {
        VoiDirection.CENTER -> Pair(0f, 0f)
        VoiDirection.LEFT -> Pair(-1f, 0f)
        VoiDirection.RIGHT -> Pair(1f, 0f)
        VoiDirection.UP -> Pair(0f, -1f)
        VoiDirection.DOWN -> Pair(0f, 1f)
        VoiDirection.UP_LEFT -> Pair(-1f, -1f)
        VoiDirection.UP_RIGHT -> Pair(1f, -1f)
        VoiDirection.DOWN_LEFT -> Pair(-1f, 1f)
        VoiDirection.DOWN_RIGHT -> Pair(1f, 1f)
    }

    LaunchedEffect(controller.direction) {
        gazeX.animateTo(
            targetGaze.first,
            spring(stiffness = 110f, dampingRatio = 0.7f)
        )
    }

    LaunchedEffect(controller.direction) {
        gazeY.animateTo(
            targetGaze.second,
            spring(stiffness = 110f, dampingRatio = 0.7f)
        )
    }

    // Eyebrows pop up when Voi starts listening.
    LaunchedEffect(isListening) {

        if (isListening) {

            eyebrowLift.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = 240f
                )
            )

            while (true) {

                listeningPulse.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 650,
                        easing = FastOutSlowInEasing
                    )
                )

                listeningPulse.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 650,
                        easing = FastOutSlowInEasing
                    )
                )
            }

        } else {

            listeningPulse.animateTo(0f, tween(180))

            eyebrowLift.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 180f
                )
            )
        }
    }

    // Mouth gently opens and closes while Voi speaks.
    LaunchedEffect(isSpeaking) {

        if (isSpeaking) {

            mouthOpen.snapTo(0.25f)

            while (true) {

                mouthOpen.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 170,
                        easing = FastOutSlowInEasing
                    )
                )

                mouthOpen.animateTo(
                    targetValue = 0.25f,
                    animationSpec = tween(
                        durationMillis = 170,
                        easing = FastOutSlowInEasing
                    )
                )
            }

        } else {

            mouthOpen.animateTo(
                targetValue = 0f,
                animationSpec = tween(140)
            )
        }
    }

    // Automatic blinking and idle gaze.
    LaunchedEffect(Unit) {

        while (true) {

            delay(Random.nextLong(2500L, 4800L))

            if (controller.expression != VoiExpression.SLEEPING) {

                blinkAmount.animateTo(0.06f, tween(90))
                delay(75L)
                blinkAmount.animateTo(1f, tween(145))
            }

            if (controller.expression == VoiExpression.IDLE) {

                val nextX = Random.nextFloat() * 0.5f - 0.25f
                val nextY = Random.nextFloat() * 0.25f - 0.12f

                gazeX.animateTo(nextX, tween(550))
                gazeY.animateTo(nextY, tween(450))

                delay(600L)

                gazeX.animateTo(targetGaze.first, tween(650))
                gazeY.animateTo(targetGaze.second, tween(550))
            }
        }
    }

    // Idle floating.
    LaunchedEffect(Unit) {

        while (true) {

            idleFloat.animateTo(
                1f,
                tween(1800, easing = FastOutSlowInEasing)
            )

            idleFloat.animateTo(
                0f,
                tween(1800, easing = FastOutSlowInEasing)
            )
        }
    }

    // One-shot animation commands.
    LaunchedEffect(controller.actionId) {

        when (controller.animation) {

            VoiAnimation.BLINK -> {
                blinkAmount.animateTo(0.05f, tween(90))
                delay(80L)
                blinkAmount.animateTo(1f, tween(150))
            }

            VoiAnimation.WINK -> {
                winkAmount.animateTo(0.05f, tween(130))
                delay(250L)
                winkAmount.animateTo(1f, tween(200))
            }

            VoiAnimation.CONFUSED_SHAKE -> {
                repeat(3) {
                    actionOffset.animateTo(-1f, tween(80))
                    actionOffset.animateTo(1f, tween(80))
                }
                actionOffset.animateTo(0f, tween(150))
            }

            VoiAnimation.LAUGH -> {
                repeat(3) {
                    actionOffset.animateTo(-0.8f, tween(100))
                    actionOffset.animateTo(0.8f, tween(100))
                }
                actionOffset.animateTo(0f, tween(180))
            }

            VoiAnimation.WAKE_UP -> {
                controller.changeExpression(VoiExpression.IDLE)
                blinkAmount.snapTo(0.05f)
                blinkAmount.animateTo(1f, tween(500))
            }

            VoiAnimation.NONE -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                onFaceClick()
            }
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val centerX = size.width / 2f
            val centerY = size.height * 0.43f

            val eyeWidth = size.height * 0.43f
            val eyeHeight = size.height * 0.49f
            val gap = size.height * 0.19f

            val movementX = eyeWidth * 0.12f
            val movementY = eyeHeight * 0.10f

            val floatY = idleFloat.value * 2.5f

            val pulseMovement =
                if (isListening) listeningPulse.value * 0.025f
                else 0f

            for (side in listOf(-1f, 1f)) {

                val eyeX =
                    centerX +
                            side * (eyeWidth + gap) / 2f +
                            gazeX.value * movementX +
                            actionOffset.value * 5f

                val eyeY =
                    centerY +
                            gazeY.value * movementY +
                            floatY

                val individualOpen =
                    if (
                        controller.animation == VoiAnimation.WINK &&
                        side > 0f
                    ) {
                        winkAmount.value
                    } else {
                        1f
                    }

                // Draw Voi's eyebrows only while listening.
                if (isListening) {

                    val browLift = eyebrowLift.value
                    val browPulse = listeningPulse.value

                    val browY =
                        eyeY -
                                eyeHeight * 0.59f -
                                eyeHeight * 0.20f * browLift

                    val browWidth = eyeWidth * 0.62f

                    val left = eyeX - browWidth / 2f
                    val right = eyeX + browWidth / 2f

                    val browPath = Path().apply {

                        moveTo(left, browY)

                        quadraticTo(
                            eyeX,
                            browY - eyeHeight * 0.13f,
                            right,
                            browY
                        )
                    }

                    drawPath(
                        path = browPath,
                        color = Color(
                            red = 0.20f,
                            green = 0.72f,
                            blue = 1f,
                            alpha = 0.25f + browPulse * 0.20f
                        ),
                        style = Stroke(
                            width = eyeHeight * 0.11f,
                            cap = StrokeCap.Round
                        )
                    )

                    drawPath(
                        path = browPath,
                        color = Color(
                            red = 0.45f,
                            green = 0.85f,
                            blue = 1f,
                            alpha = 0.75f + browPulse * 0.25f
                        ),
                        style = Stroke(
                            width = eyeHeight * 0.055f,
                            cap = StrokeCap.Round
                        )
                    )
                }

                drawVoiEye(
                    center = Offset(eyeX, eyeY),
                    eyeWidth = eyeWidth,
                    eyeHeight = eyeHeight,
                    expression = controller.expression,
                    side = side,
                    openAmount = eyeOpening *
                            blinkAmount.value *
                            individualOpen *
                            (1f + pulseMovement)
                )
            }

            // Voi's blue D-shaped speaking mouth.
            if (isSpeaking) {

                val mouthWidth = eyeWidth * 0.42f

                val mouthHeight =
                    size.height * (
                            0.025f + mouthOpen.value * 0.045f
                            )

                val mouthCenterX = centerX

                val mouthTop =
                    centerY + eyeHeight * 0.62f + floatY

                val left = mouthCenterX - mouthWidth / 2f
                val right = mouthCenterX + mouthWidth / 2f

                val mouthPath = Path().apply {

                    moveTo(left, mouthTop)
                    lineTo(right, mouthTop)

                    quadraticTo(
                        right + mouthWidth * 0.08f,
                        mouthTop + mouthHeight * 0.70f,
                        mouthCenterX,
                        mouthTop + mouthHeight
                    )

                    quadraticTo(
                        left - mouthWidth * 0.08f,
                        mouthTop + mouthHeight * 0.70f,
                        left,
                        mouthTop
                    )

                    close()
                }

                // Subtle blue glow.
                drawPath(
                    path = mouthPath,
                    color = Color(0xFF54C8FF).copy(alpha = 0.30f),
                    style = Stroke(width = 6f)
                )

                // Main mouth.
                drawPath(
                    path = mouthPath,
                    color = Color(0xFF54C8FF)
                )
            }
        }

        // Speech status and response text.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    horizontal = 24.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {

            Text(
                text = speechStatus,
                color = Color(0xFF54C8FF),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = transcript.ifBlank {
                    if (isListening) "I'm listening..." else ""
                },
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
        }
    }
}
