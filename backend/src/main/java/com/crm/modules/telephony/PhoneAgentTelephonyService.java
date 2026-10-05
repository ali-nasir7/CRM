package com.crm.modules.telephony;

import com.crm.modules.calling.ws.AgentSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PRIMARY production provider: dials through the CRM Phone Agent Android app over its live
 * WebSocket connection (see com.crm.modules.calling.ws). No adb, no USB debugging, no per-phone
 * .env - the device is identified purely by the CallingDevice row its token was issued for.
 *
 * initiate() only ever sends ONE message per invocation (no internal retry loop), so the sole
 * duplication vector is the caller invoking initiate() twice - CallingService prevents that with
 * its "device already has a call in progress" guard before this class is ever reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneAgentTelephonyService implements TelephonyService {

    public static final String PROVIDER_ID = "PHONE_AGENT_V1";

    private final AgentSessionRegistry sessions;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String providerId() { return PROVIDER_ID; }

    @Override
    public boolean available() { return true; } // per-device online check happens in initiate()

    @Override
    public InitiationResult initiate(CallCommand cmd) throws TelephonyException {
        if (cmd.deviceId() == null || cmd.callId() == null) {
            throw new TelephonyException("CALL_REQUEST_INVALID: missing device or call id");
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "CALL_REQUEST");
        message.put("callId", cmd.callId().toString());
        message.put("phoneNumber", cmd.toNumber());
        if (cmd.customerName() != null) message.put("customerName", cmd.customerName());

        String json;
        try {
            json = mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new TelephonyException("CALL_EXECUTION_FAILED: could not encode call command");
        }
        boolean sent = sessions.send(cmd.deviceId(), json);
        if (!sent) {
            throw new TelephonyException("PHONE_OFFLINE: Your phone is offline. Open CRM Phone Agent and reconnect.");
        }
        log.info("CALL_REQUEST_SENT deviceId={} callId={}", cmd.deviceId(), cmd.callId());
        return new InitiationResult(cmd.callId().toString(), "RINGING");
    }
}
