package com.evops.algae.common;

/**
 * 乐观锁版本冲突（可重试）。从事务方法抛出后当前事务回滚，
 * 由外层非事务方法重新读最新版本后发起新事务重试。
 */
public class RetryableConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RetryableConflictException(String message) {
        super(message);
    }
}
