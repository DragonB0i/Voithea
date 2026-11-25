
package com.dragonboi.voithea

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
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

private val GmailBlue = Color(0xFF54C8FF)
private val GmailBackground = Color(0xFF05070B)
private val GmailMuted = Color(0xFF929BA8)
private val GmailGreen = Color(0xFF7BE3A1)
private val GmailRed = Color(0xFFFF7777)

@Composable
fun GmailConnectionScreen(
    initialAccount1Url: String = "",
    initialAccount1Token: String = "",
    initialAccount2Url: String = "",
    initialAccount2Token: String = "",

    onTestConnection: suspend (
        String,
        String
    ) -> Result<String>,

    onContinue: suspend (
        String,
        String,
        String,
        String
    ) -> Unit
) {

    var account1Url by remember {
        mutableStateOf(initialAccount1Url)
    }

    var account1Token by remember {
        mutableStateOf(initialAccount1Token)
    }

    var account2Url by remember {
        mutableStateOf(initialAccount2Url)
    }

    var account2Token by remember {
        mutableStateOf(initialAccount2Token)
    }

    var account1Email by remember {
        mutableStateOf<String?>(null)
    }

    var account2Email by remember {
        mutableStateOf<String?>(null)
    }

    var testingAccount by remember {
        mutableStateOf<Int?>(null)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()

    val bothConnected =
        account1Email != null &&
                account2Email != null

    val differentAccounts =
        account1Email != null &&
                account2Email != null &&
                !account1Email.equals(
                    account2Email,
                    ignoreCase = true
                )

    val canContinue =
        bothConnected &&
                differentAccounts &&
                testingAccount == null &&
                !saving

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GmailBackground),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1050.dp)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 28.dp,
                    vertical = 22.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "VOITHEA",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "GMAIL CONNECTION",
                color = GmailBlue,
                fontSize = 12.sp,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Let's connect Voi to your inbox.",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = "Enter the Web App URL and API token for both Gmail accounts.",
                color = GmailMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                GmailAccountCard(
                    modifier = Modifier.weight(1f),
                    accountNumber = 1,
                    url = account1Url,
                    token = account1Token,
                    connectedEmail = account1Email,
                    testing = testingAccount == 1,
                    enabled = testingAccount == null && !saving,

                    onUrlChange = {
                        account1Url = it
                        account1Email = null
                        errorMessage = ""
                    },

                    onTokenChange = {
                        account1Token = it
                        account1Email = null
                        errorMessage = ""
                    },

                    onTest = {

                        scope.launch {

                            testingAccount = 1
                            account1Email = null
                            errorMessage = ""

                            try {

                                val result = onTestConnection(
                                    account1Url,
                                    account1Token
                                )

                                result.fold(

                                    onSuccess = { email ->
                                        account1Email = email
                                    },

                                    onFailure = { error ->
                                        errorMessage =
                                            "Account 1: ${
                                                error.message
                                                    ?: "Connection failed."
                                            }"
                                    }
                                )

                            } catch (exception: Exception) {

                                errorMessage =
                                    "Account 1: ${
                                        exception.message
                                            ?: "Connection failed."
                                    }"

                            } finally {

                                testingAccount = null
                            }
                        }
                    }
                )

                GmailAccountCard(
                    modifier = Modifier.weight(1f),
                    accountNumber = 2,
                    url = account2Url,
                    token = account2Token,
                    connectedEmail = account2Email,
                    testing = testingAccount == 2,
                    enabled = testingAccount == null && !saving,

                    onUrlChange = {
                        account2Url = it
                        account2Email = null
                        errorMessage = ""
                    },

                    onTokenChange = {
                        account2Token = it
                        account2Email = null
                        errorMessage = ""
                    },

                    onTest = {

                        scope.launch {

                            testingAccount = 2
                            account2Email = null
                            errorMessage = ""

                            try {

                                val result = onTestConnection(
                                    account2Url,
                                    account2Token
                                )

                                result.fold(

                                    onSuccess = { email ->
                                        account2Email = email
                                    },

                                    onFailure = { error ->
                                        errorMessage =
                                            "Account 2: ${
                                                error.message
                                                    ?: "Connection failed."
                                            }"
                                    }
                                )

                            } catch (exception: Exception) {

                                errorMessage =
                                    "Account 2: ${
                                        exception.message
                                            ?: "Connection failed."
                                    }"

                            } finally {

                                testingAccount = null
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage.isNotBlank()) {

                Text(
                    text = errorMessage,
                    color = GmailRed,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            if (bothConnected && !differentAccounts) {

                Text(
                    text = "Both bridges returned the same Gmail account. Please configure two different accounts.",
                    color = GmailRed,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = {

                    scope.launch {

                        saving = true
                        errorMessage = ""

                        try {

                            onContinue(
                                account1Url.trim(),
                                account1Token.trim(),
                                account2Url.trim(),
                                account2Token.trim()
                            )

                        } catch (exception: Exception) {

                            errorMessage =
                                exception.message
                                    ?: "Unable to save Gmail connections."

                        } finally {

                            saving = false
                        }
                    }
                },

                enabled = canContinue,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),

                colors = ButtonDefaults.buttonColors(
                    containerColor = GmailBlue,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF263B49),
                    disabledContentColor = Color.White
                )
            ) {

                if (saving) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "SAVING...",
                        fontWeight = FontWeight.Bold
                    )

                } else {

                    Text(
                        text = "CONTINUE TO VOI",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your Gmail bridge credentials are encrypted and stored locally on this device.",
                color = GmailMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GmailAccountCard(
    modifier: Modifier,
    accountNumber: Int,
    url: String,
    token: String,
    connectedEmail: String?,
    testing: Boolean,
    enabled: Boolean,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onTest: () -> Unit
) {

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101722)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF283747)
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "GMAIL ACCOUNT $accountNumber",
                color = GmailBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = {
                    Text("Web App URL")
                },
                placeholder = {
                    Text("https://script.google.com/.../exec")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = GmailBlue,
                    unfocusedBorderColor = Color(0xFF454D59),
                    focusedLabelColor = GmailBlue,
                    unfocusedLabelColor = GmailMuted,
                    cursorColor = GmailBlue
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = {
                    Text("API token")
                },
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
                        enabled = enabled
                    ) {

                        Text(
                            text = if (passwordVisible) "HIDE" else "SHOW",
                            color = GmailBlue
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = GmailBlue,
                    unfocusedBorderColor = Color(0xFF454D59),
                    focusedLabelColor = GmailBlue,
                    unfocusedLabelColor = GmailMuted,
                    cursorColor = GmailBlue
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onTest,
                enabled = enabled &&
                        !testing &&
                        url.isNotBlank() &&
                        token.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B3345),
                    contentColor = GmailBlue,
                    disabledContainerColor = Color(0xFF202832),
                    disabledContentColor = GmailMuted
                )
            ) {

                if (testing) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        color = GmailBlue,
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(9.dp))

                    Text("TESTING...")

                } else {

                    Text(
                        text = if (connectedEmail != null) {
                            "CONNECTION VERIFIED"
                        } else {
                            "TEST CONNECTION"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (connectedEmail != null) {

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Connected successfully",
                    color = GmailGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = connectedEmail,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}
