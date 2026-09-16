package com.example.shiproom.repository;

import com.example.shiproom.entity.LinenRecovery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinenRecoveryRepository extends JpaRepository<LinenRecovery, Long> {

    Optional<LinenRecovery> findByRecoveryNo(String recoveryNo);

    List<LinenRecovery> findByRoomIdOrderByIdDesc(Long roomId);

    List<LinenRecovery> findByDepartedShipIdOrderByIdDesc(Long shipId);

    /**
     * 该房本次换班批次（changeBatch 可能为 null，兼容老关联）下最近一份未作废回收单。
     */
    @Query("""
            select r from LinenRecovery r
            where r.roomId = :roomId
              and r.status <> 'VOID'
              and (
                    (:cycleBatch is not null and r.changeBatch = :cycleBatch)
                 or (:cycleBatch is null and r.changeBatch is null)
              )
            order by r.id desc
            """)
    List<LinenRecovery> findCycleSheets(@Param("roomId") Long roomId,
                                        @Param("cycleBatch") String cycleBatch);

    boolean existsByRecoveryNo(String recoveryNo);
}
