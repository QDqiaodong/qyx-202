package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeyOccupancyDTO {

    private Long id;

    private Long keyId;

    private String keyCode;

    private String keyName;

    private Long roomId;

    private String roomCode;

    private String roomName;

    private Long shipId;

    private String shipCode;

    private String shipName;

    private String holderName;

    private String checkoutBatch;

    private LocalDateTime checkoutTime;
}
