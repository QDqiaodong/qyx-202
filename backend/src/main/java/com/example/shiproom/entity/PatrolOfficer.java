package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 夜班巡检人员名册：只有本窗口本班的值班长（role=LEADER、status=ACTIVE）才能交班。
 */
@Data
@Entity
@Table(name = "patrol_officer")
public class PatrolOfficer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "officer_code", nullable = false, unique = true, length = 50)
    private String officerCode;

    @Column(name = "officer_name", nullable = false, length = 50)
    private String officerName;

    /** LEADER=值班长，MEMBER=巡检员（只能配合走房，不能提交交班） */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /** ACTIVE / INACTIVE，停用后不再是本班值班长 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

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

    public boolean isActiveLeader() {
        return "LEADER".equalsIgnoreCase(role) && "ACTIVE".equalsIgnoreCase(status);
    }
}
