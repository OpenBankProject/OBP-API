# Release Notes

### Most recent changes at top of file
```
Date          Commit        Action 

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

```