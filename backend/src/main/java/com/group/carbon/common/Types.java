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

  // ---------------- 市场化电力声明（凭证逐期归属） ----------------

  /** 凭证状态：有效 / 冻结未决（重复竞争） / 已撤销 / 已转让 */
  public enum CertificateStatus { ACTIVE, FROZEN, REVOKED, TRANSFERRED }

  /** 凭证事件：登记 / 重复冻结 / 注销 / 撤销 / 转让 / 入池 */
  public enum CertEventType { REGISTER, DUPLICATE_FROZEN, CANCEL, REVOKE, TRANSFER, POOL_JOIN }

  /** 归属方式：逐张分配 / 批次池扣减（两者均为一等能力） */
  public enum ClaimMethod { CERTIFICATE, POOL }

  /** 匹配结果：匹配 / 区域错配 / 期间错配 / 冻结未决 */
  public enum MatchStatus { MATCHED, REGION_MISMATCH, PERIOD_MISMATCH, FROZEN_PENDING }

  /** 声明册状态：开放（可继续归属）/ 已随报告发布 / 被新版本取代 */
  public enum StatementStatus { OPEN, PUBLISHED, SUPERSEDED }

  /** 重复上传裁决状态：未决 / 已裁决 */
  public enum DuplicateStatus { PENDING, RESOLVED }
}
