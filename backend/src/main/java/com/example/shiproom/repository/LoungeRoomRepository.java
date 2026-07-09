package com.example.shiproom.repository;

import com.example.shiproom.entity.LoungeRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoungeRoomRepository extends JpaRepository<LoungeRoom, Long> {

    Optional<LoungeRoom> findByRoomCode(String roomCode);

    List<LoungeRoom> findByStatus(String status);

    List<LoungeRoom> findByFloor(String floor);

    boolean existsByRoomCode(String roomCode);
}