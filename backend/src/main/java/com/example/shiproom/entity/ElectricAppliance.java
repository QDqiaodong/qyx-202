package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "electric_appliance")
public class ElectricAppliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, unique = true, length = 50)
    private String deviceCode;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "power", nullable = false, precision = 10, scale = 2)
    private BigDecimal power;

    @Column(name = "appliance_type", nullable = false, length = 50)
    private String applianceType;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "ship_id")
    private Long shipId;

    @Column(name = "last_change_batch", length = 64)
    private String lastChangeBatch;

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