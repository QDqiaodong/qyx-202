package com.example.shiproom.exception;

import com.example.shiproom.dto.ShiftBlockedApplianceDTO;
import lombok.Getter;

import java.util.List;

@Getter
public class ShiftBlockedException extends RuntimeException {

    private final List<ShiftBlockedApplianceDTO> blockedAppliances;

    public ShiftBlockedException(String message, List<ShiftBlockedApplianceDTO> blockedAppliances) {
        super(message);
        this.blockedAppliances = blockedAppliances;
    }
}
