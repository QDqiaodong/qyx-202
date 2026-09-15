package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "key_occupancy")
public class KeyOccupancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false, unique = true)
    private Long keyId;

    @Column(name = "key_code", nullable = false, length = 50)
    private String keyCode;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "room_code", nullable = false, length = 50)
    private String roomCode;

    @Column(name = "ship_id", nullable = false)
    private Long shipId;

    @Column(name = "ship_code", nullable = false, length = 50)
    private String shipCode;

    @Column(name = "holder_name", nullable = false, length = 50)
    private String holderName;

    @Column(name = "checkout_batch", nullable = false, length = 64)
    private String checkoutBatch;

    @Column(name = "checkout_time", nullable = false)
    private LocalDateTime checkoutTime;

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
