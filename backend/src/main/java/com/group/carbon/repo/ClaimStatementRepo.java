package com.group.carbon.repo;

import com.group.carbon.model.ClaimStatement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaimStatementRepo extends JpaRepository<ClaimStatement, Long> {
  List<ClaimStatement> findAllByOrderByIdDesc();
  List<ClaimStatement> findByPeriodOrderByIdDesc(String period);
  List<ClaimStatement> findByPlantIdAndPeriodOrderByIdDesc(Long plantId, String period);
  Optional<ClaimStatement> findByPlantIdAndPeriod(Long plantId, String period);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM ClaimStatement s WHERE s.id = :id")
  Optional<ClaimStatement> findByIdForUpdate(@Param("id") Long id);
}
