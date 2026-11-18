package com.group.carbon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 池成员明细：凭证入池量、已扣减量、冻结量。
 * 池模式汇总扣减的同时保留每张凭证的成员账，保证混池可审计、逐张可解释。
 */
@Entity
@Table(name = "certificate_pool_members")
public class CertificatePoolMember {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "pool_id", nullable = false)
  private Long poolId;
  @Column(name = "certificate_id", nullable = false)
  private Long certificateId;
  @Column(name = "joined_kwh", nullable = false)
  private BigDecimal joinedKwh;
  @Column(name = "used_kwh", nullable = false)
  private BigDecimal usedKwh = BigDecimal.ZERO;
  @Column(name = "frozen_kwh", nullable = false)
  private BigDecimal frozenKwh = BigDecimal.ZERO;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getPoolId() { return poolId; }
  public void setPoolId(Long poolId) { this.poolId = poolId; }
  public Long getCertificateId() { return certificateId; }
  public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
  public BigDecimal getJoinedKwh() { return joinedKwh; }
  public void setJoinedKwh(BigDecimal joinedKwh) { this.joinedKwh = joinedKwh; }
  public BigDecimal getUsedKwh() { return usedKwh; }
  public void setUsedKwh(BigDecimal usedKwh) { this.usedKwh = usedKwh; }
  public BigDecimal getFrozenKwh() { return frozenKwh; }
  public void setFrozenKwh(BigDecimal frozenKwh) { this.frozenKwh = frozenKwh; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
