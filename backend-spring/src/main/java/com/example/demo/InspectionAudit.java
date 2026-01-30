package com.example.demo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audits")
public class InspectionAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;
    private String status;
    private boolean isDefective;
    private Double confidence;
    private LocalDateTime scanDate;

    public InspectionAudit() { this.scanDate = LocalDateTime.now(); }

    public InspectionAudit(String filename, String status, boolean isDefective, Double confidence) {
        this.filename = filename;
        this.status = status;
        this.isDefective = isDefective;
        this.confidence = confidence;
        this.scanDate = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public String getStatus() { return status; }
    public boolean isDefective() { return isDefective; }
    public Double getConfidence() { return confidence; }
    public LocalDateTime getScanDate() { return scanDate; }
}
