package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.AllocateReq;
import com.group.carbon.dto.Requests.LedgerReq;
import com.group.carbon.dto.Requests.PoolJoinReq;
import com.group.carbon.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 市场化电力声明：
 * 用电账、凭证逐期归属（逐张分配 / 批次池扣减）、声明册与凭证链查询。
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {
  private final ClaimService claimService;

  public ClaimController(ClaimService claimService) {
    this.claimService = claimService;
  }

  // ---------------- 用电账 ----------------

  @GetMapping("/ledgers")
  public Object ledgers(@RequestParam(required = false) String period,
                        @RequestParam(required = false) Long plantId) {
    return claimService.listLedgers(period, plantId);
  }

  @PostMapping("/ledgers")
  public Object createLedger(@Valid @RequestBody LedgerReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return claimService.createLedger(AuthContext.require().username(), req);
  }

  /** 从已核验电力批次按工厂汇总生成用电账。 */
  @PostMapping("/ledgers/sync")
  public Object syncLedgers(@RequestParam String period) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return claimService.syncLedgersFromBatches(period, AuthContext.require().username());
  }

  @GetMapping("/ledgers/{id}")
  public Map<String, Object> ledgerDetail(@PathVariable Long id) {
    return claimService.ledgerDetail(id);
  }

  // ---------------- 归属 ----------------

  @PostMapping("/allocations")
  public Object allocate(@Valid @RequestBody AllocateReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return claimService.allocate(AuthContext.require().username(), req);
  }

  @DeleteMapping("/allocations/{id}")
  public Object deleteAllocation(@PathVariable Long id) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    claimService.deleteAllocation(id, AuthContext.require().username());
    return Map.of("deleted", id);
  }

  // ---------------- 批次池 ----------------

  @GetMapping("/pools")
  public Object pools() {
    return claimService.listPools();
  }

  @GetMapping("/pools/{id}")
  public Map<String, Object> poolDetail(@PathVariable Long id) {
    return claimService.poolDetail(id);
  }

  @PostMapping("/pools/join")
  public Object joinPool(@Valid @RequestBody PoolJoinReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return claimService.joinPool(AuthContext.require().username(), req.certificateId());
  }

  // ---------------- 声明册 ----------------

  @GetMapping("/statements")
  public Object statements(@RequestParam(required = false) String period,
                           @RequestParam(required = false) Long plantId) {
    return claimService.listStatements(period, plantId);
  }

  @GetMapping("/statements/{id}")
  public Map<String, Object> statementDetail(@PathVariable Long id) {
    return claimService.statementDetail(id);
  }
}
