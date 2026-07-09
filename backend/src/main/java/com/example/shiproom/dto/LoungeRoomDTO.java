package com.example.shiproom.dto;

import lombok.Data;

@Data
public class LoungeRoomDTO {

    private Long id;

    private String roomCode;

    private String roomName;

    private String floor;

    private Integer capacity;

    private String status;

    private String shipCode;

    private String shipName;
}