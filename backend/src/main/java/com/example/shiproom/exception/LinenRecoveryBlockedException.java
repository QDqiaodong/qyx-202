package com.example.shiproom.exception;

import com.example.shiproom.dto.LinenBlockedDTO;
import lombok.Getter;

/**
 * 布草回收保存 / 确认被整单退回：公斤折套数对不上、可住灯后改歪、
 * 同一间房已有一份未作废回收单等。映射为 HTTP 409。
 */
@Getter
public class LinenRecoveryBlockedException extends RuntimeException {

    private final LinenBlockedDTO blocked;

    public LinenRecoveryBlockedException(String message, LinenBlockedDTO blocked) {
        super(message);
        this.blocked = blocked;
    }
}
