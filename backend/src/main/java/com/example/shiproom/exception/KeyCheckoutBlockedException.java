package com.example.shiproom.exception;

import com.example.shiproom.dto.KeyBlockedDTO;
import lombok.Getter;

@Getter
public class KeyCheckoutBlockedException extends RuntimeException {

    private final KeyBlockedDTO blockedKey;

    public KeyCheckoutBlockedException(String message, KeyBlockedDTO blockedKey) {
        super(message);
        this.blockedKey = blockedKey;
    }
}
