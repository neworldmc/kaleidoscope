package site.neworld.utils

@JvmName("suppress")
inline fun Throwable.suppress(block:()->Unit) = this.also {
    try { block() }
    catch (e: Throwable) { addSuppressed(e) }
}

@JvmName("suppressNull")
inline fun Throwable?.suppress(block:()->Unit, factory: (Throwable)->Throwable): Throwable? {
    return try {
        block()
        this
    }
    catch (e: Throwable) {
        this?.apply { addSuppressed(e) } ?: factory(e).also { if (it != e) it.addSuppressed(e) }
    }
}
