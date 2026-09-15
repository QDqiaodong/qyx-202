package com.example.shiproom.repository;

import com.example.shiproom.entity.LoungeKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoungeKeyRepository extends JpaRepository<LoungeKey, Long> {

    Optional<LoungeKey> findByKeyCode(String keyCode);

    List<LoungeKey> findByRoomId(Long roomId);

    List<LoungeKey> findByStatus(String status);

    boolean existsByKeyCode(String keyCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select loungeKey from LoungeKey loungeKey where loungeKey.id = :id")
    Optional<LoungeKey> findByIdForUpdate(@Param("id") Long id);
}
