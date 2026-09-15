package com.example.shiproom.repository;

import com.example.shiproom.entity.LoungeRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoungeRoomRepository extends JpaRepository<LoungeRoom, Long> {

    Optional<LoungeRoom> findByRoomCode(String roomCode);

    List<LoungeRoom> findByStatus(String status);

    List<LoungeRoom> findByFloor(String floor);

    boolean existsByRoomCode(String roomCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from LoungeRoom room where room.id = :id")
    java.util.Optional<LoungeRoom> findByIdForUpdate(@Param("id") Long id);
}