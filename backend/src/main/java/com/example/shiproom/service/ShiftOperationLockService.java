package com.example.shiproom.service;

import com.example.shiproom.entity.ShiftOperationLock;
import com.example.shiproom.repository.ShiftOperationLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ShiftOperationLockService {

    private static final Long LOCK_ID = 1L;

    private final ShiftOperationLockRepository repository;

    public ShiftOperationLockService(ShiftOperationLockRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ShiftOperationLock lock() {
        ShiftOperationLock lock = repository.findByIdForUpdate(LOCK_ID).orElse(null);
        if (lock == null) {
            lock = new ShiftOperationLock();
            repository.saveAndFlush(lock);
            lock = repository.findByIdForUpdate(LOCK_ID)
                    .orElseThrow(() -> new IllegalStateException("换班锁初始化失败"));
        }
        lock.setUpdateTime(LocalDateTime.now());
        return repository.save(lock);
    }
}
