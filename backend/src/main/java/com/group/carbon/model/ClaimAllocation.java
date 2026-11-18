package com.group.carbon.model;

import com.group.carbon.common.Types.ClaimMethod;
import com.group.carbon.common.Types.MatchStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 声明分配（凭证链的边）：
 * 逐张分配与批次池扣减共用同一张表，每次归属都精确留痕；
 * 部分拆分产生同一凭证的多条边；凭证事后失效只把 chainValid 置 FALSE，链本身保留。
 */
@Entity
@Table(name = "claim_allocations")
public class ClaimAllocation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "ledger_id", nullable = false)
  private Long ledgerId;
  @Column(name = "certificate_id", nullable = false)
  private Long certificateId;
  @Column(name = "pool_id")
  private Long poolId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClaimMethod method;
  @Column(name = "allocated_kwh", nullable = false)
  private BigDecimal allocatedKwh;
  @Enumerated(EnumType.STRING)
  @Column(name = "match_status", nullable = false)
  private MatchStatus matchStatus;
  @Column(name = "generation_month", nullable = false)
  private String generationMonth;
  @Column(nullable = false)
  private String region;
  @Column(nullable = false)
  private String technology;
  @Column(name = "chain_valid", nullable = false)
  private boolean chainValid = true;
  @Column(name = "invalidated_at")
  private LocalDateTime invalidatedAt;
  @Column(name = "invalidated_reason")
  private String invalidatedReason;
  @Column(name = "report_version_id")
  private Long reportVersionId;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getLedgerId() { return ledgerId; }
  public void setLedgerId(Long ledgerId) { this.ledgerId = ledgerId; }
  public Long getCertificateId() { return certificateId; }
  public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
  public Long getPoolId() { return poolId; }
  public void setPoolId(Long poolId) { this.poolId = poolId; }
  public ClaimMethod getMethod() { return method; }
  public void setMethod(ClaimMethod method) { this.method = method; }
  public BigDecimal getAllocatedKwh() { return allocatedKwh; }
  public void setAllocatedKwh(BigDecimal allocatedKwh) { this.allocatedKwh = allocatedKwh; }
  public MatchStatus getMatchStatus() { return matchStatus; }
  public void setMatchStatus(MatchStatus matchStatus) { this.matchStatus = matchStatus; }
  public String getGenerationMonth() { return generationMonth; }
  public void setGenerationMonth(String generationMonth) { this.generationMonth = generationMonth; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getTechnology() { return technology; }
  public void setTechnology(String technology) { this.technology = technology; }
  public boolean isChainValid() { return chainValid; }
  public void setChainValid(boolean chainValid) { this.chainValid = chainValid; }
  public LocalDateTime getInvalidatedAt() { return invalidatedAt; }
  public void setInvalidatedAt(LocalDateTime invalidatedAt) { this.invalidatedAt = invalidatedAt; }
  public String getInvalidatedReason() { return invalidatedReason; }
  public void setInvalidatedReason(String invalidatedReason) { this.invalidatedReason = invalidatedReason; }
  public Long getReportVersionId() { return reportVersionId; }
  public void setReportVersionId(Long reportVersionId) { this.reportVersionId = reportVersionId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
