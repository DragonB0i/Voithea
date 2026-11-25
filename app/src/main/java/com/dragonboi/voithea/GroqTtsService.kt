package com.dragonboi.voithea

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GroqTtsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val endpoint =
        "https://api.groq.com/openai/v1/audio/speech"

    private val model = "canopylabs/orpheus-v1-english"

    private val voice = "autumn"

    suspend fun generateSpeech(
        apiKey: String,
        text: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {

        try {
            require(text.isNotBlank()) {
                "Speech text cannot be empty."
            }

            require(text.length <= 200) {
                "Speech input exceeds the 200-character limit."
            }

            val jsonBody = JSONObject()
                .put("model", model)
                .put("input", text)
                .put("voice", voice)
                .put("response_format", "wav")

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    val errorText = response.body?.string().orEmpty()

                    val message = try {
                        JSONObject(errorText)
                            .getJSONObject("error")
                            .optString("message")
                    } catch (_: Exception) {
                        "Voice generation failed: HTTP ${response.code}"
                    }

                    throw IOException(
                        message.ifBlank {
                            "Voice generation failed: HTTP ${response.code}"
                        }
                    )
                }

                val audioBytes = response.body?.bytes()
                    ?: throw IOException("Groq returned no audio data.")

                if (audioBytes.isEmpty()) {
                    throw IOException("Groq returned empty audio data.")
                }

                Result.success(audioBytes)
            }

        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    companion object {

        /**
         * Splits text into chunks that safely fit Orpheus's
         * 200-character input limit.
         */
        fun splitIntoChunks(
            text: String,
            maxLength: Int = 190
        ): List<String> {

            require(maxLength in 1..200)

            val remaining = text.trim()
            if (remaining.isBlank()) return emptyList()

            val chunks = mutableListOf<String>()
            var current = remaining

            while (current.length > maxLength) {

                var splitAt = -1

                // Prefer ending a chunk at a sentence boundary.
                val sentenceEnd = current
                    .substring(0, maxLength)
                    .indexOfLast {
                        it == '.' || it == '!' || it == '?'
                    }

                if (sentenceEnd >= maxLength / 2) {
                    splitAt = sentenceEnd + 1
                }

                // Otherwise split at a comma or semicolon.
                if (splitAt == -1) {
                    val punctuationEnd = current
                        .substring(0, maxLength)
                        .indexOfLast {
                            it == ',' || it == ';' || it == ':'
                        }

                    if (punctuationEnd >= maxLength / 2) {
                        splitAt = punctuationEnd + 1
                    }
                }

                // Last fallback: split at a word boundary.
                if (splitAt == -1) {
                    splitAt = current
                        .substring(0, maxLength)
                        .lastIndexOf(' ')
                }

                // A single unusually long word can exceed the limit.
                if (splitAt <= 0) {
                    splitAt = maxLength
                }

                val chunk = current
                    .substring(0, splitAt)
                    .trim()

                if (chunk.isNotBlank()) {
                    chunks.add(chunk)
                }

                current = current
                    .substring(splitAt)
                    .trim()
            }

            if (current.isNotBlank()) {
                chunks.add(current)
            }

            return chunks
        }
    }
}