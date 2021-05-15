package site.neworld.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext

class SecondaryDispatcher(
    context: CoroutineContext = Dispatchers.Default, workers: Int = 1
) : CoroutineDispatcher(), SyncClosable {
    private val queue = Channel<Pair<CoroutineContext, Runnable>>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        runBlocking(Dispatchers.Unconfined) {
            runCatching { queue.send(Pair(context, block)) }.onFailure { dispatchCanceled(context, block, it) }
        }
    }

    private fun dispatchCanceled(context: CoroutineContext, block: Runnable, e: Throwable) {
        context.cancel(CancellationException("The task was rejected", e))
        Dispatchers.IO.dispatch(context, block)
    }

    private suspend fun worker() {
        runCatching{ for ((_, block) in queue) { block.run() } }.onFailure {
            if (it is CancellationException) {
                // The underlying context has been cancelled prematurely for some reason.
                // we need to cancel all pending tasks and stop new tasks from entering
                queue.close(it)
                for ((context, block) in queue) dispatchCanceled(context, block, it)
            }
            else bugCheck(it) // God knows what caused this
        }
    }

    init {
        CoroutineScope(context).apply { repeat(workers) { launch { worker() } } }
    }

    override fun close() {
        queue.close()
    }
}