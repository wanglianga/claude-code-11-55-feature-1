package com.group.carbon.repo;

import com.group.carbon.model.CertificatePool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificatePoolRepo extends JpaRepository<CertificatePool, Long> {
  List<CertificatePool> findAllByOrderByIdDesc();
  Optional<CertificatePool> findByRegionAndGenerationMonthAndTechnology(
      String region, String generationMonth, String technology);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM CertificatePool p WHERE p.id = :id")
  Optional<CertificatePool> findByIdForUpdate(@Param("id") Long id);
}
