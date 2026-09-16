package com.example.shiproom.repository;

import com.example.shiproom.entity.FuelRefill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FuelRefillRepository extends JpaRepository<FuelRefill, Long> {

    Optional<FuelRefill> findByRefillNo(String refillNo);

    boolean existsByRefillNo(String refillNo);

    List<FuelRefill> findByGeneratorIdOrderByIdDesc(Long generatorId);

    List<FuelRefill> findByRefillDateOrderByIdDesc(LocalDate refillDate);

    List<FuelRefill> findByGeneratorIdAndRefillDateOrderByIdDesc(Long generatorId, LocalDate refillDate);

    /** 该台机这个自然日挂着的未复核加油（同机同日最多一条） */
    @Query("""
            select r from FuelRefill r
            where r.generatorId = :generatorId
              and r.refillDate = :refillDate
              and r.status = 'PENDING_REVIEW'
            order by r.id desc
            """)
    List<FuelRefill> findPending(@Param("generatorId") Long generatorId,
                                 @Param("refillDate") LocalDate refillDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FuelRefill r where r.id = :id")
    Optional<FuelRefill> findByIdForUpdate(@Param("id") Long id);
}
