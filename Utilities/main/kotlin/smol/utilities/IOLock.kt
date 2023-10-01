/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.utilities

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import smol.timber.Timber
import smol.timber.ktx.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

val IOLock = ObservableReentrantReadWriteLock()

object IOLocks {
    val modFolderLock = ReentrantReadWriteLock()
    val configLock = ReentrantReadWriteLock()
    val gameMainFolderLock = ReentrantReadWriteLock()
    val defaultLock = configLock + modFolderLock
    val everythingLock = configLock + modFolderLock + gameMainFolderLock
}

data class LockContext(val locks: List<ReentrantReadWriteLock>)

operator fun ReentrantReadWriteLock.plus(lock: ReentrantReadWriteLock): LockContext =
    LockContext(listOf(this, lock))

operator fun LockContext.plus(lock: ReentrantReadWriteLock): LockContext =
    this.copy(locks = this.locks + lock)

operator fun ReentrantReadWriteLock.plus(lockContext: LockContext): LockContext =
    lockContext.copy(locks = this.asList() + lockContext.locks)

class ObservableReentrantReadWriteLock() {
    val flow = MutableStateFlow(false)
    val tag = "lock"

    val stateFlow: StateFlow<Boolean> = flow.asStateFlow()

    inline fun getCurrentThreadName() = Thread.currentThread().name ?: "unknown thread"

    /**
     * Executes the given [action] under the read lock of this lock.
     * @return the return value of the action.
     */
    inline fun <T> read(lock: ReentrantReadWriteLock, action: () -> T): T =
        read(LockContext(lock.asList()), action)

    /**
     * Executes the given [action] under the read lock of this lock.
     * @return the return value of the action.
     */
    inline fun <T> read(lockContext: LockContext = IOLocks.defaultLock, action: () -> T): T {
        lockContext.locks.forEach { lock ->
            if (lock === lockContext.locks.last()) {
                val result = try {
                    lock.read {
                        smol.timber.ktx.Timber.tag(tag)
                            .d { "Read locked from ${Timber.findClassName()} on ${getCurrentThreadName()}." }
                        return@read try {
                            action.invoke()
                        } finally {
                            smol.timber.ktx.Timber.tag(tag)
                                .d { "Read unlocked from ${Timber.findClassName()} on ${getCurrentThreadName()}." }
                        }
                    }
                } finally {
                    lockContext.locks.dropLast(1).forEach {
                        smol.timber.ktx.Timber.v { "Unlocking $it" }
                        runCatching {
                            it.readLock().unlock()
                        }
                            .onFailure { smol.timber.ktx.Timber.e(it) }
                    }
                }
                return result
            } else {
                smol.timber.ktx.Timber.v { "Locking $lock" }
                lock.readLock().lock()
            }
        }

        // If no locks, run func and return result.
        return action.invoke()
    }

    inline fun <T> write(lock: ReentrantReadWriteLock, action: () -> T): T = write(
        lockContext = LockContext(lock.asList()),
        action = action
    )

    fun updateLockedFlowState() {
        if (IOLocks.everythingLock.locks.any { it.isWriteLocked }) {
            flow.tryEmit(true)
        } else {
            flow.tryEmit(false)
        }
    }

    /**
     * WARNING: do not run any code that switches threads inside of this block. It will crash.
     *
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
    @OptIn(ExperimentalCoroutinesApi::class)
    inline fun <T> write(lockContext: LockContext = IOLocks.defaultLock, action: () -> T): T {
        lockContext.locks.forEach { lock ->
            lockLock(lock)
        }

        try {
            updateLockedFlowState()
            return action()
        } finally {
            lockContext.locks.forEach { lockToUnlock ->
                unlockLock(lockToUnlock)
            }
            updateLockedFlowState()
        }
    }

    fun lockLock(lock: ReentrantReadWriteLock) {
        smol.timber.ktx.Timber.tag(tag)
            .d { "Write locked from '${Timber.findClassName()}' on ${getCurrentThreadName()}." }
        val rl = lock.readLock()
        val readCount = if (lock.writeHoldCount == 0) lock.readHoldCount else 0
        repeat(readCount) { rl.unlock() }
        lock.writeLock().lock()
    }

    fun unlockLock(lock: ReentrantReadWriteLock) {
        smol.timber.ktx.Timber.tag(tag)
            .d { "Write unlocked from '${Timber.findClassName()}' on ${getCurrentThreadName()}." }
        val readCount = if (lock.writeHoldCount == 0) lock.readHoldCount else 0
        repeat(readCount) { lock.readLock().lock() }
        lock.writeLock().unlock()
    }
}

class ObservableLock(private val lock: Lock) : Lock by lock {
    private val flow = MutableStateFlow(false)

    val stateFlow: StateFlow<Boolean> = flow.asStateFlow()

    override fun lock() {
        lock.lock()
        smol.timber.ktx.Timber.v { "Locked" }
        flow.tryEmit(true)
    }

    override fun lockInterruptibly() {
        lock.lockInterruptibly()
        smol.timber.ktx.Timber.v { "Locked" }
        flow.tryEmit(true)
    }

    override fun tryLock(): Boolean {
        val tryLock = lock.tryLock()

        if (tryLock) {
            smol.timber.ktx.Timber.v { "Locked" }
            flow.tryEmit(true)
        }

        return tryLock
    }

    override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        val tryLock = lock.tryLock(time, unit)

        if (tryLock) {
            smol.timber.ktx.Timber.v { "Locked" }
            flow.tryEmit(true)
        }

        return tryLock
    }

    override fun unlock() {
        lock.unlock()
        smol.timber.ktx.Timber.v { "Unlocked" }
        flow.tryEmit(false)
    }
}