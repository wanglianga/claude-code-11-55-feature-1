package com.group.carbon.common;

/** 平台全部业务枚举，集中定义避免散落。 */
public final class Types {
  private Types() {}

  public enum Role { ADMIN, ANALYST, AUDITOR, FINANCE }

  /** 批次状态：已上传 / 已核验 / 已驳回 / 冲突 / 被取代 / 已作废 */
  public enum BatchStatus { UPLOADED, VERIFIED, REJECTED, CONFLICT, SUPERSEDED, VOID }

  public enum RowStatus { ACTIVE, SUPERSEDED, VOID }

  /** 因子版本状态：草稿 / 已批准 / 被替换 / 已停用 */
  public enum FactorStatus { DRAFT, APPROVED, REPLACED, RETIRED }

  public enum RunStatus { RUNNING, COMPLETED, FAILED }

  /** OFFICIAL=正式运行；CANDIDATE=影响评估产生的候选运行 */
  public enum RunPurpose { OFFICIAL, CANDIDATE }

  /** OK=正常；PENDING_FACTOR=缺少适用批准因子，指标待核 */
  public enum ResultStatus { OK, PENDING_FACTOR }

  /** RECONCILE_FAILED 即“待核”状态：勾稽不平或存在待核指标 */
  public enum ReportStatus { DRAFT, PENDING_REVIEW, RECONCILE_FAILED, APPROVED, PUBLISHED }

  public enum VersionStatus { FROZEN, PUBLISHED, SUPERSEDED, RESTATED }

  public enum ReconcileStatus { OK, MISMATCH, PENDING_FACTOR }

  public enum TriggerType { SOURCE_SUPPLEMENT, SOURCE_REVISION, SOURCE_MISSING, FACTOR_REVISION }

  public enum ImpactStatus { OPEN, ACKNOWLEDGED, RESOLVED }

  public enum RestatementStatus { PROPOSED, ACCEPTED, REJECTED, RESOLVED }
}
