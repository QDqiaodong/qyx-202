package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发电机列表一行的今日加油状态：
 * 「已加油」这一列背后必须带得出可查的升数和复核人，
 * 只摆一个标记没有升数/复核人不算数。
 */
@Data
public class GeneratorFuelStateDTO {

    private Long generatorId;

    private String genCode;

    private String genName;

    private String location;

    private String status;

    /** 当前库存升数 */
    private BigDecimal fuelStockLiters;

    /** 今天的自然日 */
    private LocalDate today;

    /** NONE=今天还没加油 / PENDING_REVIEW=已登记待复核 / REVIEWED=已加油（复核通过） */
    private String todayState;

    /** 今天已复核通过的升数合计（盘库存对得上就靠它） */
    private BigDecimal todayReviewedLiters;

    /** 今天挂着的未复核单（有则同机今天不能再开下一条） */
    private Long pendingRefillId;

    private String pendingRefillNo;

    private String pendingCanNo;

    private BigDecimal pendingLiters;

    private String pendingDutyOfficer;

    private String pendingDutyShift;

    /** 今天最近一次复核通过的复核人（另一个班的人） */
    private String todayReviewerName;

    private String todayReviewerShift;

    private LocalDateTime todayReviewTime;
}
