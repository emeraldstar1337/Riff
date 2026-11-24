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

    private fun plurals(qualifier: String): Map<String, Map<String, String>> {
        val file =
            sequenceOf(
                    File("src/main/res/$qualifier/plurals.xml"),
                    File("app/src/main/res/$qualifier/plurals.xml"),
                )
                .first { it.isFile }
        val nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
                .getElementsByTagName("plurals")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            val items = mutableMapOf<String, String>()
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeName == "item") {
                    items[child.attributes.getNamedItem("quantity").nodeValue] = child.textContent
                }
            }
            name to items
        }
    }

    @Test
    fun everyMessageHasAnEnglishAndRussianTranslation() {
        assertEquals(strings("values").keys, strings("values-ru").keys)
        assertEquals(plurals("values").keys, plurals("values-ru").keys)
    }

    @Test
    fun pluralsDoNotDuplicateStringResources() {
        assertEquals(emptySet<String>(), strings("values").keys intersect plurals("values").keys)
        assertEquals(
            emptySet<String>(),
            strings("values-ru").keys intersect plurals("values-ru").keys,
        )
    }

    @Test
    fun englishHasNoUntranslatedInterfaceText() {
        strings("values").forEach { (key, value) ->
            if (value.trim('"') != "Русский")
                assertFalse(key, Regex("[А-Яа-яЁё]").containsMatchIn(value))
        }
        plurals("values").forEach { (key, forms) ->
            forms.forEach { (qty, value) ->
                assertFalse("$key[$qty]", Regex("[А-Яа-яЁё]").containsMatchIn(value))
            }
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
        val enPlurals = plurals("values")
        plurals("values-ru").forEach { (key, forms) ->
            val expected =
                pattern.findAll(enPlurals.getValue(key).getValue("other")).map { it.value }.toList()
            forms.forEach { (qty, value) ->
                assertEquals(
                    "$key[$qty]",
                    expected,
                    pattern.findAll(value).map { it.value }.toList(),
                )
            }
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
                .filterValues { Regex("%([0-9]+\\$)?[-+ #0,(0-9.]*[a-zA-Z]").containsMatchIn(it) }
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
