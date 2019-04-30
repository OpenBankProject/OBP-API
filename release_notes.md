# Release Notes

### Most recent changes at top of file
```
Date          Commit        Action 

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
02/05/2017    3084827       added 1 new caching props to sample.props.template connector.cache.ttl.seconds.APIMethods121.getTransactions. If it's omitted default value is 0 i.e. no caching. This cacahe is from API level.
10/05/2017    7f95a5c       added allow_public_views=false, we will not create the public views and will not access them (if public views are exsiting)when it is false.
17/07/2017    1530231       added account_id.length=64, this will set all relevant accountid length to 64, when create new sandbox.  
17/02/2016    e3bead1       Added Props defaultBank.bank_id. Default Bank. Incase the server wants to support a default bank so developers don't have to specify BANK_ID
```


6f9ad08