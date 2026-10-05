package com.crm.modules.calling.service;

import com.crm.common.api.ApiException;
import com.crm.modules.calling.domain.CallingDevice;
import com.crm.modules.calling.repo.CallingDeviceRepository;
import com.crm.modules.calling.ws.AgentSessionRegistry;
import com.crm.modules.identity.domain.User;
import com.crm.modules.identity.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CRM-controlled pairing: turns a 6-digit code shown in the CRM into a registered
 * {@link CallingDevice} with connectionMode=AGENT and its own long-lived device token - never a
 * copy of the user's password or JWT, never an ADB serial. The code lives in Redis only
 * (short-lived, single-use, deleted atomically on redemption); device ownership itself is
 * persisted in Postgres like everything else in the CRM.
 *
 * Trust chain: only an authenticated CRM user can mint a code (startPairing requires
 * CALL_CREATE); only someone who can read that code off the logged-in user's screen can redeem
 * it (completePairing is unauthenticated on purpose - the phone has no CRM session yet - but the
 * code itself is the credential, and it burns after one use or five minutes, whichever first).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePairingService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String REDIS_PREFIX = "phoneagent:pairing:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final CallingDeviceRepository devices;
    private final UserRepository users;
    private final AgentSessionRegistry sessions;

    public record PairingStarted(String code, Instant expiresAt) {}

    public record PairingCompleted(CallingDevice device, String rawToken, String userDisplayName) {}

    @Transactional(readOnly = true)
    public PairingStarted startPairing(UUID orgId, UUID userId) {
        if (!users.existsById(userId)) throw ApiException.notFound("User not found");
        String code;
        String key;
        int attempts = 0;
        do {
            code = String.format("%06d", RANDOM.nextInt(1_000_000));
            key = REDIS_PREFIX + code;
            attempts++;
        } while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, orgId + ":" + userId, CODE_TTL)) && attempts < 10);
        if (attempts >= 10) throw ApiException.business("Could not generate a pairing code right now. Try again.");
        return new PairingStarted(code, Instant.now().plus(CODE_TTL));
    }

    /**
     * Called by the Phone Agent app itself - no CRM session exists yet. The pairing code is the
     * only credential; it is deleted atomically on first read (GETDEL) so it can never be reused,
     * whether redemption succeeds or fails validation afterwards.
     */
    @Transactional
    public PairingCompleted completePairing(String code, String installationId, String deviceName,
                                            String platform, String appVersion, String deviceModel) {
        String normalizedCode = code == null ? "" : code.trim();
        if (!normalizedCode.matches("\\d{6}")) throw ApiException.badRequest("PAIRING_CODE_INVALID: Enter the 6-digit code shown in the CRM");
        if (installationId == null || installationId.isBlank()) throw ApiException.badRequest("Missing device installation id");

        String value = redis.opsForValue().getAndDelete(REDIS_PREFIX + normalizedCode);
        if (value == null) throw ApiException.badRequest("PAIRING_CODE_EXPIRED: This code has expired or was already used. Generate a new one in the CRM.");

        String[] parts = value.split(":", 2);
        UUID orgId = UUID.fromString(parts[0]);
        UUID userId = UUID.fromString(parts[1]);
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));

        CallingDevice device = devices.findByOrganizationIdAndUserIdAndInstallationId(orgId, userId, installationId.trim())
            .orElseGet(CallingDevice::new);
        boolean isNew = device.getId() == null;
        if (isNew) {
            device.setOrganizationId(orgId);
            device.setUserId(userId);
            device.setInstallationId(installationId.trim());
            boolean firstEver = devices.findByOrganizationIdAndUserId(orgId, userId).isEmpty();
            device.setDefault(firstEver);
        }
        String name = deviceName == null || deviceName.isBlank() ? "Phone Agent" : deviceName.trim();
        device.setDeviceName(name.length() > 80 ? name.substring(0, 80) : name);
        device.setPlatform(platform == null || platform.isBlank() ? "ANDROID" : platform.trim());
        device.setAppVersion(appVersion);
        device.setDeviceModel(deviceModel);
        device.setConnectionMode("AGENT");
        device.setRevokedAt(null);
        device.setStatus("OFFLINE"); // flips to ONLINE only once the WebSocket actually connects

        String rawToken = generateToken();
        device.setDeviceTokenHash(hash(rawToken));
        devices.save(device);

        log.info("DEVICE_PAIRED deviceId={} userId={} orgId={} installation={}", device.getId(), userId, orgId,
            installationId.length() > 8 ? installationId.substring(0, 8) + "…" : installationId);
        return new PairingCompleted(device, rawToken, user.displayName());
    }

    /** Resolves a WebSocket handshake's Authorization: Bearer token to its owning device. */
    @Transactional(readOnly = true)
    public CallingDevice authenticateDeviceToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        return devices.findFirstByDeviceTokenHashAndRevokedAtIsNull(hash(rawToken)).orElse(null);
    }

    @Transactional
    public void revoke(UUID orgId, UUID userId, UUID deviceId) {
        CallingDevice d = devices.findOwned(orgId, userId, deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        revokeInternal(d);
    }

    /** Admin/manager path: any device in the organization, not just the caller's own. */
    @Transactional
    public void revokeAsAdmin(UUID orgId, UUID deviceId) {
        CallingDevice d = devices.findInOrg(orgId, deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        revokeInternal(d);
    }

    /** "Test Connection" button on the Connected Device screen / CRM device settings. */
    @Transactional(readOnly = true)
    public boolean testConnection(UUID orgId, UUID userId, UUID deviceId) {
        CallingDevice d = devices.findOwned(orgId, userId, deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
        if (!"AGENT".equals(d.getConnectionMode())) {
            throw ApiException.business("Test Connection is only available for CRM Phone Agent devices.");
        }
        try {
            return sessions.testConnection(d.getId()).get(6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<CallingDevice> listForOrg(UUID orgId) {
        return devices.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }

    private void revokeInternal(CallingDevice d) {
        d.setRevokedAt(Instant.now());
        d.setStatus("REVOKED");
        d.setDeviceTokenHash(null); // belt-and-braces: even a leaked hash can no longer match
        devices.save(d);
        sessions.disconnect(d.getId(), "Device revoked");
        log.info("DEVICE_REVOKED deviceId={} userId={} orgId={}", d.getId(), d.getUserId(), d.getOrganizationId());
    }

    private static String generateToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is always available on the JVM
        }
    }
}
