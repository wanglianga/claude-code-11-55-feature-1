package com.group.carbon.repo;

import com.group.carbon.model.CertificatePoolMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificatePoolMemberRepo extends JpaRepository<CertificatePoolMember, Long> {
  List<CertificatePoolMember> findByPoolIdOrderByIdAsc(Long poolId);
  Optional<CertificatePoolMember> findByPoolIdAndCertificateId(Long poolId, Long certificateId);
  List<CertificatePoolMember> findByCertificateId(Long certificateId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM CertificatePoolMember m WHERE m.id = :id")
  Optional<CertificatePoolMember> findByIdForUpdate(@Param("id") Long id);
}
