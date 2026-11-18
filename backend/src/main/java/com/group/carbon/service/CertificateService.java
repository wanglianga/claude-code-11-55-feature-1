package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.Types.*;
import com.group.carbon.dto.Requests.RegisterCertificateReq;
import com.group.carbon.dto.Requests.ResolveDuplicateReq;
import com.group.carbon.dto.Requests.TransferReq;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 电力属性凭证生命周期服务：
 * - 登记保存序列号、发电/交付区间、区域、技术属性、注销状态与持有人，变更全程留痕；
 * - 同序列号被多个工厂上传时识别重复主张：竞争主张进裁决表，首张凭证的未决部分同步冻结（含批次池余额）；
 * - 注销/撤销/转让后新声明必须停止使用；撤销与转让通过 ClaimService 使既有链失效（已发布报告保留链并标记）。
 */
@Service
public class CertificateService {
  private final EnergyCertificateRepo certRepo;
  private final CertificateEventRepo eventRepo;
  private final CertificateDuplicateRepo duplicateRepo;
  private final PlantRepo plantRepo;
  private final ClaimService claimService;

  public CertificateService(EnergyCertificateRepo certRepo, CertificateEventRepo eventRepo,
                            CertificateDuplicateRepo duplicateRepo, PlantRepo plantRepo,
                            @Lazy ClaimService claimService) {
    this.certRepo = certRepo;
    this.eventRepo = eventRepo;
    this.duplicateRepo = duplicateRepo;
    this.plantRepo = plantRepo;
    this.claimService = claimService;
  }

  private static final Set<String> TECHNOLOGIES = Set.of("SOLAR", "WIND", "HYDRO", "BIOENERGY", "GEOTHERMAL");

  // ---------------- 登记与重复主张 ----------------

  @Transactional
  public Map<String, Object> register(String operator, RegisterCertificateReq req) {
    RunService.validatePeriod(req.generationMonth());
    LocalDate from = parseDate(req.deliveryFrom());
    LocalDate to = parseDate(req.deliveryTo());
    if (to.isBefore(from)) throw BizException.badRequest("交付区间止不能早于区间起");
    if (req.quantityKwh() == null || req.quantityKwh().compareTo(BigDecimal.ZERO) <= 0) {
      throw BizException.badRequest("凭证电量必须为正数");
    }
    if (!TECHNOLOGIES.contains(req.technology())) {
      throw BizException.badRequest("技术属性必须为 " + TECHNOLOGIES);
    }
    Plant plant = plantRepo.findById(req.holderPlantId())
        .orElseThrow(() -> BizException.badRequest("持有工厂不存在: " + req.holderPlantId()));

    Optional<EnergyCertificate> existing = certRepo.findBySerialNo(req.serialNo());
    if (existing.isEmpty()) {
      EnergyCertificate cert = fill(new EnergyCertificate(), req, plant);
      cert.setStatus(CertificateStatus.ACTIVE);
      certRepo.save(cert);
      event(cert.getId(), CertEventType.REGISTER, null, plant.getId(),
          "凭证登记，电量 " + req.quantityKwh() + " kWh", operator);
      return Map.of("certificate", cert, "duplicate", false);
    }

    // 同序列号已存在：重复上传 → 不新建凭证行（序列号全局唯一），竞争主张进裁决表并冻结未决部分
    EnergyCertificate first = certRepo.findByIdForUpdate(existing.get().getId()).orElseThrow();
    if (first.getHolderPlantId().equals(plant.getId())) {
      throw BizException.conflict("工厂 " + plant.getName() + " 已登记序列号 " + req.serialNo()
          + " 的凭证，同一工厂不得重复上传");
    }
    if (duplicateRepo.findBySerialNoOrderByIdDesc(req.serialNo()).stream()
        .anyMatch(d -> d.getStatus() == DuplicateStatus.PENDING && d.getPlantId().equals(plant.getId()))) {
      throw BizException.conflict("工厂 " + plant.getName() + " 对该序列号已存在未裁决的重复上传");
    }

    BigDecimal available = claimService.availableKwh(first);
    BigDecimal contested = req.quantityKwh().min(available.max(BigDecimal.ZERO));
    if (contested.compareTo(BigDecimal.ZERO) > 0) {
      first.setFrozenKwh(first.getFrozenKwh().add(contested));
      if (first.getStatus() == CertificateStatus.ACTIVE) first.setStatus(CertificateStatus.FROZEN);
      certRepo.save(first);
      claimService.freezePoolMembers(first.getId(), contested);
    }
    event(first.getId(), CertEventType.DUPLICATE_FROZEN, first.getHolderPlantId(), plant.getId(),
        "工厂 " + plant.getName() + " 重复上传同序列号凭证，冻结未决 " + contested + " kWh", operator);

    CertificateDuplicateUpload dup = new CertificateDuplicateUpload();
    dup.setSerialNo(req.serialNo());
    dup.setCertificateId(first.getId());
    dup.setPlantId(plant.getId());
    dup.setQuantityKwh(req.quantityKwh());
    dup.setContestedKwh(contested);
    dup.setStatus(DuplicateStatus.PENDING);
    dup.setCreatedBy(operator);
    duplicateRepo.save(dup);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("certificate", first);
    out.put("duplicate", true);
    out.put("canonicalCertificateId", first.getId());
    out.put("contestedKwh", contested);
    out.put("duplicateUploadId", dup.getId());
    claimService.touchCertificateStatements(first.getId());
    return out;
  }

  private EnergyCertificate fill(EnergyCertificate cert, RegisterCertificateReq req, Plant plant) {
    cert.setSerialNo(req.serialNo());
    cert.setHolderPlantId(plant.getId());
    cert.setRegion(req.region());
    cert.setTechnology(req.technology());
    cert.setGenerationMonth(req.generationMonth());
    cert.setDeliveryFrom(parseDate(req.deliveryFrom()));
    cert.setDeliveryTo(parseDate(req.deliveryTo()));
    cert.setQuantityKwh(req.quantityKwh());
    cert.setCreatedBy(currentOperator());
    return cert;
  }

  // ---------------- 注销 / 撤销 / 转让 ----------------

  /** 注销：仅未参与任何有效声明的凭证可注销；已使用的凭证须走撤销流程。 */
  @Transactional
  public EnergyCertificate cancel(Long id, String operator) {
    EnergyCertificate cert = certRepo.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("凭证不存在: " + id));
    if (cert.isCancelled() || cert.getStatus() == CertificateStatus.REVOKED) {
      throw BizException.conflict("凭证已处于终态: " + cert.getStatus());
    }
    if (cert.getStatus() == CertificateStatus.FROZEN) {
      throw BizException.conflict("凭证存在未裁决的重复上传竞争，冻结未决期间不能注销，请先完成裁决");
    }
    if (cert.getStatus() == CertificateStatus.TRANSFERRED) {
      throw BizException.conflict("凭证已转让，不能注销");
    }
    if (!claimService.allocationsOf(cert.getId()).isEmpty()) {
      throw BizException.conflict("凭证已用于声明，不能直接注销，请走撤销流程（既有声明链将保留并标记失效）");
    }
    cert.setCancelled(true);
    cert.setCancelledAt(LocalDateTime.now());
    certRepo.save(cert);
    event(cert.getId(), CertEventType.CANCEL, cert.getHolderPlantId(), null,
        "凭证注销，不再用于新声明", operator);
    return cert;
  }

  /** 撤销：新声明立即停用；既有有效链全部标记失效，已发布报告保留当时声明链。 */
  @Transactional
  public EnergyCertificate revoke(Long id, String reason, String operator) {
    EnergyCertificate cert = certRepo.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("凭证不存在: " + id));
    if (cert.getStatus() == CertificateStatus.REVOKED) {
      throw BizException.conflict("凭证已撤销");
    }
    cert.setStatus(CertificateStatus.REVOKED);
    cert.setInvalidReason(reason);
    certRepo.save(cert);
    event(cert.getId(), CertEventType.REVOKE, cert.getHolderPlantId(), null,
        "凭证撤销：" + reason + "；新声明停用，既有声明链标记后续失效", operator);
    claimService.invalidateCertificateChains(id, "凭证撤销：" + reason, operator);
    return cert;
  }

  /** 转让：持有人变更留痕；新声明必须停止使用该凭证，既有链标记后续失效。 */
  @Transactional
  public EnergyCertificate transfer(Long id, TransferReq req, String operator) {
    EnergyCertificate cert = certRepo.findByIdForUpdate(id)
        .orElseThrow(() -> BizException.notFound("凭证不存在: " + id));
    if (cert.getStatus() == CertificateStatus.REVOKED || cert.getStatus() == CertificateStatus.TRANSFERRED) {
      throw BizException.conflict("凭证当前状态 " + cert.getStatus() + "，不能转让");
    }
    Plant to = plantRepo.findById(req.toPlantId())
        .orElseThrow(() -> BizException.badRequest("受让工厂不存在: " + req.toPlantId()));
    if (to.getId().equals(cert.getHolderPlantId())) {
      throw BizException.badRequest("受让工厂与当前持有人相同");
    }
    Long from = cert.getHolderPlantId();
    cert.setHolderPlantId(to.getId());
    cert.setStatus(CertificateStatus.TRANSFERRED);
    cert.setInvalidReason(req.reason());
    certRepo.save(cert);
    event(cert.getId(), CertEventType.TRANSFER, from, to.getId(),
        "凭证转让：" + req.reason(), operator);
    claimService.invalidateCertificateChains(id, "凭证转让给 " + to.getName() + "：" + req.reason(), operator);
    return cert;
  }

  // ---------------- 重复上传裁决 ----------------

  @Transactional
  public CertificateDuplicateUpload resolveDuplicate(Long dupId, ResolveDuplicateReq req, String operator) {
    CertificateDuplicateUpload dup = duplicateRepo.findById(dupId)
        .orElseThrow(() -> BizException.notFound("重复上传记录不存在: " + dupId));
    if (dup.getStatus() != DuplicateStatus.PENDING) {
      throw BizException.conflict("该重复上传已裁决: " + dup.getResolution());
    }
    EnergyCertificate first = certRepo.findByIdForUpdate(dup.getCertificateId()).orElseThrow();
    if (first.getStatus() == CertificateStatus.REVOKED
        || first.getStatus() == CertificateStatus.TRANSFERRED || first.isCancelled()) {
      throw BizException.conflict("凭证当前状态 " + first.getStatus()
          + "，不能再裁决重复上传；请先处理凭证的撤销/转让/注销状态");
    }
    Plant contesting = plantRepo.findById(dup.getPlantId())
        .orElseThrow(() -> BizException.conflict("重复上传工厂不存在"));

    switch (req.winner()) {
      case "FIRST" -> {
        // 首张凭证有效：释放冻结额度（含批次池成员余额），竞争主张作废
        BigDecimal released = dup.getContestedKwh();
        first.setFrozenKwh(first.getFrozenKwh().subtract(released).max(BigDecimal.ZERO));
        if (first.getFrozenKwh().compareTo(BigDecimal.ZERO) == 0 && !first.isCancelled()
            && first.getStatus() == CertificateStatus.FROZEN) {
          first.setStatus(CertificateStatus.ACTIVE);
        }
        certRepo.save(first);
        claimService.releasePoolMembers(first.getId(), released);
        claimService.closeFrozenPendingChains(first.getId(), "重复上传裁决首张有效，未决主张关闭");
        dup.setResolution("FIRST_WON");
        event(first.getId(), CertEventType.DUPLICATE_FROZEN, null, first.getHolderPlantId(),
            "裁决首张凭证有效，释放冻结 " + dup.getContestedKwh() + " kWh，竞争工厂 "
                + contesting.getName() + " 的主张作废", operator);
      }
      case "DUPLICATE" -> {
        // 后传工厂胜诉：凭证按转让变更持有人，原持有人既有声明链全部标记失效并保留
        Long fromPlant = first.getHolderPlantId();
        BigDecimal released = dup.getContestedKwh();
        first.setHolderPlantId(contesting.getId());
        first.setFrozenKwh(first.getFrozenKwh().subtract(released).max(BigDecimal.ZERO));
        first.setStatus(CertificateStatus.ACTIVE);
        first.setInvalidReason(null);
        certRepo.save(first);
        claimService.releasePoolMembers(first.getId(), released);
        claimService.closeFrozenPendingChains(first.getId(), "重复上传裁决后传工厂胜诉，原持有人未决主张关闭");
        claimService.invalidateCertificateChains(first.getId(),
            "重复上传裁决：后传工厂 " + contesting.getName() + " 胜诉，原持有人声明链失效", operator);
        event(first.getId(), CertEventType.TRANSFER, fromPlant, contesting.getId(),
            "重复上传裁决后传工厂胜诉，凭证持有人变更", operator);
        dup.setResolution("DUPLICATE_WON");
      }
      default -> throw BizException.badRequest("裁决结果必须为 FIRST 或 DUPLICATE");
    }
    dup.setStatus(DuplicateStatus.RESOLVED);
    dup.setReason(req.reason());
    dup.setResolvedBy(operator);
    dup.setResolvedAt(LocalDateTime.now());
    CertificateDuplicateUpload saved = duplicateRepo.save(dup);
    claimService.touchCertificateStatements(first.getId());
    return saved;
  }

  // ---------------- 查询 ----------------

  public List<EnergyCertificate> list(String status, Long plantId) {
    List<EnergyCertificate> all = plantId != null
        ? certRepo.findByHolderPlantIdOrderByIdDesc(plantId) : certRepo.findAllByOrderByIdDesc();
    return all.stream()
        .filter(c -> status == null || status.isBlank() || c.getStatus().name().equals(status))
        .toList();
  }

  public Map<String, Object> detail(Long id) {
    EnergyCertificate cert = certRepo.findById(id)
        .orElseThrow(() -> BizException.notFound("凭证不存在: " + id));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("certificate", cert);
    out.put("availableKwh", claimService.availableKwh(cert));
    out.put("events", eventRepo.findByCertificateIdOrderByIdAsc(id));
    out.put("duplicates", duplicateRepo.findByCertificateIdOrderByIdDesc(id));
    out.put("allocations", claimService.enrichedAllocationsOf(id));
    return out;
  }

  public List<CertificateDuplicateUpload> duplicates(String status) {
    if (status == null || status.isBlank()) return duplicateRepo.findAllByOrderByIdDesc();
    try {
      return duplicateRepo.findByStatusOrderByIdDesc(DuplicateStatus.valueOf(status));
    } catch (IllegalArgumentException e) {
      throw BizException.badRequest("重复上传状态必须为 PENDING 或 RESOLVED");
    }
  }

  // ---------------- 辅助 ----------------

  private void event(Long certId, CertEventType type, Long from, Long to, String detail, String operator) {
    eventRepo.save(new CertificateEvent(certId, type, from, to, detail, operator));
  }

  private static LocalDate parseDate(String d) {
    try {
      return LocalDate.parse(d);
    } catch (Exception e) {
      throw BizException.badRequest("日期格式必须为 yyyy-MM-dd: " + d);
    }
  }

  private String currentOperator() {
    try {
      return com.group.carbon.common.AuthContext.require().username();
    } catch (Exception e) {
      return "system";
    }
  }
}
