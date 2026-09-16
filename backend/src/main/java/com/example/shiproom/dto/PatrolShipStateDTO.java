package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一条船在一个接班窗口下的当前巡检交班状态。
 * 关掉页面再打开，仍从 MySQL 还原成同一个状态：
 * NOT_HANDED 未交 / BLOCKED 卡在（漏房、跳层、房间脱挂）/ LOCKED 窗口外已锁 / HANDED 已交。
 */
@Data
public class PatrolShipStateDTO {

    private Long shipId;
    private String shipCode;
    private String shipName;
    private Long windowId;
    private String windowCode;
    private String windowName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** NOT_STARTED / OPEN / LOCKED / CLOSED */
    private String windowState;
    /** NOT_HANDED / BLOCKED / HANDED（LOCKED 时按是否已有 HANDED 仍可为 HANDED，否则 LOCKED） */
    private String handoverState;

    private String handoverBatch;
    private String leaderName;
    private String walkOrder;
    private Integer roomCount;
    private LocalDateTime submitTime;

    private PatrolBlockedRoomDTO blocked;

    /** 该船当前（锁定快照）挂靠的全部应巡房间，按楼层从低到高 */
    private List<PatrolExpectedRoomDTO> expectedRooms;

    @Data
    public static class PatrolExpectedRoomDTO {
        private Long roomId;
        private String roomCode;
        private String roomName;
        private String floor;
        private boolean checked;
    }
}
