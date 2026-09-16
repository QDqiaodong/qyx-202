package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoungeRoomDTO {

    private Long id;

    private String roomCode;

    private String roomName;

    private String floor;

    private Integer capacity;

    /** 用电承载（千瓦），可在房间档案单独调整 */
    private BigDecimal powerCapacity;

    /** 已挂电器功率合计（千瓦），服务端按该房间全部电器实时汇总 */
    private BigDecimal powerUsed;

    /** 还能接多少（千瓦）= 承载 - 已挂，为负表示已超承载 */
    private BigDecimal powerRemaining;

    /** 已挂合计是否压过承载 */
    private Boolean overCapacity;

    private String status;

    private Long shipId;

    private String shipCode;

    private String shipName;

    private String changeBatch;
}
