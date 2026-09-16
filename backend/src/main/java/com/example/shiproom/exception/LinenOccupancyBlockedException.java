package com.example.shiproom.exception;

import com.example.shiproom.dto.LinenBlockedDTO;
import lombok.Getter;

/**
 * 房间回收单还缺着（没单 / 草稿三栏未齐 / 未确认），别的船的人来占用（领钥匙）被挡回。
 * 映射为 HTTP 409，data 带卡住的房间与回收单。
 */
@Getter
public class LinenOccupancyBlockedException extends RuntimeException {

    private final LinenBlockedDTO blocked;

    public LinenOccupancyBlockedException(String message, LinenBlockedDTO blocked) {
        super(message);
        this.blocked = blocked;
    }
}
