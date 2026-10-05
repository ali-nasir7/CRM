package com.crm.modules.calling.repo;

import com.crm.modules.calling.domain.CallingDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallingDeviceRepository extends JpaRepository<CallingDevice, UUID> {

    List<CallingDevice> findByOrganizationIdAndUserIdOrderByCreatedAtAsc(UUID organizationId, UUID userId);

    @Query("select d from CallingDevice d where d.organizationId = :orgId and d.id = :id")
    Optional<CallingDevice> findInOrg(UUID orgId, UUID id);

    @Query("select d from CallingDevice d where d.organizationId = :orgId and d.userId = :userId and d.id = :id")
    Optional<CallingDevice> findOwned(UUID orgId, UUID userId, UUID id);

    /** Same derived pattern as PipelineRepository.isDefault, proven at boot. */
    Optional<CallingDevice> findFirstByUserIdAndIsDefaultTrue(UUID userId);

    List<CallingDevice> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    /** Re-pairing the same phone updates its existing row instead of creating a duplicate. */
    Optional<CallingDevice> findByOrganizationIdAndUserIdAndInstallationId(UUID organizationId, UUID userId, String installationId);

    /**
     * WebSocket handshake authentication: the device token never carries orgId/userId itself,
     * so the row found here is the ONLY source of truth for which tenant/user this connection
     * belongs to. Excludes revoked devices at the query level, not just in application code.
     */
    Optional<CallingDevice> findFirstByDeviceTokenHashAndRevokedAtIsNull(String deviceTokenHash);

    /** Admin/manager visibility across the organization (not scoped to a single user). */
    List<CallingDevice> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
