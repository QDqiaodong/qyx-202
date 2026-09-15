package com.example.shiproom.repository;

import com.example.shiproom.entity.Ship;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipRepository extends JpaRepository<Ship, Long> {

    Optional<Ship> findByShipCode(String shipCode);

    List<Ship> findByStatus(String status);

    List<Ship> findByDockCode(String dockCode);

    List<Ship> findByShipType(String shipType);

    boolean existsByShipCode(String shipCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ship from Ship ship where ship.id = :id")
    java.util.Optional<Ship> findByIdForUpdate(@Param("id") Long id);
}