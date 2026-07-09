package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ship")
public class Ship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ship_code", nullable = false, unique = true, length = 50)
    private String shipCode;

    @Column(name = "ship_name", nullable = false, length = 100)
    private String shipName;

    @Column(name = "ship_type", length = 50)
    private String shipType;

    @Column(name = "dock_code", length = 50)
    private String dockCode;

    @Column(name = "status", length = 20)
    private String status;

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