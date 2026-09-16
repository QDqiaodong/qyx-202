package com.example.shiproom.repository;

import com.example.shiproom.entity.PatrolOfficer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatrolOfficerRepository extends JpaRepository<PatrolOfficer, Long> {

    Optional<PatrolOfficer> findByOfficerCode(String officerCode);

    boolean existsByOfficerCode(String officerCode);
}
