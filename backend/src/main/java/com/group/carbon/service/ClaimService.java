package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.Types.*;
import com.group.carbon.dto.Requests.AllocateReq;
import com.group.carbon.dto.Requests.LedgerReq;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 电力属性凭证逐期归属引擎（声明册核心）。
 *
 * 两种一等归属方式，共用同一套不重复覆盖控制：
 * - CERTIFICATE 逐张分配：按凭证序列号精确归属，支持部分拆分，凭证级悲观锁；
 * - POOL 批次池扣减：同区域+同发电月份+同技术属性的凭证混池，按成员逐张扣减并保留成员明细。
 *
 * 硬规则：
 * - 非同区域或非同适用期的凭证只记录为不可匹配量，不得以总量抵销覆盖；
 * - 凭证可声明余额 = 凭证电量 − 已有效匹配量 − 冻结未决量，任何分配不得超出；
 * - 凭证撤销/转让后新声明停用，既有链标记失效但保留（已发布报告可追溯）；
 * - 声明册恒等式：total = matched + residual；unmatched（不可匹配凭证量）单独列示，
 *   不抵减剩余用电（非同区域/非同适用期凭证不得以总量抵销）。
 */
@Service
public class ClaimService {
  private final EnergyCertificateRepo certRepo;
  private final ElectricityLedgerRepo ledgerRepo;
  private final CertificatePoolRepo poolRepo;
  private final CertificatePoolMemberRepo memberRepo;
  private final ClaimAllocationRepo allocationRepo;
  private final ClaimStatementRepo statementRepo;
  private final CertificateDuplicateRepo duplicateRepo;
  private final CertificateEventRepo eventRepo;
  private final PlantRepo plantRepo;
  private final SourceBatchRepo batchRepo;
  private final SourceRowRepo rowRepo;

  public ClaimService(EnergyCertificateRepo certRepo, ElectricityLedgerRepo ledgerRepo,
                      CertificatePoolRepo poolRepo, CertificatePoolMemberRepo memberRepo,
                      ClaimAllocationRepo allocationRepo, ClaimStatementRepo statementRepo,
                      CertificateDuplicateRepo duplicateRepo, CertificateEventRepo eventRepo,
                      PlantRepo plantRepo, SourceBatchRepo batchRepo, SourceRowRepo rowRepo) {
    this.certRepo = certRepo;
    this.ledgerRepo = ledgerRepo;
    this.poolRepo = poolRepo;
    this.memberRepo = memberRepo;
    this.allocationRepo = allocationRepo;
    this.statementRepo = statementRepo;
    this.duplicateRepo = duplicateRepo;
    this.eventRepo = eventRepo;
    this.plantRepo = plantRepo;
    this.rowRepo = rowRepo;
    this.batchRepo = batchRepo;
  }

  // ================= 用电账 =================

  @Transactional
  public ElectricityLedger createLedger(String operator, LedgerReq req) {
    RunService.validatePeriod(req.period());
    Plant plant = plantRepo.findById(req.plantId())
        .orElseThrow(() -> BizException.badRequest("工厂不存在: " + req.plantId()));
    if (req.totalKwh() == null || req.totalKwh().compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.badRequest("用电总量必须为正数");
    }
    if (ledgerRepo.findByPlantIdAndPeriod(plant.getId(), req.period()).isPresent()) {
      throw BizException.conflict("工厂 " + plant.getName() + " 在 " + req.period() + " 已存在用电账，每工厂每期间唯一");
    }
    if (req.sourceBatchId() != null
        && batchRepo.findById(req.sourceBatchId()).map(b -> !"ELECTRICITY".equals(b.getCategory())).orElse(false)) {
      throw BizException.badRequest("关联批次必须是电力类别批次");
    }
    ElectricityLedger ledger = new ElectricityLedger();
    ledger.setPlantId(plant.getId());
    ledger.setPeriod(req.period());
    ledger.setRegion(req.region() != null && !req.region().isBlank() ? req.region() : plant.getRegion());
    ledger.setTotalKwh(req.totalKwh());
    ledger.setSourceBatchId(req.sourceBatchId());
    ledger.setCreatedBy(operator);
    ledgerRepo.save(ledger);
    getOrCreateStatement(ledger, operator);
    return ledger;
  }

  /** 从已核验电力批次按工厂汇总生成用电账（已存在的工厂期间跳过）。 */
  @Transactional
  public List<ElectricityLedger> syncLedgersFromBatches(String period, String operator) {
    RunService.validatePeriod(period);
    Map<Long, BigDecimal> byPlant = new LinkedHashMap<>();
    Map<Long, Long> batchRef = new HashMap<>();
    for (SourceBatch b : batchRepo.findByPeriodOrderByIdDesc(period)) {
      if (b.getCategory() != null && b.getCategory().equals("ELECTRICITY")
          && b.getStatus() == BatchStatus.VERIFIED) {
        for (SourceRow r : rowRepo.findByBatchIdOrderByRowNo(b.getId())) {
          if (r.getStatus() == RowStatus.ACTIVE && "GRID_ELEC".equals(r.getEnergyType())) {
            byPlant.merge(r.getPlantId(), r.getQuantity(), BigDecimal::add);
            batchRef.putIfAbsent(r.getPlantId(), b.getId());
          }
        }
      }
    }
    List<ElectricityLedger> created = new ArrayList<>();
    for (Map.Entry<Long, BigDecimal> e : byPlant.entrySet()) {
      if (ledgerRepo.findByPlantIdAndPeriod(e.getKey(), period).isPresent()) continue;
      Plant plant = plantRepo.findById(e.getKey()).orElseThrow();
      ElectricityLedger ledger = new ElectricityLedger();
      ledger.setPlantId(plant.getId());
      ledger.setPeriod(period);
      ledger.setRegion(plant.getRegion());
      ledger.setTotalKwh(e.getValue());
      ledger.setSourceBatchId(batchRef.get(plant.getId()));
      ledger.setCreatedBy(operator);
      ledgerRepo.save(ledger);
      getOrCreateStatement(ledger, operator);
      created.add(ledger);
    }
    return created;
  }

  public List<ElectricityLedger> listLedgers(String period, Long plantId) {
    List<ElectricityLedger> all = period != null && !period.isBlank()
        ? ledgerRepo.findByPeriodOrderByIdDesc(period) : ledgerRepo.findAllByOrderByIdDesc();
    return all.stream().filter(l -> plantId == null || plantId.equals(l.getPlantId())).toList();
  }

  public Map<String, Object> ledgerDetail(Long id) {
    ElectricityLedger ledger = ledgerRepo.findById(id)
        .orElseThrow(() -> BizException.notFound("用电账不存在: " + id));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ledger", ledger);
    out.put("allocations", enrichAllocations(allocationRepo.findByLedgerIdOrderByIdAsc(id)));
    out.put("statement", statementRepo.findByPlantIdAndPeriod(ledger.getPlantId(), ledger.getPeriod())
        .orElse(null));
    return out;
  }

  // ================= 凭证可声明余额 =================

  /** 可声明余额 = 凭证电量 − 已有效匹配量 − 冻结未决量（错配/未决尝试不占用余额）。 */
  public BigDecimal availableKwh(EnergyCertificate c) {
    BigDecimal matched = allocationRepo.findByCertificateIdAndChainValidTrue(c.getId()).stream()
        .filter(a -> a.getMatchStatus() == MatchStatus.MATCHED)
        .map(ClaimAllocation::getAllocatedKwh)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return c.getQuantityKwh().subtract(matched).subtract(c.getFrozenKwh()).max(BigDecimal.ZERO);
  }

  public List<ClaimAllocation> allocationsOf(Long certificateId) {
    return allocationRepo.findByCertificateIdOrderByIdAsc(certificateId);
  }

  /** 凭证详情用：附带凭证/池信息的声明链。 */
  public List<Map<String, Object>> enrichedAllocationsOf(Long certificateId) {
    return enrichAllocations(allocationRepo.findByCertificateIdOrderByIdAsc(certificateId));
  }

  // ================= 归属：逐张分配 / 批次池扣减 =================

  @Transactional
  public Map<String, Object> allocate(String operator, AllocateReq req) {
    ClaimMethod method;
    try {
      method = ClaimMethod.valueOf(req.method());
    } catch (Exception e) {
      throw BizException.badRequest("归属方式必须为 CERTIFICATE（逐张分配）或 POOL（批次池扣减）");
    }
    ElectricityLedger ledger = ledgerRepo.findByIdForUpdate(req.ledgerId())
        .orElseThrow(() -> BizException.notFound("用电账不存在: " + req.ledgerId()));
    ClaimStatement statement = getOrCreateStatement(ledger, operator);
    if (statement.getStatus() == StatementStatus.PUBLISHED) {
      throw BizException.conflict("该工厂期间的声明册已随报告发布，不能新增声明；如需调整请先冻结新的报告版本");
    }
    // 重复主张控制在凭证/成员余额与冻结未决链唯一性上收口：
    // 同凭证对同用电账允许部分拆分（多条 MATCHED 链累计不超可声明余额），
    // 但同一时刻只允许一条 FROZEN_PENDING 链，超余额主张直接拒绝。
    List<ClaimAllocation> created = method == ClaimMethod.CERTIFICATE
        ? allocateByCertificate(operator, req, ledger)
        : allocateByPool(operator, req, ledger);

    recomputeStatement(ledger);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ledger", ledgerRepo.findById(ledger.getId()).orElseThrow());
    out.put("statement", statementRepo.findById(statement.getId()).orElseThrow());
    out.put("allocations", enrichAllocations(created));
    BigDecimal granted = created.stream().map(ClaimAllocation::getAllocatedKwh)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal requested = req.quantityKwh() != null ? req.quantityKwh() : granted;
    out.put("requestedKwh", requested);
    out.put("grantedKwh", granted);
    BigDecimal shortfall = requested.subtract(granted).max(BigDecimal.ZERO);
    out.put("shortfallKwh", shortfall);
    out.put("pending", created.stream().anyMatch(a -> a.getMatchStatus() == MatchStatus.FROZEN_PENDING));
    List<String> warnings = new ArrayList<>(created.stream().map(this::warningOf).filter(Objects::nonNull).toList());
    if (shortfall.compareTo(BigDecimal.ZERO) > 0) {
      warnings.add("凭证/池余额或用电账剩余量不足，缺口 " + shortfall + " kWh 未形成声明（不做总量抵销）");
    }
    out.put("warnings", warnings);
    return out;
  }

  /** 逐张分配：凭证级悲观锁 + 部分拆分 + 匹配校验。 */
  private List<ClaimAllocation> allocateByCertificate(String operator, AllocateReq req,
                                                       ElectricityLedger ledger) {
    if (req.certificateId() == null) throw BizException.badRequest("逐张分配必须指定凭证 certificateId");
    EnergyCertificate cert = certRepo.findByIdForUpdate(req.certificateId())
        .orElseThrow(() -> BizException.notFound("凭证不存在: " + req.certificateId()));
    if (!cert.getHolderPlantId().equals(ledger.getPlantId())) {
      throw BizException.badRequest("凭证持有人与用电工厂不一致，不能用他厂凭证声明本厂用电");
    }
    List<ClaimAllocation> existing = allocationRepo.findByLedgerIdAndChainValidTrue(ledger.getId()).stream()
        .filter(a -> a.getCertificateId().equals(cert.getId())).toList();
    if (existing.stream().anyMatch(a -> a.getMatchStatus() == MatchStatus.FROZEN_PENDING)) {
      throw BizException.conflict("凭证 " + cert.getSerialNo() + " 对此用电账已存在冻结未决主张，裁决前不得重复发起");
    }
    BigDecimal alreadyClaimed = existing.stream().map(ClaimAllocation::getAllocatedKwh)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // 冻结未决：凭证处于重复竞争冻结时，发起的主张只登记为 FROZEN_PENDING，不形成覆盖
    if (cert.getStatus() == CertificateStatus.FROZEN) {
      BigDecimal ask = req.quantityKwh() != null ? req.quantityKwh() : cert.getFrozenKwh();
      positive(ask);
      BigDecimal pending = ask.min(cert.getFrozenKwh());
      if (pending.compareTo(BigDecimal.ZERO) <= 0) {
        throw BizException.conflict("凭证 " + cert.getSerialNo() + " 没有冻结未决额度可登记");
      }
      return List.of(saveAllocation(ledger, cert, null, ClaimMethod.CERTIFICATE,
          pending, MatchStatus.FROZEN_PENDING, operator));
    }
    if (cert.getStatus() != CertificateStatus.ACTIVE || cert.isCancelled()) {
      throw BizException.conflict("凭证当前状态 " + cert.getStatus()
          + (cert.isCancelled() ? "/已注销" : "") + "，新声明必须停止使用它");
    }

    BigDecimal available = availableKwh(cert);
    if (available.compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.conflict("凭证 " + cert.getSerialNo() + " 可声明余额为 0（已全部声明或处于冻结），重复主张被拦截");
    }
    BigDecimal remaining = ledger.getTotalKwh().subtract(ledger.getCoveredKwh()).max(BigDecimal.ZERO);
    BigDecimal ask = req.quantityKwh() != null ? req.quantityKwh() : available.min(remaining);
    positive(ask);
    // 含历史错配链在内，同凭证对同用电账的累计主张不得超过凭证电量
    BigDecimal certHeadroom = cert.getQuantityKwh().subtract(alreadyClaimed).max(BigDecimal.ZERO);
    if (certHeadroom.compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.conflict("凭证 " + cert.getSerialNo() + " 对该用电账的累计主张已达凭证电量，重复主张被拦截");
    }
    MatchStatus match = evaluate(cert, ledger);
    BigDecimal qty;
    if (match == MatchStatus.MATCHED) {
      qty = ask.min(available).min(remaining).min(certHeadroom);
      if (qty.compareTo(BigDecimal.ZERO) == 0) {
        throw BizException.conflict("用电账剩余可覆盖量或凭证可声明余额为 0，凭证不能重复扣减");
      }
      ledger.setCoveredKwh(ledger.getCoveredKwh().add(qty));
      ledgerRepo.save(ledger);
    } else {
      // 错配凭证不形成覆盖、不占用可声明余额，仅登记不可匹配量，累计不超凭证电量
      qty = ask.min(certHeadroom);
    }
    return List.of(saveAllocation(ledger, cert, null, ClaimMethod.CERTIFICATE, qty, match, operator));
  }

  /** 批次池扣减：池余额按成员逐张扣减，成员明细完整保留，混池规则由池唯一键严格限定。 */
  private List<ClaimAllocation> allocateByPool(String operator, AllocateReq req,
                                               ElectricityLedger ledger) {
    if (req.poolId() == null) throw BizException.badRequest("批次池扣减必须指定 poolId");
    CertificatePool pool = poolRepo.findByIdForUpdate(req.poolId())
        .orElseThrow(() -> BizException.notFound("批次池不存在: " + req.poolId()));
    // 池模式下每张成员凭证对同一用电账至多一条链（匹配链由成员余额约束，错配链在此显式去重）
    Set<Long> chainedCertIds = allocationRepo.findByLedgerIdAndChainValidTrue(ledger.getId()).stream()
        .map(ClaimAllocation::getCertificateId).collect(java.util.stream.Collectors.toSet());
    BigDecimal remaining = ledger.getTotalKwh().subtract(ledger.getCoveredKwh()).max(BigDecimal.ZERO);
    BigDecimal ask = req.quantityKwh() != null ? req.quantityKwh()
        : pool.getAvailableKwh().min(remaining);
    positive(ask);

    List<ClaimAllocation> created = new ArrayList<>();
    BigDecimal need = ask;
    boolean poolRegionMismatch = !pool.getRegion().equals(ledger.getRegion());
    boolean poolPeriodMismatch = !pool.getGenerationMonth().equals(ledger.getPeriod());

    for (CertificatePoolMember member : memberRepo.findByPoolIdOrderByIdAsc(pool.getId())) {
      if (need.compareTo(BigDecimal.ZERO) <= 0) break;
      CertificatePoolMember locked = memberRepo.findByIdForUpdate(member.getId()).orElseThrow();
      EnergyCertificate cert = certRepo.findByIdForUpdate(locked.getCertificateId()).orElse(null);
      if (cert == null || cert.getStatus() != CertificateStatus.ACTIVE || cert.isCancelled()) continue;
      // 只能用本厂持有的凭证声明本厂用电；同凭证对同用电账已有链的成员跳过
      if (!cert.getHolderPlantId().equals(ledger.getPlantId())) continue;
      if (chainedCertIds.contains(cert.getId())) continue;
      // 成员可扣量同时受成员账与凭证级余额（冻结/已逐张声明）约束
      BigDecimal memberAvailable = locked.getJoinedKwh()
          .subtract(locked.getUsedKwh()).subtract(locked.getFrozenKwh())
          .min(availableKwh(cert)).max(BigDecimal.ZERO);
      if (memberAvailable.compareTo(BigDecimal.ZERO) <= 0) continue;

      MatchStatus match = poolRegionMismatch ? MatchStatus.REGION_MISMATCH
          : (poolPeriodMismatch ? MatchStatus.PERIOD_MISMATCH : evaluate(cert, ledger));
      BigDecimal take = need.min(memberAvailable);
      if (match == MatchStatus.MATCHED) {
        take = take.min(remaining);
        if (take.compareTo(BigDecimal.ZERO) == 0) break;
        remaining = remaining.subtract(take);
        ledger.setCoveredKwh(ledger.getCoveredKwh().add(take));
        ledgerRepo.save(ledger);
        // 仅有效匹配才占用池与成员余额；错配链只登记不可匹配量，不锁死凭证额度
        locked.setUsedKwh(locked.getUsedKwh().add(take));
        memberRepo.save(locked);
        pool.setAvailableKwh(pool.getAvailableKwh().subtract(take).max(BigDecimal.ZERO));
        poolRepo.save(pool);
      }
      created.add(saveAllocation(ledger, cert, pool, ClaimMethod.POOL, take, match, operator));
      need = need.subtract(take);
    }
    if (created.isEmpty()) {
      throw BizException.conflict("批次池没有可用于该用电账的成员凭证（余额不足、非本厂持有或凭证均已停用）");
    }
    return created;
  }

  /** 删除声明链：仅开放状态声明册可操作，同步回补用电账覆盖量与池成员余额。 */
  @Transactional
  public void deleteAllocation(Long allocationId, String operator) {
    ClaimAllocation a = allocationRepo.findById(allocationId)
        .orElseThrow(() -> BizException.notFound("声明链不存在: " + allocationId));
    ElectricityLedger ledger = ledgerRepo.findByIdForUpdate(a.getLedgerId())
        .orElseThrow(() -> BizException.notFound("用电账不存在"));
    ClaimStatement statement = statementRepo.findByPlantIdAndPeriod(ledger.getPlantId(), ledger.getPeriod())
        .orElseThrow();
    if (statement.getStatus() == StatementStatus.PUBLISHED) {
      throw BizException.conflict("声明册已随报告发布，声明链只能通过凭证撤销/转让标记失效，不能删除");
    }
    if (a.isChainValid() && a.getMatchStatus() == MatchStatus.MATCHED) {
      ledger.setCoveredKwh(ledger.getCoveredKwh().subtract(a.getAllocatedKwh()).max(BigDecimal.ZERO));
      ledgerRepo.save(ledger);
      if (a.getPoolId() != null) {
        CertificatePool pool = poolRepo.findByIdForUpdate(a.getPoolId()).orElseThrow();
        pool.setAvailableKwh(pool.getAvailableKwh().add(a.getAllocatedKwh()));
        poolRepo.save(pool);
        CertificatePoolMember m = memberRepo.findByPoolIdAndCertificateId(a.getPoolId(), a.getCertificateId())
            .orElseThrow();
        m.setUsedKwh(m.getUsedKwh().subtract(a.getAllocatedKwh()).max(BigDecimal.ZERO));
        memberRepo.save(m);
      }
    }
    allocationRepo.delete(a);
    recomputeStatement(ledger);
  }

  // ================= 批次池 =================

  @Transactional
  public CertificatePool joinPool(String operator, Long certificateId) {
    // 不加凭证悲观锁，避免与归属路径（池→成员→凭证）形成锁序环；并发入池由成员唯一索引拦截
    EnergyCertificate cert = certRepo.findById(certificateId)
        .orElseThrow(() -> BizException.notFound("凭证不存在: " + certificateId));
    if (cert.getStatus() != CertificateStatus.ACTIVE || cert.isCancelled()) {
      throw BizException.conflict("只有有效凭证可以入池，当前状态: " + cert.getStatus());
    }
    CertificatePool pool = poolRepo.findByRegionAndGenerationMonthAndTechnology(
        cert.getRegion(), cert.getGenerationMonth(), cert.getTechnology())
        .orElseGet(() -> {
          CertificatePool p = new CertificatePool();
          p.setPoolNo(RunService.newNo("POOL"));
          p.setRegion(cert.getRegion());
          p.setGenerationMonth(cert.getGenerationMonth());
          p.setTechnology(cert.getTechnology());
          return poolRepo.save(p);
        });
    if (memberRepo.findByPoolIdAndCertificateId(pool.getId(), cert.getId()).isPresent()) {
      throw BizException.conflict("凭证已在该批次池中，不能重复入池");
    }
    BigDecimal joinable = availableKwh(cert);
    if (joinable.compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.conflict("凭证可声明余额为 0，无法入池");
    }
    CertificatePoolMember member = new CertificatePoolMember();
    member.setPoolId(pool.getId());
    member.setCertificateId(cert.getId());
    member.setJoinedKwh(joinable);
    memberRepo.save(member);
    pool.setTotalKwh(pool.getTotalKwh().add(joinable));
    pool.setAvailableKwh(pool.getAvailableKwh().add(joinable));
    poolRepo.save(pool);
    eventRepo.save(new CertificateEvent(cert.getId(), CertEventType.POOL_JOIN, null, null,
        "凭证入池 " + pool.getPoolNo() + "（" + pool.getRegion() + "/" + pool.getGenerationMonth()
            + "/" + pool.getTechnology() + "），入池量 " + joinable + " kWh", operator));
    return pool;
  }

  public List<CertificatePool> listPools() { return poolRepo.findAllByOrderByIdDesc(); }

  public Map<String, Object> poolDetail(Long id) {
    CertificatePool pool = poolRepo.findById(id)
        .orElseThrow(() -> BizException.notFound("批次池不存在: " + id));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("pool", pool);
    List<Map<String, Object>> members = new ArrayList<>();
    for (CertificatePoolMember m : memberRepo.findByPoolIdOrderByIdAsc(id)) {
      Map<String, Object> mm = new LinkedHashMap<>();
      mm.put("member", m);
      mm.put("certificate", certRepo.findById(m.getCertificateId()).orElse(null));
      members.add(mm);
    }
    out.put("members", members);
    return out;
  }

  // ================= 撤销/转让传播 =================

  /**
   * 凭证撤销或转让后调用：新声明由凭证状态拦截；
   * 既有有效链全部标记后续失效并保留，已发布报告的声明链同样保留仅打标记。
   */
  @Transactional
  public int invalidateCertificateChains(Long certificateId, String reason, String operator) {
    List<ClaimAllocation> chains = allocationRepo.findByCertificateIdAndChainValidTrue(certificateId);
    Set<Long> ledgerIds = new LinkedHashSet<>();
    for (ClaimAllocation a : chains) {
      a.setChainValid(false);
      a.setInvalidatedAt(LocalDateTime.now());
      a.setInvalidatedReason(reason);
      allocationRepo.save(a);
      ledgerIds.add(a.getLedgerId());
    }
    // 同步冻结凭证在批次池中的未用余额，池可扣减余额不再包含已停用凭证
    for (CertificatePoolMember m : memberRepo.findByCertificateId(certificateId)) {
      BigDecimal unused = m.getJoinedKwh().subtract(m.getUsedKwh()).subtract(m.getFrozenKwh()).max(BigDecimal.ZERO);
      if (unused.compareTo(BigDecimal.ZERO) > 0) {
        m.setFrozenKwh(m.getFrozenKwh().add(unused));
        memberRepo.save(m);
        poolRepo.findById(m.getPoolId()).ifPresent(p -> {
          p.setAvailableKwh(p.getAvailableKwh().subtract(unused).max(BigDecimal.ZERO));
          poolRepo.save(p);
        });
      }
    }
    for (Long ledgerId : ledgerIds) {
      ElectricityLedger ledger = ledgerRepo.findByIdForUpdate(ledgerId).orElseThrow();
      recomputeStatement(ledger);
    }
    return chains.size();
  }

  // ================= 声明册 =================

  public List<ClaimStatement> listStatements(String period, Long plantId) {
    List<ClaimStatement> all = period != null && !period.isBlank()
        ? statementRepo.findByPeriodOrderByIdDesc(period) : statementRepo.findAllByOrderByIdDesc();
    return all.stream().filter(s -> plantId == null || plantId.equals(s.getPlantId())).toList();
  }

  public Map<String, Object> statementDetail(Long id) {
    ClaimStatement s = statementRepo.findById(id)
        .orElseThrow(() -> BizException.notFound("声明册不存在: " + id));
    ElectricityLedger ledger = ledgerRepo.findByPlantIdAndPeriod(s.getPlantId(), s.getPeriod())
        .orElseThrow(() -> BizException.notFound("声明册对应用电账缺失"));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("statement", s);
    out.put("ledger", ledger);
    out.put("allocations", enrichAllocations(allocationRepo.findByLedgerIdOrderByIdAsc(ledger.getId())));
    return out;
  }

  /** 报告冻结新版本时重开期间声明册，允许在新版本发布前调整声明。 */
  @Transactional
  public void reopenStatements(String period) {
    for (ClaimStatement s : statementRepo.findByPeriodOrderByIdDesc(period)) {
      if (s.getStatus() == StatementStatus.PUBLISHED) {
        s.setStatus(StatementStatus.OPEN);
        statementRepo.save(s);
      }
    }
  }

  /** 报告发布：固化期间声明册与有效声明链（链行保留，事后失效只打标记）。 */
  @Transactional
  public void publishStatements(String period, Long reportVersionId) {
    for (ClaimStatement s : statementRepo.findByPeriodOrderByIdDesc(period)) {
      s.setStatus(StatementStatus.PUBLISHED);
      s.setReportVersionId(reportVersionId);
      statementRepo.save(s);
      ElectricityLedger ledger = ledgerRepo.findByPlantIdAndPeriod(s.getPlantId(), period).orElse(null);
      if (ledger == null) continue;
      for (ClaimAllocation a : allocationRepo.findByLedgerIdOrderByIdAsc(ledger.getId())) {
        if (a.isChainValid()) a.setReportVersionId(reportVersionId);
        allocationRepo.save(a);
      }
    }
  }

  /** 裁决首张凭证有效：关闭该凭证此前登记的冻结未决主张链（保留记录，不计入不可匹配量）。 */
  @Transactional
  public void closeFrozenPendingChains(Long certificateId, String reason) {
    Set<Long> ledgerIds = new LinkedHashSet<>();
    for (ClaimAllocation a : allocationRepo.findByCertificateIdAndChainValidTrue(certificateId)) {
      if (a.getMatchStatus() == MatchStatus.FROZEN_PENDING) {
        a.setChainValid(false);
        a.setInvalidatedAt(LocalDateTime.now());
        a.setInvalidatedReason(reason);
        allocationRepo.save(a);
        ledgerIds.add(a.getLedgerId());
      }
    }
    for (Long ledgerId : ledgerIds) {
      ledgerRepo.findById(ledgerId).ifPresent(this::recomputeStatement);
    }
  }

  /** 凭证登记/裁决后，重算其持有工厂在发电月份的声明册（若用电账已存在）。 */
  @Transactional
  public void touchCertificateStatements(Long certificateId) {
    EnergyCertificate c = certRepo.findById(certificateId).orElse(null);
    if (c == null) return;
    ledgerRepo.findByPlantIdAndPeriod(c.getHolderPlantId(), c.getGenerationMonth())
        .ifPresent(this::recomputeStatement);
  }

  /** 凭证重复竞争冻结：同步冻结其在各批次池中的成员余额。 */
  @Transactional
  public void freezePoolMembers(Long certificateId, BigDecimal kwh) {
    BigDecimal left = kwh;
    for (CertificatePoolMember m : memberRepo.findByCertificateId(certificateId)) {
      if (left.compareTo(BigDecimal.ZERO) <= 0) break;
      BigDecimal memberAvailable = m.getJoinedKwh().subtract(m.getUsedKwh())
          .subtract(m.getFrozenKwh()).max(BigDecimal.ZERO);
      BigDecimal freeze = left.min(memberAvailable);
      if (freeze.compareTo(BigDecimal.ZERO) <= 0) continue;
      m.setFrozenKwh(m.getFrozenKwh().add(freeze));
      memberRepo.save(m);
      poolRepo.findById(m.getPoolId()).ifPresent(p -> {
        p.setAvailableKwh(p.getAvailableKwh().subtract(freeze).max(BigDecimal.ZERO));
        poolRepo.save(p);
      });
      left = left.subtract(freeze);
    }
  }

  /** 裁决首张凭证有效：释放此前冻结的池成员余额。 */
  @Transactional
  public void releasePoolMembers(Long certificateId, BigDecimal kwh) {
    BigDecimal left = kwh;
    for (CertificatePoolMember m : memberRepo.findByCertificateId(certificateId)) {
      if (left.compareTo(BigDecimal.ZERO) <= 0) break;
      BigDecimal release = left.min(m.getFrozenKwh());
      if (release.compareTo(BigDecimal.ZERO) <= 0) continue;
      m.setFrozenKwh(m.getFrozenKwh().subtract(release));
      memberRepo.save(m);
      poolRepo.findById(m.getPoolId()).ifPresent(p -> {
        p.setAvailableKwh(p.getAvailableKwh().add(release));
        poolRepo.save(p);
      });
      left = left.subtract(release);
    }
  }

  // ================= 内部辅助 =================

  private ClaimAllocation saveAllocation(ElectricityLedger ledger, EnergyCertificate cert,
                                         CertificatePool pool, ClaimMethod method,
                                         BigDecimal qty, MatchStatus match, String operator) {
    ClaimAllocation a = new ClaimAllocation();
    a.setLedgerId(ledger.getId());
    a.setCertificateId(cert.getId());
    a.setPoolId(pool != null ? pool.getId() : null);
    a.setMethod(method);
    a.setAllocatedKwh(qty);
    a.setMatchStatus(match);
    a.setGenerationMonth(cert.getGenerationMonth());
    a.setRegion(cert.getRegion());
    a.setTechnology(cert.getTechnology());
    a.setCreatedBy(operator);
    return allocationRepo.save(a);
  }

  /** 适用期与区域判定：区域不同→区域错配；发电月份不同或交付区间不覆盖计量月→期间错配。 */
  private MatchStatus evaluate(EnergyCertificate cert, ElectricityLedger ledger) {
    if (!cert.getRegion().equals(ledger.getRegion())) return MatchStatus.REGION_MISMATCH;
    if (!cert.getGenerationMonth().equals(ledger.getPeriod())) return MatchStatus.PERIOD_MISMATCH;
    LocalDate monthStart = LocalDate.parse(ledger.getPeriod() + "-01");
    LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
    boolean covers = !cert.getDeliveryFrom().isAfter(monthStart)
        && !cert.getDeliveryTo().isBefore(monthEnd);
    return covers ? MatchStatus.MATCHED : MatchStatus.PERIOD_MISMATCH;
  }

  /** 重算声明册：以当前有效链为准，保证 total = matched + residual（unmatched 单列不抵销）。 */
  private void recomputeStatement(ElectricityLedger ledger) {
    ClaimStatement s = getOrCreateStatement(ledger, ledger.getCreatedBy());
    List<ClaimAllocation> all = allocationRepo.findByLedgerIdOrderByIdAsc(ledger.getId());
    BigDecimal matched = BigDecimal.ZERO, unmatched = BigDecimal.ZERO, invalidated = BigDecimal.ZERO;
    for (ClaimAllocation a : all) {
      if (!a.isChainValid()) {
        invalidated = invalidated.add(a.getAllocatedKwh());
        continue;
      }
      if (a.getMatchStatus() == MatchStatus.MATCHED) matched = matched.add(a.getAllocatedKwh());
      else unmatched = unmatched.add(a.getAllocatedKwh());
    }
    // 用电账覆盖量以有效匹配链为准自愈（撤销/转让后覆盖量回退为剩余用电）
    ledger.setCoveredKwh(matched);
    ledgerRepo.save(ledger);

    // 重复主张量：规范凭证由本工厂持有、发电月份与本期间一致且仍未裁决的冻结争议量
    BigDecimal duplicate = BigDecimal.ZERO;
    for (CertificateDuplicateUpload d : duplicateRepo.findByStatusOrderByIdDesc(DuplicateStatus.PENDING)) {
      EnergyCertificate dc = certRepo.findById(d.getCertificateId()).orElse(null);
      if (dc != null && dc.getHolderPlantId().equals(ledger.getPlantId())
          && dc.getGenerationMonth().equals(ledger.getPeriod())) {
        duplicate = duplicate.add(d.getContestedKwh());
      }
    }

    s.setTotalKwh(ledger.getTotalKwh());
    s.setRegion(ledger.getRegion());
    s.setMatchedKwh(matched);
    s.setUnmatchedKwh(unmatched);
    s.setResidualKwh(ledger.getTotalKwh().subtract(matched).max(BigDecimal.ZERO));
    s.setDuplicateClaimKwh(duplicate);
    s.setInvalidatedChainKwh(invalidated);
    statementRepo.save(s);
  }

  private ClaimStatement getOrCreateStatement(ElectricityLedger ledger, String operator) {
    return statementRepo.findByPlantIdAndPeriod(ledger.getPlantId(), ledger.getPeriod())
        .orElseGet(() -> {
          ClaimStatement s = new ClaimStatement();
          s.setStatementNo(RunService.newNo("CLM"));
          s.setPlantId(ledger.getPlantId());
          s.setPeriod(ledger.getPeriod());
          s.setRegion(ledger.getRegion());
          s.setTotalKwh(ledger.getTotalKwh());
          s.setResidualKwh(ledger.getTotalKwh());
          s.setCreatedBy(operator);
          return statementRepo.save(s);
        });
  }

  private List<Map<String, Object>> enrichAllocations(List<ClaimAllocation> allocations) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ClaimAllocation a : allocations) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("allocation", a);
      m.put("certificate", certRepo.findById(a.getCertificateId()).orElse(null));
      if (a.getPoolId() != null) m.put("pool", poolRepo.findById(a.getPoolId()).orElse(null));
      out.add(m);
    }
    return out;
  }

  private String warningOf(ClaimAllocation a) {
    return switch (a.getMatchStatus()) {
      case REGION_MISMATCH -> "区域错配：凭证区域 " + a.getRegion() + " 与用电账供电区域不一致，计入不可匹配量，不得抵销";
      case PERIOD_MISMATCH -> "期间错配：凭证发电月份 " + a.getGenerationMonth()
          + " 与计量期间不一致或交付区间不覆盖，计入不可匹配量，不得抵销";
      case FROZEN_PENDING -> "冻结未决：凭证处于重复竞争冻结，该主张不形成覆盖，裁决后处理";
      case MATCHED -> null;
    };
  }

  private static void positive(BigDecimal v) {
    if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.badRequest("归属电量必须为正数");
    }
  }
}
