# Release Notes

### Most recent changes at top of file
```
Date          Commit        Action
29/09/2022    eaa32f41      Added props excluded.response.behaviour, default is false. Set it to true to activate the props: excluded.response.field.values. Note: excluded.response.field.values can also be activated on a per call basis by the url param ?exclude-optional-fields=true
07/09/2022    53564924      renamed props `language_tag` to `default_locale`, default is en_GB.
08/08/2022    1ff7bf0a      removed props `meeting.tokbox_enabled`, `meeting.tokbox_api_key` and `meeting.tokbox_api_secret`. 
                            removed three endpoints: getMeetings, getMeeting and createMeeting in V200.
05/08/2022    ba690c1f      renamed props `transaction_request_challenge_ttl` to `transactionRequest.challenge.ttl.seconds`. 
02/08/2022    7b06563f      added new props `userAuthContextUpdateRequest.challenge.ttl.seconds`, default is 600 seconds. 
01/08/2022    d94687d6      added new props `answer_transactionRequest_challenge_allowed_attempts`, default is 3 .  
03/05/2022    5fe70270      added new props `transaction_request_challenge_ttl`, default is 600 seconds.  
31/03/2022    a0262c3f      added new value SIMPLE to props `transactionRequests_supported_types`  
                            added new props `SIMPLE_OTP_INSTRUCTION_TRANSPORT`, default value is `DUMMY`
24/02/2022    2882805c      removed `kafka`,`kafka_vJune2017` and `kafka_vMar2017` connectors
24/02/2022    c7a206fc      removed `obpjvm` relevent connectors and java adapter `obp-ri-xxx` dependences
18/01/2022    150a4d53      Added the security manager relevant props,they only used for the dynamic code endpoints
                            dynamic_code_sandbox_enable, default is false 
                            dynamic_code_sandbox_permissions, default is a list, please check sample.props.template
                            dynamic_code_compile_validate_enable, default is false
                            dynamic_code_compile_validate_dependencies, default is a list, please check sample.props.template
01/11/2021    03305d2b      Added props: rest_connector_sends_x-sign_header, default is false
17/09/2021    e65cd51d      Added props: webui_main_faq_external_link, default is obp static file: /main-faq.html
09/09/2021    65952225      Added props: webui_support_email, default is contact@openbankproject.com
02/09/2021    a826d908      Renamed Web UI props:  
                            webui_post_user_invitation_privacy_conditions_value => webui_privacy_policy
                            webui_post_user_invitation_terms_and_conditions_value => webui_terms_and_conditions
20/08/2021    2ad1a3b0      Updated implementations of Akka, Rest and Stored Procedure connectors via the builder.  
18/08/2021    83c3b4fa      Added props: webui_favicon_link_url, default is /favicon.ico
18/08/2021    5924c820      Added props: webui_api_documentation_bottom_url, default is https://github.com/OpenBankProject/OBP-API/wiki
                            Added props: webui_privacy_policy_url, default is https://openbankproject.com/privacy-policy
30/06/2021    cf2dd987      Changed props, static will cache 24 hours, dynamic only 1 hour as default.
                            dynamicResourceDocsObp.cache.ttl.seconds=3600
                            staticResourceDocsObp.cache.ttl.seconds=86400
30/06/2021    cf2dd987      Added props: email_domain_to_entitlement_mappings, default is empty
                            We can automatically grant the Entitlements required to the User has access to via their validated email domain. 
                            Entitlements are generated /refreshed both following manual locin and Direct Login token generation (POST).
29/06/2021    98c5503c      Existing Props authUser.skipEmailValidation now defaults to false (i.e. we now force email validation by default)
29/06/2021    0b08199b      Added props: email_domain_to_space_mappings, default is empty
                            We can automatically grant the Entitlements required to use all the Dynamic Endpoint roles belonging to 
                            the bank_ids (Spaces) the User has access to via their validated email domain. Entitlements are 
                            generated /refreshed both following manual locin and Direct Login token generation (POST).
26/06/2021    62fe53d9      Added props: consumer_registration.display_app_type, default is 'true'.
                            Support removing the app type checkbox during consumer registration
24/06/2021    e863c9dd      Added props: brands_enabled, default is 'false'.
                            Support multiple brands on one instance. Note this needs checking on a clustered environment
14/03/2021    e29001e2      Added props: webui_login_page_instruction_title, default is 'Log on to the Open Bank Project API'.
                            The clients can customise the login page instraction title.
13/03/2021    3c9880a9      Added props: featured_api_collection_ids, default is Empty.
                            This props is used for the featured api collections. Eg: API_Explorer will consume it to modify the Home Page.
11/03/2021    ae211dd9      Added props: default_auth_context_update_request_key, default is CUSTOMER_NUMBER.
                            This props is used for the User Onboard page, we can have the default identifier key.
                            The different banks may have different identifiers for their customers, eg: CUSTOMER_NUMBER, TAX_ID...
20/09/2020    609d4e77      Added props: entitlement_list_1 and new_user_entitlement_list . default is empty.
                            we can grant the default roles to the new validated user, eg:
                            entitlement_list_1=[CanGetConfig, CanUseAccountFirehoseAtAnyBank]
                            new_user_entitlement_list=entitlement_list_1
10/09/2020    95cd5329      Added props: glossary_requires_role. default is false. If set it as `true`, then OBP will 
                            check the authentication and CanReadGlossary Role for the endpoint: `Get API Glossary`.
12/08/2020    51621aa1      Added props: webui_legal_notice_html_text. If we set the props, the content will be showed as the legal notice on the registration page 
11/08/2020    5319a5f8      WARNING: Added new account routing system. 
                            - Impacted endpoints: Create Account, Create Account (POST) and Update Account.
                              multiple account routings can now be put in the "account_routings" field.
                            - Impacted connector messages: OutBoundUpdateBankAccount, OutBoundCreateBankAccount,
                              OutBoundCreateBankAccountLegacy, OutBoundAddBankAccount and OutBoundCreateSandboxBankAccount.
                              Parameters "accountRoutingScheme" and "accountRoutingAddress" have been replaced by a List[AccountRouting].
                              OutBoundGetBankAccountByRouting message is also impacted with an additionnal parameter: Option[BankId].
14/07/2020    376be727      Added full support for MS SQL as a mapper databas
13/07/2020    d42dda90      Added props: webui_header_content_url. If we set the props, it will override the id ="table-header" content in default.html
19/06/2020    ea819aab      Added props: refresh_user.interval. default is 30 minutes.
                            This props will set the interval for the internal refresh user process.
29/04/2020    75925d8c      Added props: allow_pre_filled_password. in Sign Up page the default password form field is ****
                            This props can set the field to empty .
29/04/2020    1ba4f3aa      Added props: webui_signup_form_submit_button_value. this will overwrite the submit button value 
                            in the sign up page.
28/04/2020    9b180f2b      Added props: webui_post_consumer_registration_submit_button_value. this will overwrite the submit button value 
                            in the consumer registration page.  
26/04/2020    9b40921c      Added props: use_custom_webapp. default is false. If we set true, we will support the custom obp-api home page
                            all the files of `resources/custom_webapp` will copy to `webapp` folder when obp-api starts.  
                            Added props: webui_signup_form_title_text. this will override the singup page title content. 
                            Added props: webui_signup_body_password_repeat_text. this will overrid the sinup page password text field.
                            Added props: webui_agree_terms_html. this will override all the agree terms content. 
                            Added props: webui_login_button_text. this will overrid the login button content.
20/02/2020    3f04a7a0      Added props: webui_featured_sdks_external_link. default is obp static file: https://static.openbankproject.com/obp/sdks.html. 
19/02/2020    3f04a7a0      Added props: resource_docs_requires_role. default is false. If set it as `true`, then OBP will 
                            check the authentication and CanReadResourceDoc Role for the endpoint: `Get Resource Docs`.
21/11/2019    51f97330      Added props: portal_hostname. default use the same value as hostname. This props is only useful when we split obp to
                            two instances: apis and portal. So portal one need its own hostname, portal_hostname can be used for it.  
18/11/2019    de4aec71      Added props: grpc.server.enabled. default is false. 
18/11/2019    4bd31563      Added props: grpc.server.port. if do not set this props, the grpc port will be set randomly when OBP starts. 
                            And you can call `Get API Configuration` endpoint to see the `grpc_port` there. When you set this props, need to
                            make sure this port is available.  
08/11/2019    13d2e88a      Added props: rest2019_connector_timeout. This set the timeout for all rest-connector methods. If connector do not get 
                            response by the specified seconds, then obp will throw the adapter timeout error.
07/11/2019    015d8420      Added props: webui_agree_privacy_policy_html_text makes this text on the sign up page /user_mgt/sign_up configurable.
                            It has the default html format value. 
04/10/2019    aa9659c7      Added props: es.warehouse.allowed.maximum.pagesize. This is the maximum size in the query for warehouse apis.
                            It has the default value 10000.
03/09/2019    f953386c      Added props: implicitly_convert_ids . it will convert Bank_Plan_Text_Reference to OBP-UUID implicitly.  
21/08/2019    4ac93f1c      Added props: webui_register_consumer_success_message_webpage and webui_register_consumer_success_message_email.
                            These  messages will be shown to developers on the webpage or email, when they register the consumer successfully. 
05/07/2019    7032ce3       Added props: webui_sandbox_introduction, To display the introduction page for sandbox.
                            It supports the markdown format.It will show the introduction OBP-API home page `INTRODUCTION` 
                            page and also for Glossary `Sandbox Introduction`. 
14/06/2019    7a1c453       Added props: sca_phone_api_key and sca_phone_api_secret. We For now, OBP-API use `nexmo` server 
                            as the SMS provider. Please check `nexmo` website, and get the api key and value there.
03/06/2019    5194b48       The table viewimpl is replaced with a table viewdefinition
                            The table viewprivileges is replaced with a table accountaccess
                            Please note that next props must be set up:
                            migration_scripts.execute=true
                            list_of_migration_scripts_to_execute=populateTableViewDefinition,populateTableAccountAccess```
                            In the table migrationscriptlog you can see results of the migration scripts.
                            Please note that 2 backup tables are created as well, something like these 2 below:
                            1. accountaccess_backup_2019_05_17_11_16_32_134
                            2.  viewdefinition_backup_2019_05_17_11_16_31_862
29/04/2019    a6b58a1       Added Props system_environment_property_name_prefix, default is OBP_. This adds the prefix only for the system environment property name, eg: db.driver --> OBP_db.driver
07/07/2018    4944572       Added Props api_instance_id, default is 1. This deceides the current api instance number, start from 1.  
29/06/2018    7422894       Added Props logging.database.queries.enable, default is false. This should enable logging all the database queries in log file.
01/06/2018    a286684       Added Props write_connector_metrics, default is false. This decides whether the connector level metric save or not
29/05/2018    c0d50b5       Added Props kafka.partitions, default is 3. This should match the partitions in Kafka config
08/05/2018    38e8641       Added Props require_scopes, default is false. Api will not use the scope role guards.
02/03/2018    6f9ad08       Added Props documented_server_url which is required for some glossary items
19/02/2018                  Added possibility of Encryption/Decryption of properties in the props file over SSL Private/Public key infrastructure
19/01/2018    189942e       Added 2 way SSL authentication to kafka connection
12/11/2017    9529c3b       Make Payments in 1.2.1 disabled. Internal Accounts API disabled.
20/09/2017                  Upgraded Lift version to 3.1.0. Script scripts/migrate/migrate_0000008.sql has to be executed at existing instances
13/09/2017    1503229       DISABLED API versions v1.0, v1.1, v1.2
09/09/2017    fa3b054       Added Props api_enabled_versions and api_enabled_endpoints
19/04/2017    4033a01       Added remotedata.secret=secret - This should be set to the same value, local and remote (or just local)
28/03/2017                  Added webui_footer2_logo_left_url and webui_footer2_middle_text - for bottom footer, default=""
28/03/2017                  Added Props webui_top_text which is used by default.html (default="")
22/03/2017    51d1742       Added Props webui_api_manager_url which is used by button on home page.
21/03/2017                  Added Props authUser.skipEmailValidation . This defaults to true to maintain current behaviour
21/03/2017    c5f6b02       Added mail.api.consumer.registered.notification.send.sensistive to Props (default is false)
17/03/2017                  Added the following to sample.props.template (name change): #webui_post_consumer_registration_more_info_url =
17/03/2017                  Added the following to sample.props.template (name change): #webui_post_consumer_registration_more_info_text =
08/03/2017    d8b6907       added new pair to props : post_consumer_registration_more_info_url, post_consumer_registration_more_info_text , details see ticket #433
20/02/2017    d8b6907       added new pair to props : # If true, get counterparties from OBP db, else put message on Kafka queue. <--> get_counterparties_from_OBP_DB = true
05/04/2017                  added 8 new caching props to sample.props.template which start with connector.cache.ttl.seconds.* and end with function names (getBank, getBanks, getAccount, getAccounts, getTransaction, getTransactions, getCounterpartyFromTransaction, getCounterpartiesFromTransaction). If it's omitted default value is 0 i.e. no caching.
02/05/2017    3084827       added 1 new caching props to sample.props.template api.cache.ttl.seconds.APIMethods121.getTransactions. If it's omitted default value is 0 i.e. no caching. This cacahe is from API level.
10/05/2017    7f95a5c       added allow_public_views=false, we will not create the public views and will not access them (if public views are exsiting)when it is false.
17/07/2017    1530231       added account_id.length=64, this will set all relevant accountid length to 64, when create new sandbox.  
17/02/2016    e3bead1       Added Props defaultBank.bank_id. Default Bank. Incase the server wants to support a default bank so developers don't have to specify BANK_ID
```


6f9ad08
