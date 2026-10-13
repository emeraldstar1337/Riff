package dev.lad.i18n

fun interface TextProvider {
    fun format(id: Int, vararg arguments: Any?): String

    operator fun get(id: Int): String = format(id)
}
