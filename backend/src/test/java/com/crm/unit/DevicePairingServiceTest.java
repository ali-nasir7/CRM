package com.crm.unit;

import com.crm.common.api.ApiException;
import com.crm.modules.calling.domain.CallingDevice;
import com.crm.modules.calling.repo.CallingDeviceRepository;
import com.crm.modules.calling.service.DevicePairingService;
import com.crm.modules.calling.ws.AgentSessionRegistry;
import com.crm.modules.identity.domain.User;
import com.crm.modules.identity.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the pairing lifecycle without a real Redis/Postgres: an in-memory fake for the
 * StringRedisTemplate value operations reproduces exactly the semantics DevicePairingService
 * depends on (setIfAbsent + TTL, getAndDelete = atomic single read-and-burn).
 */
class DevicePairingServiceTest {

    /** Minimal fake reproducing setIfAbsent/getAndDelete/expire semantics used by the service. */
    static class FakeValueOps {
        final Map<String, String> store = new HashMap<>();
    }

    private StringRedisTemplate redis;
    private FakeValueOps fake;
    private CallingDeviceRepository devices;
    private UserRepository users;
    private AgentSessionRegistry sessions;
    private DevicePairingService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        fake = new FakeValueOps();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            if (fake.store.containsKey(key)) return false;
            fake.store.put(key, value);
            return true;
        });
        when(valueOps.getAndDelete(anyString())).thenAnswer(inv -> fake.store.remove((String) inv.getArgument(0)));

        devices = mock(CallingDeviceRepository.class);
        users = mock(UserRepository.class);
        sessions = mock(AgentSessionRegistry.class);
        service = new DevicePairingService(redis, devices, users, sessions);

        when(users.existsById(userId)).thenReturn(true);
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Rep");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(devices.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(java.util.List.of());
        when(devices.save(any(CallingDevice.class))).thenAnswer(inv -> {
            CallingDevice d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
    }

    @Test
    void startPairing_returnsSixDigitCode() {
        DevicePairingService.PairingStarted started = service.startPairing(orgId, userId);
        assertThat(started.code()).matches("\\d{6}");
        assertThat(started.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void completePairing_withValidCode_registersDeviceAndReturnsRawToken() {
        DevicePairingService.PairingStarted started = service.startPairing(orgId, userId);

        DevicePairingService.PairingCompleted result = service.completePairing(
            started.code(), "install-abc-123", "My Pixel", "ANDROID", "1.0.0", "Pixel 8");

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.userDisplayName()).isEqualTo("Ada Rep");
        ArgumentCaptor<CallingDevice> captor = ArgumentCaptor.forClass(CallingDevice.class);
        verify(devices, atLeastOnce()).save(captor.capture());
        CallingDevice saved = captor.getValue();
        assertThat(saved.getConnectionMode()).isEqualTo("AGENT");
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getInstallationId()).isEqualTo("install-abc-123");
        assertThat(saved.getDeviceTokenHash()).isNotEqualTo(result.rawToken()); // never store the raw token
        assertThat(saved.getStatus()).isEqualTo("OFFLINE"); // ONLINE only once the socket actually connects
    }

    @Test
    void completePairing_isSingleUse() {
        DevicePairingService.PairingStarted started = service.startPairing(orgId, userId);
        service.completePairing(started.code(), "install-1", "Phone", "ANDROID", "1.0.0", "Model");

        assertThatThrownBy(() -> service.completePairing(started.code(), "install-2", "Phone2", "ANDROID", "1.0.0", "Model"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("PAIRING_CODE_EXPIRED");
    }

    @Test
    void completePairing_rejectsMalformedCode() {
        assertThatThrownBy(() -> service.completePairing("abc", "install-1", "Phone", "ANDROID", "1.0.0", "Model"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("PAIRING_CODE_INVALID");
    }

    @Test
    void completePairing_rejectsUnknownCode() {
        assertThatThrownBy(() -> service.completePairing("999999", "install-1", "Phone", "ANDROID", "1.0.0", "Model"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("PAIRING_CODE_EXPIRED");
    }

    @Test
    void authenticateDeviceToken_resolvesRegisteredDevice() {
        String rawToken = "abc.def-token";
        CallingDevice device = new CallingDevice();
        when(devices.findFirstByDeviceTokenHashAndRevokedAtIsNull(DevicePairingService.hash(rawToken)))
            .thenReturn(Optional.of(device));

        assertThat(service.authenticateDeviceToken(rawToken)).isSameAs(device);
        assertThat(service.authenticateDeviceToken("wrong-token")).isNull();
        assertThat(service.authenticateDeviceToken(null)).isNull();
    }

    @Test
    void revoke_marksDeviceRevokedAndDisconnectsSession() {
        UUID deviceId = UUID.randomUUID();
        CallingDevice device = new CallingDevice();
        device.setId(deviceId);
        device.setUserId(userId);
        device.setOrganizationId(orgId);
        when(devices.findOwned(orgId, userId, deviceId)).thenReturn(Optional.of(device));

        service.revoke(orgId, userId, deviceId);

        assertThat(device.getStatus()).isEqualTo("REVOKED");
        assertThat(device.getRevokedAt()).isNotNull();
        assertThat(device.getDeviceTokenHash()).isNull();
        verify(sessions).disconnect(eq(deviceId), anyString());
    }
}
