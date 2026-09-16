package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 夜班巡检走房勾（巡检明细）。
 *
 * 只有交班成功（HANDED）的同一事务里才会写入；交班被整单退回时一行都不留。
 * 每条勾记下值班长、接班窗口、走房顺序序号、当时房间实际停靠的船舶与楼层快照，
 * 因此“是谁、在哪个时间窗、按什么顺序把该船当时的房间走完”可以逐勾对账。
 * (handover_id, seq) 与 (window_id, room_id) 各有唯一约束兜底。
 */
@Data
@Entity
@Table(name = "patrol_check",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_patrol_check_seq", columnNames = {"handover_id", "seq"}),
                @UniqueConstraint(name = "uk_patrol_check_window_room", columnNames = {"window_id", "room_id"})
        })
public class PatrolCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    @Column(name = "handover_batch", nullable = false, length = 64)
    private String handoverBatch;

    @Column(name = "window_id", nullable = false)
    private Long windowId;

    @Column(name = "window_code", nullable = false, length = 50)
    private String windowCode;

    @Column(name = "ship_id", nullable = false)
    private Long shipId;

    @Column(name = "ship_code", nullable = false, length = 50)
    private String shipCode;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "room_code", nullable = false, length = 50)
    private String roomCode;

    @Column(name = "room_name", length = 100)
    private String roomName;

    /** 交班时锁定的楼层快照 */
    @Column(name = "floor", length = 20)
    private String floor;

    /** 走房顺序序号，从 1 开始，严格按楼层从低到高 */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "leader_name", nullable = false, length = 50)
    private String leaderName;

    @Column(name = "check_time", nullable = false)
    private LocalDateTime checkTime;

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
