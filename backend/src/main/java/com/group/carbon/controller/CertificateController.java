package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.RegisterCertificateReq;
import com.group.carbon.dto.Requests.ReasonReq;
import com.group.carbon.dto.Requests.ResolveDuplicateReq;
import com.group.carbon.dto.Requests.TransferReq;
import com.group.carbon.service.CertificateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 可再生电力属性凭证登记与生命周期：
 * 登记（序列号唯一）、重复上传识别与裁决、注销、撤销、转让，持有人/状态变更全程留痕。
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {
  private final CertificateService certificateService;

  public CertificateController(CertificateService certificateService) {
    this.certificateService = certificateService;
  }

  @GetMapping
  public Object list(@RequestParam(required = false) String status,
                     @RequestParam(required = false) Long plantId) {
    return certificateService.list(status, plantId);
  }

  @GetMapping("/technologies")
  public Object technologies() {
    return List.of(
        Map.of("code", "SOLAR", "label", "太阳能"),
        Map.of("code", "WIND", "label", "风能"),
        Map.of("code", "HYDRO", "label", "水电"),
        Map.of("code", "BIOENERGY", "label", "生物质能"),
        Map.of("code", "GEOTHERMAL", "label", "地热能"));
  }

  @GetMapping("/duplicates")
  public Object duplicates(@RequestParam(required = false) String status) {
    return certificateService.duplicates(status);
  }

  @PostMapping("/duplicates/{dupId}/resolve")
  public Object resolveDuplicate(@PathVariable Long dupId,
                                 @Valid @RequestBody ResolveDuplicateReq req) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return certificateService.resolveDuplicate(dupId, req, AuthContext.require().username());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable Long id) {
    return certificateService.detail(id);
  }

  @PostMapping
  public Object register(@Valid @RequestBody RegisterCertificateReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return certificateService.register(AuthContext.require().username(), req);
  }

  @PostMapping("/{id}/cancel")
  public Object cancel(@PathVariable Long id) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return certificateService.cancel(id, AuthContext.require().username());
  }

  @PostMapping("/{id}/revoke")
  public Object revoke(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return certificateService.revoke(id, req.reason(), AuthContext.require().username());
  }

  @PostMapping("/{id}/transfer")
  public Object transfer(@PathVariable Long id, @Valid @RequestBody TransferReq req) {
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return certificateService.transfer(id, req, AuthContext.require().username());
  }
}
