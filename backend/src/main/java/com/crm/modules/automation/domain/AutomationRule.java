package com.crm.modules.automation.domain;

import com.crm.common.domain.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Workflow automation rule (§41/§42). Trigger + optional conditions + actions.
 * The visual workflow builder is a planned frontend feature; the backend evaluates these rules today.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "automations", indexes = @Index(name = "ix_automations_org", columnList = "organization_id"))
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "organization_id = CAST(:orgId AS uuid)")
public class AutomationRule extends TenantEntity {

    public enum Trigger { LEAD_CREATED, LEAD_STAGE_CHANGED, CALL_LOGGED, EMAIL_SENT, NO_REPLY_AFTER, TASK_OVERDUE }

    public enum Action { CREATE_TASK, ADD_TAG, NOTIFY, CHANGE_STAGE, SEND_EMAIL }

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    
    @Column(name = "trigger_type", nullable = false, length = 24)
    private Trigger trigger;

    /** Trigger-specific conditions, e.g. {"stageName":"Contacted"} or {"days":3}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> conditions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Action action;

    /** Action payload: {"title":"...", "dueInDays":2, "tag":"...", "stageName":"...", "message":"..."} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_config", columnDefinition = "jsonb")
    private Map<String, Object> actionConfig;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "run_count", nullable = false)
    private int runCount;
}
