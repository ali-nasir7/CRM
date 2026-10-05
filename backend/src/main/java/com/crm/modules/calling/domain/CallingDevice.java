package com.crm.modules.calling.domain;

import java.util.UUID;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A user's personal calling device (an Android phone reached through the bridge app). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "calling_devices", uniqueConstraints =
    @UniqueConstraint(name = "uk_calling_device_org_name", columnNames = {"user_id", "device_name"}),
    indexes = {
        @Index(name = "ix_calling_devices_user", columnList = "user_id"),
        @Index(name = "ix_calling_devices_org", columnList = "organization_id")})
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class CallingDevice extends TenantEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_name", nullable = false, length = 80)
    private String deviceName;

    /** Public contact number of the SIM. Never expose IMSI/ICCID-style identifiers. */
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    /** e.g. ANDROID.Kept free-form so future platforms fit without migrations. */
    @Column(length = 24)
    private String platform;

    /** ONLINE / OFFLINE / BUSY / DISCONNECTED. ONLINE means heartbeat within the freshness window. */
    @Column(nullable = false, length = 16)
    private String status = "OFFLINE";

    /**
     * Base URL of the bridge app that dials THIS phone (announced via bridge heartbeat).
     * Blank = fall back to the global crm.bridge.base-url (single-bridge deployments).
     */
    @Column(name = "bridge_url", length = 500)
    private String bridgeUrl;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    private boolean isDefault;

    /**
     * BRIDGE (legacy: PC-side adb bridge, {@link com.crm.modules.telephony.AndroidBridgeTelephonyService})
     * or AGENT (CRM Phone Agent app, paired to this row and connected over WebSocket -
     * {@link com.crm.modules.telephony.PhoneAgentTelephonyService}). Exactly one of the two
     * TelephonyService implementations ever handles a given call: CallRoutingTelephonyService
     * dispatches on this field, so a device can never be double-dialed through both paths.
     */
    @Column(name = "connection_mode", nullable = false, length = 16)
    private String connectionMode = "BRIDGE";

    /** Stable app-generated installation identity (never a hardware/ADB serial). AGENT only. */
    @Column(name = "installation_id", length = 128)
    private String installationId;

    /** SHA-256 hex of the long-lived device session token issued at pairing. Never the raw token. */
    @Column(name = "device_token_hash", length = 128)
    private String deviceTokenHash;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "device_model", length = 80)
    private String deviceModel;

    /** Set when a rep replaces this phone or an admin revokes it. A revoked device's token is
     *  permanently rejected at the WebSocket handshake, even if it is still physically online. */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
