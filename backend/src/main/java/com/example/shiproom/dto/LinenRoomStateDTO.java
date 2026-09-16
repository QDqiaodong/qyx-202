package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单间房的布草回收 + 可住灯状态（房间卡片据此渲染，二者互斥不会并排亮）。
 *
 * linenState：
 * - NOT_NEEDED   还没经历过换船（当前靠泊就是首班），没有要回收的脏床品
 * - PENDING      回收单缺着（连草稿都没有）——「回收未齐」亮
 * - DRAFT        有回收单但三栏没齐 / 公斤没对上——「回收未齐」亮
 * - RECOVERED    已确认，三栏齐且对账通过——「可住灯」亮
 */
@Data
public class LinenRoomStateDTO {

    private Long roomId;

    private String roomCode;

    private String roomName;

    private String floor;

    private Long currentShipId;

    private String currentShipCode;

    private String currentShipName;

    private Long departedShipId;

    private String departedShipCode;

    private String departedShipName;

    /** NOT_NEEDED / PENDING / DRAFT / RECOVERED */
    private String linenState;

    /** 可住灯：仅 RECOVERED 为 true，与「回收未齐」互斥 */
    private boolean availableLight;

    /** 回收未齐灯：PENDING / DRAFT 为 true */
    private boolean recoveryPendingLight;

    private Long recoveryId;

    private String recoveryNo;

    private Integer setCount;

    private BigDecimal bagWeight;

    private String witnessName;

    private String status;

    /** 每套约定公斤区间（随单留底；尚未开单给系统默认约定） */
    private BigDecimal kgPerSetMin;

    private BigDecimal kgPerSetMax;
}
