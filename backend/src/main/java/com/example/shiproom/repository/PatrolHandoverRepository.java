package com.example.shiproom.repository;

import com.example.shiproom.entity.PatrolHandover;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatrolHandoverRepository extends JpaRepository<PatrolHandover, Long> {

    Optional<PatrolHandover> findByHandoverBatch(String handoverBatch);

    /** 一条船在一个窗口最多一条 HANDED，由 handedUniqueKey 唯一约束在数据库兜底 */
    Optional<PatrolHandover> findByHandedUniqueKey(String handedUniqueKey);

    List<PatrolHandover> findByWindowIdOrderByIdAsc(Long windowId);

    List<PatrolHandover> findByShipIdOrderByIdDesc(Long shipId);

    List<PatrolHandover> findByWindowIdAndShipIdOrderByIdAsc(Long windowId, Long shipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select handover from PatrolHandover handover where handover.handedUniqueKey = :key")
    Optional<PatrolHandover> findByHandedUniqueKeyForUpdate(@Param("key") String key);
}
