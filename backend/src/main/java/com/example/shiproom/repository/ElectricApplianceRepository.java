package com.example.shiproom.repository;

import com.example.shiproom.entity.ElectricAppliance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appliance from ElectricAppliance appliance where appliance.roomId = :roomId")
    List<ElectricAppliance> findByRoomIdForUpdate(@Param("roomId") Long roomId);

    /**
     * 已挂在某间房的全部电器功率合计；房间一台电器都没有时返回 null（按 0 处理）。
     * 合计只认 electric_appliance 表里 room_id 指向该房的行，保证跟逐台加总对得上。
     */
    @Query("select coalesce(sum(appliance.power), 0) from ElectricAppliance appliance where appliance.roomId = :roomId")
    java.math.BigDecimal sumPowerByRoomId(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appliance from ElectricAppliance appliance where appliance.shipId = :shipId")
    List<ElectricAppliance> findByShipIdForUpdate(@Param("shipId") Long shipId);
}