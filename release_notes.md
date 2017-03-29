# Release Notes

### Most recent changes at top of file
```
Date          Commit        Action 

28/03/2017                  Added webui_footer2_logo_left_url and webui_footer2_middle_text - for bottom footer, default=""
28/03/2017                  Added Props webui_top_text which is used by default.html (default="")
22/03/2017    51d1742       Added Props webui_api_manager_url which is used by button on home page.
21/03/2017                  Added Props authUser.skipEmailValidation . This defaults to true to maintain current behaviour
21/03/2017    c5f6b02       Added mail.api.consumer.registered.notification.send.sensistive to Props (default is false)
17/03/2017                  Added the following to sample.props.template (name change): #webui_post_consumer_registration_more_info_url =
17/03/2017                  Added the following to sample.props.template (name change): #webui_post_consumer_registration_more_info_text =
08/03/2017    d8b6907       added new pair to props : post_consumer_registration_more_info_url, post_consumer_registration_more_info_text , details see ticket #433
20/02/2017    d8b6907       added new pair to props : # If true, get counterparties from OBP db, else put message on Kafka queue. <--> get_counterparties_from_OBP_DB = true

```