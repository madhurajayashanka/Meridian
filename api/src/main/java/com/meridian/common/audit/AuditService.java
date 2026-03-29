package com.meridian.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Writes to the audit_logs table for compliance and security tracing.
 * All writes are async so they never block the request path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Async
    public void log(UUID userId, String entityType, UUID entityId,
                    String action, Map<String, Object> changes,
                    String ipAddress, String userAgent) {
        try {
            String changesJson = changes != null ? objectMapper.writeValueAsString(changes) : null;
            jdbc.update(
                """
                INSERT INTO audit_logs
                  (id, user_id, entity_type, entity_id, action, changes, ip_address, user_agent, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?::jsonb, ?::inet, ?, NOW())
                """,
                userId, entityType, entityId, action, changesJson, ipAddress, userAgent
            );
        } catch (Exception e) {
            log.error("Failed to write audit log: entity={}/{} action={}", entityType, entityId, action, e);
        }
    }

    /** Convenience overload without IP/UA (internal service calls). */
    @Async
    public void log(UUID userId, String entityType, UUID entityId, String action) {
        log(userId, entityType, entityId, action, null, null, null);
    }
}
