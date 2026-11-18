package com.group.carbon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** API 请求体定义。 */
public final class Requests {
  private Requests() {}

  public record LoginReq(@NotBlank(message = "用户名不能为空") String username,
                         @NotBlank(message = "密码不能为空") String password) {}

  /** plantId 为空时取批次工厂；集团采购批次可按行归属不同工厂 */
  public record RowInput(Long plantId,
                         Long productLineId,
                         @NotBlank(message = "能源类型不能为空") String energyType,
                         @NotNull(message = "数量不能为空") BigDecimal quantity,
                         String voucherNo) {}

  public record CreateBatchReq(@NotNull(message = "工厂不能为空") Long plantId,
                               @NotBlank(message = "批次类别不能为空") String category,
                               @NotBlank(message = "期间不能为空") String period,
                               String batchNo,
                               @NotEmpty(message = "批次至少包含一行数据") List<RowInput> rows) {}

  public record VoidReq(@NotBlank(message = "作废原因不能为空") String reason) {}

  public record CreateFactorReq(@NotBlank(message = "因子编码不能为空") String code,
                                @NotBlank(message = "因子名称不能为空") String name,
                                @NotBlank(message = "能源类型不能为空") String energyType,
                                @NotBlank(message = "地域不能为空") String region) {}

  public record CreateVersionReq(@NotNull(message = "因子值不能为空") BigDecimal value,
                                 @NotBlank(message = "适用期起不能为空") String validFrom,
                                 String validTo) {}

  public record CreateRunReq(@NotBlank(message = "期间不能为空") String period) {}

  public record CreateReportReq(@NotBlank(message = "报告标题不能为空") String title,
                                @NotBlank(message = "期间不能为空") String period,
                                @NotNull(message = "必须选择核算运行") Long runId) {}

  public record FreezeReq(@NotNull(message = "必须选择核算运行") Long runId, String note) {}

  public record ReasonReq(String reason) {}

  // ---------------- 市场化电力声明 ----------------

  /** 凭证登记：序列号、发电/交付区间、区域、技术属性、电量与持有人 */
  public record RegisterCertificateReq(
      @NotBlank(message = "凭证序列号不能为空") String serialNo,
      @NotNull(message = "持有工厂不能为空") Long holderPlantId,
      @NotBlank(message = "交付区域不能为空") String region,
      @NotBlank(message = "技术属性不能为空") String technology,
      @NotBlank(message = "发电月份不能为空") String generationMonth,
      @NotBlank(message = "交付区间起不能为空") String deliveryFrom,
      @NotBlank(message = "交付区间止不能为空") String deliveryTo,
      @NotNull(message = "凭证电量不能为空") BigDecimal quantityKwh) {}

  /** 用电账登记：工厂 × 计量期间唯一 */
  public record LedgerReq(
      @NotNull(message = "工厂不能为空") Long plantId,
      @NotBlank(message = "计量期间不能为空") String period,
      @NotNull(message = "用电总量不能为空") BigDecimal totalKwh,
      String region,
      Long sourceBatchId) {}

  /**
   * 归属请求：
   * method=CERTIFICATE 时传 certificateId（逐张分配，支持部分拆分）；
   * method=POOL 时传 poolId（批次池扣减，成员逐张留痕）；
   * quantityKwh 为空表示用尽可匹配余额（逐张）或池余额。
   */
  public record AllocateReq(
      @NotBlank(message = "归属方式不能为空") String method,
      @NotNull(message = "用电账不能为空") Long ledgerId,
      Long certificateId,
      Long poolId,
      BigDecimal quantityKwh) {}

  public record PoolJoinReq(@NotNull(message = "凭证不能为空") Long certificateId) {}

  public record TransferReq(
      @NotNull(message = "受让工厂不能为空") Long toPlantId,
      @NotBlank(message = "转让说明不能为空") String reason) {}

  /** 重复主张裁决：FIRST=首张凭证有效；DUPLICATE=后传凭证有效 */
  public record ResolveDuplicateReq(
      @NotBlank(message = "裁决结果不能为空") String winner,
      String reason) {}
}
