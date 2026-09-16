package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 挂电器会压过房间承载时的退回说明：
 * 这间房承载多少、当前已挂多少、这一台多少、挂上之后合计多少，一次性写清。
 */
@Data
public class RoomOverCapacityDTO {

    private Long roomId;

    private String roomCode;

    private String roomName;

    /** 房间用电承载（千瓦） */
    private BigDecimal powerCapacity;

    /** 当前已挂电器功率合计（千瓦，不含本次这台） */
    private BigDecimal powerUsed;

    /** 还能接多少（千瓦） */
    private BigDecimal powerRemaining;

    /** 本次要挂的电器 ID */
    private Long applianceId;

    private String applianceCode;

    private String applianceName;

    /** 本次要挂的这台电器功率（千瓦） */
    private BigDecimal appliancePower;

    /** 假设挂上后的合计（千瓦） */
    private BigDecimal projectedTotal;

    /** 说明文案 */
    private String message;
}
