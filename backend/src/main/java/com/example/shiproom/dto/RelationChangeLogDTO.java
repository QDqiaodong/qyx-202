package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RelationChangeLogDTO {

    private Long id;

    private String changeType;

    private String changeBatch;

    private Long deviceId;

    private String deviceCode;

    private Long roomId;

    private String roomCode;

    private Long shipId;

    private String shipCode;

    private Long oldRoomId;

    private String oldRoomCode;

    private Long oldShipId;

    private String oldShipCode;

    private String operator;

    private String remark;

    private LocalDateTime changeTime;
}