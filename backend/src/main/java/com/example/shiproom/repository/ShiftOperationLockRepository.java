package com.example.shiproom.repository;

import com.example.shiproom.entity.ShiftOperationLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShiftOperationLockRepository extends JpaRepository<ShiftOperationLock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from ShiftOperationLock lock where lock.id = :id")
    Optional<ShiftOperationLock> findByIdForUpdate(@Param("id") Long id);
}
