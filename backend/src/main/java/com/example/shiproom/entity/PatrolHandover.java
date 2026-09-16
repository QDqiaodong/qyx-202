package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 夜班巡检交班记录（交班流水）。
 *
 * 一次提交一条：
 * - HANDED  = 交班成功，此时同事务在 patrol_check 落下该船本窗口全部房间的走房勾。
 * - BLOCKED = 整份交班被退回（漏房 / 跳层 / 房间已不再停靠本船），只留这条退回说明，
 *             不写任何 patrol_check，不留半截勾。
 *
 * handed_unique_key 仅在 HANDED 时赋值为 shipId + "#" + windowId，配合唯一约束兜底，
 * 保证同船同窗口在两人同时交班时最多只有一份 HANDED。
 */
@Data
@Entity
@Table(name = "patrol_handover",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_patrol_handover_success",
                columnNames = {"handed_unique_key"}))
public class PatrolHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_batch", nullable = false, unique = true, length = 64)
    private String handoverBatch;

    @Column(name = "window_id", nullable = false)
    private Long windowId;

    @Column(name = "window_code", nullable = false, length = 50)
    private String windowCode;

    @Column(name = "ship_id", nullable = false)
    private Long shipId;

    @Column(name = "ship_code", nullable = false, length = 50)
    private String shipCode;

    /** 交班时锁定的船名快照 */
    @Column(name = "ship_name", length = 100)
    private String shipName;

    /** 实际提交交班的值班长 */
    @Column(name = "leader_id")
    private Long leaderId;

    @Column(name = "leader_code", length = 50)
    private String leaderCode;

    @Column(name = "leader_name", nullable = false, length = 50)
    private String leaderName;

    /** HANDED / BLOCKED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 仅 HANDED 时赋值为 shipId#windowId，配合唯一约束保证同船同窗口只有一份已交 */
    @Column(name = "handed_unique_key", length = 80)
    private String handedUniqueKey;

    /** BLOCKED 时的退回原因码：ROOM_DETACHED / MISSING_ROOM / FLOOR_SKIP / BAD_ENTRY */
    @Column(name = "blocked_reason", length = 30)
    private String blockedReason;

    /** 成功时的走房顺序文本，例如 1F:R-101 > 1F:R-102 > 2F:R-201 */
    @Column(name = "walk_order", length = 2000)
    private String walkOrder;

    @Column(name = "room_count")
    private Integer roomCount;

    /** BLOCKED 时卡住的房间快照 */
    @Column(name = "blocked_room_id")
    private Long blockedRoomId;

    @Column(name = "blocked_room_code", length = 50)
    private String blockedRoomCode;

    @Column(name = "blocked_room_name", length = 100)
    private String blockedRoomName;

    @Column(name = "blocked_floor", length = 20)
    private String blockedFloor;

    /** ROOM_DETACHED 时该房间当前实际停靠的船舶快照（可能为空） */
    @Column(name = "current_ship_id")
    private Long currentShipId;

    @Column(name = "current_ship_code", length = 50)
    private String currentShipCode;

    @Column(name = "current_ship_name", length = 100)
    private String currentShipName;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "submit_time", nullable = false)
    private LocalDateTime submitTime;

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
