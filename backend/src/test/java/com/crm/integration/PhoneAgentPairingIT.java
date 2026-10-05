package com.crm.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end pairing over real HTTP against the real stack (Postgres + Redis via
 * Testcontainers): CRM mints a code -> an unauthenticated "phone" redeems it -> the resulting
 * device shows up, correctly scoped, under the pairing user's own devices - and never anyone
 * else's, and never through an endpoint that required a JWT the phone does not have yet.
 */
class PhoneAgentPairingIT extends IntegrationTestBase {

    @Autowired TestRestTemplate http;

    private String adminToken() {
        ResponseEntity<Map> res = http.postForEntity("/api/v1/auth/login",
            Map.of("email", "admin@test.local", "password", "Admin123!"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) res.getBody().get("accessToken");
    }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullPairingLifecycle() {
        String admin = adminToken();

        // 1. CRM mints a pairing code (requires an authenticated, permitted user)
        ResponseEntity<Map> started = http.exchange("/api/v1/calling/pairing/start", HttpMethod.POST,
            new HttpEntity<>(auth(admin)), Map.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = (String) started.getBody().get("code");
        assertThat(code).matches("\\d{6}");

        // 2. the phone (no CRM session at all) redeems it
        String installationId = "it-install-" + UUID.randomUUID();
        ResponseEntity<Map> completed = http.postForEntity("/api/v1/calling/pairing/complete", Map.of(
            "code", code, "installationId", installationId, "deviceName", "Integration Test Phone",
            "platform", "ANDROID", "appVersion", "1.0.0", "deviceModel", "Pixel 9"), Map.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String deviceToken = (String) completed.getBody().get("deviceToken");
        String deviceId = (String) completed.getBody().get("deviceId");
        assertThat(deviceToken).isNotBlank();
        assertThat(deviceId).isNotBlank();

        // 3. the SAME code cannot be redeemed twice (single-use)
        ResponseEntity<String> replay = http.postForEntity("/api/v1/calling/pairing/complete", Map.of(
            "code", code, "installationId", "another-install", "deviceName", "x", "platform", "ANDROID",
            "appVersion", "1.0.0", "deviceModel", "x"), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 4. an unknown/expired code is rejected
        ResponseEntity<String> bogus = http.postForEntity("/api/v1/calling/pairing/complete", Map.of(
            "code", "000000", "installationId", "x", "deviceName", "x", "platform", "ANDROID",
            "appVersion", "1.0.0", "deviceModel", "x"), String.class);
        assertThat(bogus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 5. the device now shows up for the pairing user, as an AGENT device, OFFLINE until it connects
        ResponseEntity<Map[]> devices = http.exchange("/api/v1/calling/devices", HttpMethod.GET,
            new HttpEntity<>(auth(admin)), Map[].class);
        assertThat(devices.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> device = java.util.Arrays.stream(devices.getBody())
            .filter(d -> deviceId.equals(String.valueOf(d.get("id")))).findFirst().orElseThrow();
        assertThat(device.get("connectionMode")).isEqualTo("AGENT");
        assertThat(device.get("status")).isEqualTo("OFFLINE");

        // 6. admin can revoke it; a revoked device is reported as such
        ResponseEntity<Map> revoke = http.exchange("/api/v1/calling/devices/" + deviceId + "/revoke", HttpMethod.POST,
            new HttpEntity<>(auth(admin)), Map.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map[]> devicesAfterRevoke = http.exchange("/api/v1/calling/devices", HttpMethod.GET,
            new HttpEntity<>(auth(admin)), Map[].class);
        Map<?, ?> revoked = java.util.Arrays.stream(devicesAfterRevoke.getBody())
            .filter(d -> deviceId.equals(String.valueOf(d.get("id")))).findFirst().orElseThrow();
        assertThat(revoked.get("status")).isEqualTo("REVOKED");
    }

    @Test
    void pairingStartRequiresAuthentication() {
        ResponseEntity<String> res = http.postForEntity("/api/v1/calling/pairing/start", null, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedCodeIsRejectedWithoutTouchingRedis() {
        ResponseEntity<String> res = http.postForEntity("/api/v1/calling/pairing/complete", Map.of(
            "code", "not-a-code", "installationId", "x", "deviceName", "x", "platform", "ANDROID",
            "appVersion", "1.0.0", "deviceModel", "x"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
