package com.group.carbon.repo;

import com.group.carbon.model.ElectricityLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElectricityLedgerRepo extends JpaRepository<ElectricityLedger, Long> {
  List<ElectricityLedger> findAllByOrderByIdDesc();
  List<ElectricityLedger> findByPlantIdOrderByIdDesc(Long plantId);
  List<ElectricityLedger> findByPeriodOrderByIdDesc(String period);
  Optional<ElectricityLedger> findByPlantIdAndPeriod(Long plantId, String period);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM ElectricityLedger l WHERE l.id = :id")
  Optional<ElectricityLedger> findByIdForUpdate(@Param("id") Long id);
}
