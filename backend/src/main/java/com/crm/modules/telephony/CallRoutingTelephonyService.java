package com.crm.modules.telephony;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The single authoritative call-execution path. CallingService depends only on TelephonyService
 * (never on a concrete provider), and Spring wires exactly this bean in because it is @Primary -
 * so there is one place, and only one place, that decides which provider dials a given call.
 * A device's connectionMode ("AGENT" or "BRIDGE") is set once, at pairing/registration time, so
 * the same call can never be dispatched down both paths.
 */
@Component
@Primary
@RequiredArgsConstructor
public class CallRoutingTelephonyService implements TelephonyService {

    private final PhoneAgentTelephonyService agent;
    private final AndroidBridgeTelephonyService legacyBridge;

    @Override
    public String providerId() { return "ROUTED"; }

    @Override
    public boolean available() { return agent.available() || legacyBridge.available(); }

    @Override
    public InitiationResult initiate(CallCommand cmd) throws TelephonyException {
        return "AGENT".equals(cmd.connectionMode()) ? agent.initiate(cmd) : legacyBridge.initiate(cmd);
    }
}
