package com.example.shiproom.dto;

import lombok.Data;

@Data
public class KeyBlockedDTO {

    private Long keyId;

    private String keyCode;

    private String keyName;

    private Long roomId;

    private String roomCode;

    private String roomName;

    private Long requestedShipId;

    private String requestedShipCode;

    private String requestedShipName;

    private Long currentShipId;

    private String currentShipCode;

    private String currentShipName;

    private String holderName;

    private String reason;
}
