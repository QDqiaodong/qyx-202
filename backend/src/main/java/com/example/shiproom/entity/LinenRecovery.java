package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 布草回收单（换船后脏床品回收登记）。
 *
 * 一单对一间休息室的一次换船周期：
 * - DRAFT     = 草稿，套数 / 封袋公斤数 / 见证人三栏还没齐，可补登、可改；此时房间不可住。
 * - CONFIRMED = 已确认，三栏齐且公斤折套数对得上，可住灯亮起；此后套数与公斤数锁定，
 *               谁再改歪保存一律失败，要纠正只能先作废再重开。
 * - VOID      = 已作废（填错纠正），不再占用“同一间房只挂一份未作废单”的名额，也不点亮可住灯。
 *
 * active_unique_key 仅在非 VOID 时赋值为 roomId，配合唯一约束兜底，
 * 保证同一间房同一时刻最多只有一份未作废回收单（两人同时保存，后到者失败）。
 */
@Data
@Entity
@Table(name = "linen_recovery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_linen_recovery_active_room",
                columnNames = {"active_unique_key"}))
public class LinenRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recovery_no", nullable = false, unique = true, length = 64)
    private String recoveryNo;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "room_code", nullable = false, length = 50)
    private String roomCode;

    @Column(name = "room_name", length = 100)
    private String roomName;

    /** 换走的上一班船（离泊船），建单时快照；补登时可能已无 ACTIVE 靠泊，故允许为空 */
    @Column(name = "departed_ship_id")
    private Long departedShipId;

    @Column(name = "departed_ship_code", length = 50)
    private String departedShipCode;

    @Column(name = "departed_ship_name", length = 100)
    private String departedShipName;

    /** 回收单对应的换班批次（靠泊关联的 change_batch），用来锁定是哪一班船留下的脏床品 */
    @Column(name = "change_batch", length = 64)
    private String changeBatch;

    /** 收走的脏床品套数；未填为空 */
    @Column(name = "set_count")
    private Integer setCount;

    /** 秤上的封袋公斤数；未填为空 */
    @Column(name = "bag_weight", precision = 10, scale = 2)
    private BigDecimal bagWeight;

    /** 建单时约定的每套公斤区间下界，随单留底 */
    @Column(name = "kg_per_set_min", precision = 10, scale = 2)
    private BigDecimal kgPerSetMin;

    /** 建单时约定的每套公斤区间上界，随单留底 */
    @Column(name = "kg_per_set_max", precision = 10, scale = 2)
    private BigDecimal kgPerSetMax;

    /** 见证人姓名；未填为空 */
    @Column(name = "witness_name", length = 50)
    private String witnessName;

    /** 登记/保洁员 */
    @Column(name = "operator", length = 50)
    private String operator;

    /** DRAFT / CONFIRMED / VOID */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 非 VOID 时赋值为 roomId#changeBatch，配合唯一约束保证同房同换班周期只有一份未作废单 */
    @Column(name = "active_unique_key", length = 100)
    private String activeUniqueKey;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "confirmed_time")
    private LocalDateTime confirmedTime;

    @Column(name = "confirmed_by", length = 50)
    private String confirmedBy;

    @Column(name = "void_time")
    private LocalDateTime voidTime;

    @Column(name = "void_by", length = 50)
    private String voidBy;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

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
