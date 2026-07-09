package com.example.shiproom.repository;

import com.example.shiproom.entity.RelationChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RelationChangeLogRepository extends JpaRepository<RelationChangeLog, Long> {

    List<RelationChangeLog> findByDeviceId(Long deviceId);

    List<RelationChangeLog> findByRoomId(Long roomId);

    List<RelationChangeLog> findByShipId(Long shipId);

    List<RelationChangeLog> findByChangeType(String changeType);

    List<RelationChangeLog> findByChangeTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    List<RelationChangeLog> findByOperator(String operator);
}