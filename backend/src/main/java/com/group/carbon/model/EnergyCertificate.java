package com.group.carbon.model;

import com.group.carbon.common.Types.CertificateStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 可再生电力属性凭证登记：
 * 序列号、发电/交付区间、交付区域、技术属性、注销状态与当前持有人。
 */
@Entity
@Table(name = "energy_certificates")
public class EnergyCertificate {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "serial_no", nullable = false, unique = true)
  private String serialNo;
  @Column(name = "holder_plant_id", nullable = false)
  private Long holderPlantId;
  @Column(nullable = false)
  private String region;
  @Column(nullable = false)
  private String technology;
  @Column(name = "generation_month", nullable = false)
  private String generationMonth;
  @Column(name = "delivery_from", nullable = false)
  private LocalDate deliveryFrom;
  @Column(name = "delivery_to", nullable = false)
  private LocalDate deliveryTo;
  @Column(name = "quantity_kwh", nullable = false)
  private BigDecimal quantityKwh;
  /** 重复竞争冻结的未决电量（不计入可声明余额） */
  @Column(name = "frozen_kwh", nullable = false)
  private BigDecimal frozenKwh = BigDecimal.ZERO;
  @Column(nullable = false)
  private boolean cancelled = false;
  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CertificateStatus status = CertificateStatus.ACTIVE;
  @Column(name = "invalid_reason")
  private String invalidReason;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getSerialNo() { return serialNo; }
  public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
  public Long getHolderPlantId() { return holderPlantId; }
  public void setHolderPlantId(Long holderPlantId) { this.holderPlantId = holderPlantId; }
  public String getRegion() { return region; }
  public void setRegion(String region) { this.region = region; }
  public String getTechnology() { return technology; }
  public void setTechnology(String technology) { this.technology = technology; }
  public String getGenerationMonth() { return generationMonth; }
  public void setGenerationMonth(String generationMonth) { this.generationMonth = generationMonth; }
  public LocalDate getDeliveryFrom() { return deliveryFrom; }
  public void setDeliveryFrom(LocalDate deliveryFrom) { this.deliveryFrom = deliveryFrom; }
  public LocalDate getDeliveryTo() { return deliveryTo; }
  public void setDeliveryTo(LocalDate deliveryTo) { this.deliveryTo = deliveryTo; }
  public BigDecimal getQuantityKwh() { return quantityKwh; }
  public void setQuantityKwh(BigDecimal quantityKwh) { this.quantityKwh = quantityKwh; }
  public BigDecimal getFrozenKwh() { return frozenKwh; }
  public void setFrozenKwh(BigDecimal frozenKwh) { this.frozenKwh = frozenKwh; }
  public boolean isCancelled() { return cancelled; }
  public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
  public LocalDateTime getCancelledAt() { return cancelledAt; }
  public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
  public CertificateStatus getStatus() { return status; }
  public void setStatus(CertificateStatus status) { this.status = status; }
  public String getInvalidReason() { return invalidReason; }
  public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
