package com.example.shiproom.exception;

/**
 * 同船同窗口已有一份 HANDED（含两人同时交班竞争失败），不能再交。映射为 HTTP 409。
 */
public class PatrolAlreadyHandedException extends RuntimeException {

    public PatrolAlreadyHandedException(String message) {
        super(message);
    }
}
