package com.example.shiproom.dto;

import lombok.Data;

@Data
public class ShipDTO {

    private Long id;

    private String shipCode;

    private String shipName;

    private String shipType;

    private String dockCode;

    private String status;
}