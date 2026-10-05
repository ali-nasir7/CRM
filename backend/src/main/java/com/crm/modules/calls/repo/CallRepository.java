package com.crm.modules.calls.repo;

import com.crm.modules.calls.domain.Call;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallRepository extends JpaRepository<Call, UUID>, JpaSpecificationExecutor<Call> {

    Page<Call> findByOrganizationIdAndLeadIdOrderByOccurredAtDesc(UUID organizationId, UUID leadId, Pageable pageable);

    long countByOrganizationIdAndUserIdAndOccurredAtBetween(UUID orgId, UUID userId, Instant from, Instant to);

    long countByOrganizationIdAndUserIdAndOutcomeAndOccurredAtBetween(UUID orgId, UUID userId, String outcome, Instant from, Instant to);

    List<Call> findByOrganizationIdAndUserIdAndOccurredAtBetween(UUID orgId, UUID userId, Instant from, Instant to);

    Optional<Call> findFirstByProviderRef(String providerRef);

    /** Idempotency guard: a device already mid-call cannot be asked to dial again (duplicate
     *  clicks, retried requests). Mirrors the one-call-at-a-time rule the legacy bridge enforces
     *  locally, but here it is authoritative for BOTH calling paths. */
    boolean existsByOrganizationIdAndDeviceIdAndStatusIn(UUID orgId, UUID deviceId, List<String> statuses);

    long countByOrganizationIdAndOccurredAtBetween(UUID orgId, Instant from, Instant to);

    @Query("select c.userId, count(c) from Call c where c.organizationId = :orgId and c.occurredAt between :from and :to group by c.userId")
    List<Object[]> countByUserBetween(UUID orgId, Instant from, Instant to);

    @Query("select c.userId, count(c) from Call c where c.organizationId = :orgId and c.occurredAt between :from and :to " +
           "and c.outcome in ('CONNECTED','INTERESTED','QUALIFIED','MEETING_BOOKED') group by c.userId")
    List<Object[]> countConnectedByUserBetween(UUID orgId, Instant from, Instant to);
}
