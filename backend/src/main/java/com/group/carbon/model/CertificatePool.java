package com.group.carbon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可互换批次池：同区域 + 同发电月份 + 同技术属性的凭证可混池。
 * 混池规则由唯一键 (region, generation_month, technology) 严格限定；
 * 成员明细见 CertificatePoolMember，扣减可逐张解释。
 */
@Entity
@Table(name = "certificate_pools")
public class CertificatePool {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "pool_no", nullable = false, unique = true)
  private String poolNo;
  @Column(nullable = false)
  private String region;
  @Column(name = "generation_month", nullable = false)
  private String generationMonth;
  @Column(nullable = false)
  private String technology;
  @Column(name = "total_kwh", nullable = false)
  private BigDecimal totalKwh = BigDecimal.ZERO;
  @Column(name = "available_kwh", nullable = false)
  private BigDecimal availableKwh = BigDecimal.ZERO;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getPoolNo() { return poolNo; }
  public void setPoolNo(String poolNo) { this.poolNo = poolNo; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getGenerationMonth() { return generationMonth; }
  public void setGenerationMonth(String generationMonth) { this.generationMonth = generationMonth; }
  public String getTechnology() { return technology; }
  public void setTechnology(String technology) { this.technology = technology; }
  public BigDecimal getTotalKwh() { return totalKwh; }
  public void setTotalKwh(BigDecimal totalKwh) { this.totalKwh = totalKwh; }
  public BigDecimal getAvailableKwh() { return availableKwh; }
  public void setAvailableKwh(BigDecimal availableKwh) { this.availableKwh = availableKwh; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
