-- Sales Managers manage their own SMTP accounts.  This keeps the existing
-- organization roles aligned with RoleFactory and the email account UI.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.key in ('EMAIL_ACCOUNT_CREATE', 'EMAIL_ACCOUNT_UPDATE', 'EMAIL_ACCOUNT_DELETE')
where r.is_system = true and r.key = 'SALES_MANAGER'
on conflict do nothing;