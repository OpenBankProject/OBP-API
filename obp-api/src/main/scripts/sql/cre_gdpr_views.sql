-- this script relies on the view v_auth_user from "cre_views.sql" being created first.

drop view v_auth_user_gdpr cascade;
create view v_auth_user_gdpr as
select
	username,
	firstname,
	lastname,
	email
from
	v_auth_user
union
select
	username,
	firstname,
	lastname,
	email
from
	v_auth_user
where
    1 = 0;
    
