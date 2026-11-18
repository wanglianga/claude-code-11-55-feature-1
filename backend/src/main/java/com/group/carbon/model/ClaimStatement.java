package com.group.carbon.model;

import com.group.carbon.common.Types.StatementStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 声明册：每个工厂 × 计量期间一行汇总。
 * matchedKwh   可匹配量（同区域 + 同适用期的有效声明覆盖）
 * unmatchedKwh 不可匹配量（跨区 / 错月 / 冻结未决，凭证不得以总量抵销）
 * residualKwh  剩余量（用电事实中未被任何有效声明覆盖的部分）
 */
@Entity
@Table(name = "claim_statements")
public class ClaimStatement {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "statement_no", nullable = false, unique = true)
  private String statementNo;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(nullable = false)
  private String period;
  @Column(nullable = false)
  private String region;
  @Column(name = "total_kwh", nullable = false)
  private BigDecimal totalKwh;
  @Column(name = "matched_kwh", nullable = false)
  private BigDecimal matchedKwh = BigDecimal.ZERO;
  @Column(name = "unmatched_kwh", nullable = false)
  private BigDecimal unmatchedKwh = BigDecimal.ZERO;
  @Column(name = "residual_kwh", nullable = false)
  private BigDecimal residualKwh = BigDecimal.ZERO;
  @Column(name = "duplicate_claim_kwh", nullable = false)
  private BigDecimal duplicateClaimKwh = BigDecimal.ZERO;
  @Column(name = "invalidated_chain_kwh", nullable = false)
  private BigDecimal invalidatedChainKwh = BigDecimal.ZERO;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StatementStatus status = StatementStatus.OPEN;
  @Column(name = "report_version_id")
  private Long reportVersionId;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getStatementNo() { return statementNo; }
  public void setStatementNo(String statementNo) { this.statementNo = statementNo; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public BigDecimal getTotalKwh() { return totalKwh; }
  public void setTotalKwh(BigDecimal totalKwh) { this.totalKwh = totalKwh; }
  public BigDecimal getMatchedKwh() { return matchedKwh; }
  public void setMatchedKwh(BigDecimal matchedKwh) { this.matchedKwh = matchedKwh; }
  public BigDecimal getUnmatchedKwh() { return unmatchedKwh; }
  public void setUnmatchedKwh(BigDecimal unmatchedKwh) { this.unmatchedKwh = unmatchedKwh; }
  public BigDecimal getResidualKwh() { return residualKwh; }
  public void setResidualKwh(BigDecimal residualKwh) { this.residualKwh = residualKwh; }
  public BigDecimal getDuplicateClaimKwh() { return duplicateClaimKwh; }
  public void setDuplicateClaimKwh(BigDecimal duplicateClaimKwh) { this.duplicateClaimKwh = duplicateClaimKwh; }
  public BigDecimal getInvalidatedChainKwh() { return invalidatedChainKwh; }
  public void setInvalidatedChainKwh(BigDecimal invalidatedChainKwh) { this.invalidatedChainKwh = invalidatedChainKwh; }
  public StatementStatus getStatus() { return status; }
  public void setStatus(StatementStatus status) { this.status = status; }
  public Long getReportVersionId() { return reportVersionId; }
  public void setReportVersionId(Long reportVersionId) { this.reportVersionId = reportVersionId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
