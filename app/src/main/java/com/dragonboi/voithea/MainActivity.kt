
package com.dragonboi.voithea

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val keyStore = SecureKeyStore(this)

        val groqService = GroqService()
        val groqTtsService = GroqTtsService()
        val mailBridgeService = MailBridgeService()

        setContent {

            var savedApiKey by remember {
                mutableStateOf<String?>(null)
            }

            var account1 by remember {
                mutableStateOf<StoredMailBridge?>(null)
            }

            var account2 by remember {
                mutableStateOf<StoredMailBridge?>(null)
            }

            var loading by remember {
                mutableStateOf(true)
            }

            val voiController = remember {
                VoiController()
            }

            val commandDispatcher = remember(voiController) {
                VoiCommandDispatcher(voiController)
            }

            val context = LocalContext.current

            val speechManager = remember(context) {
                SpeechRecognitionManager(context)
            }

            val voiceManager = remember(context) {
                VoiVoiceManager(context, groqTtsService)
            }

            val scope = rememberCoroutineScope()

            var transcript by remember {
                mutableStateOf("")
            }

            var listening by remember {
                mutableStateOf(false)
            }

            var isProcessing by remember {
                mutableStateOf(false)
            }

            var isSpeaking by remember {
                mutableStateOf(false)
            }

            var speechStatus by remember {
                mutableStateOf("Tap anywhere to talk to Voi")
            }

            var activeResponseJob by remember {
                mutableStateOf<Job?>(null)
            }

            var monitoringAccounts by remember {
                mutableStateOf<Set<Int>>(emptySet())
            }

            // =====================================
            // ANNOUNCEMENT MANAGER
            // =====================================

            val announcementManager = remember(
                scope,
                voiceManager
            ) {

                VoiAnnouncementManager(
                    scope = scope,
                    voiceManager = voiceManager,
                    apiKeyProvider = {
                        savedApiKey
                    },
                    onSpeakingChanged = { speaking ->

                        isSpeaking = speaking

                        if (speaking) {
                            speechStatus = "Voi is speaking..."
                        }
                    },
                    onAnnouncementChanged = { message ->

                        transcript = message
                    }
                )
            }

            // =====================================
            // SCHEDULER
            // =====================================

            val voiScheduler = remember(
                scope,
                announcementManager
            ) {
                VoiScheduler(
                    scope = scope,
                    announcements = announcementManager
                )
            }

            // Keep the polling coroutine updated with current UI states.
            val currentIsProcessing by rememberUpdatedState(isProcessing)
            val currentIsSpeaking by rememberUpdatedState(isSpeaking)
            val currentListening by rememberUpdatedState(listening)

            // =====================================
            // LOAD SAVED CONFIGURATION
            // =====================================

            LaunchedEffect(Unit) {

                val configuration = withContext(Dispatchers.IO) {

                    Triple(
                        keyStore.get(),
                        keyStore.getMailBridge(1),
                        keyStore.getMailBridge(2)
                    )
                }

                savedApiKey = configuration.first
                account1 = configuration.second
                account2 = configuration.third

                loading = false
            }

            // =====================================
            // START SCHEDULER AFTER SETUP
            // =====================================

            LaunchedEffect(savedApiKey) {

                if (!savedApiKey.isNullOrBlank()) {
                    voiScheduler.start()
                }
            }

            // =====================================
            // PROCESS GROQ MESSAGE
            // =====================================

            fun processMessage(userMessage: String) {

                val apiKey = savedApiKey

                if (apiKey.isNullOrBlank()) {
                    speechStatus = "Voi is not connected."
                    return
                }

                isProcessing = true
                speechStatus = "Thinking..."

                lateinit var responseJob: Job

                responseJob = scope.launch(
                    start = CoroutineStart.LAZY
                ) {

                    try {

                        val result = groqService.sendMessage(
                            apiKey = apiKey,
                            userMessage = userMessage,
                            dispatcher = commandDispatcher,
                            mailBridgeService = mailBridgeService,
                            account1 = account1,
                            account2 = account2,
                            scheduler = voiScheduler,

                            onMonitoringChanged = { accounts, enabled ->

                                monitoringAccounts =
                                    if (enabled) {
                                        monitoringAccounts + accounts
                                    } else {
                                        monitoringAccounts - accounts
                                    }

                                speechStatus =
                                    if (monitoringAccounts.isEmpty()) {
                                        "Email monitoring stopped."
                                    } else {
                                        "Email monitoring is active."
                                    }
                            }
                        )

                        val answer = result.getOrThrow()

                        transcript = answer

                        // Ordinary conversational speech also enters
                        // the queue, below scheduled announcements.
                        announcementManager.enqueue(
                            text = answer,
                            priority =
                                VoiAnnouncementPriority.CONVERSATION
                        )

                        speechStatus = "Response queued."

                    } catch (exception: CancellationException) {

                        throw exception

                    } catch (exception: Exception) {

                        transcript = userMessage

                        speechStatus = exception.message
                            ?: "Something went wrong."

                    } finally {

                        if (activeResponseJob === responseJob) {

                            activeResponseJob = null
                            isProcessing = false
                        }
                    }
                }

                activeResponseJob = responseJob
                responseJob.start()
            }

            // =====================================
            // EMAIL MONITORING POLLER
            // =====================================

            LaunchedEffect(
                monitoringAccounts,
                savedApiKey,
                account1,
                account2
            ) {

                val apiKey = savedApiKey

                if (
                    apiKey.isNullOrBlank() ||
                    monitoringAccounts.isEmpty()
                ) {
                    return@LaunchedEffect
                }

                while (isActive) {

                    delay(60_000L)

                    val accountsToCheck =
                        monitoringAccounts.toList()

                    for (accountNumber in accountsToCheck) {

                        if (!isActive) break

                        val bridge = when (accountNumber) {
                            1 -> account1
                            2 -> account2
                            else -> null
                        } ?: continue

                        try {

                            val fetchResult =
                                mailBridgeService.fetchPendingEmails(
                                    bridge.webAppUrl,
                                    bridge.apiToken
                                )

                            val fetched = fetchResult.getOrThrow()

                            if (fetched.emails.isEmpty()) {
                                continue
                            }

                            speechStatus = "New email received..."

                            voiController.thinking()

                            val summaryResult =
                                groqService.summarizeNewEmails(
                                    apiKey = apiKey,
                                    accountNumber = accountNumber,
                                    accountEmail = fetched.account,
                                    emails = fetched.emails
                                )

                            val summary = summaryResult.getOrThrow()

                            // Acknowledge only after summary generation.
                            mailBridgeService.acknowledgeEmails(
                                webAppUrl = bridge.webAppUrl,
                                apiToken = bridge.apiToken,
                                ids = fetched.emails.map { it.id }
                            ).getOrThrow()

                            transcript = summary

                            // Email announcements now use the shared queue.
                            announcementManager.enqueue(
                                text = summary,
                                priority = VoiAnnouncementPriority.EMAIL
                            )

                            speechStatus =
                                "Email announcement queued."

                        } catch (exception: CancellationException) {

                            throw exception

                        } catch (exception: Exception) {

                            speechStatus =
                                "Email check failed: ${
                                    exception.message ?: "Unknown error"
                                }"
                        }
                    }
                }
            }

            // =====================================
            // SPEECH RECOGNITION
            // =====================================

            fun startSpeechRecognition() {

                transcript = ""
                listening = true

                voiController.listening()

                speechStatus = "Listening..."

                speechManager.startListening(

                    onReady = {
                        listening = true
                        speechStatus = "I'm listening..."
                    },

                    onPartialResult = { text ->
                        transcript = text
                    },

                    onFinalResult = { text ->

                        listening = false

                        if (text.isBlank()) {

                            speechStatus =
                                "I didn't catch that. Try again."

                        } else {

                            transcript = text

                            voiController.thinking()

                            processMessage(text)
                        }
                    },

                    onError = { message ->

                        listening = false
                        speechStatus = message
                    }
                )
            }

            // =====================================
            // MICROPHONE PERMISSION
            // =====================================

            val microphonePermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->

                    if (granted) {

                        startSpeechRecognition()

                    } else {

                        listening = false

                        speechStatus =
                            "Microphone permission denied."
                    }
                }

            // =====================================
            // HANDLE VOI FACE TAP
            // =====================================

            fun handleFaceTap() {

                if (isProcessing || isSpeaking) {

                    val previousJob = activeResponseJob

                    activeResponseJob = null

                    previousJob?.cancel()

                    // Stop current playback without clearing the
                    // other pending announcements.
                    announcementManager.stopCurrentSpeech()

                    isProcessing = false
                    isSpeaking = false

                    startSpeechRecognition()

                    return
                }

                if (listening) {

                    speechManager.stopListening()

                    listening = false

                    speechStatus = "Listening stopped."

                    return
                }

                val permissionGranted =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                if (permissionGranted) {

                    startSpeechRecognition()

                } else {

                    microphonePermissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            }

            // =====================================
            // CLEANUP
            // =====================================

            DisposableEffect(
                speechManager,
                voiceManager,
                announcementManager,
                voiScheduler
            ) {

                onDispose {

                    voiScheduler.close()
                    announcementManager.close()

                    speechManager.destroy()

                    voiceManager.stop()
                    voiceManager.release()
                }
            }

            // =====================================
            // ONBOARDING NAVIGATION
            // =====================================

            when {

                loading -> {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {

                        CircularProgressIndicator(
                            color = Color(0xFF54C8FF)
                        )
                    }
                }

                // STEP 1: GROQ CONNECTION

                savedApiKey == null -> {

                    SetupScreen(

                        onConnect = { enteredKey ->

                            val validationResult =
                                groqService.validateKey(enteredKey)

                            if (validationResult.isSuccess) {

                                withContext(Dispatchers.IO) {
                                    keyStore.save(enteredKey)
                                }
                            }

                            validationResult.map {
                                "Voi is online."
                            }
                        },

                        onEnter = { enteredKey ->
                            savedApiKey = enteredKey
                        }
                    )
                }

                // STEP 2: GMAIL CONNECTION

                account1 == null || account2 == null -> {

                    GmailConnectionScreen(

                        initialAccount1Url =
                            account1?.webAppUrl.orEmpty(),

                        initialAccount1Token =
                            account1?.apiToken.orEmpty(),

                        initialAccount2Url =
                            account2?.webAppUrl.orEmpty(),

                        initialAccount2Token =
                            account2?.apiToken.orEmpty(),

                        onTestConnection = { url, token ->

                            mailBridgeService.testConnection(
                                webAppUrl = url,
                                apiToken = token
                            )
                        },

                        onContinue = {
                                url1,
                                token1,
                                url2,
                                token2 ->

                            withContext(Dispatchers.IO) {

                                keyStore.saveMailBridge(
                                    accountNumber = 1,
                                    webAppUrl = url1,
                                    apiToken = token1
                                )

                                keyStore.saveMailBridge(
                                    accountNumber = 2,
                                    webAppUrl = url2,
                                    apiToken = token2
                                )
                            }

                            account1 = StoredMailBridge(
                                webAppUrl = url1,
                                apiToken = token1
                            )

                            account2 = StoredMailBridge(
                                webAppUrl = url2,
                                apiToken = token2
                            )
                        }
                    )
                }

                // STEP 3: VOI IS READY

                else -> {

                    VoiFace(
                        controller = voiController,
                        transcript = transcript,
                        speechStatus = speechStatus,
                        isListening = listening,
                        isSpeaking = isSpeaking,
                        onFaceClick = {
                            handleFaceTap()
                        }
                    )
                }
            }
        }
    }
}
