package com.group.carbon.repo;

import com.group.carbon.common.Types.DuplicateStatus;
import com.group.carbon.model.CertificateDuplicateUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateDuplicateRepo extends JpaRepository<CertificateDuplicateUpload, Long> {
  List<CertificateDuplicateUpload> findAllByOrderByIdDesc();
  List<CertificateDuplicateUpload> findByCertificateIdOrderByIdDesc(Long certificateId);
  List<CertificateDuplicateUpload> findByStatusOrderByIdDesc(DuplicateStatus status);
  List<CertificateDuplicateUpload> findBySerialNoOrderByIdDesc(String serialNo);
}
