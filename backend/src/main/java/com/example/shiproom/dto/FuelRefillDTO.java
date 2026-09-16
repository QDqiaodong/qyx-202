package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一条加油登记（含复核结论与库存快照），盘库存时对得上：
 * 哪台机、哪一罐、实加多少升、谁加的、谁核的、核完库存从多少变多少。
 */
@Data
public class FuelRefillDTO {

    private Long id;

    private String refillNo;

    private Long generatorId;

    private String genCode;

    private String genName;

    private LocalDate refillDate;

    private String canNo;

    private BigDecimal liters;

    private String dutyOfficer;

    private String dutyShift;

    /** PENDING_REVIEW / REVIEWED */
    private String status;

    private String reviewerName;

    private String reviewerShift;

    private LocalDateTime reviewTime;

    private String reviewConclusion;

    private String reviewComment;

    /** 复核通过时库存升数（加之前） */
    private BigDecimal stockBeforeLiters;

    /** 复核通过时库存升数（加之后） */
    private BigDecimal stockAfterLiters;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
