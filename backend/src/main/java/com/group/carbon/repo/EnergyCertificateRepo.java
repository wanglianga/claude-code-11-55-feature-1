package com.group.carbon.repo;

import com.group.carbon.model.EnergyCertificate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnergyCertificateRepo extends JpaRepository<EnergyCertificate, Long> {
  List<EnergyCertificate> findAllByOrderByIdDesc();
  Optional<EnergyCertificate> findBySerialNo(String serialNo);
  boolean existsBySerialNo(String serialNo);
  List<EnergyCertificate> findByHolderPlantIdOrderByIdDesc(Long holderPlantId);

  /** 悲观写锁：归属/撤销/转让竞争同一张凭证时串行化。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM EnergyCertificate c WHERE c.id = :id")
  Optional<EnergyCertificate> findByIdForUpdate(@Param("id") Long id);
}
