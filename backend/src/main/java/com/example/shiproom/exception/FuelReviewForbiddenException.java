package com.example.shiproom.exception;

/**
 * 复核人资格不符：复核人必须是另一个班的人，自己加的不能自己核。
 * 映射为 HTTP 403。
 */
public class FuelReviewForbiddenException extends RuntimeException {

    public FuelReviewForbiddenException(String message) {
        super(message);
    }
}
