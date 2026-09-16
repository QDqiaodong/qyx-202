package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加油登记 / 复核被退回时的明细：
 * - 同机同日已有未复核单：带上已在库的那条单号、罐号、升数、经办；
 * - 复核撞车：带上先写完的那次复核结论（复核人、班次、时间），后到的人看得见这条已经核过。
 */
@Data
public class FuelBlockedDTO {

    /** DUPLICATE_PENDING / ALREADY_REVIEWED */
    private String reason;

    private Long generatorId;

    private String genCode;

    private String genName;

    private Long refillId;

    private String refillNo;

    private String canNo;

    private BigDecimal liters;

    private String dutyOfficer;

    private String dutyShift;

    /** 已在库未复核单号（重复登记时带） */
    private String existingRefillNo;

    private Long existingRefillId;

    /** 先写完的那次复核（复核撞车时带） */
    private String reviewerName;

    private String reviewerShift;

    private LocalDateTime reviewTime;

    private String reviewConclusion;

    private String message;
}
