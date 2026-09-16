package com.example.shiproom.exception;

import com.example.shiproom.dto.RoomOverCapacityDTO;
import lombok.Getter;

/**
 * 电器挂入房间后合计会压过承载（或房间已处于超限状态），本次挂入不落账。
 */
@Getter
public class RoomOverCapacityException extends RuntimeException {

    private final RoomOverCapacityDTO detail;

    public RoomOverCapacityException(String message, RoomOverCapacityDTO detail) {
        super(message);
        this.detail = detail;
    }
}
