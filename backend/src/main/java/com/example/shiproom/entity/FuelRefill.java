package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发电机加油登记（一机一自然日最多一条未复核）。
 *
 * - PENDING_REVIEW = 已登记未复核：本罐编号、实加升数、经办值班已写下，
 *                    此时库存升数不动；同机同日只允许挂着这一条。
 * - REVIEWED       = 复核通过：复核人必须是另一个班的人（自己加的不能自己核），
 *                    复核结论与库存升数同事务落库；此后这台机当天才能再开下一条。
 *
 * active_unique_key 仅在 PENDING_REVIEW 时赋值为 generatorId#refillDate，
 * 配合唯一约束兜底：两人同时给同一台机登记，后写库的那条失败。
 */
@Data
@Entity
@Table(name = "fuel_refill",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fuel_refill_active",
                columnNames = {"active_unique_key"}))
public class FuelRefill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 加油单号 */
    @Column(name = "refill_no", nullable = false, unique = true, length = 64)
    private String refillNo;

    @Column(name = "generator_id", nullable = false)
    private Long generatorId;

    @Column(name = "gen_code", nullable = false, length = 50)
    private String genCode;

    @Column(name = "gen_name", length = 100)
    private String genName;

    /** 加油发生的自然日（按天卡「同机同日只挂一条未复核」） */
    @Column(name = "refill_date", nullable = false)
    private LocalDate refillDate;

    /** 本罐编号（哪一罐油加进来的） */
    @Column(name = "can_no", nullable = false, length = 50)
    private String canNo;

    /** 实加升数 */
    @Column(name = "liters", nullable = false, precision = 10, scale = 2)
    private BigDecimal liters;

    /** 经办值班姓名 */
    @Column(name = "duty_officer", nullable = false, length = 50)
    private String dutyOfficer;

    /** 经办班次：DAY=早班 / MIDDLE=中班 / NIGHT=夜班 */
    @Column(name = "duty_shift", nullable = false, length = 20)
    private String dutyShift;

    /** PENDING_REVIEW / REVIEWED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** PENDING_REVIEW 时为 generatorId#refillDate，复核通过后清空 */
    @Column(name = "active_unique_key", length = 100)
    private String activeUniqueKey;

    /** 复核人姓名（必须是另一个班的人） */
    @Column(name = "reviewer_name", length = 50)
    private String reviewerName;

    /** 复核人班次，必须与经办班次不同 */
    @Column(name = "reviewer_shift", length = 20)
    private String reviewerShift;

    @Column(name = "review_time")
    private LocalDateTime reviewTime;

    /** 复核结论：PASS=通过 */
    @Column(name = "review_conclusion", length = 20)
    private String reviewConclusion;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    /** 复核通过那一刻的库存升数（加之前），随单留底供盘库存对账 */
    @Column(name = "stock_before_liters", precision = 10, scale = 2)
    private BigDecimal stockBeforeLiters;

    /** 复核通过那一刻的库存升数（加之后 = 加之前 + 实加升数） */
    @Column(name = "stock_after_liters", precision = 10, scale = 2)
    private BigDecimal stockAfterLiters;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
