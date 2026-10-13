package dev.lad.data

import dev.lad.domain.SoundProfile
import dev.lad.util.attempt
import org.json.JSONArray
import org.json.JSONObject

object SoundProfiles {
    fun read(text: String, device: String): SoundProfile = attempt {
        val item =
            JSONObject(text).let {
                it.optJSONObject(device) ?: it.optJSONObject(device.substringBefore('@'))
            } ?: return@attempt SoundProfile()
        val bands = item.optJSONArray("bands")
        SoundProfile(
            item.optBoolean("enabled"),
            List(5) { bands?.optDouble(it, 0.0)?.toFloat()?.coerceIn(-12f, 12f) ?: 0f },
            item.optDouble("balance", 0.0).toFloat().coerceIn(-1f, 1f),
            item.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
        )
    }
        .getOrDefault(SoundProfile())

    fun write(text: String, device: String, profile: SoundProfile): String {
        val json = attempt { JSONObject(text) }.getOrDefault(JSONObject())
        json.put(
            device,
            JSONObject()
                .put("enabled", profile.enabled)
                .put("bands", JSONArray(profile.bands))
                .put("balance", profile.balance)
                .put("volume", profile.volume),
        )
        return json.toString()
    }

    val presets =
        mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
            "Bass" to listOf(6f, 3f, 0f, -1f, 0f),
            "Vocals" to listOf(-2f, 0f, 4f, 3f, 0f),
            "Gentle" to listOf(2f, 1f, 0f, -2f, -4f),
        )
}
