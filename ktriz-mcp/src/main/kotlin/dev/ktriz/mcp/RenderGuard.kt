package dev.ktriz.mcp

import kotlinx.coroutines.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** Hard deadline for one render call. */
const val RENDER_TIMEOUT_MS = 10_000L

/** Max. number of render workers that may exist at the same time (running or hung). */
const val MAX_CONCURRENT_RENDERS = 2

/** Max. length of a produced SVG document (characters) that is returned to the caller. */
const val MAX_SVG_OUTPUT_CHARS = 2_000_000

/** Result of [guardedRender]. */
sealed interface RenderOutcome {
    data class Ok(
        val svg: String,
    ) : RenderOutcome

    data object TimedOut : RenderOutcome

    data object Busy : RenderOutcome
}

private val workerCounter = AtomicInteger()

private val renderPermits = Semaphore(MAX_CONCURRENT_RENDERS)

private val renderExecutor: ExecutorService by lazy {
    Executors.newCachedThreadPool(
        ThreadFactory { task ->
            Thread(task, "ktriz-mcp-render-${workerCounter.incrementAndGet()}").apply { isDaemon = true }
        },
    )
}

/**
 * Runs [render] on a daemon worker thread with a deadline of [timeoutMs].
 *
 * Capacity: at most [MAX_CONCURRENT_RENDERS] workers exist at any time. The permit is released by
 * the **worker** in its `finally`, not by the caller: a layout run that hangs (ELK does not react
 * to interrupts) keeps counting against the limit until it really ends, so a flood of hostile
 * inputs can never accumulate more than [MAX_CONCURRENT_RENDERS] stuck threads. A caller finding
 * no free permit gets [RenderOutcome.Busy] immediately (no queueing).
 *
 * Timeout: on expiry the caller gets [RenderOutcome.TimedOut] and the future is cancelled with
 * `cancel(true)` -- a single best-effort interrupt. This is **not** a guarantee that the worker
 * stops; it only guarantees that the *response* is released on time. The threads are daemons, so
 * they never block JVM exit.
 *
 * A failure inside [render] is rethrown unchanged to the caller (the [ExecutionException] is
 * unwrapped), so `mcpToolCall` reports it as `internal_error` with the exception class name only.
 */
fun guardedRender(
    timeoutMs: Long = RENDER_TIMEOUT_MS,
    render: () -> String,
): RenderOutcome {
    if (!renderPermits.tryAcquire()) return RenderOutcome.Busy
    val future =
        try {
            renderExecutor.submit<String> {
                try {
                    render()
                } finally {
                    renderPermits.release()
                }
            }
        } catch (e: Throwable) {
            // submit() failed before the task could ever run its own finally
            renderPermits.release()
            throw e
        }
    return try {
        RenderOutcome.Ok(future.get(timeoutMs, TimeUnit.MILLISECONDS))
    } catch (_: TimeoutException) {
        future.cancel(true)
        RenderOutcome.TimedOut
    } catch (e: ExecutionException) {
        throw (e.cause ?: e)
    } catch (e: InterruptedException) {
        future.cancel(true)
        Thread.currentThread().interrupt()
        throw CancellationException("render interrupted")
    }
}
