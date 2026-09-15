package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeyCheckoutRecordDTO {

    private Long id;

    private String checkoutBatch;

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

    private String operator;

    private String status;

    private LocalDateTime checkoutTime;

    private LocalDateTime returnTime;

    private String returnOperator;

    private String remark;
}
