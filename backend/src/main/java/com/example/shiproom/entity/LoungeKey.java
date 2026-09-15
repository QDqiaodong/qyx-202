package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "lounge_key")
public class LoungeKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_code", nullable = false, unique = true, length = 50)
    private String keyCode;

    @Column(name = "key_name", nullable = false, length = 100)
    private String keyName;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

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
