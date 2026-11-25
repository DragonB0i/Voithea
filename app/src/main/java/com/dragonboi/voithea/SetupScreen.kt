
package com.dragonboi.voithea

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val VoiBlue = Color(0xFF54C8FF)
private val VoiBackground = Color(0xFF05070B)
private val VoiMuted = Color(0xFF929BA8)

@Composable
fun SetupScreen(
    onConnect: suspend (String) -> Result<String>,
    onEnter: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }

    var responseMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    // Immediate lock: prevents multiple network requests.
    val requestLock = remember { AtomicBoolean(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoiBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "VOITHEA",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "YOUR COMPANION IS ALMOST READY",
                color = VoiBlue,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (!connected) {

                Text(
                    text = "Connect the brain",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter your Groq API key to bring Voi online.",
                    color = VoiMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connecting,
                    singleLine = true,
                    label = { Text("Groq API key") },
                    placeholder = { Text("gsk_...") },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                passwordVisible = !passwordVisible
                            },
                            enabled = !connecting
                        ) {
                            Text(
                                text = if (passwordVisible) "HIDE" else "SHOW",
                                color = VoiBlue
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = VoiBlue,
                        unfocusedBorderColor = Color(0xFF454D59),
                        focusedLabelColor = VoiBlue,
                        unfocusedLabelColor = VoiMuted,
                        cursorColor = VoiBlue
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val enteredKey = apiKey.trim()

                        if (enteredKey.isBlank()) {
                            return@Button
                        }

                        // This lock is acquired synchronously.
                        // Only ONE tap can pass this point.
                        if (!requestLock.compareAndSet(false, true)) {
                            return@Button
                        }

                        connecting = true
                        errorMessage = ""
                        responseMessage = ""

                        scope.launch {
                            try {
                                val result = onConnect(enteredKey)

                                result.fold(
                                    onSuccess = { message ->
                                        responseMessage = message
                                        connected = true
                                    },
                                    onFailure = { error ->
                                        errorMessage =
                                            error.message
                                                ?: "Connection failed."
                                    }
                                )
                            } catch (exception: Exception) {
                                errorMessage =
                                    exception.message
                                        ?: "Unable to connect to Groq."
                            } finally {
                                connecting = false

                                // Unlock only after the request finishes.
                                requestLock.set(false)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = apiKey.isNotBlank() && !connecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoiBlue,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF263B49),
                        disabledContentColor = Color.White
                    )
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )

                        Spacer(modifier = Modifier.padding(5.dp))

                        Text(
                            text = "CONNECTING...",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            text = "CONNECT TO GROQ",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                if (connecting) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Voi is contacting Groq...",
                        color = VoiBlue,
                        fontSize = 12.sp
                    )
                }

                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF7777),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Your API key is stored locally on this device.",
                    color = VoiMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

            } else {

                Text(
                    text = "Voi is online!",
                    color = VoiBlue,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Groq connection successful.",
                    color = Color(0xFF7BE3A1),
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = responseMessage,
                    color = VoiMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = {
                        onEnter(apiKey.trim())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoiBlue,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "ENTER VOI",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
