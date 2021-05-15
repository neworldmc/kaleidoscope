@file:OptIn(ExperimentalContracts::class)
package site.neworld.utils

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@JvmInline
value class Once(val flag: AtomicBoolean = AtomicBoolean(false)) {
    inline fun run(content: ()->Unit) {
        contract { callsInPlace(content, InvocationKind.AT_MOST_ONCE) }
        if (!flag.compareAndExchange(false, true)) content()
    }
}