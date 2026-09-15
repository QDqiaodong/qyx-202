package com.example.shiproom.repository;

import com.example.shiproom.entity.RoomShipRelation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomShipRelationRepository extends JpaRepository<RoomShipRelation, Long> {

    Optional<RoomShipRelation> findByRoomIdAndShipId(Long roomId, Long shipId);

    List<RoomShipRelation> findByRoomId(Long roomId);

    List<RoomShipRelation> findByShipId(Long shipId);

    List<RoomShipRelation> findByStatus(String status);

    boolean existsByRoomIdAndShipId(Long roomId, Long shipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select relation from RoomShipRelation relation where relation.roomId = :roomId")
    List<RoomShipRelation> findByRoomIdForUpdate(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select relation from RoomShipRelation relation where relation.shipId = :shipId")
    List<RoomShipRelation> findByShipIdForUpdate(@Param("shipId") Long shipId);
}