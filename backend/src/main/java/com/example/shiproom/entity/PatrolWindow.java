package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 夜班巡检交班窗口：一条船一个接班窗口，窗口起止时间一旦建立不可修改。
 * 接班窗口一过（now &gt; windowEnd），该船在本窗口的补勾、改交班一律锁死。
 */
@Data
@Entity
@Table(name = "patrol_window")
public class PatrolWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "window_code", nullable = false, unique = true, length = 50)
    private String windowCode;

    @Column(name = "window_name", length = 100)
    private String windowName;

    @Column(name = "ship_id", nullable = false)
    private Long shipId;

    @Column(name = "ship_code", nullable = false, length = 50)
    private String shipCode;

    /** 接班窗口开始时间 */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** 接班窗口截止时间，过点即锁，不允许再补勾或改交班 */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** ACTIVE / CLOSED，CLOSED 为人工提前关闭；到点锁由 endTime 判定 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "operator", length = 50)
    private String operator;

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
