package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 登记一次加油：选中哪一台机，写下本罐编号、实加升数和经办值班（姓名+班次）。
 * 登记即「未复核」，同一台机同一自然日只允许挂着一条。
 */
@Data
public class FuelRefillSaveDTO {

    private Long generatorId;

    /** 本罐编号 */
    private String canNo;

    /** 实加升数 */
    private BigDecimal liters;

    /** 经办值班姓名 */
    private String dutyOfficer;

    /** 经办班次：DAY=早班 / MIDDLE=中班 / NIGHT=夜班 */
    private String dutyShift;

    private String remark;
}
