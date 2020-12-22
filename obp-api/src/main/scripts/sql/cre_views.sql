
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


-- In case when we can create a bank at OBP-API side but we get it from CBS(core banking system)
-- via this view we expose such created bank to CBS in order to be able to list they
-- We don't want to end up with scenario where you create bank with success code 201 but you cannot get it
create view v_bank as
select
	permalink bank_id,
	fullbankname bank_name,
	shortbankname short_bank_name,
	national_identifier national_identifier,
	mbankroutingscheme bank_routing_scheme,
	mbankroutingaddress bank_routing_address,
	logourl logo_url,
	websiteurl website_url,
	updatedat updated_at,
	createdat created_at,
	swiftbic swift_bic
from
	mappedbank
union
select
	null bank_id,
	null bank_name,
	null short_bank_name,
	null national_identifier,
	null bank_routing_scheme,
	null bank_routing_address,
	null logo_url,
	null website_url,
	null updated_at,
	null created_at,
	null swift_bic
from
	mappedbank
where
	1 = 0;


-- In case when we can create a branch at OBP-API side but we get it from CBS(core banking system)
-- via this view we expose such created bank to CBS in order to be able to list they
-- We don't want to end up with scenario where you create branch with success code 201 but you cannot get it
create view v_branch as
select
	mbankid bank_id,
	mname name,
	mbranchid branch_id,
	mline1 line_1,
	mline2 line_2,
	mline3 line_3,
	mcity city,
	mcounty county,
	mstate state,
	mcountrycode country_code,
	mpostcode post_code,
	mlocationlatitude location_latitude,
	mlocationlongitude location_longitude,
	mlicenseid license_id,
	mlicensename license_name,
	mlobbyhours lobby_hours,
	mdriveuphours drive_up_hours,
	mbranchroutingscheme branch_routing_scheme,
	mbranchroutingaddress branch_routing_address,
	mlobbyopeningtimeonmonday lobby_opening_time_on_monday,
	mlobbyclosingtimeonmonday lobby_closing_time_on_monday,
	mlobbyopeningtimeontuesday lobby_opening_time_on_tuesday,
	mlobbyclosingtimeontuesday lobby_closing_time_on_tuesday,
	mlobbyopeningtimeonwednesday lobby_opening_time_on_wednesday,
	mlobbyclosingtimeonwednesday lobby_closing_time_on_wednesday,
	mlobbyopeningtimeonthursday lobby_opening_time_on_thursday,
	mlobbyclosingtimeonthursday lobby_closing_time_on_thursday,
	mlobbyopeningtimeonfriday lobby_opening_time_on_friday,
	mlobbyclosingtimeonfriday lobby_closing_time_on_friday,
	mlobbyopeningtimeonsaturday lobby_opening_time_on_saturday,
	mlobbyclosingtimeonsaturday lobby_closing_time_on_saturday,
	mlobbyopeningtimeonsunday lobby_opening_time_on_sunday,
	mlobbyclosingtimeonsunday lobby_closing_time_on_sunday,
	mdriveupopeningtimeonmonday drive_up_opening_time_on_monday,
	mdriveupclosingtimeonmonday drive_up_closing_time_on_monday,
	mdriveupopeningtimeontuesday drive_up_opening_time_on_tuesday,
	mdriveupclosingtimeontuesday drive_up_closing_time_on_tuesday,
	mdriveupopeningtimeonwednesday drive_up_opening_time_on_wednesday,
	mdriveupclosingtimeonwednesday drive_up_closing_time_on_wednesday,
	mdriveupopeningtimeonthursday drive_up_opening_time_on_thursday,
	mdriveupclosingtimeonthursday drive_up_closing_time_on_thursday,
	mdriveupopeningtimeonfriday drive_up_opening_time_on_friday,
	mdriveupclosingtimeonfriday drive_up_closing_time_on_friday,
	mdriveupopeningtimeonsaturday drive_up_opening_time_on_saturday,
	mdriveupclosingtimeonsaturday drive_up_closing_time_on_saturday,
	mdriveupopeningtimeonsunday drive_up_opening_time_on_sunday,
	mdriveupclosingtimeonsunday drive_up_closing_time_on_sunday,
	misaccessible is_accessible,
	maccessiblefeatures accessible_features,
	mbranchtype branch_type,
	mmoreinfo more_info,
	mphonenumber phone_number,
	misdeleted is_deleted
from
	mappedbranch
union
select
	null bank_id,
	null name,
	null branch_id,
	null line_1,
	null line_2,
	null line_3,
	null city,
	null county,
	null state,
	null country_code,
	null post_code,
	null location_latitude,
	null location_longitude,
	null license_id,
	null license_name,
	null lobby_hours,
	null drive_up_hours,
	null branch_routing_scheme,
	null branch_routing_address,
	null lobby_opening_time_on_monday,
	null lobby_closing_time_on_monday,
	null lobby_opening_time_on_tuesday,
	null lobby_closing_time_on_tuesday,
	null lobby_opening_time_on_wednesday,
	null lobby_closing_time_on_wednesday,
	null lobby_opening_time_on_thursday,
	null lobby_closing_time_on_thursday,
	null lobby_opening_time_on_friday,
	null lobby_closing_time_on_friday,
	null lobby_opening_time_on_saturday,
	null lobby_closing_time_on_saturday,
	null lobby_opening_time_on_sunday,
	null lobby_closing_time_on_sunday,
	null drive_up_opening_time_on_monday,
	null drive_up_closing_time_on_monday,
	null drive_up_opening_time_on_tuesday,
	null drive_up_closing_time_on_tuesday,
	null drive_up_opening_time_on_wednesday,
	null drive_up_closing_time_on_wednesday,
	null drive_up_opening_time_on_thursday,
	null drive_up_closing_time_on_thursday,
	null drive_up_opening_time_on_friday,
	null drive_up_closing_time_on_friday,
	null drive_up_opening_time_on_saturday,
	null drive_up_closing_time_on_saturday,
	null drive_up_opening_time_on_sunday,
	null drive_up_closing_time_on_sunday,
	null is_accessible,
	null accessible_features,
	null branch_type,
	null more_info,
	null phone_number,
	null is_deleted
where
	1 = 0

-- In case when we can create a account at OBP-API side but we get it from CBS(core banking system)
-- via this view we expose such created bank to CBS in order to be able to list they
-- We don't want to end up with scenario where you create account with success code 201 but you cannot get it
create view v_account as
select
	kind kind,
	holder holder,
	bank bank_id,
	theaccountid account_id,
	accountcurrency account_currency,
	accountnumber account_number,
	updatedat updated_at,
	createdat created_at,
	mbranchid branch_id,
	accountiban account_iban,
	accountbalance account_balance,
	accountname account_name,
	accountlabel account_label,
	accountlastupdate account_last_update,
	maccountroutingscheme account_routing_scheme,
	maccountroutingaddress account_routing_address,
	accountrulescheme1 account_rule_scheme_1,
	accountrulevalue1 account_rule_value_1,
	accountrulescheme2 account_rule_scheme_2,
	accountrulevalue2 account_rule_value_2
from
	mappedbankaccount
union
select
	null kind,
	null holder,
	null bank_id,
	null account_id,
	null account_currency,
	null account_number,
	null updated_at,
	null created_at,
	null branch_id,
	null account_iban,
	null account_balance,
	null account_name,
	null account_label,
	null account_last_update,
	null account_routing_scheme,
	null account_routing_address,
	null account_rule_scheme_1,
	null account_rule_value_1,
	null account_rule_scheme_2,
	null account_rule_value_2
where
	1 = 0


create view v_account_routing as
select
	updatedat updated_at,
	createdat created_at,
	bankid bank_id,
	accountid account_id,
	accountroutingscheme account_routhing_scheme,
	accountroutingaddress account_routing_address
from
	bankaccountrouting
union
select
	null updated_at,
	null created_at,
	null bank_id,
	null account_id,
	null account_routhing_scheme,
	null account_routing_address
from
	bankaccountrouting
where
	1 = 0;


create view v_transaction_request as
SELECT
	updatedat updated_at,
	createdat created_at,
	mname name,
	mthisbankid this_bank_id,
	mthisaccountid this_accoun_tid,
	mthisviewid this_view_id,
	motheraccountroutingscheme other_account_routing_scheme,
	motheraccountroutingaddress other_account_routing_address,
	motherbankroutingscheme other_bank_routing_scheme,
	motherbankroutingaddress other_bankrouting_address,
	misbeneficiary is_beneficiary,
	mtransactionrequestid transaction_request_id,
	mtype type,
	mtransactionids transaction_ids,
	mstatus status,
	mstartdate start_date,
	menddate end_date,
	mchallenge_id challenge_id,
	mchallenge_allowedattempts challenge_allowed_attempts,
	mchallenge_challengetype challenge_challenge_type,
	mcharge_summary charge_summary,
	mcharge_amount charge_amount,
	mcharge_currency charge_currency,
	mcharge_policy charge_policy,
	mbody_value_currency body_value_currency,
	mbody_value_amount body_value_amount,
	mbody_description body_description,
	mdetails details,
 -- cast(mdetails as nvarchar(MAX)) details,  MSSQL case
	mfrom_bankid from_bank_id,
	mfrom_accountid from_account_id,
	mto_bankid to_bank_id,
	mto_accountid to_account_id,
	mcounterpartyid counterparty_id
FROM
	mappedtransactionrequest
union
SELECT
	null updated_at,
	null created_at,
	null name,
	null this_bank_id,
	null this_accoun_tid,
	null this_view_id,
	null other_account_routing_scheme,
	null other_account_routing_address,
	null other_bank_routing_scheme,
	null other_bankrouting_address,
	null is_beneficiary,
	null transaction_request_id,
	null type,
	null transaction_ids,
	null status,
	null start_date,
	null end_date,
	null challenge_id,
	null challenge_allowed_attempts,
	null challenge_challenge_type,
	null charge_summary,
	null charge_amount,
	null charge_currency,
	null charge_policy,
	null body_value_currency,
	null body_value_amount,
	null body_description,
	null details,
	null from_bank_id,
	null from_account_id,
	null to_bank_id,
	null to_account_id,
	null counterparty_id
where
	1 = 0
