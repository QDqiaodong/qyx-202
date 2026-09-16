package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PatrolCheckDTO {

    private Long id;
    private Long handoverId;
    private String handoverBatch;
    private Long windowId;
    private String windowCode;
    private Long shipId;
    private String shipCode;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String floor;
    private Integer seq;
    private String leaderName;
    private LocalDateTime checkTime;
}
