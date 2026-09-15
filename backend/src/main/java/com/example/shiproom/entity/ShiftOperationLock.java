package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "shift_operation_lock")
public class ShiftOperationLock {

    @Id
    @Column(name = "id")
    private Long id = 1L;

    @Column(name = "lock_name", nullable = false, length = 50)
    private String lockName = "RELATION_SHIFT_LOCK";

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
