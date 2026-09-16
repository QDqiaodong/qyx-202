package com.example.shiproom.repository;

import com.example.shiproom.entity.PatrolWindow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatrolWindowRepository extends JpaRepository<PatrolWindow, Long> {

    Optional<PatrolWindow> findByWindowCode(String windowCode);

    List<PatrolWindow> findByShipIdOrderByStartTimeAsc(Long shipId);

    boolean existsByWindowCode(String windowCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select window from PatrolWindow window where window.id = :id")
    Optional<PatrolWindow> findByIdForUpdate(@Param("id") Long id);
}
