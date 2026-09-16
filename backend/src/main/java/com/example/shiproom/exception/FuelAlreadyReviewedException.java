package com.example.shiproom.exception;

import com.example.shiproom.dto.FuelBlockedDTO;
import lombok.Getter;

/**
 * 复核撞车：两人抢着复核同一条，先写完的那次结论留下，
 * 后到的人收到 409，并看得见这条已经核过（复核人、班次、时间、结论）。
 */
@Getter
public class FuelAlreadyReviewedException extends RuntimeException {

    private final FuelBlockedDTO blocked;

    public FuelAlreadyReviewedException(String message, FuelBlockedDTO blocked) {
        super(message);
        this.blocked = blocked;
    }
}
