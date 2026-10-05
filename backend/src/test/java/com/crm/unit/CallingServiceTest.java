package com.crm.unit;

import com.crm.common.api.ApiException;
import com.crm.modules.activity.service.ActivityService;
import com.crm.modules.calling.domain.CallingDevice;
import com.crm.modules.calling.repo.CallingDeviceRepository;
import com.crm.modules.calling.service.CallingService;
import com.crm.modules.calling.ws.AgentSessionRegistry;
import com.crm.modules.calls.domain.Call;
import com.crm.modules.calls.repo.CallRepository;
import com.crm.modules.identity.repo.UserRepository;
import com.crm.modules.leads.service.LeadAccessPolicy;
import com.crm.modules.tasks.service.TaskService;
import com.crm.modules.telephony.BridgeHealthProbe;
import com.crm.modules.telephony.TelephonyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Covers CallingService.initiate()'s dispatch guards: this is the ONLY place that decides
 *  whether a device is allowed to be asked to dial at all, for both calling paths. */
class CallingServiceTest {

    private CallingDeviceRepository devices;
    private CallRepository calls;
    private UserRepository users;
    private LeadAccessPolicy leadAccess;
    private ActivityService activities;
    private TaskService taskService;
    private TelephonyService telephony;
    private BridgeHealthProbe bridgeProbe;
    private AgentSessionRegistry agentSessions;
    private CallingService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        devices = mock(CallingDeviceRepository.class);
        calls = mock(CallRepository.class);
        users = mock(UserRepository.class);
        leadAccess = mock(LeadAccessPolicy.class);
        activities = mock(ActivityService.class);
        taskService = mock(TaskService.class);
        telephony = mock(TelephonyService.class);
        bridgeProbe = mock(BridgeHealthProbe.class);
        agentSessions = mock(AgentSessionRegistry.class);
        service = new CallingService(devices, calls, users, leadAccess, activities, taskService, telephony, bridgeProbe, agentSessions);
        when(calls.save(any(Call.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CallingDevice agentDevice(boolean online) {
        CallingDevice d = new CallingDevice();
        d.setId(UUID.randomUUID());
        d.setOrganizationId(orgId);
        d.setUserId(userId);
        d.setDeviceName("Pixel");
        d.setConnectionMode("AGENT");
        d.setDefault(true);
        when(agentSessions.isOnline(d.getId())).thenReturn(online);
        return d;
    }

    @Test
    void initiate_throwsWhenNoDeviceRegistered() {
        when(devices.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.initiate(orgId, userId, null, null, "+15551234567"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("NO_PHONE_REGISTERED");
    }

    @Test
    void initiate_throwsWhenDeviceOffline() {
        CallingDevice device = agentDevice(false);
        when(devices.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(List.of(device));

        assertThatThrownBy(() -> service.initiate(orgId, userId, null, null, "+15551234567"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("PHONE_OFFLINE");
    }

    @Test
    void initiate_throwsWhenDeviceRevoked() {
        CallingDevice device = agentDevice(true);
        device.setRevokedAt(Instant.now());
        when(devices.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(List.of(device));

        assertThatThrownBy(() -> service.initiate(orgId, userId, null, null, "+15551234567"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("PHONE_REVOKED");
    }

    @Test
    void initiate_throwsWhenDeviceAlreadyMidCall() {
        CallingDevice device = agentDevice(true);
        when(devices.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(List.of(device));
        when(calls.existsByOrganizationIdAndDeviceIdAndStatusIn(orgId, device.getId(),
            List.of("INITIATING", "RINGING", "CONNECTED"))).thenReturn(true);

        assertThatThrownBy(() -> service.initiate(orgId, userId, null, null, "+15551234567"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("CALL_ALREADY_PROCESSED");

        verifyNoInteractions(telephony);
    }

    @Test
    void initiate_rejectsInvalidPhoneNumber() {
        assertThatThrownBy(() -> service.initiate(orgId, userId, null, null, "not-a-number"))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(devices);
    }

    @Test
    void initiate_happyPath_dispatchesThroughTelephonyWithAgentConnectionMode() throws Exception {
        CallingDevice device = agentDevice(true);
        when(devices.findByOrganizationIdAndUserId(orgId, userId)).thenReturn(List.of(device));
        when(calls.existsByOrganizationIdAndDeviceIdAndStatusIn(any(), any(), anyList())).thenReturn(false);
        when(telephony.initiate(any())).thenReturn(new TelephonyService.InitiationResult("call-ref-1", "RINGING"));

        Call result = service.initiate(orgId, userId, null, null, "+15551234567");

        assertThat(result.getStatus()).isEqualTo("INITIATING");
        assertThat(result.getProviderRef()).isEqualTo("call-ref-1");
        assertThat(result.getDeviceId()).isEqualTo(device.getId());

        ArgumentCaptor<TelephonyService.CallCommand> captor = ArgumentCaptor.forClass(TelephonyService.CallCommand.class);
        verify(telephony).initiate(captor.capture());
        assertThat(captor.getValue().connectionMode()).isEqualTo("AGENT");
        assertThat(captor.getValue().callId()).isNotNull();
        assertThat(captor.getValue().toNumber()).isEqualTo("+15551234567");
    }

    @Test
    void endCall_finalizesAnInProgressCallAndReleasesTheDeviceLock() {
        Call call = new Call();
        call.setId(UUID.randomUUID());
        call.setOrganizationId(orgId);
        call.setUserId(userId);
        call.setStatus("INITIATING");
        call.setStartedAt(Instant.now().minusSeconds(30));
        when(calls.findById(call.getId())).thenReturn(Optional.of(call));

        Call result = service.endCall(orgId, userId, call.getId());

        assertThat(result.getStatus()).isEqualTo("ENDED");
        assertThat(result.getOutcome()).isEqualTo("CONNECTED");
        assertThat(result.getEndedAt()).isNotNull();
        verify(calls).save(call);
    }

    @Test
    void endCall_isIdempotentForAnAlreadyEndedCall() {
        Call call = new Call();
        call.setId(UUID.randomUUID());
        call.setOrganizationId(orgId);
        call.setUserId(userId);
        call.setStatus("ENDED");
        call.setOutcome("NO_ANSWER");
        when(calls.findById(call.getId())).thenReturn(Optional.of(call));

        Call result = service.endCall(orgId, userId, call.getId());

        assertThat(result.getOutcome()).isEqualTo("NO_ANSWER"); // untouched, not overwritten with the sentinel
        verify(calls, never()).save(any());
    }

    @Test
    void endCall_rejectsCallsBelongingToAnotherUser() {
        Call call = new Call();
        call.setId(UUID.randomUUID());
        call.setOrganizationId(orgId);
        call.setUserId(UUID.randomUUID()); // different user
        when(calls.findById(call.getId())).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> service.endCall(orgId, userId, call.getId()))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void agentDisconnected_leavesRevokedDevicesRevoked() {
        UUID deviceId = UUID.randomUUID();
        CallingDevice device = new CallingDevice();
        device.setId(deviceId);
        device.setStatus("REVOKED");
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));

        service.agentDisconnected(deviceId);

        assertThat(device.getStatus()).isEqualTo("REVOKED"); // not silently flipped back to OFFLINE
    }
}
