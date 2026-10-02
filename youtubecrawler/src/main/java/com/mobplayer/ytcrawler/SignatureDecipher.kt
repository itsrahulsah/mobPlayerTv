package com.mobplayer.ytcrawler

import com.mobplayer.ytcrawler.exception.HttpClientException
import com.mobplayer.ytcrawler.exception.SignatureDecryptException
import com.mobplayer.ytcrawler.internal.Utils
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

interface SignatureDecipher {
    @Throws(SignatureDecryptException::class)
    fun decrypt(vid: String, playerUrl: String, encryptedSignature: List<String>): List<String>
}

class MusicAppSignatureDecipher(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : SignatureDecipher {

    companion object {
        private const val ENDPOINT = "https://music-app.me/api/public/decrypter"
        private const val VID_PARAM = "vid"
        private const val PLAYER_URL_PARAM = "player_url"
        private const val ENCRYPTED_SIGNATURE_PARAM = "s"
        private const val RESULT_KEY = "res"
        private const val DELIMITER = "|"
    }

    override fun decrypt(
        vid: String,
        playerUrl: String,
        encryptedSignature: List<String>
    ): List<String> {
        if (encryptedSignature.isEmpty()) return emptyList()

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(
                FormBody.Builder()
                    .add(VID_PARAM, vid)
                    .add(PLAYER_URL_PARAM, playerUrl)
                    .add(ENCRYPTED_SIGNATURE_PARAM, encryptedSignature.joinToString(DELIMITER))
                    .build()
            )
            .build()

        var response: Response? = null
        var exception: Exception? = null
        try {
            response = okHttpClient.newCall(request).execute()
            if (response.code / 100 == 2) {
                val body = response.body?.string() ?: ""
                val jsonObject = gson.fromJson(body, JsonObject::class.java)
                val result = jsonObject.getAsJsonArray(RESULT_KEY)
                val decrypted = mutableListOf<String>()
                for (element in result) {
                    decrypted.add(element.asString)
                }
                return decrypted
            } else {
                exception = HttpClientException(response.code, response.message)
            }
        } catch (e: Exception) {
            exception = e
        } finally {
            Utils.closeQuietly(response)
        }
        throw SignatureDecryptException("Decrypt signature failed", exception, vid)
    }
}
