package site.neworld.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

interface SyncClosable : AutoCloseable

interface AsyncClosable : SyncClosable {
    suspend fun closeAsync()

    override fun close() = runBlocking { closeAsync() }
}

inline fun <T : SyncClosable?, R> T.use(block: T.() -> R): R {
    return runCatching { block() }.fold({ it.also { this?.close() } }, { throw it.suppress { this?.close() } })
}

suspend inline fun <T : AsyncClosable?, R> T.use(block: T.() -> R): R {
    return runCatching { block() }.fold(
        { it.also { this?.closeAsync() } },
        { throw it.suppress { this?.closeAsync() } })
}

fun multiClose(vararg objs: SyncClosable, shade: Throwable? = null) {
    var shadeMut = shade
    for (o in objs.reversed()) shadeMut = shadeMut.suppress({ o.close() }, { Throwable("Multi-Close Failure") })
    if (shade != shadeMut) throw shadeMut!!
}

suspend fun multiClose(vararg objs: AsyncClosable, shade: Throwable? = null) {
    var shadeMut = shade
    for (o in objs.reversed()) shadeMut = shadeMut.suppress({ o.closeAsync() }, { Throwable("Multi-Close Failure") })
    if (shade != shadeMut) throw shadeMut!!
}

class ClosingContext(var shade: Throwable?) {
    private fun factory(o: Throwable) = Throwable("Exception In Close Context")

    fun sync(o: SyncClosable) {
        shade = shade.suppress({ o.close() }, this::factory)
    }

    suspend fun async(o: AsyncClosable) {
        shade = shade.suppress({ o.closeAsync() }, this::factory)
    }

    fun multi(o: Collection<SyncClosable>) = o.forEach(this::sync)

    suspend fun multi(o: Collection<AsyncClosable>) = o.forEach { async(it) }

    @JvmName("multiParallel1")
    suspend fun multiParallel(o: Collection<SyncClosable>) {
        supervisorScope { o.map { async { if (it is AsyncClosable) it.closeAsync() else it.close() } } }
            .forEach { shade = shade.suppress({ it.await() }, this::factory) }
    }

    @JvmName("multiParallel2")
    suspend fun multiParallel(o: Collection<AsyncClosable>) {
        supervisorScope { o.map { async { it.closeAsync() } } }
            .forEach { shade = shade.suppress({ it.await() }, this::factory) }
    }

    @JvmName("multiParallelSync1")
    fun multiParallelSync(o: Collection<SyncClosable>) = runBlocking { multiParallel(o) }

    @JvmName("multiParallelSync2")
    fun multiParallelSync(o: Collection<AsyncClosable>) = runBlocking { multiParallel(o) }

    fun multi(vararg o: SyncClosable) = o.forEach(this::sync)

    suspend fun multi(vararg o: AsyncClosable) = o.forEach { async(it) }

    @JvmName("multiParallel1")
    suspend fun multiParallel(vararg o: SyncClosable) {
        supervisorScope { o.map { async { if (it is AsyncClosable) it.closeAsync() else it.close() } } }
            .forEach { shade = shade.suppress({ it.await() }, this::factory) }
    }

    @JvmName("multiParallel2")
    suspend fun multiParallel(vararg o: AsyncClosable) {
        supervisorScope { o.map { async { it.closeAsync() } } }
            .forEach { shade = shade.suppress({ it.await() }, this::factory) }
    }

    @JvmName("multiParallelSync1")
    fun multiParallelSync(vararg o: SyncClosable) = runBlocking { multiParallel(*o) }

    @JvmName("multiParallelSync2")
    fun multiParallelSync(vararg o: AsyncClosable) = runBlocking { multiParallel(*o) }

    fun unaryPlus(o: SyncClosable) = sync(o)

    suspend fun unaryPlus(o: AsyncClosable) = async(o)

    fun unaryPlus(o: Collection<SyncClosable>) = multi(o)

    suspend fun unaryPlus(o: Collection<AsyncClosable>) = multi(o)

    fun unaryPlus(o: Array<SyncClosable>) = multi(*o)

    suspend fun unaryPlus(o: Array<AsyncClosable>) = multi(*o)
}

inline fun close(block: ClosingContext.() -> Unit) {
    ClosingContext(null).apply(block).shade.also { if (it != null) throw it }
}

interface AutoCloseContext: SyncClosable {
    fun <T : AutoCloseable> use(obj: T): T
}

fun autoCloseContext() = object : AutoCloseContext {
    private val stack = ArrayList<AutoCloseable>()

    override fun <T : AutoCloseable> use(obj: T) = obj.also { stack.add(it) }

    override fun close() {
        var shade: Throwable? = null
        while (stack.isNotEmpty())
            shade = shade.suppress({ stack.removeLast().close() }, { Throwable("Error on AutoClose") })
        if (shade != shade) throw shade!!
    }
}

inline fun <T> autoClose(content: AutoCloseContext.() -> T): T {
    return autoCloseContext().use(content)
}

inline fun <T> safeStart(content: AutoCloseContext.() -> T): T {
    val context = autoCloseContext()
    try {
        return context.content()
    } catch (e: Throwable) {
        throw e.also { context.close() }
    }
}
