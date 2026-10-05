package com.crm.unit;

import com.crm.modules.telephony.AndroidBridgeTelephonyService;
import com.crm.modules.telephony.CallRoutingTelephonyService;
import com.crm.modules.telephony.PhoneAgentTelephonyService;
import com.crm.modules.telephony.TelephonyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Guards the "never dispatched down both paths" invariant: exactly one concrete provider is
 * invoked per call, chosen solely by the device's persisted connectionMode - never by which
 * beans happen to be configured, request order, or any other incidental state.
 */
class CallRoutingTelephonyServiceTest {

    private PhoneAgentTelephonyService agent;
    private AndroidBridgeTelephonyService legacyBridge;
    private CallRoutingTelephonyService router;

    @BeforeEach
    void setup() {
        agent = mock(PhoneAgentTelephonyService.class);
        legacyBridge = mock(AndroidBridgeTelephonyService.class);
        router = new CallRoutingTelephonyService(agent, legacyBridge);
    }

    private TelephonyService.CallCommand commandWithMode(String mode) {
        return new TelephonyService.CallCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "Device", "+10000000000", "+19999999999", "Customer", null, mode, UUID.randomUUID());
    }

    @Test
    void agentModeRoutesOnlyToPhoneAgent() throws Exception {
        TelephonyService.CallCommand cmd = commandWithMode("AGENT");
        when(agent.initiate(cmd)).thenReturn(new TelephonyService.InitiationResult(cmd.callId().toString(), "RINGING"));

        router.initiate(cmd);

        verify(agent, times(1)).initiate(cmd);
        verify(legacyBridge, never()).initiate(any());
    }

    @Test
    void bridgeModeRoutesOnlyToLegacyBridge() throws Exception {
        TelephonyService.CallCommand cmd = commandWithMode("BRIDGE");
        when(legacyBridge.initiate(cmd)).thenReturn(new TelephonyService.InitiationResult("bridge-ref", "RINGING"));

        router.initiate(cmd);

        verify(legacyBridge, times(1)).initiate(cmd);
        verify(agent, never()).initiate(any());
    }
}
