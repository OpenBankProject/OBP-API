
drop view v_resource_user cascade;
create or replace view v_resource_user as select userid_ resource_user_id, name_ username, email, id numeric_resource_user_id, provider_ provider, providerid provider_id from resourceuser;

drop view v_auth_user cascade;
create or replace view v_auth_user as select username, firstname, lastname, email, uniqueid auth_user_id, id numeric_auth_user_id, user_c, validated from authuser;

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
	mappedcustomer;
