package com.example.shiproom.exception;

/**
 * 接班窗口已过（或未开始 / 已关闭），不能再补勾或提交/修改交班。
 * 映射为 HTTP 423 Locked。
 */
public class PatrolWindowLockedException extends RuntimeException {

    public PatrolWindowLockedException(String message) {
        super(message);
    }
}
