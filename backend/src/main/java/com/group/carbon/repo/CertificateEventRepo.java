package com.group.carbon.repo;

import com.group.carbon.model.CertificateEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateEventRepo extends JpaRepository<CertificateEvent, Long> {
  List<CertificateEvent> findByCertificateIdOrderByIdAsc(Long certificateId);
}
