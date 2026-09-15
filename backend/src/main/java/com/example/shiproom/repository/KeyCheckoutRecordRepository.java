package com.example.shiproom.repository;

import com.example.shiproom.entity.KeyCheckoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeyCheckoutRecordRepository extends JpaRepository<KeyCheckoutRecord, Long> {

    Optional<KeyCheckoutRecord> findByCheckoutBatch(String checkoutBatch);

    Optional<KeyCheckoutRecord> findByCheckoutBatchAndStatus(String checkoutBatch, String status);

    List<KeyCheckoutRecord> findByKeyId(Long keyId);

    List<KeyCheckoutRecord> findByRoomId(Long roomId);

    List<KeyCheckoutRecord> findByShipId(Long shipId);

    List<KeyCheckoutRecord> findByStatus(String status);

    List<KeyCheckoutRecord> findByKeyIdAndStatus(Long keyId, String status);
}
