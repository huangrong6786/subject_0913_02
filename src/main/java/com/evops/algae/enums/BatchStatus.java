package com.evops.algae.enums;

/**
 * 培养批次状态机：
 * CREATED --START--> RUNNING --COMPLETE--> COMPLETED --ACCEPT--> ACCEPTED
 * CREATED/RUNNING --TERMINATE--> TERMINATED
 */
public enum BatchStatus {
    CREATED,
    RUNNING,
    COMPLETED,
    TERMINATED,
    ACCEPTED
}
