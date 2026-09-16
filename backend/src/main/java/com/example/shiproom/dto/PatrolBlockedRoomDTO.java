package com.example.shiproom.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 交班被整单退回时，明确告诉前端是哪间房、在哪个窗口卡住。
 */
@Data
@Builder
public class PatrolBlockedRoomDTO {

    private String reason;
    private Long windowId;
    private String windowCode;
    private Long shipId;
    private String shipCode;
    private String shipName;

    private Long roomId;
    private String roomCode;
    private String roomName;
    private String floor;

    /** 跳层时，上一间走过的楼层（能看到从哪层跳过来） */
    private String previousFloor;
    private String previousRoomCode;

    /** 房间已不再停靠本船时，房间当前实际停靠的船舶 */
    private Long currentShipId;
    private String currentShipCode;
    private String currentShipName;

    private String message;
}
