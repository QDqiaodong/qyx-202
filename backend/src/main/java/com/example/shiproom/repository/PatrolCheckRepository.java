package com.example.shiproom.repository;

import com.example.shiproom.entity.PatrolCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatrolCheckRepository extends JpaRepository<PatrolCheck, Long> {

    List<PatrolCheck> findByHandoverIdOrderBySeqAsc(Long handoverId);

    List<PatrolCheck> findByWindowIdOrderBySeqAsc(Long windowId);

    List<PatrolCheck> findByShipIdOrderByCheckTimeDesc(Long shipId);
}
