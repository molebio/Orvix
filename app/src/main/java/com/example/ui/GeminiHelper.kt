package com.example.ui

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiHelper {
    private const val TAG = "GeminiHelper"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a request to Gemini 3.5 Flash to generate content.
     */
    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("مفتاح واجهة برمجة متاح Gemini لم يتم العثور عليه. الرجاء إعداده عبر لوحة الأسرار (Secrets panel) في الذكاء الاصطناعي."))
        }

        val url = "$BASE_URL?key=$apiKey"

        try {
            // Build contents array
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            }

            // Create main request JSON
            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                
                // Add systemInstruction if present
                if (systemInstruction != null) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstruction)
                            })
                        })
                    })
                }

                // Add temperature setting for friendly & creative Arabic answers
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBodyJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: code=${response.code} body=$responseStr")
                    return@withContext Result.failure(Exception("عذراً، فشل الاتصال بخادم الذكاء الاصطناعي (رمز الخطأ: ${response.code})"))
                }

                try {
                    val root = JSONObject(responseStr)
                    val candidates = root.getJSONArray("candidates")
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val resultText = parts.getJSONObject(0).getString("text")
                    
                    Result.success(resultText)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parsing error: ${e.message}", e)
                    Result.failure(Exception("خطأ في قراءة رد الذكاء الاصطناعي."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception: ${e.message}", e)
            Result.failure(Exception("عذراَ، تعذر الاتصال بالإنترنت لإتمام مهمة المساعد الذكي."))
        }
    }
}
