package vn.com.tns_hrms.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.com.tns_hrms.entity.PunchRaw;

public interface PunchRawRepository extends JpaRepository<PunchRaw, Long> {
    // the natural key of a punch, used to find which punches of an incoming batch are already stored
    interface PunchKey {
        String getUserCode();

        LocalDateTime getPunchTime();
    }

    @Query("SELECT p.userCode AS userCode, p.punchTime AS punchTime FROM PunchRaw p "
            + "WHERE p.branchCode = :branchCode AND p.punchTime BETWEEN :from AND :to")
    List<PunchKey> findKeys(@Param("branchCode") String branchCode, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // the punches of a person in a period; the order comes from the Pageable
    @Query("SELECT p FROM PunchRaw p WHERE p.userCode = :userCode AND p.punchTime >= :from AND p.punchTime < :to")
    Page<PunchRaw> findPunches(@Param("userCode") String userCode, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, Pageable pageable);

    // where the next incremental pull of a branch can start from
    @Query("SELECT MAX(p.punchTime) FROM PunchRaw p WHERE p.branchCode = :branchCode")
    Optional<LocalDateTime> findLastPunchTime(@Param("branchCode") String branchCode);
}
