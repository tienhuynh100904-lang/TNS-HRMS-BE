package vn.com.tns_hrms.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.com.tns_hrms.entity.PunchRaw;

public interface PunchRawRepository extends JpaRepository<PunchRaw, Long> {
    // the natural key of a punch, used to find which punches of an incoming batch are already stored
    interface PunchKey {
        String getEmployeeCode();

        LocalDateTime getPunchTime();
    }

    @Query("SELECT p.employeeCode AS employeeCode, p.punchTime AS punchTime FROM PunchRaw p "
            + "WHERE p.branchCode = :branchCode AND p.punchTime BETWEEN :from AND :to")
    List<PunchKey> findKeys(@Param("branchCode") String branchCode, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // where the next incremental pull of a branch can start from
    @Query("SELECT MAX(p.punchTime) FROM PunchRaw p WHERE p.branchCode = :branchCode")
    Optional<LocalDateTime> findLastPunchTime(@Param("branchCode") String branchCode);
}
