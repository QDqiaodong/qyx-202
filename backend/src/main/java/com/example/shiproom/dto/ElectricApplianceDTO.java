package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ElectricApplianceDTO {

    private Long id;

    private String deviceCode;

    private String deviceName;

    private BigDecimal power;

    private String applianceType;

    private String status;

    private Long roomId;

    private String roomCode;

    private String roomName;

    private Long shipId;

    private String shipCode;

    private String shipName;
}