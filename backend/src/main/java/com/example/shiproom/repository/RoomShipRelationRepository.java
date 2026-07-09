package com.example.shiproom.repository;

import com.example.shiproom.entity.RoomShipRelation;
import org.springframework.data.jpa.repository.JpaRepository;
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
}