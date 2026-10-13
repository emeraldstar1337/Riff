package dev.lad.data

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LocalizationTest {
    private fun strings(qualifier: String): Map<String, String> {
        val file =
            sequenceOf(
                    File("src/main/res/$qualifier/strings.xml"),
                    File("app/src/main/res/$qualifier/strings.xml"),
                )
                .first { it.isFile }
        val nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
                .getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test
    fun everyMessageHasAnEnglishAndRussianTranslation() {
        assertEquals(strings("values").keys, strings("values-ru").keys)
    }

    @Test
    fun englishHasNoUntranslatedInterfaceText() {
        strings("values").forEach { (key, value) ->
            if (value.trim('"') != "Русский")
                assertFalse(key, Regex("[А-Яа-яЁё]").containsMatchIn(value))
        }
    }

    @Test
    fun translatedMessagesPreserveFormatArguments() {
        val en = strings("values")
        val pattern = Regex("%([0-9]+\\$)?[-+ #0,(0-9.]*[a-zA-Z]")
        strings("values-ru").forEach { (key, value) ->
            assertEquals(
                key,
                pattern.findAll(value).map { it.value }.toList(),
                pattern.findAll(en.getValue(key)).map { it.value }.toList(),
            )
        }
    }

    @Test
    fun numericControlsHaveValidLocalizedFormats() {
        val en = strings("values")
        fun format(key: String, locale: Locale, value: Float) =
            String.format(locale, en.getValue(key).trim('"'), value)
        assertEquals("0.25", format("decimal_two", Locale.ENGLISH, 0.25f))
        assertEquals("0,25", format("decimal_two", Locale.forLanguageTag("ru"), 0.25f))
        assertEquals("+1.5", format("signed_decimal_one", Locale.ENGLISH, 1.5f))
        assertEquals("-1.5", format("signed_decimal_one", Locale.ENGLISH, -1.5f))
        assertEquals("1.25×", format("playback_speed_value", Locale.ENGLISH, 1.25f))
    }

    @Test
    fun formattedMessagesAreNotUsedAsPlainLabels() {
        val templates =
            strings("values")
                .filterValues {
                    Regex("%([0-9]+\\$)?[-+ #0,(0-9.]*[a-zA-Z]").containsMatchIn(it)
                }
                .keys
        val source =
            sequenceOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val plainLabel = Regex("strings\\s*\\[\\s*R\\s*\\.\\s*string\\s*\\.\\s*(\\w+)\\s*\\]")
        source
            .walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                plainLabel.findAll(file.readText()).forEach { match ->
                    assertFalse(
                        "${file.name}: ${match.groupValues[1]}",
                        match.groupValues[1] in templates,
                    )
                }
            }
    }
}
