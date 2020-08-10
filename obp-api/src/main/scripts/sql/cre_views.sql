
drop view v_resource_user cascade;
create or replace view v_resource_user as select userid_ resource_user_id, name_ username, email, id numeric_resource_user_id, provider_ provider, providerid provider_id from resourceuser;

drop view v_auth_user cascade;
create view v_auth_user as
select
	username,
	firstname,
	lastname,
	email,
	uniqueid auth_user_id,
	id numeric_auth_user_id,
	user_c,
	validated
from
	authuser
union
select
	null username,
	null firstname,
	null lastname,
	null email,
	null auth_user_id,
	null numeric_auth_user_id,
	null user_c,
	null validated
from
	authuser
where
	1 = 0;
	
drop view v_auth_user_resource_user cascade;
create or replace view v_auth_user_resource_user as select au.username from v_auth_user au, v_resource_user ru where au.numeric_auth_user_id = ru.numeric_resource_user_id;

create or replace view v_view as select bankpermalink bank_id, accountpermalink account_id, permalink_ view_id,  description_ description from viewimpl;

create or replace view v_entitlement as select mentitlementid entitlement_id, muserid resource_user_id, mbankid bank_id, mrolename role_name, id numeric_entitlement_id, createdat created_at, updatedat updated_id  from mappedentitlement;

create or replace view v_account_holder as select accountbankpermalink bank_id, accountpermalink account_id, user_c resource_user_id, id internal_id from mappedaccountholder; 

create or replace view v_user_account_holder as select username, ah.bank_id, ah.account_id from v_account_holder ah , v_resource_user ru where ah.resource_user_id = ru.resource_user_id;


create or replace view v_transaction_image as select id numeric_transaciton_image_id, transaction_c transaction_id, view_c view_id, url, imagedescription description from mappedtransactionimage;


create or replace view v_transaction_narrative as select id numeric_transaciton_narrative_id, bank bank_id, account account_id, transaction_c transaction_id, narrative from mappednarrative;


create or replace view v_transaction_comment as select id numeric_transaciton_comment_id, bank bank_id, account account_id, transaction_c transaction_id, text_ comment_text, createdat created_at, apiid resource_user_id from mappedcomment;

create or replace view v_view_privilege as select id numeric_view_privilege_id, user_c numeric_resource_user_id, view_c numeric_view_id  from viewprivileges;

create or replace view v_transaction_request_type_charge as select id, mbankid bank_id, mtransactionrequesttypeid transaction_request_type_id, mchargecurrency currency , mchargeamount amount, mchargesummary summary from mappedtransactionrequesttypecharge;

-- In case when we can create a customer at OBP-API side but we get it from CBS(core banking system)
-- via this view we expose such created customers to CBS in order to be able to list they
-- We don't want to end up with scenario where you create customer with success code 201 but you cannot get it
create view v_customer as
select
	mbank bank_id,
	mbranchid branch_id,
	mcustomerid customer_id,
	mnumber customer_number,
	mmobilenumber mobile_number,
	memail email,
	mtitle title,
	mlegalname legal_name,
	mnamesuffix name_suffix,
	mfaceimageurl face_image_url,
	mfaceimagetime face_image_time,
	mdateofbirth date_of_birth,
	mrelationshipstatus relationship_status,
	mdependents dependents,
	mhighesteducationattained highest_education_attained,
	memploymentstatus employment_status,
	mcreditrating credit_rating,
	mcreditsource credit_source,
	mcreditlimitcurrency credit_limit_currency,
	mcreditlimitamount credit_limit_amount,
	createdat insert_date,
	updatedat update_date
from
	mappedcustomer
union
select
	null bank_id,
	null branch_id,
	null customer_id,
	null customer_number,
	null mobile_number,
	null email,
	null title,
	null legal_name,
	null name_suffix,
	null face_image_url,
	null face_image_time,
	null date_of_birth,
	null relationship_status,
	null dependents,
	null highest_education_attained,
	null employment_status,
	null credit_rating,
	null credit_source,
	null credit_limit_currency,
	null credit_limit_amount,
	null insert_date,
	null update_date
where
	1 = 0

-- In case when we can create a atm at OBP-API side but we get it from CBS(core banking system)
-- via this view we expose such created atm to CBS in order to be able to list they
-- We don't want to end up with scenario where you create atm with success code 201 but you cannot get it
create view v_atm as
select
	mname name,
	mbankid bank_id,
	mline1 line_1,
	mline2 line_2,
	mline3 line_3,
	mcity city,
	mcounty county,
	mstate state,
	mcountrycode country_code,
	mpostcode post_code,
	mlocatedat located_at,
	mlocationlatitude location_latitude,
	mlocationlongitude location_longitude,
	mlicenseid license_id,
	mlicensename license_name,
	mopeningtimeonmonday opening_time_on_monday,
	mclosingtimeonmonday mclosing_time_on_monday,
	mopeningtimeontuesday opening_time_on_tuesday,
	mclosingtimeontuesday closing_time_on_tuesday,
	mopeningtimeonwednesday opening_time_on_wednesday,
	mclosingtimeonwednesday closing_time_on_wednesday,
	mopeningtimeonthursday opening_time_on_thursday,
	mclosingtimeonthursday closing_time_on_thursday,
	mopeningtimeonfriday opening_time_on_friday,
	mclosingtimeonfriday closing_time_on_friday,
	mopeningtimeonsaturday opening_time_on_saturday,
	mclosingtimeonsaturday closing_time_on_saturday,
	mopeningtimeonsunday opening_time_on_sunday,
	mclosingtimeonsunday closing_time_on_sunday,
	mhasdepositcapability has_deposit_capability,
	misaccessible is_accessible,
	mmoreinfo more_info,
	matmid atm_id
FROM
	mappedatm
union
select
	null name,
	null bank_id,
	null line_1,
	null line_2,
	null line_3,
	null city,
	null county,
	null state,
	null country_code,
	null post_code,
	null located_at,
	null location_latitude,
	null location_longitude,
	null license_id,
	null license_name,
	null opening_time_on_monday,
	null mclosing_time_on_monday,
	null opening_time_on_tuesday,
	null closing_time_on_tuesday,
	null opening_time_on_wednesday,
	null closing_time_on_wednesday,
	null opening_time_on_thursday,
	null closing_time_on_thursday,
	null opening_time_on_friday,
	null closing_time_on_friday,
	null opening_time_on_saturday,
	null closing_time_on_saturday,
	null opening_time_on_sunday,
	null closing_time_on_sunday,
	null has_deposit_capability,
	null is_accessible,
	null more_info,
	null atm_id
from
	mappedatm
where
	1 = 0;