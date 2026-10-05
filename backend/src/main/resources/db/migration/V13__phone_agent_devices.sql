-- V13: CRM Phone Agent (WebSocket-connected Android app) as a first-class device type,
-- alongside the existing ADB/HTTP bridge. A calling_devices row now carries a
-- connection_mode: BRIDGE (legacy adb bridge, default - zero behavior change for existing
-- rows) or AGENT (paired Phone Agent app, authenticated by its own device token, never a
-- CRM user password/JWT). installation_id/device_token_hash/app_version/device_model are
-- only populated for AGENT devices. revoked_at marks a device permanently unusable even if
-- its token leaks; the token itself is never stored in plaintext.

ALTER TABLE calling_devices ADD COLUMN connection_mode VARCHAR(16) NOT NULL DEFAULT 'BRIDGE';
ALTER TABLE calling_devices ADD COLUMN installation_id VARCHAR(128);
ALTER TABLE calling_devices ADD COLUMN device_token_hash VARCHAR(128);
ALTER TABLE calling_devices ADD COLUMN app_version VARCHAR(32);
ALTER TABLE calling_devices ADD COLUMN device_model VARCHAR(80);
ALTER TABLE calling_devices ADD COLUMN revoked_at TIMESTAMPTZ;

-- One row per installation (a phone re-pairing to the same account updates its existing
-- row instead of creating a duplicate); NULL is fine for legacy BRIDGE rows (multiple NULLs
-- are allowed by a unique index in Postgres).
CREATE UNIQUE INDEX uk_calling_devices_installation ON calling_devices (installation_id);

-- Device-token lookups happen on every WebSocket handshake, so this must be indexed.
CREATE INDEX ix_calling_devices_token_hash ON calling_devices (device_token_hash);
