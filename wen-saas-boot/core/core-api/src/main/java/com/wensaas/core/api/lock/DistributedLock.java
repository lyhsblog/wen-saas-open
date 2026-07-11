package com.wensaas.core.api.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 分布式锁（底座共性基础能力，架构审查 M1）。
 *
 * <p>底座对外暴露的框架无关契约：调用方面向本接口编程，不直接耦合具体实现（当前为 Redisson）。
 * 实现以「获取—执行—释放」模板方法为主，确保临界区在同一线程内完成获取与释放，并在 {@code finally}
 * 中安全释放，避免调用方误用裸 {@code lock/unlock} 造成的死锁与跨线程释放问题。
 *
 * <p>租期语义：
 * <ul>
 *   <li>不传 {@code leaseTime} 的重载启用 <b>watchdog 自动续期</b>——锁持有期间后台续约，适合
 *       执行时长不可预估的变长临界区（如数据导入/导出、对账）。</li>
 *   <li>传 {@code leaseTime} 的重载使用 <b>固定租期、无续期</b>——到期自动释放，适合可预估上界、
 *       且希望进程崩溃后能被兜底释放的临界区。</li>
 * </ul>
 *
 * <p>Key 由实现统一加命名空间前缀（{@code wen-saas-boot:lock:}），调用方只传业务维度的 key。
 */
public interface DistributedLock {

    /**
     * 阻塞获取锁（watchdog 自动续期），执行 {@code action} 后自动释放。
     *
     * @param key    业务锁 key（实现自动加命名空间前缀）
     * @param action 临界区逻辑
     * @return action 的返回值
     * @throws LockAcquireException 等待获取锁过程中线程被中断
     */
    <T> T executeWithLock(String key, Supplier<T> action);

    /** 阻塞获取锁（watchdog 自动续期），执行 {@code action} 后自动释放。 */
    void executeWithLock(String key, Runnable action);

    /**
     * 阻塞获取锁（固定租期、无续期），执行 {@code action} 后自动释放。
     *
     * @param leaseTime 锁租期；到期未主动释放则自动释放（兜底进程崩溃）
     */
    <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> action);

    /** 阻塞获取锁（固定租期、无续期），执行 {@code action} 后自动释放。 */
    void executeWithLock(String key, Duration leaseTime, Runnable action);

    /**
     * 有界等待获取锁（watchdog 自动续期）；获取成功则执行并自动释放，未获取到则不执行。
     *
     * @param waitTime 最长等待时长；{@link Duration#ZERO} 表示立即尝试一次（适合「多实例单跑」类场景）
     * @return 获取到锁时为 {@code Optional.ofNullable(action 返回值)}；未获取到锁时为 {@link Optional#empty()}。
     *         <b>约定 action 不应返回 {@code null}</b>，否则无法区分「未获取到」与「执行返回 null」——
     *         此类场景请改用 {@link #tryExecuteWithLock(String, Duration, Runnable)}。
     * @throws LockAcquireException 等待过程中线程被中断
     */
    <T> Optional<T> tryExecuteWithLock(String key, Duration waitTime, Supplier<T> action);

    /**
     * 有界等待获取锁（watchdog 自动续期）；获取成功则执行并自动释放。
     *
     * @return 获取到锁并执行返回 {@code true}；未获取到锁返回 {@code false}（不执行）
     * @throws LockAcquireException 等待过程中线程被中断
     */
    boolean tryExecuteWithLock(String key, Duration waitTime, Runnable action);
}
