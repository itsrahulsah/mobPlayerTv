package com.mobplayer.ytcrawler.internal

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.math.BigDecimal

object NaturalDeserializer {
    @JvmStatic
    fun deserialize(json: JsonElement?): Any? {
        if (json == null || json.isJsonNull) return null
        return when {
            json.isJsonPrimitive -> handlePrimitive(json.asJsonPrimitive)
            json.isJsonArray -> handleArray(json.asJsonArray)
            else -> handleObject(json.asJsonObject)
        }
    }

    private fun handlePrimitive(json: JsonPrimitive): Any? {
        return when {
            json.isBoolean -> json.asBoolean
            json.isString -> json.asString
            else -> {
                val bigDec: BigDecimal = json.asBigDecimal
                try {
                    bigDec.toBigIntegerExact()
                    try {
                        return bigDec.intValueExact()
                    } catch (ignored: ArithmeticException) {
                    }
                    return bigDec.longValueExact()
                } catch (ignored: ArithmeticException) {
                }
                bigDec.toDouble()
            }
        }
    }

    private fun handleArray(json: JsonArray): Array<Any?> {
        val array = arrayOfNulls<Any>(json.size())
        for (i in 0 until json.size()) {
            array[i] = deserialize(json.get(i))
        }
        return array
    }

    private fun handleObject(json: JsonObject): Map<String, Any?> {
        val map = HashMap<String, Any?>()
        for ((key, value) in json.entrySet()) {
            map[key] = deserialize(value)
        }
        return map
    }
}
