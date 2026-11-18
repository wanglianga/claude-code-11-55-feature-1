package com.group.carbon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用电账：工厂 × 计量期间的外购用电事实。
 * coveredKwh 维护已被有效声明覆盖的电量，剩余 = total - covered。
 */
@Entity
@Table(name = "electricity_ledgers")
public class ElectricityLedger {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(nullable = false)
  private String period;                 // YYYY-MM
  @Column(nullable = false)
  private String region;
  @Column(name = "total_kwh", nullable = false)
  private BigDecimal totalKwh;
  @Column(name = "covered_kwh", nullable = false)
  private BigDecimal coveredKwh = BigDecimal.ZERO;
  @Column(name = "source_batch_id")
  private Long sourceBatchId;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public BigDecimal getTotalKwh() { return totalKwh; }
  public void setTotalKwh(BigDecimal totalKwh) { this.totalKwh = totalKwh; }
  public BigDecimal getCoveredKwh() { return coveredKwh; }
  public void setCoveredKwh(BigDecimal coveredKwh) { this.coveredKwh = coveredKwh; }
  public Long getSourceBatchId() { return sourceBatchId; }
  public void setSourceBatchId(Long sourceBatchId) { this.sourceBatchId = sourceBatchId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
