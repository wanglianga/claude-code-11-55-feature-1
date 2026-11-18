package com.group.carbon.service;

import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.*;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import com.group.carbon.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 样例数据初始化（仅当库为空时执行）：
 * 工厂/产品线/用户、因子目录（含未批准版本以演示待核指标）、
 * 2025-05 已核验批次 → 正式运行 → 已发布报告；
 * 2025-06 批次（含未分摊行、缺因子行、集团采购批次）用于验收回放。
 */
@Component
public class SampleDataInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);

  private final UserRepo userRepo;
  private final PlantRepo plantRepo;
  private final ProductLineRepo lineRepo;
  private final FactorRepo factorRepo;
  private final FactorVersionRepo versionRepo;
  private final BatchService batchService;
  private final RunService runService;
  private final FactorService factorService;
  private final DisclosureService disclosureService;
  private final CertificateService certificateService;
  private final ClaimService claimService;

  public SampleDataInitializer(UserRepo userRepo, PlantRepo plantRepo, ProductLineRepo lineRepo,
                               FactorRepo factorRepo, FactorVersionRepo versionRepo,
                               BatchService batchService, RunService runService,
                               FactorService factorService, DisclosureService disclosureService,
                               CertificateService certificateService, ClaimService claimService) {
    this.userRepo = userRepo;
    this.plantRepo = plantRepo;
    this.lineRepo = lineRepo;
    this.factorRepo = factorRepo;
    this.versionRepo = versionRepo;
    this.batchService = batchService;
    this.runService = runService;
    this.factorService = factorService;
    this.disclosureService = disclosureService;
    this.certificateService = certificateService;
    this.claimService = claimService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepo.count() > 0) {
      log.info("样例数据已存在，跳过初始化");
      return;
    }
    log.info("开始初始化样例数据...");

    // ---------- 用户 ----------
    user("admin", "admin123", "平台管理员", Role.ADMIN);
    user("analyst", "analyst123", "分析员-小苏", Role.ANALYST);
    user("auditor", "auditor123", "审核员-老沈", Role.AUDITOR);
    user("finance", "finance123", "财务披露-老周", Role.FINANCE);

    // ---------- 工厂与产品线 ----------
    Plant sz = plant("SZ", "苏州工厂", "CN-EAST");
    Plant cd = plant("CD", "成都工厂", "CN-WEST");
    ProductLine l1 = line("L1", "精密结构件");
    ProductLine l2 = line("L2", "电池模组");

    // ---------- 因子目录 ----------
    Factor elecEast = factor("ELEC-CNEAST", "华东电网电力排放因子", "GRID_ELEC", "CN-EAST");
    approveVersion(elecEast, "0.5566", "2025-01-01", "2025-12-31");
    Factor elecWest = factor("ELEC-CNWEST", "西南电网电力排放因子", "GRID_ELEC", "CN-WEST");
    approveVersion(elecWest, "0.6100", "2025-01-01", "2025-12-31");
    Factor ngEast = factor("NG-CNEAST", "华东天然气排放因子", "NATURAL_GAS", "CN-EAST");
    approveVersion(ngEast, "2.1500", "2025-01-01", "2025-12-31");
    // 西南天然气因子仅有草稿：成都天然气指标将列为待核
    Factor ngWest = factor("NG-CNWEST", "西南天然气排放因子", "NATURAL_GAS", "CN-WEST");
    draftVersion(ngWest, "2.0800", "2025-01-01", "2025-12-31");
    Factor dieselEast = factor("DIESEL-CNEAST", "华东柴油排放因子", "DIESEL", "CN-EAST");
    approveVersion(dieselEast, "2.6800", "2025-01-01", "2025-12-31");
    Factor steelEast = factor("STEEL-CNEAST", "华东采购钢材排放因子", "PURCH_STEEL", "CN-EAST");
    approveVersion(steelEast, "2100.0", "2025-01-01", "2025-12-31");
    Factor steelWest = factor("STEEL-CNWEST", "西南采购钢材排放因子", "PURCH_STEEL", "CN-WEST");
    approveVersion(steelWest, "1950.0", "2025-01-01", "2025-12-31");

    // ---------- 2025-05 来源批次（全部已核验，因子齐备） ----------
    verify(batch("B-2025-05-ELEC-SZ", sz, "ELECTRICITY", "2025-05", List.of(
        row(l1, "GRID_ELEC", "680000", null),
        row(l2, "GRID_ELEC", "420000", null))));
    verify(batch("B-2025-05-FUEL-SZ", sz, "FUEL", "2025-05", List.of(
        row(l1, "NATURAL_GAS", "28000", null),
        row(l1, "DIESEL", "4000", null))));
    verify(batch("B-2025-05-ELEC-CD", cd, "ELECTRICITY", "2025-05", List.of(
        row(l1, "GRID_ELEC", "350000", null),
        row(l2, "GRID_ELEC", "150000", null))));
    verify(batch("B-2025-05-PROC-GP", sz, "PROCUREMENT", "2025-05", List.of(
        row(sz, l1, "PURCH_STEEL", "480", "V-9001"),
        row(cd, l1, "PURCH_STEEL", "260", "V-9002"))));

    // ---------- 2025-05 正式运行 → 冻结 → 审批 → 发布 ----------
    CalcRun mayRun = runService.createOfficialRun("2025-05", "analyst");
    runService.execute(mayRun.getId());
    Report mayReport = disclosureService.createReport("analyst",
        "2025年5月集团碳排放披露报告", "2025-05", mayRun.getId());

    // ---------- 2025-05 市场化电力声明（发布前建链，发布时随版本固化） ----------
    seedMayClaims(sz, cd);

    disclosureService.approve(mayReport.getId(), "finance");
    disclosureService.publish(mayReport.getId(), "finance");
    log.info("2025-05 报告已发布: {}", mayReport.getCode());

    // ---------- 2025-06 来源批次（用于验收回放） ----------
    // 苏州电力：含一行未分摊到产品线的厂区公共用电 → 勾稽差异演示
    verify(batch("B-2025-06-ELEC-SZ", sz, "ELECTRICITY", "2025-06", List.of(
        row(l1, "GRID_ELEC", "700000", null),
        row(l2, "GRID_ELEC", "500000", null),
        row(null, "GRID_ELEC", "100000", null))));
    verify(batch("B-2025-06-FUEL-SZ", sz, "FUEL", "2025-06", List.of(
        row(l1, "NATURAL_GAS", "30000", null),
        row(l2, "NATURAL_GAS", "18000", null))));
    verify(batch("B-2025-06-ELEC-CD", cd, "ELECTRICITY", "2025-06", List.of(
        row(l1, "GRID_ELEC", "400000", null))));
    // 成都天然气：西南天然气因子未批准 → 待核指标演示
    verify(batch("B-2025-06-FUEL-CD", cd, "FUEL", "2025-06", List.of(
        row(l2, "NATURAL_GAS", "12000", null))));
    // 集团采购批次：两家工厂共享，用于并发修订冲突演示
    verify(batch("B-2025-06-PROC-GP", sz, "PROCUREMENT", "2025-06", List.of(
        row(sz, l1, "PURCH_STEEL", "500", "V-1001"),
        row(cd, l1, "PURCH_STEEL", "300", "V-1002"))));

    // ---------- 2025-06 市场化电力声明种子（开放状态，供验收回放继续归属/裁决/撤销） ----------
    seedJuneClaims(sz, cd);

    log.info("样例数据初始化完成");
  }

  /** 2025-05 声明：逐张分配（含部分拆分与跨区错配），随报告发布固化。 */
  private void seedMayClaims(Plant sz, Plant cd) {
    claimService.syncLedgersFromBatches("2025-05", "analyst");
    ElectricityLedger szMay = ledger(sz, "2025-05");
    ElectricityLedger cdMay = ledger(cd, "2025-05");

    EnergyCertificate c1 = cert("REC-SZ-001", sz, "CN-EAST", "SOLAR", "2025-05",
        "2025-05-01", "2025-05-31", "600000");
    EnergyCertificate c2 = cert("REC-SZ-002", sz, "CN-EAST", "WIND", "2025-05",
        "2025-05-01", "2025-05-31", "300000");
    EnergyCertificate c3 = cert("REC-CD-001", cd, "CN-WEST", "HYDRO", "2025-05",
        "2025-05-01", "2025-05-31", "500000");
    EnergyCertificate cx = cert("REC-XREG-001", sz, "CN-WEST", "SOLAR", "2025-05",
        "2025-05-01", "2025-05-31", "100000");

    claimService.allocate("analyst", new AllocateReq("CERTIFICATE", szMay.getId(), c1.getId(), null, null));
    claimService.allocate("analyst", new AllocateReq("CERTIFICATE", szMay.getId(), c2.getId(), null, new BigDecimal("300000")));
    claimService.allocate("analyst", new AllocateReq("CERTIFICATE", cdMay.getId(), c3.getId(), null, null));
    // 跨区凭证：只登记不可匹配量，不得抵销
    claimService.allocate("analyst", new AllocateReq("CERTIFICATE", szMay.getId(), cx.getId(), null, new BigDecimal("100000")));
  }

  /** 2025-06 声明：批次池（两张同属性凭证）、逐张分配、月度错配凭证、重复上传冻结。 */
  private void seedJuneClaims(Plant sz, Plant cd) {
    claimService.syncLedgersFromBatches("2025-06", "analyst");
    ElectricityLedger szJun = ledger(sz, "2025-06");
    ElectricityLedger cdJun = ledger(cd, "2025-06");

    EnergyCertificate p1 = cert("REC-SZ-101", sz, "CN-EAST", "SOLAR", "2025-06",
        "2025-06-01", "2025-06-30", "400000");
    EnergyCertificate p2 = cert("REC-SZ-102", sz, "CN-EAST", "SOLAR", "2025-06",
        "2025-06-01", "2025-06-30", "200000");
    claimService.joinPool("analyst", p1.getId());
    claimService.joinPool("analyst", p2.getId());

    EnergyCertificate cd1 = cert("REC-CD-101", cd, "CN-WEST", "HYDRO", "2025-06",
        "2025-06-01", "2025-06-30", "150000");
    claimService.allocate("analyst", new AllocateReq("CERTIFICATE", cdJun.getId(), cd1.getId(), null, null));

    // 月度错配凭证：发电月份 2025-04、交付区间不覆盖 6 月
    cert("REC-MONTH-001", sz, "CN-EAST", "SOLAR", "2025-04",
        "2025-04-01", "2025-04-30", "80000");

    // 重复上传：同一序列号被成都工厂再次上传 → 首张凭证争议额度冻结，等待裁决
    cert("REC-DUP-001", sz, "CN-EAST", "SOLAR", "2025-06",
        "2025-06-01", "2025-06-30", "120000");
    certificateService.register("analyst", new RegisterCertificateReq(
        "REC-DUP-001", cd.getId(), "CN-EAST", "SOLAR", "2025-06",
        "2025-06-01", "2025-06-30", new BigDecimal("120000")));
  }

  private ElectricityLedger ledger(Plant plant, String period) {
    return claimService.listLedgers(period, plant.getId()).stream()
        .findFirst().orElseThrow(() -> new IllegalStateException("用电账未生成: " + plant.getCode() + " " + period));
  }

  private EnergyCertificate cert(String serial, Plant holder, String region, String technology,
                                 String genMonth, String deliveryFrom, String deliveryTo, String qtyKwh) {
    var res = certificateService.register("analyst", new RegisterCertificateReq(
        serial, holder.getId(), region, technology, genMonth, deliveryFrom, deliveryTo, new BigDecimal(qtyKwh)));
    return (EnergyCertificate) res.get("certificate");
  }

  // ---------- 辅助 ----------

  private void user(String username, String password, String displayName, Role role) {
    User u = new User();
    u.setUsername(username);
    u.setPasswordHash(AuthService.hashPassword(username, password));
    u.setDisplayName(displayName);
    u.setRole(role);
    userRepo.save(u);
  }

  private Plant plant(String code, String name, String region) {
    Plant p = new Plant();
    p.setCode(code);
    p.setName(name);
    p.setRegion(region);
    return plantRepo.save(p);
  }

  private ProductLine line(String code, String name) {
    ProductLine l = new ProductLine();
    l.setCode(code);
    l.setName(name);
    return lineRepo.save(l);
  }

  private Factor factor(String code, String name, String energyType, String region) {
    return factorService.createFactor("admin", new CreateFactorReq(code, name, energyType, region));
  }

  private void approveVersion(Factor f, String value, String from, String to) {
    FactorVersion v = factorService.createVersion(f.getId(), "analyst",
        new CreateVersionReq(new BigDecimal(value), from, to));
    factorService.approve(v.getId(), "auditor");
  }

  private void draftVersion(Factor f, String value, String from, String to) {
    factorService.createVersion(f.getId(), "analyst",
        new CreateVersionReq(new BigDecimal(value), from, to));
  }

  private RowInput row(ProductLine line, String energyType, String quantity, String voucher) {
    return new RowInput(null, line != null ? line.getId() : null, energyType,
        new BigDecimal(quantity), voucher);
  }

  private RowInput row(Plant plant, ProductLine line, String energyType, String quantity, String voucher) {
    return new RowInput(plant.getId(), line != null ? line.getId() : null, energyType,
        new BigDecimal(quantity), voucher);
  }

  private SourceBatch batch(String batchNo, Plant plant, String category, String period, List<RowInput> rows) {
    return batchService.create("analyst",
        new CreateBatchReq(plant.getId(), category, period, batchNo, rows));
  }

  private void verify(SourceBatch batch) {
    batchService.verify(batch.getId(), "auditor");
  }
}
