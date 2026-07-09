package com.example.shiproom.repository;

import com.example.shiproom.entity.ElectricAppliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElectricApplianceRepository extends JpaRepository<ElectricAppliance, Long> {

    Optional<ElectricAppliance> findByDeviceCode(String deviceCode);

    List<ElectricAppliance> findByRoomId(Long roomId);

    List<ElectricAppliance> findByShipId(Long shipId);

    List<ElectricAppliance> findByRoomIdAndShipId(Long roomId, Long shipId);

    List<ElectricAppliance> findByApplianceType(String applianceType);

    List<ElectricAppliance> findByStatus(String status);

    boolean existsByDeviceCode(String deviceCode);
}