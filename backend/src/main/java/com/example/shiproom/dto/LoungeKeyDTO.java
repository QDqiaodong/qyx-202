package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoungeKeyDTO {

    private Long id;

    private String keyCode;

    private String keyName;

    private Long roomId;

    private String roomCode;

    private String roomName;

    private String status;

    private String holderName;

    private Long shipId;

    private String shipCode;

    private String shipName;

    private String checkoutBatch;

    private LocalDateTime checkoutTime;
}
