package com.crm.modules.identity.service;

import com.crm.modules.identity.domain.DataScope;
import com.crm.modules.identity.domain.Permission;
import com.crm.modules.identity.domain.Role;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Creates the five seeded system roles with their default permission sets and data scopes. */
public final class RoleFactory {
    private RoleFactory() {}

    public record RoleSpec(String key, String name, String description, DataScope scope, List<String> permissions) {}

    public static List<RoleSpec> systemRoles() {
        return List.of(
            new RoleSpec("SUPER_ADMIN", "Super Admin", "Unrestricted system access", DataScope.ORG, PermissionKeys.keys()),
            new RoleSpec("ADMIN", "Administrator", "Full business administration", DataScope.ORG,
                PermissionKeys.keys().stream().filter(k -> !k.equals(PermissionKeys.ROLE_DELETE)).toList()),
            new RoleSpec("SALES_MANAGER", "Sales Manager", "Manages a sales team", DataScope.TEAM,
                List.of(PermissionKeys.LEAD_VIEW, PermissionKeys.LEAD_CREATE, PermissionKeys.LEAD_UPDATE, PermissionKeys.LEAD_DELETE,
                    PermissionKeys.LEAD_ASSIGN, PermissionKeys.LEAD_EXPORT, PermissionKeys.LEAD_CONVERT,
                    PermissionKeys.LEAD_IMPORT, PermissionKeys.IMPORT_VIEW,
                    PermissionKeys.COMPANY_VIEW, PermissionKeys.COMPANY_CREATE, PermissionKeys.COMPANY_UPDATE,
                    PermissionKeys.CONTACT_VIEW, PermissionKeys.CONTACT_CREATE, PermissionKeys.CONTACT_UPDATE,
                    PermissionKeys.CALL_VIEW, PermissionKeys.CALL_CREATE,
                    PermissionKeys.TASK_VIEW, PermissionKeys.TASK_CREATE, PermissionKeys.TASK_UPDATE, PermissionKeys.TASK_DELETE,
                    PermissionKeys.MEETING_VIEW, PermissionKeys.MEETING_CREATE, PermissionKeys.MEETING_UPDATE,
                    PermissionKeys.EMAIL_VIEW, PermissionKeys.EMAIL_SEND, PermissionKeys.EMAIL_ACCOUNT_VIEW,
                    PermissionKeys.EMAIL_ACCOUNT_CREATE, PermissionKeys.EMAIL_ACCOUNT_UPDATE, PermissionKeys.EMAIL_ACCOUNT_DELETE,
                    PermissionKeys.TEMPLATE_VIEW, PermissionKeys.TEMPLATE_CREATE, PermissionKeys.TEMPLATE_UPDATE,
                    PermissionKeys.CAMPAIGN_VIEW, PermissionKeys.CAMPAIGN_CREATE, PermissionKeys.CAMPAIGN_UPDATE, PermissionKeys.CAMPAIGN_SEND,
                    PermissionKeys.DEAL_VIEW, PermissionKeys.DEAL_CREATE, PermissionKeys.DEAL_UPDATE,
                    PermissionKeys.PROPOSAL_VIEW, PermissionKeys.PROPOSAL_CREATE, PermissionKeys.PROPOSAL_UPDATE, PermissionKeys.PROPOSAL_SEND,
                    PermissionKeys.CLIENT_VIEW, PermissionKeys.CLIENT_UPDATE,
                    PermissionKeys.DOCUMENT_VIEW, PermissionKeys.DOCUMENT_CREATE,
                    PermissionKeys.PIPELINE_VIEW, PermissionKeys.PIPELINE_UPDATE,
                    PermissionKeys.TAG_VIEW, PermissionKeys.SOURCE_VIEW,
                    PermissionKeys.SCORING_VIEW, PermissionKeys.AUTOMATION_VIEW, PermissionKeys.AI_USE, PermissionKeys.REPORT_VIEW,
                    PermissionKeys.USER_VIEW, PermissionKeys.TEAM_VIEW, PermissionKeys.TEAM_CREATE, PermissionKeys.TEAM_UPDATE,
                    PermissionKeys.ROLE_VIEW, PermissionKeys.ORG_VIEW, PermissionKeys.SETTINGS_VIEW)),
            new RoleSpec("SALES_REP", "Sales Representative", "Works assigned leads", DataScope.OWN,
                List.of(PermissionKeys.LEAD_VIEW, PermissionKeys.LEAD_CREATE, PermissionKeys.LEAD_UPDATE, PermissionKeys.LEAD_CONVERT,
                    PermissionKeys.LEAD_IMPORT, PermissionKeys.IMPORT_VIEW,
                    PermissionKeys.COMPANY_VIEW, PermissionKeys.COMPANY_CREATE, PermissionKeys.COMPANY_UPDATE,
                    PermissionKeys.CONTACT_VIEW, PermissionKeys.CONTACT_CREATE, PermissionKeys.CONTACT_UPDATE,
                    PermissionKeys.CALL_VIEW, PermissionKeys.CALL_CREATE,
                    PermissionKeys.TASK_VIEW, PermissionKeys.TASK_CREATE, PermissionKeys.TASK_UPDATE,
                    PermissionKeys.MEETING_VIEW, PermissionKeys.MEETING_CREATE, PermissionKeys.MEETING_UPDATE,
                    PermissionKeys.EMAIL_VIEW, PermissionKeys.EMAIL_SEND, PermissionKeys.EMAIL_ACCOUNT_VIEW,
                    PermissionKeys.EMAIL_ACCOUNT_CREATE, PermissionKeys.EMAIL_ACCOUNT_UPDATE, PermissionKeys.EMAIL_ACCOUNT_DELETE,
                    PermissionKeys.TEMPLATE_VIEW, PermissionKeys.CAMPAIGN_VIEW,
                    PermissionKeys.DEAL_VIEW, PermissionKeys.DEAL_CREATE, PermissionKeys.DEAL_UPDATE,
                    PermissionKeys.PROPOSAL_VIEW, PermissionKeys.PROPOSAL_CREATE, PermissionKeys.PROPOSAL_UPDATE, PermissionKeys.PROPOSAL_SEND,
                    PermissionKeys.CLIENT_VIEW, PermissionKeys.DOCUMENT_VIEW, PermissionKeys.DOCUMENT_CREATE,
                    PermissionKeys.PIPELINE_VIEW, PermissionKeys.TAG_VIEW, PermissionKeys.SOURCE_VIEW,
                    PermissionKeys.AI_USE, PermissionKeys.REPORT_VIEW)),
            new RoleSpec("VIEWER", "Viewer", "Read-only access", DataScope.ORG,
                List.of(PermissionKeys.LEAD_VIEW, PermissionKeys.COMPANY_VIEW, PermissionKeys.CONTACT_VIEW,
                    PermissionKeys.CALL_VIEW, PermissionKeys.TASK_VIEW, PermissionKeys.MEETING_VIEW,
                    PermissionKeys.EMAIL_VIEW, PermissionKeys.TEMPLATE_VIEW, PermissionKeys.CAMPAIGN_VIEW,
                    PermissionKeys.DEAL_VIEW, PermissionKeys.PROPOSAL_VIEW, PermissionKeys.CLIENT_VIEW,
                    PermissionKeys.DOCUMENT_VIEW, PermissionKeys.PIPELINE_VIEW, PermissionKeys.TAG_VIEW,
                    PermissionKeys.SOURCE_VIEW, PermissionKeys.REPORT_VIEW, PermissionKeys.USER_VIEW,
                    PermissionKeys.TEAM_VIEW, PermissionKeys.ROLE_VIEW, PermissionKeys.ORG_VIEW)));
    }

    public static List<Role> createSystemRoles(java.util.UUID organizationId, List<Permission> allPermissions) {
        Map<String, Permission> byKey = allPermissions.stream().collect(Collectors.toMap(Permission::getKey, Function.identity()));
        return systemRoles().stream().map(spec -> {
            Role r = new Role();
            r.setOrganizationId(organizationId);
            r.setKey(spec.key());
            r.setName(spec.name());
            r.setDescription(spec.description());
            r.setDataScope(spec.scope());
            r.setSystem(true);
            Set<Permission> perms = new java.util.HashSet<>();
            for (String k : spec.permissions()) {
                Permission p = byKey.get(k);
                if (p != null) perms.add(p);
            }
            r.setPermissions(perms);
            return r;
        }).toList();
    }
}
