package com.crm.modules.calling.service;

import com.crm.common.api.ApiException;
import com.crm.modules.activity.domain.ActivityType;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.calling.domain.CallingDevice;
import com.crm.modules.calling.repo.CallingDeviceRepository;
import com.crm.modules.calling.ws.AgentSessionRegistry;
import com.crm.modules.calls.domain.Call;
import com.crm.modules.calls.repo.CallRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.telephony.TelephonyService;
import com.crm.modules.tasks.dto.TaskDtos.CreateTaskRequest;
import com.crm.modules.tasks.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-specific cellular calling: "call this number with MY phone".
 * Devices belong to exactly one user; nobody can ring somebody else's device.
 * The bridge reports state changes asynchronously; terminal states finalize the
 * Call row (timestamps, duration, outcome) and append the activity-timeline entry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallingService {

    /** A device counts as ONLINE only with a fresh heartbeat. */
    private static final Duration HEARTBEAT_FRESHNESS = Duration.ofMinutes(2);

    public static final List<String> OUTCOMES = List.of(
        "NO_ANSWER", "BUSY", "WRONG_NUMBER", "INTERESTED", "NOT_INTERESTED", "CALL_BACK_LATER", "MEETING_BOOKED");

    private final CallingDeviceRepository devices;
    private final CallRepository calls;
    private final UserRepository users;
    private final LeadAccessPolicy leadAccess;
    private final ActivityService activities;
    private final TaskService taskService;
    private final TelephonyService telephony;
    private final com.crm.modules.telephony.BridgeHealthProbe bridgeProbe;
    private final AgentSessionRegistry agentSessions;

    @Value("${crm.bridge.token:}")
    private String bridgeToken;

    // ---- devices ------------------------------------------------------------

    @Transactional
    public CallingDevice register(UUID orgId, UUID userId, String deviceName, String phoneNumber, String platform) {
        String name = deviceName == null ? "" : deviceName.trim();
        if (name.isEmpty() || name.length() > 80) throw ApiException.badRequest("Device name is required (max 80 chars)");
        boolean first = devices.findByOrganizationIdAndUserId(orgId, userId).isEmpty();
        CallingDevice d = new CallingDevice();
        d.setOrganizationId(orgId);
        d.setUserId(userId);
        d.setDeviceName(name);
        d.setPhoneNumber(phoneNumber);
        d.setPlatform(platform == null ? "ANDROID" : platform);
        d.setStatus("OFFLINE");
        d.setDefault(first);
        return devices.save(d);
    }

    @Transactional(readOnly = true)
    public List<CallingDevice> list(UUID orgId, UUID userId) {
        List<CallingDevice> out = devices.findByOrganizationIdAndUserIdOrderByCreatedAtAsc(orgId, userId);
        out.forEach(this::applyEffectiveStatus);
        return out;
    }

    /**
     * Diagnostics for the "Call health" panel (Calls page). Read-only: probes the global
     * bridge /status (if crm.bridge.base-url is set) and reports this user's devices with
     * their heartbeat freshness. Never returns the bridge token or URLs.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> health(UUID orgId, UUID userId) {
        List<CallingDevice> mine = devices.findByOrganizationIdAndUserIdOrderByCreatedAtAsc(orgId, userId);
        mine.forEach(this::applyEffectiveStatus);

        Map<String, Object> bridge = bridgeProbe.probe();

        String mode;
        if (Boolean.TRUE.equals(bridge.get("configured"))) {
            mode = "GLOBAL_BRIDGE";
        } else if (mine.stream().anyMatch(d -> d.getBridgeUrl() != null && !d.getBridgeUrl().isBlank())) {
            // Per-device bridge: the backend only knows heartbeat freshness, it may not be
            // able to reach the device-announced URL itself, so we do not probe it here.
            mode = "DEVICE_BRIDGE";
        } else {
            mode = "NOT_CONFIGURED";
        }

        List<Map<String, Object>> deviceItems = mine.stream().map(d -> {
            Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("id", d.getId());
            m.put("deviceName", d.getDeviceName());
            m.put("platform", d.getPlatform());
            m.put("status", d.getStatus());
            m.put("lastSeenAt", d.getLastSeenAt());
            m.put("isDefault", d.isDefault());
            m.put("bridgeUrlSet", d.getBridgeUrl() != null && !d.getBridgeUrl().isBlank());
            return m;
        }).toList();

        Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
        out.put("mode", mode);
        out.put("bridge", bridge);
        out.put("devices", deviceItems);
        return out;
    }

    @Transactional
    public void delete(UUID orgId, UUID userId, UUID deviceId) {
        CallingDevice d = devices.findOwned(orgId, userId, deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        devices.delete(d);
    }

    @Transactional
    public CallingDevice setDefault(UUID orgId, UUID userId, UUID deviceId) {
        CallingDevice d = devices.findOwned(orgId, userId, deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        for (CallingDevice other : devices.findByOrganizationIdAndUserId(orgId, userId)) {
            other.setDefault(other.getId().equals(d.getId()));
            devices.save(other);
        }
        return d;
    }

    /** Liveness ping from the bridge app (shared token) or the device owner while testing. */
    @Transactional
    public void heartbeat(UUID deviceId, UUID ownerCheck) {
        CallingDevice d = devices.findById(deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        if (ownerCheck != null && !d.getUserId().equals(ownerCheck)) {
            throw ApiException.forbidden("This device belongs to another user");
        }
        d.setLastSeenAt(Instant.now());
        d.setStatus("ONLINE");
        devices.save(d);
    }

    /**
     * Liveness + address announcement from the bridge app itself (shared bridge token, no
     * user JWT - this runs headless on the user's PC). Marks the device ONLINE while the
     * bridge can actually reach the phone, and stores the URL the backend must dial through.
     * Trust model: whoever holds CRM_BRIDGE_TOKEN may set this; treat that token as a secret.
     */
    @Transactional
    public void bridgeHeartbeat(UUID deviceId, String url) {
        CallingDevice d = devices.findById(deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found for bridge heartbeat"));
        d.setLastSeenAt(Instant.now());
        d.setStatus("ONLINE");
        if (url != null && !url.isBlank()) d.setBridgeUrl(url.trim());
        devices.save(d);
    }

    // ---- calling ------------------------------------------------------------

    @Transactional
    public Call initiate(UUID orgId, UUID userId, UUID leadId, UUID requestedDeviceId, String rawNumber) {
        String number = rawNumber == null ? "" : rawNumber.replaceAll("[\\s\\-()]", "");
        if (!number.matches("\\+?\\d{7,15}")) {
            throw ApiException.badRequest("Enter a valid phone number (7-15 digits, optional + prefix)");
        }
        List<CallingDevice> mine = devices.findByOrganizationIdAndUserId(orgId, userId);
        if (mine.isEmpty()) {
            throw ApiException.business("NO_PHONE_REGISTERED: No phone connected. Please connect your CRM Phone Agent, "
                + "or add a legacy bridge device under Calls > My calling devices.");
        }
        CallingDevice device = requestedDeviceId != null
            ? devices.findOwned(orgId, userId, requestedDeviceId)
                .orElseThrow(() -> ApiException.forbidden("Device not found for your account"))
            : mine.stream().filter(CallingDevice::isDefault).findFirst().orElse(mine.get(0));
        if (device.getRevokedAt() != null) {
            throw ApiException.business("PHONE_REVOKED: \"" + device.getDeviceName() + "\" has been disconnected and can no longer place calls.");
        }
        applyEffectiveStatus(device);
        if (!"ONLINE".equals(device.getStatus())) {
            throw ApiException.business("AGENT".equals(device.getConnectionMode())
                ? "PHONE_OFFLINE: Your phone is offline. Open CRM Phone Agent and reconnect."
                : "PHONE_OFFLINE: Calling device \"" + device.getDeviceName() + "\" is offline. Start the bridge app, or pick another device.");
        }
        // Idempotency: one authoritative call at a time per device, regardless of which path
        // dials it. Collapses double-clicks and retried requests into a single real dial.
        if (calls.existsByOrganizationIdAndDeviceIdAndStatusIn(orgId, device.getId(),
                List.of("INITIATING", "RINGING", "CONNECTED"))) {
            throw ApiException.business("CALL_ALREADY_PROCESSED: This device already has a call in progress.");
        }
        String customerName = null;
        if (leadId != null) {
            customerName = leadAccess.loadVisible(orgId, leadId).getBusinessName(); // 404-masked visibility check
        }

        TelephonyService.InitiationResult result;
        try {
            result = telephony.initiate(new TelephonyService.CallCommand(
                orgId, userId, device.getId(), device.getDeviceName(), device.getPhoneNumber(),
                number, customerName, device.getBridgeUrl(), device.getConnectionMode(), UUID.randomUUID()));
        } catch (TelephonyService.TelephonyException e) {
            throw ApiException.business(e.getMessage());
        }

        Instant now = Instant.now();
        Call call = new Call();
        call.setOrganizationId(orgId);
        call.setUserId(userId);
        call.setLeadId(leadId);
        call.setDeviceId(device.getId());
        call.setDirection("OUTGOING");
        call.setOccurredAt(now);
        call.setStartedAt(now);
        call.setStatus("INITIATING");
        call.setOutcome("IN_PROGRESS");
        call.setProviderRef(result.providerRef());
        call.setNotes(number);
        calls.save(call);
        return call;
    }

    /** Bridge callback: RINGING, CONNECTED, ENDED, NO_ANSWER, BUSY, FAILED. */
    @Transactional
    public void applyBridgeState(String providerRef, String state) {
        Call call = calls.findFirstByProviderRef(providerRef)
            .orElseThrow(() -> ApiException.notFound("Call not found for bridge reference"));
        Instant now = Instant.now();
        switch (state == null ? "" : state.toUpperCase()) {
            case "RINGING" -> call.setStatus("RINGING");
            case "CONNECTED" -> { call.setStatus("CONNECTED"); call.setAnsweredAt(now); }
            case "ENDED" -> finalizeCall(call, "CONNECTED", now);
            case "NO_ANSWER" -> finalizeCall(call, "NO_ANSWER", now);
            case "BUSY" -> finalizeCall(call, "BUSY", now);
            case "FAILED" -> finalizeCall(call, "FAILED", now);
            default -> throw ApiException.badRequest("Unknown call state: " + state);
        }
        calls.save(call);
    }

    @Transactional(readOnly = true)
    public Call stateFor(UUID orgId, UUID userId, UUID callId) {
        return calls.findById(callId)
            .filter(c -> c.getOrganizationId().equals(orgId) && c.getUserId().equals(userId))
            .orElseThrow(() -> ApiException.notFound("Call not found"));
    }

    /**
     * Rep-initiated "End call": lets the rep unstick their device from the CRM side without
     * waiting on (or being blocked forever by) a device-reported state change - the device may
     * be offline, mid-reconnect, or simply never sent one for this call. Idempotent: ending an
     * already-ENDED call is a no-op. Reuses the same "CONNECTED" outcome sentinel the automatic
     * bridge/agent ENDED path already uses (see applyBridgeState) so the frontend's existing
     * "ask the rep what actually happened" outcome picker shows up either way.
     */
    @Transactional
    public Call endCall(UUID orgId, UUID userId, UUID callId) {
        Call call = calls.findById(callId)
            .filter(c -> c.getOrganizationId().equals(orgId) && c.getUserId().equals(userId))
            .orElseThrow(() -> ApiException.notFound("Call not found"));
        if (!"ENDED".equals(call.getStatus())) {
            finalizeCall(call, "CONNECTED", Instant.now());
            calls.save(call);
        }
        return call;
    }

    /** Rep sets the business outcome after the call (and optionally books a follow-up). */
    @Transactional
    public Call finalize(UUID orgId, UUID userId, UUID callId, String outcome, String notes,
                         boolean createFollowUp, java.time.Instant followUpDueAt) {
        Call call = calls.findById(callId)
            .filter(c -> c.getOrganizationId().equals(orgId) && c.getUserId().equals(userId))
            .orElseThrow(() -> ApiException.notFound("Call not found"));
        if (!OUTCOMES.contains(outcome)) throw ApiException.badRequest("Invalid call outcome: " + outcome);
        finalizeCall(call, outcome, Instant.now());
        if (notes != null) call.setNotes(notes);
        calls.save(call);

        if (call.getLeadId() != null) {
            activities.record(orgId,
                ActivityType.OUTGOING_CALL,
                call.getLeadId(),
                "Call: " + outcome,
                notes,
                Map.of("outcome", outcome,
                       "durationSeconds", call.getDurationSeconds() == null ? 0 : call.getDurationSeconds(),
                       "device", String.valueOf(call.getDeviceId())),
                userId);
        }
        if (createFollowUp && "CALL_BACK_LATER".equals(outcome)) {
            String title = "Call back" + (call.getLeadId() != null ? " lead" : " customer") + " (from call " + call.getId() + ")";
            taskService.create(orgId, userId, new CreateTaskRequest(
                title, "Follow-up created from call outcome CALL_BACK_LATER",
                call.getLeadId(), null, call.getContactId(), "FOLLOW_UP",
                userId, followUpDueAt != null ? followUpDueAt : Instant.now().plus(Duration.ofDays(1)),
                "MEDIUM"));
        }
        return call;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics(UUID orgId, UUID userId, int days) {
        Instant from = Instant.now().minus(Duration.ofDays(days));
        Map<String, Object> m = new LinkedHashMap<>();
        long total = 0;
        long totalSeconds = 0;
        for (String outcome : OUTCOMES) {
            long n = calls.countByOrganizationIdAndUserIdAndOutcomeAndOccurredAtBetween(orgId, userId, outcome, from, Instant.now());
            m.put(outcome, n);
            total += n;
        }
        for (Call c : calls.findByOrganizationIdAndUserIdAndOccurredAtBetween(orgId, userId, from, Instant.now())) {
            if (c.getDurationSeconds() != null) totalSeconds += c.getDurationSeconds();
        }
        m.put("total", total);
        m.put("totalDurationSeconds", totalSeconds);
        return m;
    }

    /** Shared-token check for the bridge callback endpoint. Blank token = endpoint disabled. */
    public boolean bridgeTokenValid(String presented) {
        if (bridgeToken == null || bridgeToken.isBlank() || presented == null) return false;
        return MessageDigest.isEqual(
            bridgeToken.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private void finalizeCall(Call call, String outcome, Instant endedAt) {
        call.setStatus("ENDED");
        call.setEndedAt(endedAt);
        call.setOutcome(outcome);
        if (call.getStartedAt() != null) {
            call.setDurationSeconds((int) Duration.between(call.getStartedAt(), endedAt).getSeconds());
        }
        if (call.getAnsweredAt() == null && "CONNECTED".equals(outcome)) call.setAnsweredAt(call.getStartedAt());
    }

    private void applyEffectiveStatus(CallingDevice d) {
        if (d.getRevokedAt() != null) { d.setStatus("REVOKED"); return; }
        if ("AGENT".equals(d.getConnectionMode())) {
            // The live WebSocket connection map is authoritative for Phone Agent devices - not a
            // heartbeat freshness window - so a dropped connection reads OFFLINE immediately
            // instead of staying falsely ONLINE for up to HEARTBEAT_FRESHNESS.
            d.setStatus(agentSessions.isOnline(d.getId()) ? "ONLINE" : "OFFLINE");
            return;
        }
        boolean fresh = d.getLastSeenAt() != null && d.getLastSeenAt().isAfter(Instant.now().minus(HEARTBEAT_FRESHNESS));
        d.setStatus(fresh ? "ONLINE" : "OFFLINE");
    }

    // ---- Phone Agent presence (called from the WebSocket layer only) ------------------------

    @Transactional
    public void agentConnected(UUID deviceId) {
        devices.findById(deviceId).ifPresent(d -> {
            d.setStatus("ONLINE");
            d.setLastSeenAt(Instant.now());
            devices.save(d);
        });
        log.info("DEVICE_CONNECTED deviceId={}", deviceId);
    }

    @Transactional
    public void agentDisconnected(UUID deviceId) {
        devices.findById(deviceId).ifPresent(d -> {
            if (!"REVOKED".equals(d.getStatus())) d.setStatus("OFFLINE");
            devices.save(d);
        });
        log.info("DEVICE_DISCONNECTED deviceId={}", deviceId);
    }

    @Transactional
    public void agentHeartbeat(UUID deviceId) {
        devices.findById(deviceId).ifPresent(d -> {
            d.setLastSeenAt(Instant.now());
            devices.save(d);
        });
    }

    /** Applies inbound {reportedAppVersion, reportedModel} learned at connect time (best-effort). */
    @Transactional
    public void updateAgentMeta(UUID deviceId, String appVersion, String deviceModel) {
        devices.findById(deviceId).ifPresent(d -> {
            if (appVersion != null && !appVersion.isBlank()) d.setAppVersion(appVersion);
            if (deviceModel != null && !deviceModel.isBlank()) d.setDeviceModel(deviceModel);
            devices.save(d);
        });
    }
}
