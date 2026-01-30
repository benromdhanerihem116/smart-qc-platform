package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<InspectionAudit, Long> {
    List<InspectionAudit> findTop10ByOrderByScanDateDesc();
}