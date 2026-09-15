package com.example.shiproom.repository;

import com.example.shiproom.entity.KeyOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeyOccupancyRepository extends JpaRepository<KeyOccupancy, Long> {

    Optional<KeyOccupancy> findByKeyId(Long keyId);

    List<KeyOccupancy> findByRoomId(Long roomId);

    List<KeyOccupancy> findByShipId(Long shipId);

    List<KeyOccupancy> findByCheckoutBatch(String checkoutBatch);

    boolean existsByKeyId(Long keyId);
}
