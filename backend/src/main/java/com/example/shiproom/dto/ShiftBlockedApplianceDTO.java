package com.example.shiproom.dto;

import lombok.Data;

@Data
public class ShiftBlockedApplianceDTO {

    private Long id;

    private String deviceCode;

    private String deviceName;

    private String status;

    private Long roomId;

    private String roomCode;

    private String roomName;

    private Long shipId;

    private String shipCode;

    private String shipName;
}
