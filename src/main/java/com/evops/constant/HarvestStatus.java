package com.evops.constant;

/** 采收批次状态：草稿 -> 已落账 -> 已验收。落账或验收后不能直接删除。 */
public final class HarvestStatus {
    public static final String DRAFT = "DRAFT";
    public static final String POSTED = "POSTED";
    public static final String ACCEPTED = "ACCEPTED";

    private HarvestStatus() {
    }
}
