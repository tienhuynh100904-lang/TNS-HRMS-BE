package vn.com.tns_hrms.repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Reads punch_raw joined to the employee that owns the user code (employees.user_code). The query is built piece by
 * piece so that a filter that is not given adds nothing to it (no "? IS NULL" tricks, which SQL Server cannot type).
 */
@Repository
public class PunchReportRepository {
    /** One punch with the person it belongs to. */
    public record PunchLine(String branchCode, String userCode, String employeeCode, String fullName,
            String departmentName, LocalDateTime punchTime) {
    }

    /** The punches of one person (one user code at one branch) inside the period asked for. */
    public record PunchSummary(String branchCode, String userCode, String employeeCode, String fullName,
            String departmentName, long punchCount, LocalDateTime firstPunchTime, LocalDateTime lastPunchTime) {
    }

    private record Filter(String fromWhere, Map<String, Object> params) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Every punch in the period, oldest first.
     *
     * @param branchCode only this branch, null for all
     * @param keyword part of the user code, employee code or full name (case insensitive), null for everyone
     * @param from first moment included, null for no lower limit
     * @param toExclusive first moment not included, null for no upper limit
     */
    public Page<PunchLine> punches(String branchCode, String keyword, LocalDateTime from, LocalDateTime toExclusive,
            Pageable pageable) {
        Filter filter = filter(branchCode, keyword, from, toExclusive);

        TypedQuery<Long> countQuery = entityManager.createQuery("SELECT COUNT(p)" + filter.fromWhere(), Long.class);
        filter.params().forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<Object[]> dataQuery = entityManager.createQuery(
                "SELECT p.branchCode, p.userCode, e.employeeCode, e.fullName, d.name, p.punchTime"
                        + filter.fromWhere() + " ORDER BY p.punchTime, p.id",
                Object[].class);
        filter.params().forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<PunchLine> content = dataQuery.getResultList().stream()
                .map(r -> new PunchLine((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                        (LocalDateTime) r[5]))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    /** One row per person, with the number of punches and the first and last one in the period. */
    public Page<PunchSummary> summarize(String branchCode, String keyword, LocalDateTime from,
            LocalDateTime toExclusive, Pageable pageable) {
        Filter filter = filter(branchCode, keyword, from, toExclusive);

        // a user code belongs to at most one employee, so the groups are exactly the (branch, user code) pairs
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(DISTINCT CONCAT(p.branchCode, '|', p.userCode))" + filter.fromWhere(), Long.class);
        filter.params().forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<Object[]> dataQuery = entityManager.createQuery(
                "SELECT p.branchCode, p.userCode, e.employeeCode, e.fullName, d.name, COUNT(p), MIN(p.punchTime),"
                        + " MAX(p.punchTime)" + filter.fromWhere()
                        + " GROUP BY p.branchCode, p.userCode, e.employeeCode, e.fullName, d.name"
                        + " ORDER BY p.branchCode, LENGTH(p.userCode), p.userCode",
                Object[].class);
        filter.params().forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<PunchSummary> content = dataQuery.getResultList().stream()
                .map(r -> new PunchSummary((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                        (Long) r[5], (LocalDateTime) r[6], (LocalDateTime) r[7]))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    private static Filter filter(String branchCode, String keyword, LocalDateTime from, LocalDateTime toExclusive) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(
                " FROM PunchRaw p LEFT JOIN Employee e ON e.userCode = p.userCode LEFT JOIN e.department d WHERE 1 = 1");
        if (branchCode != null) {
            where.append(" AND p.branchCode = :branchCode");
            params.put("branchCode", branchCode);
        }
        if (from != null) {
            where.append(" AND p.punchTime >= :from");
            params.put("from", from);
        }
        if (toExclusive != null) {
            where.append(" AND p.punchTime < :to");
            params.put("to", toExclusive);
        }
        if (keyword != null) {
            where.append(" AND (LOWER(p.userCode) LIKE :keyword ESCAPE '\\' OR LOWER(e.employeeCode) LIKE :keyword"
                    + " ESCAPE '\\' OR LOWER(e.fullName) LIKE :keyword ESCAPE '\\')");
            params.put("keyword", "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%");
        }
        return new Filter(where.toString(), params);
    }

    // % and _ typed by the user are text, not wildcards
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
