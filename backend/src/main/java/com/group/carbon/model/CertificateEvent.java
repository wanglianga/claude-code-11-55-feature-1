package com.group.carbon.model;

import com.group.carbon.common.Types.CertEventType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 凭证持有人/状态变更留痕：登记、重复冻结、注销、撤销、转让。 */
@Entity
@Table(name = "certificate_events")
public class CertificateEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "certificate_id", nullable = false)
  private Long certificateId;
  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private CertEventType eventType;
  @Column(name = "from_plant_id")
  private Long fromPlantId;
  @Column(name = "to_plant_id")
  private Long toPlantId;
  private String detail;
  @Column(nullable = false)
  private String operator;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public CertificateEvent() {}

  public CertificateEvent(Long certificateId, CertEventType eventType, Long fromPlantId,
                          Long toPlantId, String detail, String operator) {
    this.certificateId = certificateId;
    this.eventType = eventType;
    this.fromPlantId = fromPlantId;
    this.toPlantId = toPlantId;
    this.detail = detail;
    this.operator = operator;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getCertificateId() { return certificateId; }
  public void setCertificateId(Long certificateId) { this.certificateId = certificateId; }
  public CertEventType getEventType() { return eventType; }
  public void setEventType(CertEventType eventType) { this.eventType = eventType; }
  public Long getFromPlantId() { return fromPlantId; }
  public void setFromPlantId(Long fromPlantId) { this.fromPlantId = fromPlantId; }
  public Long getToPlantId() { return toPlantId; }
  public void setToPlantId(Long toPlantId) { this.toPlantId = toPlantId; }
  public String getDetail() { return detail; }
  public void setDetail(String detail) { this.detail = detail; }
  public String getOperator() { return operator; }
  public void setOperator(String operator) { this.operator = operator; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
