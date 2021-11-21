package smol_access.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tinylog.kotlin.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

val IOLock = ObservableReentrantReadWriteLock(ReentrantReadWriteLock())

class ObservableReentrantReadWriteLock(val lock: ReentrantReadWriteLock) {
    val flow = MutableStateFlow(false)

    val stateFlow: StateFlow<Boolean> = flow.asStateFlow()

    /**
     * Executes the given [action] under the read lock of this lock.
     * @return the return value of the action.
     */
    inline fun <T> read(action: () -> T): T = lock.read { action() }

    /**
     * Executes the given [action] under the write lock of this lock.
     *
     * The function does upgrade from read to write lock if needed, but this upgrade is not atomic
     * as such upgrade is not supported by [ReentrantReadWriteLock].
     * In order to do such upgrade this function first releases all read locks held by this thread,
     * then acquires write lock, and after releasing it acquires read locks back again.
     *
     * Therefore if the [action] inside write lock has been initiated by checking some condition,
     * the condition must be rechecked inside the [action] to avoid possible races.
     *
     * @return the return value of the action.
     */
    inline fun <T> write(action: () -> T): T {
        return lock.write {
            Logger.trace { "Locked" }
            flow.tryEmit(true)
            val ret = action()
            Logger.trace { "Unlocked" }
            flow.tryEmit(false)
            ret
        }
    }
}

class ObservableLock(private val lock: Lock) : Lock by lock {
    private val flow = MutableStateFlow(false)

    val stateFlow: StateFlow<Boolean> = flow.asStateFlow()

    override fun lock() {
        lock.lock()
        Logger.trace { "Locked" }
        flow.tryEmit(true)
    }

    override fun lockInterruptibly() {
        lock.lockInterruptibly()
        Logger.trace { "Locked" }
        flow.tryEmit(true)
    }

    override fun tryLock(): Boolean {
        val tryLock = lock.tryLock()

        if (tryLock) {
            Logger.trace { "Locked" }
            flow.tryEmit(true)
        }

        return tryLock
    }

    override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        val tryLock = lock.tryLock(time, unit)

        if (tryLock) {
            Logger.trace { "Locked" }
            flow.tryEmit(true)
        }

        return tryLock
    }

    override fun unlock() {
        lock.unlock()
        Logger.trace { "Unlocked" }
        flow.tryEmit(false)
    }
}