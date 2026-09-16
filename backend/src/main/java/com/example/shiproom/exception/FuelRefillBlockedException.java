package com.example.shiproom.exception;

import com.example.shiproom.dto.FuelBlockedDTO;
import lombok.Getter;

/**
 * 加油登记被整单退回：同一台机同一自然日已挂着一条未复核的加油，
 * 后登记的这一条失败。映射为 HTTP 409，data 带已在库那条的单号/罐号/升数/经办。
 */
@Getter
public class FuelRefillBlockedException extends RuntimeException {

    private final FuelBlockedDTO blocked;

    public FuelRefillBlockedException(String message, FuelBlockedDTO blocked) {
        super(message);
        this.blocked = blocked;
    }
}
