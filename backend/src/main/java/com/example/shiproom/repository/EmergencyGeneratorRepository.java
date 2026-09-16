package com.example.shiproom.repository;

import com.example.shiproom.entity.EmergencyGenerator;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmergencyGeneratorRepository extends JpaRepository<EmergencyGenerator, Long> {

    Optional<EmergencyGenerator> findByGenCode(String genCode);

    boolean existsByGenCode(String genCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from EmergencyGenerator g where g.id = :id")
    Optional<EmergencyGenerator> findByIdForUpdate(@Param("id") Long id);
}
