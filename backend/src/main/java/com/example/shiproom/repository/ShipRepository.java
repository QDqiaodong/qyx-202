package com.example.shiproom.repository;

import com.example.shiproom.entity.Ship;
import org.springframework.data.jpa.repository.JpaRepository;
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
}