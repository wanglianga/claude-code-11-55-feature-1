package com.group.carbon.model;

import com.group.carbon.common.Types.DuplicateStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 重复上传登记：同一凭证序列号被多个工厂上传时，
 * 首张之外的竞争主张在此登记并冻结未决部分，裁决前不得用于新声明。
 */
@Entity
@Table(name = "certificate_duplicate_uploads")
public class CertificateDuplicateUpload {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "serial_no", nullable = false)
  private String serialNo;
  /** 指向首张（规范）凭证 */
  @Column(name = "certificate_id", nullable = false)
  private Long certificateId;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "quantity_kwh", nullable = false)
  private BigDecimal quantityKwh;
  @Column(name = "contested_kwh", nullable = false)
  private BigDecimal contestedKwh;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DuplicateStatus status = DuplicateStatus.PENDING;
  /** FIRST_WON=首张有效（竞争量继续冻结/作废）；DUPLICATE_WON=后传凭证有效（首张释放对应额度） */
  private String resolution;
  private String reason;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
  @Column(name = "resolved_by")
  private String resolvedBy;
  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getSerialNo() { return serialNo; }
  public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
  public Long getCertificateId() { return certificateId; }
  public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public BigDecimal getQuantityKwh() { return quantityKwh; }
  public void setQuantityKwh(BigDecimal quantityKwh) { this.quantityKwh = quantityKwh; }
  public BigDecimal getContestedKwh() { return contestedKwh; }
  public void setContestedKwh(BigDecimal contestedKwh) { this.contestedKwh = contestedKwh; }
  public DuplicateStatus getStatus() { return status; }
  public void setStatus(DuplicateStatus status) { this.status = status; }
  public String getResolution() { return resolution; }
  public void setResolution(String resolution) { this.resolution = resolution; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public String getResolvedBy() { return resolvedBy; }
  public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
  public LocalDateTime getResolvedAt() { return resolvedAt; }
  public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
