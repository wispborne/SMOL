package util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tinylog.kotlin.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

val IOLock = ObservableLock(ReentrantLock())

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