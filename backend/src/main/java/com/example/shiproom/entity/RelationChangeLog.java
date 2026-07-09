package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "relation_change_log")
public class RelationChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_code", length = 50)
    private String deviceCode;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "room_code", length = 50)
    private String roomCode;

    @Column(name = "ship_id")
    private Long shipId;

    @Column(name = "ship_code", length = 50)
    private String shipCode;

    @Column(name = "old_room_id")
    private Long oldRoomId;

    @Column(name = "old_room_code", length = 50)
    private String oldRoomCode;

    @Column(name = "old_ship_id")
    private Long oldShipId;

    @Column(name = "old_ship_code", length = 50)
    private String oldShipCode;

    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "change_time", nullable = false)
    private LocalDateTime changeTime;

    @PrePersist
    protected void onCreate() {
        changeTime = LocalDateTime.now();
    }
}