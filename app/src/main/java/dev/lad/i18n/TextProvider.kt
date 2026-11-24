package dev.lad.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

fun interface TextProvider {
    fun format(@StringRes id: Int, vararg arguments: Any?): String

    fun plural(@PluralsRes id: Int, quantity: Number, vararg arguments: Any?): String =
        format(id, quantity, *arguments)

    operator fun get(@StringRes id: Int): String = format(id)
}
