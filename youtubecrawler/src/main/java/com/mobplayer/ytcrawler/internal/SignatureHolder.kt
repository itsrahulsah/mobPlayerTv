package com.mobplayer.ytcrawler.internal

import com.mobplayer.ytcrawler.Lazy
import com.mobplayer.ytcrawler.SignatureDecipher
import com.mobplayer.ytcrawler.exception.BadExtractorException
import java.util.Collections

class SignatureHolder(
    private val signatureDecipher: SignatureDecipher,
    private val videoId: String,
    playerUrl: String
) {
    private val encryptedSignature = mutableListOf<String>()
    private var decryptSignatureLazy: Lazy<List<String>>? = null
    private var invalidate = true
    val playerUrl: String = checkPlayerUrl(playerUrl)

    @Synchronized
    fun addEncryptedSignature(s: String) {
        encryptedSignature.add(s)
        invalidate = true
    }

    private fun checkPlayerUrl(playerUrl: String): String {
        if (playerUrl.isEmpty()) {
            throw BadExtractorException("playerUrl == null or empty", videoId)
        }
        return playerUrl
    }

    @Synchronized
    private fun getDecryptSignatureLazy(): Lazy<List<String>> {
        if (invalidate || decryptSignatureLazy == null) {
            invalidate = false
            val finalSignatureList = Collections.unmodifiableList(ArrayList(encryptedSignature))
            decryptSignatureLazy = Lazy {
                signatureDecipher.decrypt(videoId, playerUrl, finalSignatureList)
            }
        }
        return decryptSignatureLazy!!
    }

    @Synchronized
    fun getDecryptSignature(theEncrypted: String): String {
        val index = encryptedSignature.indexOf(theEncrypted)
        if (index > -1) {
            return getDecryptSignatureLazy().get()[index]
        }
        return theEncrypted
    }
}
