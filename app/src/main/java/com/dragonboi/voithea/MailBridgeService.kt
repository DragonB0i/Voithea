
package com.dragonboi.voithea

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MailEmail(
    val id: String,
    val threadId: String,
    val sender: String,
    val recipient: String,
    val subject: String,
    val date: String,
    val snippet: String
)

data class MailFetchResult(
    val account: String,
    val emails: List<MailEmail>
)

class MailBridgeService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType =
        "application/json; charset=utf-8".toMediaType()

    // =====================================
    // TEST CONNECTION
    // =====================================

    suspend fun testConnection(
        webAppUrl: String,
        apiToken: String
    ): Result<String> = withContext(Dispatchers.IO) {

        runCatching {
            val response = post(
                webAppUrl = webAppUrl,
                apiToken = apiToken,
                action = "fetch"
            )

            require(response.optBoolean("success")) {
                response.optString(
                    "error",
                    "Connection test failed."
                )
            }

            val account = response.optString("account")

            require(account.isNotBlank()) {
                "The bridge did not return a Gmail account."
            }

            account
        }
    }

    // =====================================
    // FETCH PENDING EMAILS
    // =====================================

    suspend fun fetchPendingEmails(
        webAppUrl: String,
        apiToken: String
    ): Result<MailFetchResult> = withContext(Dispatchers.IO) {

        runCatching {
            val response = post(
                webAppUrl = webAppUrl,
                apiToken = apiToken,
                action = "fetch"
            )

            require(response.optBoolean("success")) {
                response.optString(
                    "error",
                    "Could not fetch emails."
                )
            }

            val emailsJson =
                response.optJSONArray("emails") ?: JSONArray()

            val emails = buildList {
                for (index in 0 until emailsJson.length()) {
                    val item = emailsJson.optJSONObject(index)
                        ?: continue

                    add(
                        MailEmail(
                            id = item.optString("id"),
                            threadId = item.optString("threadId"),
                            sender = item.optString("sender"),
                            recipient = item.optString("recipient"),
                            subject = item.optString("subject"),
                            date = item.optString("date"),
                            snippet = item.optString("snippet")
                        )
                    )
                }
            }

            MailFetchResult(
                account = response.optString("account"),
                emails = emails
            )
        }
    }

    // =====================================
    // ACKNOWLEDGE EMAILS
    // =====================================

    suspend fun acknowledgeEmails(
        webAppUrl: String,
        apiToken: String,
        ids: List<String>
    ): Result<Int> = withContext(Dispatchers.IO) {

        runCatching {
            if (ids.isEmpty()) {
                return@runCatching 0
            }

            val idArray = JSONArray().apply {
                ids.forEach { put(it) }
            }

            val response = post(
                webAppUrl = webAppUrl,
                apiToken = apiToken,
                action = "ack",
                extra = JSONObject().put("ids", idArray)
            )

            require(response.optBoolean("success")) {
                response.optString(
                    "error",
                    "Could not acknowledge emails."
                )
            }

            response.optInt("acknowledged")
        }
    }

    // =====================================
    // START MONITORING
    // =====================================

    suspend fun startMonitoring(
        webAppUrl: String,
        apiToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {

        runCatching {
            val response = post(
                webAppUrl = webAppUrl,
                apiToken = apiToken,
                action = "start_monitoring"
            )

            require(response.optBoolean("success")) {
                response.optString(
                    "error",
                    "Could not start email monitoring."
                )
            }

            Unit
        }
    }

    // =====================================
    // STOP MONITORING
    // =====================================

    suspend fun stopMonitoring(
        webAppUrl: String,
        apiToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {

        runCatching {
            val response = post(
                webAppUrl = webAppUrl,
                apiToken = apiToken,
                action = "stop_monitoring"
            )

            require(response.optBoolean("success")) {
                response.optString(
                    "error",
                    "Could not stop email monitoring."
                )
            }

            Unit
        }
    }

    // =====================================
    // COMMON HTTP POST
    // =====================================

    private fun post(
        webAppUrl: String,
        apiToken: String,
        action: String,
        extra: JSONObject = JSONObject()
    ): JSONObject {

        val cleanUrl = webAppUrl.trim()
        val cleanToken = apiToken.trim()

        require(cleanUrl.isNotBlank()) {
            "Web app URL is missing."
        }

        require(cleanToken.isNotBlank()) {
            "API token is missing."
        }

        val uri = Uri.parse(cleanUrl)

        require(uri.scheme == "https") {
            "The bridge URL must use HTTPS."
        }

        require(uri.host == "script.google.com") {
            "The URL must be a Google Apps Script web app URL."
        }

        require(uri.path.orEmpty().endsWith("/exec")) {
            "The URL must end with /exec."
        }

        val payload = JSONObject()
            .put("token", cleanToken)
            .put("action", action)

        val keys = extra.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            payload.put(key, extra.get(key))
        }

        val request = Request.Builder()
            .url(cleanUrl)
            .post(
                payload.toString().toRequestBody(jsonMediaType)
            )
            .header("Accept", "application/json")
            .build()

        // Explicit return fixes the missing-return compiler error.
        return client.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IOException(
                    "Bridge HTTP error: ${response.code}"
                )
            }

            val body = response.body?.string()
                ?: throw IOException(
                    "Bridge returned an empty response."
                )

            val json = try {
                JSONObject(body)
            } catch (exception: Exception) {
                throw IOException(
                    "Bridge returned invalid JSON.",
                    exception
                )
            }

            if (json.has("success") && !json.optBoolean("success")) {
                throw IOException(
                    json.optString(
                        "error",
                        "Bridge request failed."
                    )
                )
            }

            json
        }
    }
}
