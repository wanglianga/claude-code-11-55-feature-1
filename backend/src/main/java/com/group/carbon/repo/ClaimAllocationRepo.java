package com.group.carbon.repo;

import com.group.carbon.model.ClaimAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimAllocationRepo extends JpaRepository<ClaimAllocation, Long> {
  List<ClaimAllocation> findByLedgerIdOrderByIdAsc(Long ledgerId);
  List<ClaimAllocation> findByCertificateIdOrderByIdAsc(Long certificateId);
  List<ClaimAllocation> findByReportVersionId(Long reportVersionId);

  /** 某张凭证当前仍有效（chain_valid=TRUE）的声明链 */
  List<ClaimAllocation> findByCertificateIdAndChainValidTrue(Long certificateId);

  /** 某用电账当前仍有效的声明链 */
  List<ClaimAllocation> findByLedgerIdAndChainValidTrue(Long ledgerId);
}
