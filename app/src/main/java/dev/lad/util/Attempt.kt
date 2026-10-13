package dev.lad.util

import java.util.concurrent.CancellationException

inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
