package com.example.shiproom.exception;

/**
 * 提交人不是本班值班长，无权交班。映射为 HTTP 403。
 */
public class PatrolForbiddenException extends RuntimeException {

    public PatrolForbiddenException(String message) {
        super(message);
    }
}
