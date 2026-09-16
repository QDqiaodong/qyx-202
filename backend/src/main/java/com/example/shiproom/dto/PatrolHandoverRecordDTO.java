package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交班流水（一条交班记录 + 其走房勾）。
 */
@Data
public class PatrolHandoverRecordDTO {

    private Long id;
    private String handoverBatch;
    private Long windowId;
    private String windowCode;
    private Long shipId;
    private String shipCode;
    private String shipName;
    private Long leaderId;
    private String leaderCode;
    private String leaderName;
    /** HANDED / BLOCKED */
    private String status;
    private String blockedReason;
    private String walkOrder;
    private Integer roomCount;
    private String remark;
    private LocalDateTime submitTime;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    private PatrolBlockedRoomDTO blockedRoom;
    private List<PatrolCheckDTO> checks;
}
