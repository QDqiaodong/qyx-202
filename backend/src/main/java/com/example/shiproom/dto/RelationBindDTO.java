package com.example.shiproom.dto;

import lombok.Data;

@Data
public class RelationBindDTO {

    private Long deviceId;

    private String deviceCode;

    private Long roomId;

    private String roomCode;

    private Long shipId;

    private String shipCode;

    private String operator;

    private String remark;
}