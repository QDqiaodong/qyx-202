package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 布草回收保存/确认失败时的退回明细：
 * 带上每套约定公斤区间、这次秤重、按公斤折出来的套数（区间），以及失败原因码。
 */
@Data
public class LinenBlockedDTO {

    private String reason;

    private Long roomId;

    private String roomCode;

    private String roomName;

    /** 换走的上一班船（离泊船） */
    private Long departedShipId;

    private String departedShipCode;

    private String departedShipName;

    private Long recoveryId;

    private String recoveryNo;

    /** 已经在库的未作废回收单号（重复保存时带） */
    private String existingRecoveryNo;

    private Long existingRecoveryId;

    private Integer submittedSetCount;

    /** 这次秤重（公斤） */
    private BigDecimal submittedBagWeight;

    private String submittedWitnessName;

    /** 每套约定公斤区间下界 */
    private BigDecimal kgPerSetMin;

    /** 每套约定公斤区间上界 */
    private BigDecimal kgPerSetMax;

    /** 按本次秤重折出来的套数下界（floor(weight / 每套上界)） */
    private Integer inferredSetCountMin;

    /** 按本次秤重折出来的套数上界（floor(weight / 每套下界)） */
    private Integer inferredSetCountMax;

    private String message;
}
