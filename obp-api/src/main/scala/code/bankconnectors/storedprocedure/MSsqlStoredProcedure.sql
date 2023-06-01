-- auto generated MS sql server procedures script, create on 2023-06-01T08:47:14Z

-- drop procedure obp_get_adapter_info
DROP PROCEDURE IF EXISTS obp_get_adapter_info;
GO
-- create procedure obp_get_adapter_info
CREATE PROCEDURE obp_get_adapter_info
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "errorCode":"error code",
         "backendMessages":[
           {
             "source":"",
             "status":"Status string",
             "errorCode":"errorCode string",
             "text":"text string"
           }
         ],
         "name":"NAME",
         "version":"version string",
         "git_commit":"git_commit",
         "date":"date String"
       }
     }'
	);
GO

 
 


-- drop procedure obp_validate_and_check_iban_number
DROP PROCEDURE IF EXISTS obp_validate_and_check_iban_number;
GO
-- create procedure obp_validate_and_check_iban_number
CREATE PROCEDURE obp_validate_and_check_iban_number
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "iban":"DE91 1000 0000 0123 4567 89"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "isValid":true,
         "details":{
           "bic":"BUKBGB22",
           "bank":"",
           "branch":"string",
           "address":"",
           "city":"",
           "zip":"string",
           "phone":"",
           "country":"string",
           "countryIso":"string",
           "sepaCreditTransfer":"yes",
           "sepaDirectDebit":"yes",
           "sepaSddCore":"yes",
           "sepaB2b":"yes",
           "sepaCardClearing":"no"
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_challenge_threshold
DROP PROCEDURE IF EXISTS obp_get_challenge_threshold;
GO
-- create procedure obp_get_challenge_threshold
CREATE PROCEDURE obp_get_challenge_threshold
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
       "viewId":"owner",
       "transactionRequestType":"SEPA",
       "currency":"EUR",
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "username":"felixsmith"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "currency":"EUR",
         "amount":"10.12"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_charge_level
DROP PROCEDURE IF EXISTS obp_get_charge_level;
GO
-- create procedure obp_get_charge_level
CREATE PROCEDURE obp_get_charge_level
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "viewId":{
         "value":"owner"
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "username":"felixsmith",
       "transactionRequestType":"SEPA",
       "currency":"EUR"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "currency":"EUR",
         "amount":"10.12"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_charge_level_c2
DROP PROCEDURE IF EXISTS obp_get_charge_level_c2;
GO
-- create procedure obp_get_charge_level_c2
CREATE PROCEDURE obp_get_charge_level_c2
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "viewId":{
         "value":"owner"
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "username":"felixsmith",
       "transactionRequestType":"SEPA",
       "currency":"EUR",
       "amount":"10.12",
       "toAccountRoutings":[
         {
           "scheme":"IBAN",
           "address":"DE91 1000 0000 0123 4567 89"
         }
       ],
       "customAttributes":[
         {
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "attributeType":"STRING",
           "value":"5987953"
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "currency":"EUR",
         "amount":"10.12"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_challenge
DROP PROCEDURE IF EXISTS obp_create_challenge;
GO
-- create procedure obp_create_challenge
CREATE PROCEDURE obp_create_challenge
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "transactionRequestType":{
         "value":"SEPA"
       },
       "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
       "scaMethod":"SMS"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":"string"
     }'
	);
GO

 
 


-- drop procedure obp_create_challenges
DROP PROCEDURE IF EXISTS obp_create_challenges;
GO
-- create procedure obp_create_challenges
CREATE PROCEDURE obp_create_challenges
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "userIds":[
         ""
       ],
       "transactionRequestType":{
         "value":"SEPA"
       },
       "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
       "scaMethod":"SMS"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         ""
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_challenges_c2
DROP PROCEDURE IF EXISTS obp_create_challenges_c2;
GO
-- create procedure obp_create_challenges_c2
CREATE PROCEDURE obp_create_challenges_c2
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "userIds":[
         ""
       ],
       "challengeType":"OBP_TRANSACTION_REQUEST_CHALLENGE",
       "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
       "scaMethod":"SMS",
       "scaStatus":"received",
       "consentId":"",
       "authenticationMethodId":"string"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
           "expectedAnswer":"string",
           "expectedUserId":"string",
           "salt":"string",
           "successful":true,
           "challengeType":"",
           "consentId":"",
           "scaMethod":"SMS",
           "scaStatus":"received",
           "authenticationMethodId":"string",
           "attemptCounter":0
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_validate_challenge_answer
DROP PROCEDURE IF EXISTS obp_validate_challenge_answer;
GO
-- create procedure obp_validate_challenge_answer
CREATE PROCEDURE obp_validate_challenge_answer
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
       "hashOfSuppliedAnswer":"a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_validate_challenge_answer_c2
DROP PROCEDURE IF EXISTS obp_validate_challenge_answer_c2;
GO
-- create procedure obp_validate_challenge_answer_c2
CREATE PROCEDURE obp_validate_challenge_answer_c2
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
       "consentId":"",
       "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
       "hashOfSuppliedAnswer":"a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
         "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
         "expectedAnswer":"string",
         "expectedUserId":"string",
         "salt":"string",
         "successful":true,
         "challengeType":"",
         "consentId":"",
         "scaMethod":"SMS",
         "scaStatus":"received",
         "authenticationMethodId":"string",
         "attemptCounter":0
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_challenges_by_transaction_request_id
DROP PROCEDURE IF EXISTS obp_get_challenges_by_transaction_request_id;
GO
-- create procedure obp_get_challenges_by_transaction_request_id
CREATE PROCEDURE obp_get_challenges_by_transaction_request_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
           "expectedAnswer":"string",
           "expectedUserId":"string",
           "salt":"string",
           "successful":true,
           "challengeType":"",
           "consentId":"",
           "scaMethod":"SMS",
           "scaStatus":"received",
           "authenticationMethodId":"string",
           "attemptCounter":0
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_challenges_by_consent_id
DROP PROCEDURE IF EXISTS obp_get_challenges_by_consent_id;
GO
-- create procedure obp_get_challenges_by_consent_id
CREATE PROCEDURE obp_get_challenges_by_consent_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "consentId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
           "expectedAnswer":"string",
           "expectedUserId":"string",
           "salt":"string",
           "successful":true,
           "challengeType":"",
           "consentId":"",
           "scaMethod":"SMS",
           "scaStatus":"received",
           "authenticationMethodId":"string",
           "attemptCounter":0
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_challenge
DROP PROCEDURE IF EXISTS obp_get_challenge;
GO
-- create procedure obp_get_challenge
CREATE PROCEDURE obp_get_challenge
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "challengeId":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
         "transactionRequestId":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1",
         "expectedAnswer":"string",
         "expectedUserId":"string",
         "salt":"string",
         "successful":true,
         "challengeType":"",
         "consentId":"",
         "scaMethod":"SMS",
         "scaStatus":"received",
         "authenticationMethodId":"string",
         "attemptCounter":0
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_bank
DROP PROCEDURE IF EXISTS obp_get_bank;
GO
-- create procedure obp_get_bank
CREATE PROCEDURE obp_get_bank
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "shortName":"bank shortName string",
         "fullName":"bank fullName string",
         "logoUrl":"bank logoUrl string",
         "websiteUrl":"bank websiteUrl string",
         "bankRoutingScheme":"BIC",
         "bankRoutingAddress":"GENODEM1GLS"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_banks
DROP PROCEDURE IF EXISTS obp_get_banks;
GO
-- create procedure obp_get_banks
CREATE PROCEDURE obp_get_banks
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "shortName":"bank shortName string",
           "fullName":"bank fullName string",
           "logoUrl":"bank logoUrl string",
           "websiteUrl":"bank websiteUrl string",
           "bankRoutingScheme":"BIC",
           "bankRoutingAddress":"GENODEM1GLS"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_bank_accounts_for_user
DROP PROCEDURE IF EXISTS obp_get_bank_accounts_for_user;
GO
-- create procedure obp_get_bank_accounts_for_user
CREATE PROCEDURE obp_get_bank_accounts_for_user
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "provider":"ETHEREUM",
       "username":"felixsmith"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":"gh.29.uk",
           "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
           "viewsToGenerate":[
             "Owner",
             "Accountant",
             "Auditor"
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_user
DROP PROCEDURE IF EXISTS obp_get_user;
GO
-- create procedure obp_get_user
CREATE PROCEDURE obp_get_user
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "name":"felixsmith",
       "password":"password"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "email":"felixsmith@example.com",
         "password":"password",
         "displayName":""
       }
     }'
	);
GO

 
 


-- drop procedure obp_check_external_user_credentials
DROP PROCEDURE IF EXISTS obp_check_external_user_credentials;
GO
-- create procedure obp_check_external_user_credentials
CREATE PROCEDURE obp_check_external_user_credentials
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "username":"felixsmith",
       "password":"password"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "aud":"String",
         "exp":"String",
         "iat":"String",
         "iss":"String",
         "sub":"felixsmith",
         "azp":"string",
         "email":"felixsmith@example.com",
         "emailVerified":"String",
         "name":"felixsmith",
         "userAuthContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_check_external_user_exists
DROP PROCEDURE IF EXISTS obp_check_external_user_exists;
GO
-- create procedure obp_check_external_user_exists
CREATE PROCEDURE obp_check_external_user_exists
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "username":"felixsmith"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "aud":"String",
         "exp":"String",
         "iat":"String",
         "iss":"String",
         "sub":"felixsmith",
         "azp":"string",
         "email":"felixsmith@example.com",
         "emailVerified":"String",
         "name":"felixsmith",
         "userAuthContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_bank_account_old
DROP PROCEDURE IF EXISTS obp_get_bank_account_old;
GO
-- create procedure obp_get_bank_account_old
CREATE PROCEDURE obp_get_bank_account_old
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_bank_account_by_iban
DROP PROCEDURE IF EXISTS obp_get_bank_account_by_iban;
GO
-- create procedure obp_get_bank_account_by_iban
CREATE PROCEDURE obp_get_bank_account_by_iban
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "iban":"DE91 1000 0000 0123 4567 89"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_bank_account_by_routing
DROP PROCEDURE IF EXISTS obp_get_bank_account_by_routing;
GO
-- create procedure obp_get_bank_account_by_routing
CREATE PROCEDURE obp_get_bank_account_by_routing
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "scheme":"scheme value",
       "address":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_bank_accounts
DROP PROCEDURE IF EXISTS obp_get_bank_accounts;
GO
-- create procedure obp_get_bank_accounts
CREATE PROCEDURE obp_get_bank_accounts
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankIdAccountIds":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           }
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "accountType":"AC",
           "balance":"10",
           "currency":"EUR",
           "name":"bankAccount name string",
           "number":"bankAccount number string",
           "bankId":{
             "value":"gh.29.uk"
           },
           "lastUpdate":"2018-03-09T00:00:00Z",
           "branchId":"DERBY6",
           "accountRoutings":[
             {
               "scheme":"IBAN",
               "address":"DE91 1000 0000 0123 4567 89"
             }
           ],
           "attributes":[
             {
               "name":"STATUS",
               "type":"STRING",
               "value":"closed"
             }
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_bank_accounts_balances
DROP PROCEDURE IF EXISTS obp_get_bank_accounts_balances;
GO
-- create procedure obp_get_bank_accounts_balances
CREATE PROCEDURE obp_get_bank_accounts_balances
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankIdAccountIds":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           }
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accounts":[
           {
             "id":"d8839721-ad8f-45dd-9f78-2080414b93f9",
             "label":"My Account",
             "bankId":"gh.29.uk",
             "accountRoutings":[
               {
                 "scheme":"IBAN",
                 "address":"DE91 1000 0000 0123 4567 89"
               }
             ],
             "balance":{
               "currency":"EUR",
               "amount":"50.89"
             }
           }
         ],
         "overallBalance":{
           "currency":"EUR",
           "amount":"10.12"
         },
         "overallBalanceDate":"2020-01-27T00:00:00Z"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_core_bank_accounts
DROP PROCEDURE IF EXISTS obp_get_core_bank_accounts;
GO
-- create procedure obp_get_core_bank_accounts
CREATE PROCEDURE obp_get_core_bank_accounts
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankIdAccountIds":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           }
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
           "label":"My Account",
           "bankId":"gh.29.uk",
           "accountType":"AC",
           "accountRoutings":[
             {
               "scheme":"IBAN",
               "address":"DE91 1000 0000 0123 4567 89"
             }
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_bank_accounts_held
DROP PROCEDURE IF EXISTS obp_get_bank_accounts_held;
GO
-- create procedure obp_get_bank_accounts_held
CREATE PROCEDURE obp_get_bank_accounts_held
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankIdAccountIds":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           }
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "id":"d8839721-ad8f-45dd-9f78-2080414b93f9",
           "label":"My Account",
           "bankId":"gh.29.uk",
           "number":"",
           "accountRoutings":[
             {
               "scheme":"IBAN",
               "address":"DE91 1000 0000 0123 4567 89"
             }
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_check_bank_account_exists
DROP PROCEDURE IF EXISTS obp_check_bank_account_exists;
GO
-- create procedure obp_check_bank_account_exists
CREATE PROCEDURE obp_check_bank_account_exists
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_counterparty_trait
DROP PROCEDURE IF EXISTS obp_get_counterparty_trait;
GO
-- create procedure obp_get_counterparty_trait
CREATE PROCEDURE obp_get_counterparty_trait
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "couterpartyId":"string"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "createdByUserId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "currency":"EUR",
         "thisBankId":"",
         "thisAccountId":"",
         "thisViewId":"",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "otherAccountRoutingScheme":"",
         "otherAccountRoutingAddress":"",
         "otherAccountSecondaryRoutingScheme":"",
         "otherAccountSecondaryRoutingAddress":"",
         "otherBankRoutingScheme":"",
         "otherBankRoutingAddress":"",
         "otherBranchRoutingScheme":"",
         "otherBranchRoutingAddress":"",
         "isBeneficiary":true,
         "bespoke":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_counterparty_by_counterparty_id
DROP PROCEDURE IF EXISTS obp_get_counterparty_by_counterparty_id;
GO
-- create procedure obp_get_counterparty_by_counterparty_id
CREATE PROCEDURE obp_get_counterparty_by_counterparty_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "counterpartyId":{
         "value":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "createdByUserId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "currency":"EUR",
         "thisBankId":"",
         "thisAccountId":"",
         "thisViewId":"",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "otherAccountRoutingScheme":"",
         "otherAccountRoutingAddress":"",
         "otherAccountSecondaryRoutingScheme":"",
         "otherAccountSecondaryRoutingAddress":"",
         "otherBankRoutingScheme":"",
         "otherBankRoutingAddress":"",
         "otherBranchRoutingScheme":"",
         "otherBranchRoutingAddress":"",
         "isBeneficiary":true,
         "bespoke":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_counterparty_by_iban
DROP PROCEDURE IF EXISTS obp_get_counterparty_by_iban;
GO
-- create procedure obp_get_counterparty_by_iban
CREATE PROCEDURE obp_get_counterparty_by_iban
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "iban":"DE91 1000 0000 0123 4567 89"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "createdByUserId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "currency":"EUR",
         "thisBankId":"",
         "thisAccountId":"",
         "thisViewId":"",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "otherAccountRoutingScheme":"",
         "otherAccountRoutingAddress":"",
         "otherAccountSecondaryRoutingScheme":"",
         "otherAccountSecondaryRoutingAddress":"",
         "otherBankRoutingScheme":"",
         "otherBankRoutingAddress":"",
         "otherBranchRoutingScheme":"",
         "otherBranchRoutingAddress":"",
         "isBeneficiary":true,
         "bespoke":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_counterparty_by_iban_and_bank_account_id
DROP PROCEDURE IF EXISTS obp_get_counterparty_by_iban_and_bank_account_id;
GO
-- create procedure obp_get_counterparty_by_iban_and_bank_account_id
CREATE PROCEDURE obp_get_counterparty_by_iban_and_bank_account_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "iban":"DE91 1000 0000 0123 4567 89",
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "createdByUserId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "currency":"EUR",
         "thisBankId":"",
         "thisAccountId":"",
         "thisViewId":"",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "otherAccountRoutingScheme":"",
         "otherAccountRoutingAddress":"",
         "otherAccountSecondaryRoutingScheme":"",
         "otherAccountSecondaryRoutingAddress":"",
         "otherBankRoutingScheme":"",
         "otherBankRoutingAddress":"",
         "otherBranchRoutingScheme":"",
         "otherBranchRoutingAddress":"",
         "isBeneficiary":true,
         "bespoke":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_counterparties
DROP PROCEDURE IF EXISTS obp_get_counterparties;
GO
-- create procedure obp_get_counterparties
CREATE PROCEDURE obp_get_counterparties
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "thisBankId":{
         "value":""
       },
       "thisAccountId":{
         "value":""
       },
       "viewId":{
         "value":"owner"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "createdByUserId":"",
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
           "currency":"EUR",
           "thisBankId":"",
           "thisAccountId":"",
           "thisViewId":"",
           "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "otherAccountRoutingScheme":"",
           "otherAccountRoutingAddress":"",
           "otherAccountSecondaryRoutingScheme":"",
           "otherAccountSecondaryRoutingAddress":"",
           "otherBankRoutingScheme":"",
           "otherBankRoutingAddress":"",
           "otherBranchRoutingScheme":"",
           "otherBranchRoutingAddress":"",
           "isBeneficiary":true,
           "bespoke":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_transactions
DROP PROCEDURE IF EXISTS obp_get_transactions;
GO
-- create procedure obp_get_transactions
CREATE PROCEDURE obp_get_transactions
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "limit":100,
       "offset":100,
       "fromDate":"2018-03-09",
       "toDate":"2018-03-09"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "id":{
             "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
           },
           "thisAccount":{
             "accountId":{
               "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
             },
             "accountType":"AC",
             "balance":"10",
             "currency":"EUR",
             "name":"bankAccount name string",
             "label":"My Account",
             "number":"bankAccount number string",
             "bankId":{
               "value":"gh.29.uk"
             },
             "lastUpdate":"2018-03-09T00:00:00Z",
             "branchId":"DERBY6",
             "accountRoutings":[
               {
                 "scheme":"IBAN",
                 "address":"DE91 1000 0000 0123 4567 89"
               }
             ],
             "accountRules":[
               {
                 "scheme":"AccountRule scheme string",
                 "value":"AccountRule value string"
               }
             ],
             "accountHolder":"bankAccount accountHolder string",
             "attributes":[
               {
                 "name":"STATUS",
                 "type":"STRING",
                 "value":"closed"
               }
             ]
           },
           "otherAccount":{
             "kind":"Counterparty kind string",
             "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
             "counterpartyName":"John Smith Ltd.",
             "thisBankId":{
               "value":""
             },
             "thisAccountId":{
               "value":""
             },
             "isBeneficiary":true
           },
           "transactionType":"DEBIT",
           "amount":"19.64",
           "currency":"EUR",
           "description":"The piano lession-Invoice No:68",
           "startDate":"2019-09-07T00:00:00Z",
           "finishDate":"2019-09-08T00:00:00Z",
           "balance":"10"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_transactions_core
DROP PROCEDURE IF EXISTS obp_get_transactions_core;
GO
-- create procedure obp_get_transactions_core
CREATE PROCEDURE obp_get_transactions_core
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "limit":100,
       "offset":100,
       "fromDate":"",
       "toDate":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "id":{
             "value":"d8839721-ad8f-45dd-9f78-2080414b93f9"
           },
           "thisAccount":{
             "accountId":{
               "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
             },
             "accountType":"AC",
             "balance":"10",
             "currency":"EUR",
             "name":"bankAccount name string",
             "label":"My Account",
             "number":"bankAccount number string",
             "bankId":{
               "value":"gh.29.uk"
             },
             "lastUpdate":"2018-03-09T00:00:00Z",
             "branchId":"DERBY6",
             "accountRoutings":[
               {
                 "scheme":"IBAN",
                 "address":"DE91 1000 0000 0123 4567 89"
               }
             ],
             "accountRules":[
               {
                 "scheme":"AccountRule scheme string",
                 "value":"AccountRule value string"
               }
             ],
             "accountHolder":"bankAccount accountHolder string",
             "attributes":[
               {
                 "name":"STATUS",
                 "type":"STRING",
                 "value":"closed"
               }
             ]
           },
           "otherAccount":{
             "kind":"",
             "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
             "counterpartyName":"John Smith Ltd.",
             "thisBankId":{
               "value":""
             },
             "thisAccountId":{
               "value":""
             },
             "otherBankRoutingScheme":"",
             "otherBankRoutingAddress":"",
             "otherAccountRoutingScheme":"",
             "otherAccountRoutingAddress":"",
             "otherAccountProvider":"",
             "isBeneficiary":true
           },
           "transactionType":"DEBIT",
           "amount":"10.12",
           "currency":"EUR",
           "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
           "startDate":"2020-01-27T00:00:00Z",
           "finishDate":"2020-01-27T00:00:00Z",
           "balance":"10"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_transaction
DROP PROCEDURE IF EXISTS obp_get_transaction;
GO
-- create procedure obp_get_transaction
CREATE PROCEDURE obp_get_transaction
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "transactionId":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "id":{
           "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
         },
         "thisAccount":{
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "accountType":"AC",
           "balance":"10",
           "currency":"EUR",
           "name":"bankAccount name string",
           "label":"My Account",
           "number":"bankAccount number string",
           "bankId":{
             "value":"gh.29.uk"
           },
           "lastUpdate":"2018-03-09T00:00:00Z",
           "branchId":"DERBY6",
           "accountRoutings":[
             {
               "scheme":"IBAN",
               "address":"DE91 1000 0000 0123 4567 89"
             }
           ],
           "accountRules":[
             {
               "scheme":"AccountRule scheme string",
               "value":"AccountRule value string"
             }
           ],
           "accountHolder":"bankAccount accountHolder string",
           "attributes":[
             {
               "name":"STATUS",
               "type":"STRING",
               "value":"closed"
             }
           ]
         },
         "otherAccount":{
           "kind":"Counterparty kind string",
           "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "counterpartyName":"John Smith Ltd.",
           "thisBankId":{
             "value":""
           },
           "thisAccountId":{
             "value":""
           },
           "isBeneficiary":true
         },
         "transactionType":"DEBIT",
         "amount":"19.64",
         "currency":"EUR",
         "description":"The piano lession-Invoice No:68",
         "startDate":"2019-09-07T00:00:00Z",
         "finishDate":"2019-09-08T00:00:00Z",
         "balance":"10"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_physical_cards_for_user
DROP PROCEDURE IF EXISTS obp_get_physical_cards_for_user;
GO
-- create procedure obp_get_physical_cards_for_user
CREATE PROCEDURE obp_get_physical_cards_for_user
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "user":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
           "bankId":"gh.29.uk",
           "bankCardNumber":"364435172576215",
           "cardType":"Credit",
           "nameOnCard":"SusanSmith",
           "issueNumber":"1",
           "serialNumber":"1324234",
           "validFrom":"2020-01-27T00:00:00Z",
           "expires":"2021-01-27T00:00:00Z",
           "enabled":true,
           "cancelled":true,
           "onHotList":false,
           "technology":"technology1",
           "networks":[
             ""
           ],
           "allows":[
             "DEBIT"
           ],
           "account":{
             "accountId":{
               "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
             },
             "accountType":"AC",
             "balance":"10",
             "currency":"EUR",
             "name":"bankAccount name string",
             "label":"My Account",
             "number":"546387432",
             "bankId":{
               "value":"gh.29.uk"
             },
             "lastUpdate":"2018-03-09T00:00:00Z",
             "branchId":"DERBY6",
             "accountRoutings":[
               {
                 "scheme":"IBAN",
                 "address":"DE91 1000 0000 0123 4567 89"
               }
             ],
             "accountRules":[
               {
                 "scheme":"AccountRule scheme string",
                 "value":"AccountRule value string"
               }
             ],
             "accountHolder":"bankAccount accountHolder string",
             "attributes":[
               {
                 "name":"STATUS",
                 "type":"STRING",
                 "value":"closed"
               }
             ]
           },
           "replacement":{
             "requestedDate":"2020-01-27T00:00:00Z",
             "reasonRequested":"FIRST"
           },
           "pinResets":[
             {
               "requestedDate":"2020-01-27T00:00:00Z",
               "reasonRequested":"FORGOT"
             }
           ],
           "collected":{
             "date":"2020-01-27T00:00:00Z"
           },
           "posted":{
             "date":"2020-01-27T00:00:00Z"
           },
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_physical_card_for_bank
DROP PROCEDURE IF EXISTS obp_get_physical_card_for_bank;
GO
-- create procedure obp_get_physical_card_for_bank
CREATE PROCEDURE obp_get_physical_card_for_bank
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e "
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
         "bankId":"gh.29.uk",
         "bankCardNumber":"364435172576215",
         "cardType":"Credit",
         "nameOnCard":"SusanSmith",
         "issueNumber":"1",
         "serialNumber":"1324234",
         "validFrom":"2020-01-27T00:00:00Z",
         "expires":"2021-01-27T00:00:00Z",
         "enabled":true,
         "cancelled":true,
         "onHotList":false,
         "technology":"technology1",
         "networks":[
           ""
         ],
         "allows":[
           "DEBIT"
         ],
         "account":{
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "accountType":"AC",
           "balance":"10",
           "currency":"EUR",
           "name":"bankAccount name string",
           "label":"My Account",
           "number":"546387432",
           "bankId":{
             "value":"gh.29.uk"
           },
           "lastUpdate":"2018-03-09T00:00:00Z",
           "branchId":"DERBY6",
           "accountRoutings":[
             {
               "scheme":"IBAN",
               "address":"DE91 1000 0000 0123 4567 89"
             }
           ],
           "accountRules":[
             {
               "scheme":"AccountRule scheme string",
               "value":"AccountRule value string"
             }
           ],
           "accountHolder":"bankAccount accountHolder string",
           "attributes":[
             {
               "name":"STATUS",
               "type":"STRING",
               "value":"closed"
             }
           ]
         },
         "replacement":{
           "requestedDate":"2020-01-27T00:00:00Z",
           "reasonRequested":"FIRST"
         },
         "pinResets":[
           {
             "requestedDate":"2020-01-27T00:00:00Z",
             "reasonRequested":"FORGOT"
           }
         ],
         "collected":{
           "date":"2020-01-27T00:00:00Z"
         },
         "posted":{
           "date":"2020-01-27T00:00:00Z"
         },
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       }
     }'
	);
GO

 
 


-- drop procedure obp_delete_physical_card_for_bank
DROP PROCEDURE IF EXISTS obp_delete_physical_card_for_bank;
GO
-- create procedure obp_delete_physical_card_for_bank
CREATE PROCEDURE obp_delete_physical_card_for_bank
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e "
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_get_physical_cards_for_bank
DROP PROCEDURE IF EXISTS obp_get_physical_cards_for_bank;
GO
-- create procedure obp_get_physical_cards_for_bank
CREATE PROCEDURE obp_get_physical_cards_for_bank
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bank":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "shortName":"bank shortName string",
         "fullName":"bank fullName string",
         "logoUrl":"bank logoUrl string",
         "websiteUrl":"bank websiteUrl string",
         "bankRoutingScheme":"BIC",
         "bankRoutingAddress":"GENODEM1GLS",
         "swiftBic":"bank swiftBic string",
         "nationalIdentifier":"bank nationalIdentifier string"
       },
       "user":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "limit":100,
       "offset":100,
       "fromDate":"",
       "toDate":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
           "bankId":"gh.29.uk",
           "bankCardNumber":"364435172576215",
           "cardType":"Credit",
           "nameOnCard":"SusanSmith",
           "issueNumber":"1",
           "serialNumber":"1324234",
           "validFrom":"2020-01-27T00:00:00Z",
           "expires":"2021-01-27T00:00:00Z",
           "enabled":true,
           "cancelled":true,
           "onHotList":false,
           "technology":"technology1",
           "networks":[
             ""
           ],
           "allows":[
             "DEBIT"
           ],
           "account":{
             "accountId":{
               "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
             },
             "accountType":"AC",
             "balance":"10",
             "currency":"EUR",
             "name":"bankAccount name string",
             "label":"My Account",
             "number":"546387432",
             "bankId":{
               "value":"gh.29.uk"
             },
             "lastUpdate":"2018-03-09T00:00:00Z",
             "branchId":"DERBY6",
             "accountRoutings":[
               {
                 "scheme":"IBAN",
                 "address":"DE91 1000 0000 0123 4567 89"
               }
             ],
             "accountRules":[
               {
                 "scheme":"AccountRule scheme string",
                 "value":"AccountRule value string"
               }
             ],
             "accountHolder":"bankAccount accountHolder string",
             "attributes":[
               {
                 "name":"STATUS",
                 "type":"STRING",
                 "value":"closed"
               }
             ]
           },
           "replacement":{
             "requestedDate":"2020-01-27T00:00:00Z",
             "reasonRequested":"FIRST"
           },
           "pinResets":[
             {
               "requestedDate":"2020-01-27T00:00:00Z",
               "reasonRequested":"FORGOT"
             }
           ],
           "collected":{
             "date":"2020-01-27T00:00:00Z"
           },
           "posted":{
             "date":"2020-01-27T00:00:00Z"
           },
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_physical_card
DROP PROCEDURE IF EXISTS obp_create_physical_card;
GO
-- create procedure obp_create_physical_card
CREATE PROCEDURE obp_create_physical_card
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankCardNumber":"364435172576215",
       "nameOnCard":"SusanSmith",
       "cardType":"Credit",
       "issueNumber":"1",
       "serialNumber":"1324234",
       "validFrom":"2020-01-27T00:00:00Z",
       "expires":"2021-01-27T00:00:00Z",
       "enabled":true,
       "cancelled":true,
       "onHotList":false,
       "technology":"technology1",
       "networks":[
         ""
       ],
       "allows":[
         "[credit",
         "debit",
         "cash_withdrawal]"
       ],
       "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
       "bankId":"gh.29.uk",
       "replacement":{
         "requestedDate":"2020-01-27T00:00:00Z",
         "reasonRequested":"FIRST"
       },
       "pinResets":[
         {
           "requestedDate":"2020-01-27T00:00:00Z",
           "reasonRequested":"FORGOT"
         }
       ],
       "collected":{
         "date":"2020-01-27T00:00:00Z"
       },
       "posted":{
         "date":"2020-01-27T00:00:00Z"
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "cvv":"123",
       "brand":"Visa"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
         "bankId":"gh.29.uk",
         "bankCardNumber":"364435172576215",
         "cardType":"Credit",
         "nameOnCard":"SusanSmith",
         "issueNumber":"1",
         "serialNumber":"1324234",
         "validFrom":"2020-01-27T00:00:00Z",
         "expires":"2021-01-27T00:00:00Z",
         "enabled":true,
         "cancelled":true,
         "onHotList":false,
         "technology":"technology1",
         "networks":[
           ""
         ],
         "allows":[
           "DEBIT"
         ],
         "account":{
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "accountType":"AC",
           "balance":"10",
           "currency":"EUR",
           "name":"bankAccount name string",
           "label":"My Account",
           "number":"546387432",
           "bankId":{
             "value":"gh.29.uk"
           },
           "lastUpdate":"2018-03-09T00:00:00Z",
           "branchId":"DERBY6",
           "accountRoutings":[
             {
               "scheme":"IBAN",
               "address":"DE91 1000 0000 0123 4567 89"
             }
           ],
           "accountRules":[
             {
               "scheme":"AccountRule scheme string",
               "value":"AccountRule value string"
             }
           ],
           "accountHolder":"bankAccount accountHolder string",
           "attributes":[
             {
               "name":"STATUS",
               "type":"STRING",
               "value":"closed"
             }
           ]
         },
         "replacement":{
           "requestedDate":"2020-01-27T00:00:00Z",
           "reasonRequested":"FIRST"
         },
         "pinResets":[
           {
             "requestedDate":"2020-01-27T00:00:00Z",
             "reasonRequested":"FORGOT"
           }
         ],
         "collected":{
           "date":"2020-01-27T00:00:00Z"
         },
         "posted":{
           "date":"2020-01-27T00:00:00Z"
         },
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       }
     }'
	);
GO

 
 


-- drop procedure obp_update_physical_card
DROP PROCEDURE IF EXISTS obp_update_physical_card;
GO
-- create procedure obp_update_physical_card
CREATE PROCEDURE obp_update_physical_card
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
       "bankCardNumber":"364435172576215",
       "nameOnCard":"SusanSmith",
       "cardType":"Credit",
       "issueNumber":"1",
       "serialNumber":"1324234",
       "validFrom":"2020-01-27T00:00:00Z",
       "expires":"2021-01-27T00:00:00Z",
       "enabled":true,
       "cancelled":true,
       "onHotList":false,
       "technology":"technology1",
       "networks":[
         ""
       ],
       "allows":[
         "[credit",
         "debit",
         "cash_withdrawal]"
       ],
       "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
       "bankId":"gh.29.uk",
       "replacement":{
         "requestedDate":"2020-01-27T00:00:00Z",
         "reasonRequested":"FIRST"
       },
       "pinResets":[
         {
           "requestedDate":"2020-01-27T00:00:00Z",
           "reasonRequested":"FORGOT"
         }
       ],
       "collected":{
         "date":"2020-01-27T00:00:00Z"
       },
       "posted":{
         "date":"2020-01-27T00:00:00Z"
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
         "bankId":"gh.29.uk",
         "bankCardNumber":"364435172576215",
         "cardType":"Credit",
         "nameOnCard":"SusanSmith",
         "issueNumber":"1",
         "serialNumber":"1324234",
         "validFrom":"2020-01-27T00:00:00Z",
         "expires":"2021-01-27T00:00:00Z",
         "enabled":true,
         "cancelled":true,
         "onHotList":false,
         "technology":"technology1",
         "networks":[
           ""
         ],
         "allows":[
           "DEBIT"
         ],
         "account":{
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "accountType":"AC",
           "balance":"10",
           "currency":"EUR",
           "name":"bankAccount name string",
           "label":"My Account",
           "number":"546387432",
           "bankId":{
             "value":"gh.29.uk"
           },
           "lastUpdate":"2018-03-09T00:00:00Z",
           "branchId":"DERBY6",
           "accountRoutings":[
             {
               "scheme":"IBAN",
               "address":"DE91 1000 0000 0123 4567 89"
             }
           ],
           "accountRules":[
             {
               "scheme":"AccountRule scheme string",
               "value":"AccountRule value string"
             }
           ],
           "accountHolder":"bankAccount accountHolder string",
           "attributes":[
             {
               "name":"STATUS",
               "type":"STRING",
               "value":"closed"
             }
           ]
         },
         "replacement":{
           "requestedDate":"2020-01-27T00:00:00Z",
           "reasonRequested":"FIRST"
         },
         "pinResets":[
           {
             "requestedDate":"2020-01-27T00:00:00Z",
             "reasonRequested":"FORGOT"
           }
         ],
         "collected":{
           "date":"2020-01-27T00:00:00Z"
         },
         "posted":{
           "date":"2020-01-27T00:00:00Z"
         },
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       }
     }'
	);
GO

 
 


-- drop procedure obp_make_paymentv210
DROP PROCEDURE IF EXISTS obp_make_paymentv210;
GO
-- create procedure obp_make_paymentv210
CREATE PROCEDURE obp_make_paymentv210
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "transactionRequestId":{
         "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
       },
       "transactionRequestCommonBody":{
         "value":{
           "currency":"EUR",
           "amount":"10.12"
         },
         "description":"This an optional field. Maximum length is 2000. It can be any characters here."
       },
       "amount":"10.12",
       "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
       "transactionRequestType":{
         "value":"SEPA"
       },
       "chargePolicy":"SHARED"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_transaction_requestv210
DROP PROCEDURE IF EXISTS obp_create_transaction_requestv210;
GO
-- create procedure obp_create_transaction_requestv210
CREATE PROCEDURE obp_create_transaction_requestv210
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "initiator":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "viewId":{
         "value":"owner"
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "transactionRequestType":{
         "value":"SEPA"
       },
       "transactionRequestCommonBody":{
         "value":{
           "currency":"EUR",
           "amount":"10.12"
         },
         "description":"This an optional field. Maximum length is 2000. It can be any characters here."
       },
       "detailsPlain":"string",
       "chargePolicy":"SHARED",
       "challengeType":"",
       "scaMethod":"SMS"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_transaction_requestv400
DROP PROCEDURE IF EXISTS obp_create_transaction_requestv400;
GO
-- create procedure obp_create_transaction_requestv400
CREATE PROCEDURE obp_create_transaction_requestv400
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "initiator":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "viewId":{
         "value":"owner"
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "transactionRequestType":{
         "value":"SEPA"
       },
       "transactionRequestCommonBody":{
         "value":{
           "currency":"EUR",
           "amount":"10.12"
         },
         "description":"This an optional field. Maximum length is 2000. It can be any characters here."
       },
       "detailsPlain":"string",
       "chargePolicy":"SHARED",
       "challengeType":"",
       "scaMethod":"SMS",
       "reasons":[
         {
           "code":"125",
           "documentNumber":"",
           "amount":"10.12",
           "currency":"EUR",
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         }
       ],
       "berlinGroupPayments":{
         "endToEndIdentification":"string",
         "instructionIdentification":"string",
         "debtorName":"string",
         "debtorAccount":{
           "iban":"string"
         },
         "debtorId":"string",
         "ultimateDebtor":"string",
         "instructedAmount":{
           "currency":"EUR",
           "amount":"10.12"
         },
         "currencyOfTransfer":"string",
         "exchangeRateInformation":"string",
         "creditorAccount":{
           "iban":"string"
         },
         "creditorAgent":"string",
         "creditorAgentName":"string",
         "creditorName":"string",
         "creditorId":"string",
         "creditorAddress":"string",
         "creditorNameAndAddress":"string",
         "ultimateCreditor":"string",
         "purposeCode":"string",
         "chargeBearer":"string",
         "serviceLevel":"string",
         "remittanceInformationUnstructured":"string",
         "remittanceInformationUnstructuredArray":"string",
         "remittanceInformationStructured":"string",
         "remittanceInformationStructuredArray":"string",
         "requestedExecutionDate":"string",
         "requestedExecutionTime":"string"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_transaction_requests210
DROP PROCEDURE IF EXISTS obp_get_transaction_requests210;
GO
-- create procedure obp_get_transaction_requests210
CREATE PROCEDURE obp_get_transaction_requests210
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "initiator":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "id":{
             "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
           },
           "type":"SEPA",
           "from":{
             "bank_id":"gh.29.uk",
             "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "body":{
             "value":{
               "currency":"EUR",
               "amount":"10.12"
             },
             "description":"This an optional field. Maximum length is 2000. It can be any characters here."
           },
           "transaction_ids":"string",
           "status":"",
           "start_date":"2019-09-07T00:00:00Z",
           "end_date":"2019-09-08T00:00:00Z",
           "challenge":{
             "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
             "allowed_attempts":123,
             "challenge_type":"string"
           },
           "charge":{
             "summary":"",
             "value":{
               "currency":"EUR",
               "amount":"10.12"
             }
           }
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_transaction_request_impl
DROP PROCEDURE IF EXISTS obp_get_transaction_request_impl;
GO
-- create procedure obp_get_transaction_request_impl
CREATE PROCEDURE obp_get_transaction_request_impl
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "transactionRequestId":{
         "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_transaction_after_challenge_v210
DROP PROCEDURE IF EXISTS obp_create_transaction_after_challenge_v210;
GO
-- create procedure obp_create_transaction_after_challenge_v210
CREATE PROCEDURE obp_create_transaction_after_challenge_v210
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "transactionRequest":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_update_bank_account
DROP PROCEDURE IF EXISTS obp_update_bank_account;
GO
-- create procedure obp_update_bank_account
CREATE PROCEDURE obp_update_bank_account
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "accountType":"AC",
       "accountLabel":"string",
       "branchId":"DERBY6",
       "accountRoutings":[
         {
           "scheme":"IBAN",
           "address":"DE91 1000 0000 0123 4567 89"
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_bank_account
DROP PROCEDURE IF EXISTS obp_create_bank_account;
GO
-- create procedure obp_create_bank_account
CREATE PROCEDURE obp_create_bank_account
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "accountType":"AC",
       "accountLabel":"string",
       "currency":"EUR",
       "initialBalance":"123.321",
       "accountHolderName":"string",
       "branchId":"DERBY6",
       "accountRoutings":[
         {
           "scheme":"IBAN",
           "address":"DE91 1000 0000 0123 4567 89"
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_account_exists
DROP PROCEDURE IF EXISTS obp_account_exists;
GO
-- create procedure obp_account_exists
CREATE PROCEDURE obp_account_exists
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountNumber":"546387432"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_get_products
DROP PROCEDURE IF EXISTS obp_get_products;
GO
-- create procedure obp_get_products
CREATE PROCEDURE obp_get_products
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "bankId":{
         "value":"gh.29.uk"
       },
       "params":[
         {
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "value":[
             "5987953"
           ]
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "code":{
             "value":"1234BW"
           },
           "parentProductCode":{
             "value":"787LOW"
           },
           "name":"Deposit Account 1",
           "category":"",
           "family":"",
           "superFamily":"",
           "moreInfoUrl":"www.example.com/abc",
           "termsAndConditionsUrl":"www.example.com/xyz",
           "details":"",
           "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
           "meta":{
             "license":{
               "id":"ODbL-1.0",
               "name":"Open Database License"
             }
           }
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_product
DROP PROCEDURE IF EXISTS obp_get_product;
GO
-- create procedure obp_get_product
CREATE PROCEDURE obp_get_product
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "bankId":{
         "value":"gh.29.uk"
       },
       "productCode":{
         "value":"1234BW"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "code":{
           "value":"1234BW"
         },
         "parentProductCode":{
           "value":"787LOW"
         },
         "name":"Deposit Account 1",
         "category":"",
         "family":"",
         "superFamily":"",
         "moreInfoUrl":"www.example.com/abc",
         "termsAndConditionsUrl":"www.example.com/xyz",
         "details":"",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "meta":{
           "license":{
             "id":"ODbL-1.0",
             "name":"Open Database License"
           }
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_branch
DROP PROCEDURE IF EXISTS obp_get_branch;
GO
-- create procedure obp_get_branch
CREATE PROCEDURE obp_get_branch
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "branchId":{
         "value":"DERBY6"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "branchId":{
           "value":"DERBY6"
         },
         "bankId":{
           "value":"gh.29.uk"
         },
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "address":{
           "line1":"",
           "line2":"",
           "line3":"",
           "city":"",
           "county":"",
           "state":"",
           "postCode":"789",
           "countryCode":"1254"
         },
         "location":{
           "latitude":38.8951,
           "longitude":-77.0364,
           "date":"2020-01-27T00:00:00Z",
           "user":{
             "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
             "provider":"ETHEREUM",
             "username":"felixsmith"
           }
         },
         "lobbyString":{
           "hours":"string"
         },
         "driveUpString":{
           "hours":"string"
         },
         "meta":{
           "license":{
             "id":"ODbL-1.0",
             "name":"Open Database License"
           }
         },
         "branchRouting":{
           "scheme":"BRANCH-CODE",
           "address":"DERBY6"
         },
         "lobby":{
           "monday":[
             {
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           ],
           "tuesday":[
             {
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           ],
           "wednesday":[
             {
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           ],
           "thursday":[
             {
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           ],
           "friday":[
             {
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           ],
           "saturday":[
             {
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           ],
           "sunday":[
             {
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           ]
         },
         "driveUp":{
           "monday":{
             "openingTime":"",
             "closingTime":"2020-01-27"
           },
           "tuesday":{
             "openingTime":"",
             "closingTime":"2020-01-27"
           },
           "wednesday":{
             "openingTime":"",
             "closingTime":"2020-01-27"
           },
           "thursday":{
             "openingTime":"",
             "closingTime":"2020-01-27"
           },
           "friday":{
             "openingTime":"",
             "closingTime":"2020-01-27"
           },
           "saturday":{
             "openingTime":"",
             "closingTime":"2020-01-27"
           },
           "sunday":{
             "openingTime":"",
             "closingTime":"2020-01-27"
           }
         },
         "isAccessible":true,
         "accessibleFeatures":"string",
         "branchType":"",
         "moreInfo":"More information about this fee",
         "phoneNumber":"",
         "isDeleted":true
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_branches
DROP PROCEDURE IF EXISTS obp_get_branches;
GO
-- create procedure obp_get_branches
CREATE PROCEDURE obp_get_branches
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "limit":100,
       "offset":100,
       "fromDate":"",
       "toDate":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "branchId":{
             "value":"DERBY6"
           },
           "bankId":{
             "value":"gh.29.uk"
           },
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "address":{
             "line1":"",
             "line2":"",
             "line3":"",
             "city":"",
             "county":"",
             "state":"",
             "postCode":"789",
             "countryCode":"1254"
           },
           "location":{
             "latitude":38.8951,
             "longitude":-77.0364,
             "date":"2020-01-27T00:00:00Z",
             "user":{
               "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
               "provider":"ETHEREUM",
               "username":"felixsmith"
             }
           },
           "lobbyString":{
             "hours":"string"
           },
           "driveUpString":{
             "hours":"string"
           },
           "meta":{
             "license":{
               "id":"ODbL-1.0",
               "name":"Open Database License"
             }
           },
           "branchRouting":{
             "scheme":"BRANCH-CODE",
             "address":"DERBY6"
           },
           "lobby":{
             "monday":[
               {
                 "openingTime":"",
                 "closingTime":"2020-01-27"
               }
             ],
             "tuesday":[
               {
                 "openingTime":"",
                 "closingTime":"2020-01-27"
               }
             ],
             "wednesday":[
               {
                 "openingTime":"",
                 "closingTime":"2020-01-27"
               }
             ],
             "thursday":[
               {
                 "openingTime":"",
                 "closingTime":"2020-01-27"
               }
             ],
             "friday":[
               {
                 "openingTime":"",
                 "closingTime":"2020-01-27"
               }
             ],
             "saturday":[
               {
                 "openingTime":"",
                 "closingTime":"2020-01-27"
               }
             ],
             "sunday":[
               {
                 "openingTime":"",
                 "closingTime":"2020-01-27"
               }
             ]
           },
           "driveUp":{
             "monday":{
               "openingTime":"",
               "closingTime":"2020-01-27"
             },
             "tuesday":{
               "openingTime":"",
               "closingTime":"2020-01-27"
             },
             "wednesday":{
               "openingTime":"",
               "closingTime":"2020-01-27"
             },
             "thursday":{
               "openingTime":"",
               "closingTime":"2020-01-27"
             },
             "friday":{
               "openingTime":"",
               "closingTime":"2020-01-27"
             },
             "saturday":{
               "openingTime":"",
               "closingTime":"2020-01-27"
             },
             "sunday":{
               "openingTime":"",
               "closingTime":"2020-01-27"
             }
           },
           "isAccessible":true,
           "accessibleFeatures":"string",
           "branchType":"",
           "moreInfo":"More information about this fee",
           "phoneNumber":"",
           "isDeleted":true
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_atm
DROP PROCEDURE IF EXISTS obp_get_atm;
GO
-- create procedure obp_get_atm
CREATE PROCEDURE obp_get_atm
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "atmId":{
         "value":"atme0352a-9a0f-4bfa-b30b-9003aa467f51"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "atmId":{
           "value":"atme0352a-9a0f-4bfa-b30b-9003aa467f51"
         },
         "bankId":{
           "value":"gh.29.uk"
         },
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "address":{
           "line1":"",
           "line2":"",
           "line3":"",
           "city":"",
           "county":"",
           "state":"",
           "postCode":"789",
           "countryCode":"1254"
         },
         "location":{
           "latitude":38.8951,
           "longitude":-77.0364,
           "date":"2020-01-27T00:00:00Z",
           "user":{
             "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
             "provider":"ETHEREUM",
             "username":"felixsmith"
           }
         },
         "meta":{
           "license":{
             "id":"ODbL-1.0",
             "name":"Open Database License"
           }
         },
         "OpeningTimeOnMonday":"string",
         "ClosingTimeOnMonday":"string",
         "OpeningTimeOnTuesday":"string",
         "ClosingTimeOnTuesday":"string",
         "OpeningTimeOnWednesday":"string",
         "ClosingTimeOnWednesday":"string",
         "OpeningTimeOnThursday":"string",
         "ClosingTimeOnThursday":"string",
         "OpeningTimeOnFriday":"string",
         "ClosingTimeOnFriday":"string",
         "OpeningTimeOnSaturday":"string",
         "ClosingTimeOnSaturday":"string",
         "OpeningTimeOnSunday":"string",
         "ClosingTimeOnSunday":"string",
         "isAccessible":true,
         "locatedAt":"",
         "moreInfo":"More information about this fee",
         "hasDepositCapability":true,
         "supportedLanguages":[
           "[\"es\"",
           "\"fr\"",
           "\"de\"]"
         ],
         "services":[
           ""
         ],
         "accessibilityFeatures":[
           "[\"ATAC\"",
           "\"ATAD\"]"
         ],
         "supportedCurrencies":[
           "[\"EUR\"",
           "\"MXN\"",
           "\"USD\"]"
         ],
         "notes":[
           ""
         ],
         "locationCategories":[
           ""
         ],
         "minimumWithdrawal":"string",
         "branchIdentification":"string",
         "siteIdentification":"",
         "siteName":"string",
         "cashWithdrawalNationalFee":"",
         "cashWithdrawalInternationalFee":"",
         "balanceInquiryFee":""
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_atms
DROP PROCEDURE IF EXISTS obp_get_atms;
GO
-- create procedure obp_get_atms
CREATE PROCEDURE obp_get_atms
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "limit":100,
       "offset":100,
       "fromDate":"",
       "toDate":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "atmId":{
             "value":"atme0352a-9a0f-4bfa-b30b-9003aa467f51"
           },
           "bankId":{
             "value":"gh.29.uk"
           },
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "address":{
             "line1":"",
             "line2":"",
             "line3":"",
             "city":"",
             "county":"",
             "state":"",
             "postCode":"789",
             "countryCode":"1254"
           },
           "location":{
             "latitude":38.8951,
             "longitude":-77.0364,
             "date":"2020-01-27T00:00:00Z",
             "user":{
               "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
               "provider":"ETHEREUM",
               "username":"felixsmith"
             }
           },
           "meta":{
             "license":{
               "id":"ODbL-1.0",
               "name":"Open Database License"
             }
           },
           "OpeningTimeOnMonday":"string",
           "ClosingTimeOnMonday":"string",
           "OpeningTimeOnTuesday":"string",
           "ClosingTimeOnTuesday":"string",
           "OpeningTimeOnWednesday":"string",
           "ClosingTimeOnWednesday":"string",
           "OpeningTimeOnThursday":"string",
           "ClosingTimeOnThursday":"string",
           "OpeningTimeOnFriday":"string",
           "ClosingTimeOnFriday":"string",
           "OpeningTimeOnSaturday":"string",
           "ClosingTimeOnSaturday":"string",
           "OpeningTimeOnSunday":"string",
           "ClosingTimeOnSunday":"string",
           "isAccessible":true,
           "locatedAt":"",
           "moreInfo":"More information about this fee",
           "hasDepositCapability":true,
           "supportedLanguages":[
             "[\"es\"",
             "\"fr\"",
             "\"de\"]"
           ],
           "services":[
             ""
           ],
           "accessibilityFeatures":[
             "[\"ATAC\"",
             "\"ATAD\"]"
           ],
           "supportedCurrencies":[
             "[\"EUR\"",
             "\"MXN\"",
             "\"USD\"]"
           ],
           "notes":[
             ""
           ],
           "locationCategories":[
             ""
           ],
           "minimumWithdrawal":"string",
           "branchIdentification":"string",
           "siteIdentification":"",
           "siteName":"string",
           "cashWithdrawalNationalFee":"",
           "cashWithdrawalInternationalFee":"",
           "balanceInquiryFee":""
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_current_fx_rate
DROP PROCEDURE IF EXISTS obp_get_current_fx_rate;
GO
-- create procedure obp_get_current_fx_rate
CREATE PROCEDURE obp_get_current_fx_rate
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "bankId":{
         "value":"gh.29.uk"
       },
       "fromCurrencyCode":"",
       "toCurrencyCode":"EUR"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "fromCurrencyCode":"",
         "toCurrencyCode":"EUR",
         "conversionValue":100.0,
         "inverseConversionValue":50.0,
         "effectiveDate":"2020-01-27T00:00:00Z"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_transaction_after_challengev300
DROP PROCEDURE IF EXISTS obp_create_transaction_after_challengev300;
GO
-- create procedure obp_create_transaction_after_challengev300
CREATE PROCEDURE obp_create_transaction_after_challengev300
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "initiator":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "transReqId":{
         "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
       },
       "transactionRequestType":{
         "value":"SEPA"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_make_paymentv300
DROP PROCEDURE IF EXISTS obp_make_paymentv300;
GO
-- create procedure obp_make_paymentv300
CREATE PROCEDURE obp_make_paymentv300
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "initiator":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toCounterparty":{
         "createdByUserId":"",
         "name":"John Smith Ltd.",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "currency":"EUR",
         "thisBankId":"",
         "thisAccountId":"",
         "thisViewId":"",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "otherAccountRoutingScheme":"OBP",
         "otherAccountRoutingAddress":"36f8a9e6-c2b1-407a-8bd0-421b7119307e",
         "otherAccountSecondaryRoutingScheme":"IBAN",
         "otherAccountSecondaryRoutingAddress":"DE89370400440532013000",
         "otherBankRoutingScheme":"OBP",
         "otherBankRoutingAddress":"gh.29.uk",
         "otherBranchRoutingScheme":"OBP",
         "otherBranchRoutingAddress":"12f8a9e6-c2b1-407a-8bd0-421b7119307e",
         "isBeneficiary":true,
         "bespoke":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "transactionRequestCommonBody":{
         "value":{
           "currency":"EUR",
           "amount":"10.12"
         },
         "description":"This an optional field. Maximum length is 2000. It can be any characters here."
       },
       "transactionRequestType":{
         "value":"SEPA"
       },
       "chargePolicy":"SHARED"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_transaction_requestv300
DROP PROCEDURE IF EXISTS obp_create_transaction_requestv300;
GO
-- create procedure obp_create_transaction_requestv300
CREATE PROCEDURE obp_create_transaction_requestv300
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "initiator":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "viewId":{
         "value":"owner"
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toCounterparty":{
         "createdByUserId":"",
         "name":"John Smith Ltd.",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "currency":"EUR",
         "thisBankId":"",
         "thisAccountId":"",
         "thisViewId":"",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "otherAccountRoutingScheme":"OBP",
         "otherAccountRoutingAddress":"36f8a9e6-c2b1-407a-8bd0-421b7119307e",
         "otherAccountSecondaryRoutingScheme":"IBAN",
         "otherAccountSecondaryRoutingAddress":"DE89370400440532013000",
         "otherBankRoutingScheme":"OBP",
         "otherBankRoutingAddress":"gh.29.uk",
         "otherBranchRoutingScheme":"OBP",
         "otherBranchRoutingAddress":"12f8a9e6-c2b1-407a-8bd0-421b7119307e",
         "isBeneficiary":true,
         "bespoke":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "transactionRequestType":{
         "value":"SEPA"
       },
       "transactionRequestCommonBody":{
         "value":{
           "currency":"EUR",
           "amount":"10.12"
         },
         "description":"This an optional field. Maximum length is 2000. It can be any characters here."
       },
       "detailsPlain":"string",
       "chargePolicy":"SHARED"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       }
     }'
	);
GO

 
 


-- drop procedure obp_make_payment_v400
DROP PROCEDURE IF EXISTS obp_make_payment_v400;
GO
-- create procedure obp_make_payment_v400
CREATE PROCEDURE obp_make_payment_v400
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "transactionRequest":{
         "id":{
           "value":"8138a7e4-6d02-40e3-a129-0b2bf89de9f1"
         },
         "type":"SEPA",
         "from":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "body":{
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           },
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         },
         "transaction_ids":"string",
         "status":"",
         "start_date":"2019-09-07T00:00:00Z",
         "end_date":"2019-09-08T00:00:00Z",
         "challenge":{
           "id":"123chaneid13-6d02-40e3-a129-0b2bf89de9f0",
           "allowed_attempts":123,
           "challenge_type":"string"
         },
         "charge":{
           "summary":"",
           "value":{
             "currency":"EUR",
             "amount":"10.12"
           }
         }
       },
       "reasons":[
         {
           "code":"125",
           "documentNumber":"",
           "amount":"10.12",
           "currency":"EUR",
           "description":"This an optional field. Maximum length is 2000. It can be any characters here."
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       }
     }'
	);
GO

 
 


-- drop procedure obp_cancel_payment_v400
DROP PROCEDURE IF EXISTS obp_cancel_payment_v400;
GO
-- create procedure obp_cancel_payment_v400
CREATE PROCEDURE obp_cancel_payment_v400
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "transactionId":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "canBeCancelled":true,
         "startSca":true
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_counterparty
DROP PROCEDURE IF EXISTS obp_create_counterparty;
GO
-- create procedure obp_create_counterparty
CREATE PROCEDURE obp_create_counterparty
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "name":"ACCOUNT_MANAGEMENT_FEE",
       "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
       "currency":"EUR",
       "createdByUserId":"",
       "thisBankId":"",
       "thisAccountId":"",
       "thisViewId":"",
       "otherAccountRoutingScheme":"",
       "otherAccountRoutingAddress":"",
       "otherAccountSecondaryRoutingScheme":"",
       "otherAccountSecondaryRoutingAddress":"",
       "otherBankRoutingScheme":"",
       "otherBankRoutingAddress":"",
       "otherBranchRoutingScheme":"",
       "otherBranchRoutingAddress":"",
       "isBeneficiary":true,
       "bespoke":[
         {
           "key":"CustomerNumber",
           "value":"5987953"
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "createdByUserId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
         "currency":"EUR",
         "thisBankId":"",
         "thisAccountId":"",
         "thisViewId":"",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "otherAccountRoutingScheme":"",
         "otherAccountRoutingAddress":"",
         "otherAccountSecondaryRoutingScheme":"",
         "otherAccountSecondaryRoutingAddress":"",
         "otherBankRoutingScheme":"",
         "otherBankRoutingAddress":"",
         "otherBranchRoutingScheme":"",
         "otherBranchRoutingAddress":"",
         "isBeneficiary":true,
         "bespoke":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_check_customer_number_available
DROP PROCEDURE IF EXISTS obp_check_customer_number_available;
GO
-- create procedure obp_check_customer_number_available
CREATE PROCEDURE obp_check_customer_number_available
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "customerNumber":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_create_customer
DROP PROCEDURE IF EXISTS obp_create_customer;
GO
-- create procedure obp_create_customer
CREATE PROCEDURE obp_create_customer
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "legalName":"Eveline Tripman",
       "mobileNumber":"+44 07972 444 876",
       "email":"felixsmith@example.com",
       "faceImage":{
         "date":"2019-09-08T00:00:00Z",
         "url":"http://www.example.com/id-docs/123/image.png"
       },
       "dateOfBirth":"2018-03-09T00:00:00Z",
       "relationshipStatus":"single",
       "dependents":2,
       "dobOfDependents":[
         "2019-09-08T00:00:00Z",
         "2019-01-03T00:00:00Z"
       ],
       "highestEducationAttained":"Master",
       "employmentStatus":"worker",
       "kycStatus":true,
       "lastOkDate":"2019-09-12T00:00:00Z",
       "creditRating":{
         "rating":"",
         "source":""
       },
       "creditLimit":{
         "currency":"EUR",
         "amount":"1000.00"
       },
       "title":"Dr.",
       "branchId":"DERBY6",
       "nameSuffix":"Sr"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "bankId":"gh.29.uk",
         "number":"5987953",
         "legalName":"Eveline Tripman",
         "mobileNumber":"+44 07972 444 876",
         "email":"felixsmith@example.com",
         "faceImage":{
           "date":"2019-09-08T00:00:00Z",
           "url":"http://www.example.com/id-docs/123/image.png"
         },
         "dateOfBirth":"2018-03-09T00:00:00Z",
         "relationshipStatus":"single",
         "dependents":2,
         "dobOfDependents":[
           "2019-09-08T00:00:00Z",
           "2019-01-03T00:00:00Z"
         ],
         "highestEducationAttained":"Master",
         "employmentStatus":"worker",
         "creditRating":{
           "rating":"",
           "source":""
         },
         "creditLimit":{
           "currency":"EUR",
           "amount":"1000.00"
         },
         "kycStatus":true,
         "lastOkDate":"2019-09-08T00:00:00Z",
         "title":"title of customer",
         "branchId":"DERBY6",
         "nameSuffix":"Sr"
       }
     }'
	);
GO

 
 


-- drop procedure obp_update_customer_sca_data
DROP PROCEDURE IF EXISTS obp_update_customer_sca_data;
GO
-- create procedure obp_update_customer_sca_data
CREATE PROCEDURE obp_update_customer_sca_data
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "mobileNumber":"+44 07972 444 876",
       "email":"felixsmith@example.com",
       "customerNumber":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "bankId":"gh.29.uk",
         "number":"5987953",
         "legalName":"Eveline Tripman",
         "mobileNumber":"+44 07972 444 876",
         "email":"felixsmith@example.com",
         "faceImage":{
           "date":"2019-09-08T00:00:00Z",
           "url":"http://www.example.com/id-docs/123/image.png"
         },
         "dateOfBirth":"2018-03-09T00:00:00Z",
         "relationshipStatus":"single",
         "dependents":2,
         "dobOfDependents":[
           "2019-09-08T00:00:00Z",
           "2019-01-03T00:00:00Z"
         ],
         "highestEducationAttained":"Master",
         "employmentStatus":"worker",
         "creditRating":{
           "rating":"",
           "source":""
         },
         "creditLimit":{
           "currency":"EUR",
           "amount":"1000.00"
         },
         "kycStatus":true,
         "lastOkDate":"2019-09-08T00:00:00Z",
         "title":"title of customer",
         "branchId":"DERBY6",
         "nameSuffix":"Sr"
       }
     }'
	);
GO

 
 


-- drop procedure obp_update_customer_credit_data
DROP PROCEDURE IF EXISTS obp_update_customer_credit_data;
GO
-- create procedure obp_update_customer_credit_data
CREATE PROCEDURE obp_update_customer_credit_data
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "creditRating":"",
       "creditSource":"string",
       "creditLimit":{
         "currency":"EUR",
         "amount":"1000.00"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "bankId":"gh.29.uk",
         "number":"5987953",
         "legalName":"Eveline Tripman",
         "mobileNumber":"+44 07972 444 876",
         "email":"felixsmith@example.com",
         "faceImage":{
           "date":"2019-09-08T00:00:00Z",
           "url":"http://www.example.com/id-docs/123/image.png"
         },
         "dateOfBirth":"2018-03-09T00:00:00Z",
         "relationshipStatus":"single",
         "dependents":2,
         "dobOfDependents":[
           "2019-09-08T00:00:00Z",
           "2019-01-03T00:00:00Z"
         ],
         "highestEducationAttained":"Master",
         "employmentStatus":"worker",
         "creditRating":{
           "rating":"",
           "source":""
         },
         "creditLimit":{
           "currency":"EUR",
           "amount":"1000.00"
         },
         "kycStatus":true,
         "lastOkDate":"2019-09-08T00:00:00Z",
         "title":"title of customer",
         "branchId":"DERBY6",
         "nameSuffix":"Sr"
       }
     }'
	);
GO

 
 


-- drop procedure obp_update_customer_general_data
DROP PROCEDURE IF EXISTS obp_update_customer_general_data;
GO
-- create procedure obp_update_customer_general_data
CREATE PROCEDURE obp_update_customer_general_data
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "legalName":"Eveline Tripman",
       "faceImage":{
         "date":"2019-09-08T00:00:00Z",
         "url":"http://www.example.com/id-docs/123/image.png"
       },
       "dateOfBirth":"2018-03-09T00:00:00Z",
       "relationshipStatus":"single",
       "dependents":2,
       "highestEducationAttained":"Master",
       "employmentStatus":"worker",
       "title":"Dr.",
       "branchId":"DERBY6",
       "nameSuffix":"Sr"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "bankId":"gh.29.uk",
         "number":"5987953",
         "legalName":"Eveline Tripman",
         "mobileNumber":"+44 07972 444 876",
         "email":"felixsmith@example.com",
         "faceImage":{
           "date":"2019-09-08T00:00:00Z",
           "url":"http://www.example.com/id-docs/123/image.png"
         },
         "dateOfBirth":"2018-03-09T00:00:00Z",
         "relationshipStatus":"single",
         "dependents":2,
         "dobOfDependents":[
           "2019-09-08T00:00:00Z",
           "2019-01-03T00:00:00Z"
         ],
         "highestEducationAttained":"Master",
         "employmentStatus":"worker",
         "creditRating":{
           "rating":"",
           "source":""
         },
         "creditLimit":{
           "currency":"EUR",
           "amount":"1000.00"
         },
         "kycStatus":true,
         "lastOkDate":"2019-09-08T00:00:00Z",
         "title":"title of customer",
         "branchId":"DERBY6",
         "nameSuffix":"Sr"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_customers_by_user_id
DROP PROCEDURE IF EXISTS obp_get_customers_by_user_id;
GO
-- create procedure obp_get_customers_by_user_id
CREATE PROCEDURE obp_get_customers_by_user_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "bankId":"gh.29.uk",
           "number":"5987953",
           "legalName":"Eveline Tripman",
           "mobileNumber":"+44 07972 444 876",
           "email":"felixsmith@example.com",
           "faceImage":{
             "date":"2019-09-08T00:00:00Z",
             "url":"http://www.example.com/id-docs/123/image.png"
           },
           "dateOfBirth":"2018-03-09T00:00:00Z",
           "relationshipStatus":"single",
           "dependents":2,
           "dobOfDependents":[
             "2019-09-08T00:00:00Z",
             "2019-01-03T00:00:00Z"
           ],
           "highestEducationAttained":"Master",
           "employmentStatus":"worker",
           "creditRating":{
             "rating":"",
             "source":""
           },
           "creditLimit":{
             "currency":"EUR",
             "amount":"1000.00"
           },
           "kycStatus":true,
           "lastOkDate":"2019-09-08T00:00:00Z",
           "title":"title of customer",
           "branchId":"DERBY6",
           "nameSuffix":"Sr"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_customer_by_customer_id
DROP PROCEDURE IF EXISTS obp_get_customer_by_customer_id;
GO
-- create procedure obp_get_customer_by_customer_id
CREATE PROCEDURE obp_get_customer_by_customer_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "bankId":"gh.29.uk",
         "number":"5987953",
         "legalName":"Eveline Tripman",
         "mobileNumber":"+44 07972 444 876",
         "email":"felixsmith@example.com",
         "faceImage":{
           "date":"2019-09-08T00:00:00Z",
           "url":"http://www.example.com/id-docs/123/image.png"
         },
         "dateOfBirth":"2018-03-09T00:00:00Z",
         "relationshipStatus":"single",
         "dependents":2,
         "dobOfDependents":[
           "2019-09-08T00:00:00Z",
           "2019-01-03T00:00:00Z"
         ],
         "highestEducationAttained":"Master",
         "employmentStatus":"worker",
         "creditRating":{
           "rating":"",
           "source":""
         },
         "creditLimit":{
           "currency":"EUR",
           "amount":"1000.00"
         },
         "kycStatus":true,
         "lastOkDate":"2019-09-08T00:00:00Z",
         "title":"title of customer",
         "branchId":"DERBY6",
         "nameSuffix":"Sr"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_customer_by_customer_number
DROP PROCEDURE IF EXISTS obp_get_customer_by_customer_number;
GO
-- create procedure obp_get_customer_by_customer_number
CREATE PROCEDURE obp_get_customer_by_customer_number
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerNumber":"5987953",
       "bankId":{
         "value":"gh.29.uk"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "bankId":"gh.29.uk",
         "number":"5987953",
         "legalName":"Eveline Tripman",
         "mobileNumber":"+44 07972 444 876",
         "email":"felixsmith@example.com",
         "faceImage":{
           "date":"2019-09-08T00:00:00Z",
           "url":"http://www.example.com/id-docs/123/image.png"
         },
         "dateOfBirth":"2018-03-09T00:00:00Z",
         "relationshipStatus":"single",
         "dependents":2,
         "dobOfDependents":[
           "2019-09-08T00:00:00Z",
           "2019-01-03T00:00:00Z"
         ],
         "highestEducationAttained":"Master",
         "employmentStatus":"worker",
         "creditRating":{
           "rating":"",
           "source":""
         },
         "creditLimit":{
           "currency":"EUR",
           "amount":"1000.00"
         },
         "kycStatus":true,
         "lastOkDate":"2019-09-08T00:00:00Z",
         "title":"title of customer",
         "branchId":"DERBY6",
         "nameSuffix":"Sr"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_customer_address
DROP PROCEDURE IF EXISTS obp_get_customer_address;
GO
-- create procedure obp_get_customer_address
CREATE PROCEDURE obp_get_customer_address
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "customerAddressId":"",
           "line1":"",
           "line2":"",
           "line3":"",
           "city":"",
           "county":"",
           "state":"",
           "postcode":"",
           "countryCode":"1254",
           "status":"",
           "tags":"Create-My-User",
           "insertDate":"2020-01-27T00:00:00Z"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_customer_address
DROP PROCEDURE IF EXISTS obp_create_customer_address;
GO
-- create procedure obp_create_customer_address
CREATE PROCEDURE obp_create_customer_address
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "line1":"",
       "line2":"",
       "line3":"",
       "city":"",
       "county":"",
       "state":"",
       "postcode":"",
       "countryCode":"1254",
       "tags":"Create-My-User",
       "status":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "customerAddressId":"",
         "line1":"",
         "line2":"",
         "line3":"",
         "city":"",
         "county":"",
         "state":"",
         "postcode":"",
         "countryCode":"1254",
         "status":"",
         "tags":"Create-My-User",
         "insertDate":"2020-01-27T00:00:00Z"
       }
     }'
	);
GO

 
 


-- drop procedure obp_update_customer_address
DROP PROCEDURE IF EXISTS obp_update_customer_address;
GO
-- create procedure obp_update_customer_address
CREATE PROCEDURE obp_update_customer_address
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerAddressId":"",
       "line1":"",
       "line2":"",
       "line3":"",
       "city":"",
       "county":"",
       "state":"",
       "postcode":"",
       "countryCode":"1254",
       "tags":"Create-My-User",
       "status":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "customerAddressId":"",
         "line1":"",
         "line2":"",
         "line3":"",
         "city":"",
         "county":"",
         "state":"",
         "postcode":"",
         "countryCode":"1254",
         "status":"",
         "tags":"Create-My-User",
         "insertDate":"2020-01-27T00:00:00Z"
       }
     }'
	);
GO

 
 


-- drop procedure obp_delete_customer_address
DROP PROCEDURE IF EXISTS obp_delete_customer_address;
GO
-- create procedure obp_delete_customer_address
CREATE PROCEDURE obp_delete_customer_address
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerAddressId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_create_tax_residence
DROP PROCEDURE IF EXISTS obp_create_tax_residence;
GO
-- create procedure obp_create_tax_residence
CREATE PROCEDURE obp_create_tax_residence
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "domain":"",
       "taxNumber":"456"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "taxResidenceId":"",
         "domain":"",
         "taxNumber":"456"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_tax_residence
DROP PROCEDURE IF EXISTS obp_get_tax_residence;
GO
-- create procedure obp_get_tax_residence
CREATE PROCEDURE obp_get_tax_residence
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "taxResidenceId":"",
           "domain":"",
           "taxNumber":"456"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_delete_tax_residence
DROP PROCEDURE IF EXISTS obp_delete_tax_residence;
GO
-- create procedure obp_delete_tax_residence
CREATE PROCEDURE obp_delete_tax_residence
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "taxResourceId":"string"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_get_customers
DROP PROCEDURE IF EXISTS obp_get_customers;
GO
-- create procedure obp_get_customers
CREATE PROCEDURE obp_get_customers
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "limit":100,
       "offset":100,
       "fromDate":"",
       "toDate":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "bankId":"gh.29.uk",
           "number":"5987953",
           "legalName":"Eveline Tripman",
           "mobileNumber":"+44 07972 444 876",
           "email":"felixsmith@example.com",
           "faceImage":{
             "date":"2019-09-08T00:00:00Z",
             "url":"http://www.example.com/id-docs/123/image.png"
           },
           "dateOfBirth":"2018-03-09T00:00:00Z",
           "relationshipStatus":"single",
           "dependents":2,
           "dobOfDependents":[
             "2019-09-08T00:00:00Z",
             "2019-01-03T00:00:00Z"
           ],
           "highestEducationAttained":"Master",
           "employmentStatus":"worker",
           "creditRating":{
             "rating":"",
             "source":""
           },
           "creditLimit":{
             "currency":"EUR",
             "amount":"1000.00"
           },
           "kycStatus":true,
           "lastOkDate":"2019-09-08T00:00:00Z",
           "title":"title of customer",
           "branchId":"DERBY6",
           "nameSuffix":"Sr"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_customers_by_customer_phone_number
DROP PROCEDURE IF EXISTS obp_get_customers_by_customer_phone_number;
GO
-- create procedure obp_get_customers_by_customer_phone_number
CREATE PROCEDURE obp_get_customers_by_customer_phone_number
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "phoneNumber":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "bankId":"gh.29.uk",
           "number":"5987953",
           "legalName":"Eveline Tripman",
           "mobileNumber":"+44 07972 444 876",
           "email":"felixsmith@example.com",
           "faceImage":{
             "date":"2019-09-08T00:00:00Z",
             "url":"http://www.example.com/id-docs/123/image.png"
           },
           "dateOfBirth":"2018-03-09T00:00:00Z",
           "relationshipStatus":"single",
           "dependents":2,
           "dobOfDependents":[
             "2019-09-08T00:00:00Z",
             "2019-01-03T00:00:00Z"
           ],
           "highestEducationAttained":"Master",
           "employmentStatus":"worker",
           "creditRating":{
             "rating":"",
             "source":""
           },
           "creditLimit":{
             "currency":"EUR",
             "amount":"1000.00"
           },
           "kycStatus":true,
           "lastOkDate":"2019-09-08T00:00:00Z",
           "title":"title of customer",
           "branchId":"DERBY6",
           "nameSuffix":"Sr"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_checkbook_orders
DROP PROCEDURE IF EXISTS obp_get_checkbook_orders;
GO
-- create procedure obp_get_checkbook_orders
CREATE PROCEDURE obp_get_checkbook_orders
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "account":{
           "bank_id":"gh.29.uk",
           "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
           "account_type":"string",
           "account_routings":[
             {
               "scheme":"scheme value",
               "address":""
             }
           ],
           "branch_routings":[
             {
               "scheme":"scheme value",
               "address":""
             }
           ]
         },
         "orders":[
           {
             "order":{
               "order_id":"string",
               "order_date":"string",
               "number_of_checkbooks":"string",
               "distribution_channel":"string",
               "status":"",
               "first_check_number":"string",
               "shipping_code":"string"
             }
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_status_of_credit_card_order
DROP PROCEDURE IF EXISTS obp_get_status_of_credit_card_order;
GO
-- create procedure obp_get_status_of_credit_card_order
CREATE PROCEDURE obp_get_status_of_credit_card_order
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "card_type":"string",
           "card_description":"string",
           "use_type":"string"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_user_auth_context
DROP PROCEDURE IF EXISTS obp_create_user_auth_context;
GO
-- create procedure obp_create_user_auth_context
CREATE PROCEDURE obp_create_user_auth_context
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "key":"CustomerNumber",
       "value":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "userAuthContextId":"",
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "key":"CustomerNumber",
         "value":"5987953",
         "timeStamp":"1100-01-01T00:00:00Z",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_user_auth_context_update
DROP PROCEDURE IF EXISTS obp_create_user_auth_context_update;
GO
-- create procedure obp_create_user_auth_context_update
CREATE PROCEDURE obp_create_user_auth_context_update
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "key":"CustomerNumber",
       "value":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "userAuthContextUpdateId":"",
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "key":"CustomerNumber",
         "value":"5987953",
         "challenge":"",
         "status":"",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       }
     }'
	);
GO

 
 


-- drop procedure obp_delete_user_auth_contexts
DROP PROCEDURE IF EXISTS obp_delete_user_auth_contexts;
GO
-- create procedure obp_delete_user_auth_contexts
CREATE PROCEDURE obp_delete_user_auth_contexts
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_delete_user_auth_context_by_id
DROP PROCEDURE IF EXISTS obp_delete_user_auth_context_by_id;
GO
-- create procedure obp_delete_user_auth_context_by_id
CREATE PROCEDURE obp_delete_user_auth_context_by_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "userAuthContextId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_get_user_auth_contexts
DROP PROCEDURE IF EXISTS obp_get_user_auth_contexts;
GO
-- create procedure obp_get_user_auth_contexts
CREATE PROCEDURE obp_get_user_auth_contexts
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "userAuthContextId":"",
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "key":"CustomerNumber",
           "value":"5987953",
           "timeStamp":"1100-01-01T00:00:00Z",
           "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_product_attribute
DROP PROCEDURE IF EXISTS obp_create_or_update_product_attribute;
GO
-- create procedure obp_create_or_update_product_attribute
CREATE PROCEDURE obp_create_or_update_product_attribute
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "productCode":{
         "value":"1234BW"
       },
       "productAttributeId":"",
       "name":"ACCOUNT_MANAGEMENT_FEE",
       "productAttributeType":"STRING",
       "value":"5987953",
       "isActive":true
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "productCode":{
           "value":"1234BW"
         },
         "productAttributeId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "attributeType":"STRING",
         "value":"5987953",
         "isActive":true
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_product_attribute_by_id
DROP PROCEDURE IF EXISTS obp_get_product_attribute_by_id;
GO
-- create procedure obp_get_product_attribute_by_id
CREATE PROCEDURE obp_get_product_attribute_by_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "productAttributeId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "productCode":{
           "value":"1234BW"
         },
         "productAttributeId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "attributeType":"STRING",
         "value":"5987953",
         "isActive":true
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_product_attributes_by_bank_and_code
DROP PROCEDURE IF EXISTS obp_get_product_attributes_by_bank_and_code;
GO
-- create procedure obp_get_product_attributes_by_bank_and_code
CREATE PROCEDURE obp_get_product_attributes_by_bank_and_code
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bank":{
         "value":""
       },
       "productCode":{
         "value":"1234BW"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "productCode":{
             "value":"1234BW"
           },
           "productAttributeId":"",
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "attributeType":"STRING",
           "value":"5987953",
           "isActive":true
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_delete_product_attribute
DROP PROCEDURE IF EXISTS obp_delete_product_attribute;
GO
-- create procedure obp_delete_product_attribute
CREATE PROCEDURE obp_delete_product_attribute
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "productAttributeId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_get_account_attribute_by_id
DROP PROCEDURE IF EXISTS obp_get_account_attribute_by_id;
GO
-- create procedure obp_get_account_attribute_by_id
CREATE PROCEDURE obp_get_account_attribute_by_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "accountAttributeId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "productCode":{
           "value":"1234BW"
         },
         "accountAttributeId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "attributeType":"STRING",
         "value":"5987953"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_transaction_attribute_by_id
DROP PROCEDURE IF EXISTS obp_get_transaction_attribute_by_id;
GO
-- create procedure obp_get_transaction_attribute_by_id
CREATE PROCEDURE obp_get_transaction_attribute_by_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "transactionAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "transactionId":{
           "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
         },
         "transactionAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "attributeType":"STRING",
         "name":"HOUSE_RENT",
         "value":"123456789"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_account_attribute
DROP PROCEDURE IF EXISTS obp_create_or_update_account_attribute;
GO
-- create procedure obp_create_or_update_account_attribute
CREATE PROCEDURE obp_create_or_update_account_attribute
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "productCode":{
         "value":"1234BW"
       },
       "productAttributeId":"",
       "name":"ACCOUNT_MANAGEMENT_FEE",
       "accountAttributeType":"STRING",
       "value":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "productCode":{
           "value":"1234BW"
         },
         "accountAttributeId":"",
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "attributeType":"STRING",
         "value":"5987953"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_customer_attribute
DROP PROCEDURE IF EXISTS obp_create_or_update_customer_attribute;
GO
-- create procedure obp_create_or_update_customer_attribute
CREATE PROCEDURE obp_create_or_update_customer_attribute
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "customerId":{
         "value":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       },
       "customerAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "name":"ACCOUNT_MANAGEMENT_FEE",
       "attributeType":"STRING",
       "value":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "customerId":{
           "value":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
         },
         "customerAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "attributeType":"STRING",
         "name":"SPECIAL_TAX_NUMBER",
         "value":"123456789"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_transaction_attribute
DROP PROCEDURE IF EXISTS obp_create_or_update_transaction_attribute;
GO
-- create procedure obp_create_or_update_transaction_attribute
CREATE PROCEDURE obp_create_or_update_transaction_attribute
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "transactionId":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       },
       "transactionAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "name":"ACCOUNT_MANAGEMENT_FEE",
       "attributeType":"STRING",
       "value":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "transactionId":{
           "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
         },
         "transactionAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "attributeType":"STRING",
         "name":"HOUSE_RENT",
         "value":"123456789"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_account_attributes
DROP PROCEDURE IF EXISTS obp_create_account_attributes;
GO
-- create procedure obp_create_account_attributes
CREATE PROCEDURE obp_create_account_attributes
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       },
       "productCode":{
         "value":"1234BW"
       },
       "accountAttributes":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "productCode":{
             "value":"1234BW"
           },
           "productAttributeId":"",
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "attributeType":"STRING",
           "value":"5987953",
           "isActive":true
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "productCode":{
             "value":"1234BW"
           },
           "accountAttributeId":"",
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "attributeType":"STRING",
           "value":"5987953"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_account_attributes_by_account
DROP PROCEDURE IF EXISTS obp_get_account_attributes_by_account;
GO
-- create procedure obp_get_account_attributes_by_account
CREATE PROCEDURE obp_get_account_attributes_by_account
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "accountId":{
         "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "accountId":{
             "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
           },
           "productCode":{
             "value":"1234BW"
           },
           "accountAttributeId":"",
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "attributeType":"STRING",
           "value":"5987953"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_customer_attributes
DROP PROCEDURE IF EXISTS obp_get_customer_attributes;
GO
-- create procedure obp_get_customer_attributes
CREATE PROCEDURE obp_get_customer_attributes
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "customerId":{
         "value":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "customerId":{
             "value":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
           },
           "customerAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "attributeType":"STRING",
           "name":"SPECIAL_TAX_NUMBER",
           "value":"123456789"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_customer_ids_by_attribute_name_values
DROP PROCEDURE IF EXISTS obp_get_customer_ids_by_attribute_name_values;
GO
-- create procedure obp_get_customer_ids_by_attribute_name_values
CREATE PROCEDURE obp_get_customer_ids_by_attribute_name_values
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "nameValues":{
         "some_name":[
           "name1",
           "name2"
         ]
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         ""
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_customer_attributes_for_customers
DROP PROCEDURE IF EXISTS obp_get_customer_attributes_for_customers;
GO
-- create procedure obp_get_customer_attributes_for_customers
CREATE PROCEDURE obp_get_customer_attributes_for_customers
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customers":[
         {
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "bankId":"gh.29.uk",
           "number":"5987953",
           "legalName":"Eveline Tripman",
           "mobileNumber":"+44 07972 444 876",
           "email":"felixsmith@example.com",
           "faceImage":{
             "date":"2019-09-08T00:00:00Z",
             "url":"http://www.example.com/id-docs/123/image.png"
           },
           "dateOfBirth":"2018-03-09T00:00:00Z",
           "relationshipStatus":"single",
           "dependents":2,
           "dobOfDependents":[
             "2019-09-08T00:00:00Z",
             "2019-01-03T00:00:00Z"
           ],
           "highestEducationAttained":"Master",
           "employmentStatus":"worker",
           "creditRating":{
             "rating":"",
             "source":""
           },
           "creditLimit":{
             "currency":"EUR",
             "amount":"1000.00"
           },
           "kycStatus":true,
           "lastOkDate":"2019-09-08T00:00:00Z",
           "title":"title of customer",
           "branchId":"DERBY6",
           "nameSuffix":"Sr"
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "customer":{
             "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
             "bankId":"gh.29.uk",
             "number":"546387432",
             "legalName":"Eveline Tripman",
             "mobileNumber":"+44 07972 444 876",
             "email":"felixsmith@example.com",
             "faceImage":{
               "date":"1100-01-01T00:00:00Z",
               "url":"http://www.example.com/id-docs/123/image.png"
             },
             "dateOfBirth":"1100-01-01T00:00:00Z",
             "relationshipStatus":"single",
             "dependents":2,
             "dobOfDependents":[
               "1100-01-01T00:00:00Z"
             ],
             "highestEducationAttained":"Master",
             "employmentStatus":"worker",
             "creditRating":{
               "rating":"",
               "source":""
             },
             "creditLimit":{
               "currency":"EUR",
               "amount":"50.89"
             },
             "kycStatus":true,
             "lastOkDate":"1100-01-01T00:00:00Z",
             "title":"Dr.",
             "branchId":"DERBY6",
             "nameSuffix":"Sr"
           },
           "attributes":[
             {
               "bankId":{
                 "value":"gh.29.uk"
               },
               "customerId":{
                 "value":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
               },
               "customerAttributeId":"some_customer_attributeId_value",
               "attributeType":"INTEGER",
               "name":"customer_attribute_field",
               "value":"example_value"
             }
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_transaction_ids_by_attribute_name_values
DROP PROCEDURE IF EXISTS obp_get_transaction_ids_by_attribute_name_values;
GO
-- create procedure obp_get_transaction_ids_by_attribute_name_values
CREATE PROCEDURE obp_get_transaction_ids_by_attribute_name_values
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "nameValues":{
         "some_name":[
           "name1",
           "name2"
         ]
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         ""
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_transaction_attributes
DROP PROCEDURE IF EXISTS obp_get_transaction_attributes;
GO
-- create procedure obp_get_transaction_attributes
CREATE PROCEDURE obp_get_transaction_attributes
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "transactionId":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":{
             "value":"gh.29.uk"
           },
           "transactionId":{
             "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
           },
           "transactionAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "attributeType":"STRING",
           "name":"HOUSE_RENT",
           "value":"123456789"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_customer_attribute_by_id
DROP PROCEDURE IF EXISTS obp_get_customer_attribute_by_id;
GO
-- create procedure obp_get_customer_attribute_by_id
CREATE PROCEDURE obp_get_customer_attribute_by_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":{
           "value":"gh.29.uk"
         },
         "customerId":{
           "value":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
         },
         "customerAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "attributeType":"STRING",
         "name":"SPECIAL_TAX_NUMBER",
         "value":"123456789"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_card_attribute
DROP PROCEDURE IF EXISTS obp_create_or_update_card_attribute;
GO
-- create procedure obp_create_or_update_card_attribute
CREATE PROCEDURE obp_create_or_update_card_attribute
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
       "cardAttributeId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
       "name":"ACCOUNT_MANAGEMENT_FEE",
       "cardAttributeType":"STRING",
       "value":"5987953"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "name":"OVERDRAFT_START_DATE",
         "card_id":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
         "attribute_type":"STRING",
         "bank_id":{
           "value":"gh.29.uk"
         },
         "value":"2012-04-23",
         "card_attribute_id":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_card_attribute_by_id
DROP PROCEDURE IF EXISTS obp_get_card_attribute_by_id;
GO
-- create procedure obp_get_card_attribute_by_id
CREATE PROCEDURE obp_get_card_attribute_by_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "cardAttributeId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "name":"OVERDRAFT_START_DATE",
         "card_id":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
         "attribute_type":"STRING",
         "bank_id":{
           "value":"gh.29.uk"
         },
         "value":"2012-04-23",
         "card_attribute_id":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_card_attributes_from_provider
DROP PROCEDURE IF EXISTS obp_get_card_attributes_from_provider;
GO
-- create procedure obp_get_card_attributes_from_provider
CREATE PROCEDURE obp_get_card_attributes_from_provider
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "cardId":"36f8a9e6-c2b1-407a-8bd0-421b7119307e "
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "name":"OVERDRAFT_START_DATE",
           "card_id":"36f8a9e6-c2b1-407a-8bd0-421b7119307e ",
           "attribute_type":"STRING",
           "bank_id":{
             "value":"gh.29.uk"
           },
           "value":"2012-04-23",
           "card_attribute_id":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_account_application
DROP PROCEDURE IF EXISTS obp_create_account_application;
GO
-- create procedure obp_create_account_application
CREATE PROCEDURE obp_create_account_application
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "productCode":{
         "value":"1234BW"
       },
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountApplicationId":"",
         "productCode":{
           "value":"1234BW"
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "dateOfApplication":"2020-01-27T00:00:00Z",
         "status":""
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_all_account_application
DROP PROCEDURE IF EXISTS obp_get_all_account_application;
GO
-- create procedure obp_get_all_account_application
CREATE PROCEDURE obp_get_all_account_application
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "accountApplicationId":"",
           "productCode":{
             "value":"1234BW"
           },
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "dateOfApplication":"2020-01-27T00:00:00Z",
           "status":""
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_account_application_by_id
DROP PROCEDURE IF EXISTS obp_get_account_application_by_id;
GO
-- create procedure obp_get_account_application_by_id
CREATE PROCEDURE obp_get_account_application_by_id
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "accountApplicationId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountApplicationId":"",
         "productCode":{
           "value":"1234BW"
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "dateOfApplication":"2020-01-27T00:00:00Z",
         "status":""
       }
     }'
	);
GO

 
 


-- drop procedure obp_update_account_application_status
DROP PROCEDURE IF EXISTS obp_update_account_application_status;
GO
-- create procedure obp_update_account_application_status
CREATE PROCEDURE obp_update_account_application_status
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "accountApplicationId":"",
       "status":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "accountApplicationId":"",
         "productCode":{
           "value":"1234BW"
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "dateOfApplication":"2020-01-27T00:00:00Z",
         "status":""
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_or_create_product_collection
DROP PROCEDURE IF EXISTS obp_get_or_create_product_collection;
GO
-- create procedure obp_get_or_create_product_collection
CREATE PROCEDURE obp_get_or_create_product_collection
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "collectionCode":"",
       "productCodes":[
         ""
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "collectionCode":"",
           "productCode":"1234BW"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_product_collection
DROP PROCEDURE IF EXISTS obp_get_product_collection;
GO
-- create procedure obp_get_product_collection
CREATE PROCEDURE obp_get_product_collection
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "collectionCode":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "collectionCode":"",
           "productCode":"1234BW"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_or_create_product_collection_item
DROP PROCEDURE IF EXISTS obp_get_or_create_product_collection_item;
GO
-- create procedure obp_get_or_create_product_collection_item
CREATE PROCEDURE obp_get_or_create_product_collection_item
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "collectionCode":"",
       "memberProductCodes":[
         ""
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "collectionCode":"",
           "memberProductCode":""
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_product_collection_item
DROP PROCEDURE IF EXISTS obp_get_product_collection_item;
GO
-- create procedure obp_get_product_collection_item
CREATE PROCEDURE obp_get_product_collection_item
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "collectionCode":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "collectionCode":"",
           "memberProductCode":""
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_product_collection_items_tree
DROP PROCEDURE IF EXISTS obp_get_product_collection_items_tree;
GO
-- create procedure obp_get_product_collection_items_tree
CREATE PROCEDURE obp_get_product_collection_items_tree
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "collectionCode":"",
       "bankId":"gh.29.uk"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "productCollectionItem":{
             "collectionCode":"",
             "memberProductCode":""
           },
           "product":{
             "bankId":{
               "value":"gh.29.uk"
             },
             "code":{
               "value":"1234BW"
             },
             "parentProductCode":{
               "value":"787LOW"
             },
             "name":"Deposit Account 1",
             "category":"",
             "family":"",
             "superFamily":"",
             "moreInfoUrl":"www.example.com/abc",
             "termsAndConditionsUrl":"www.example.com/xyz",
             "details":"",
             "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
             "meta":{
               "license":{
                 "id":"ODbL-1.0",
                 "name":"Open Database License"
               }
             }
           },
           "attributes":[
             {
               "bankId":{
                 "value":"gh.29.uk"
               },
               "productCode":{
                 "value":"1234BW"
               },
               "productAttributeId":"",
               "name":"ACCOUNT_MANAGEMENT_FEE",
               "attributeType":"STRING",
               "value":"5987953",
               "isActive":true
             }
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_meeting
DROP PROCEDURE IF EXISTS obp_create_meeting;
GO
-- create procedure obp_create_meeting
CREATE PROCEDURE obp_create_meeting
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "staffUser":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "customerUser":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "providerId":"",
       "purposeId":"",
       "when":"2020-01-27T00:00:00Z",
       "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
       "customerToken":"",
       "staffToken":"",
       "creator":{
         "name":"ACCOUNT_MANAGEMENT_FEE",
         "phone":"",
         "email":"felixsmith@example.com"
       },
       "invitees":[
         {
           "contactDetails":{
             "name":"ACCOUNT_MANAGEMENT_FEE",
             "phone":"",
             "email":"felixsmith@example.com"
           },
           "status":""
         }
       ]
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "meetingId":"",
         "providerId":"",
         "purposeId":"",
         "bankId":"gh.29.uk",
         "present":{
           "staffUserId":"",
           "customerUserId":""
         },
         "keys":{
           "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
           "customerToken":"",
           "staffToken":""
         },
         "when":"2020-01-27T00:00:00Z",
         "creator":{
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "phone":"",
           "email":"felixsmith@example.com"
         },
         "invitees":[
           {
             "contactDetails":{
               "name":"ACCOUNT_MANAGEMENT_FEE",
               "phone":"",
               "email":"felixsmith@example.com"
             },
             "status":""
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_meetings
DROP PROCEDURE IF EXISTS obp_get_meetings;
GO
-- create procedure obp_get_meetings
CREATE PROCEDURE obp_get_meetings
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "user":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       }
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "meetingId":"",
           "providerId":"",
           "purposeId":"",
           "bankId":"gh.29.uk",
           "present":{
             "staffUserId":"",
             "customerUserId":""
           },
           "keys":{
             "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
             "customerToken":"",
             "staffToken":""
           },
           "when":"2020-01-27T00:00:00Z",
           "creator":{
             "name":"ACCOUNT_MANAGEMENT_FEE",
             "phone":"",
             "email":"felixsmith@example.com"
           },
           "invitees":[
             {
               "contactDetails":{
                 "name":"ACCOUNT_MANAGEMENT_FEE",
                 "phone":"",
                 "email":"felixsmith@example.com"
               },
               "status":""
             }
           ]
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_meeting
DROP PROCEDURE IF EXISTS obp_get_meeting;
GO
-- create procedure obp_get_meeting
CREATE PROCEDURE obp_get_meeting
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "user":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "meetingId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "meetingId":"",
         "providerId":"",
         "purposeId":"",
         "bankId":"gh.29.uk",
         "present":{
           "staffUserId":"",
           "customerUserId":""
         },
         "keys":{
           "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
           "customerToken":"",
           "staffToken":""
         },
         "when":"2020-01-27T00:00:00Z",
         "creator":{
           "name":"ACCOUNT_MANAGEMENT_FEE",
           "phone":"",
           "email":"felixsmith@example.com"
         },
         "invitees":[
           {
             "contactDetails":{
               "name":"ACCOUNT_MANAGEMENT_FEE",
               "phone":"",
               "email":"felixsmith@example.com"
             },
             "status":""
           }
         ]
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_kyc_check
DROP PROCEDURE IF EXISTS obp_create_or_update_kyc_check;
GO
-- create procedure obp_create_or_update_kyc_check
CREATE PROCEDURE obp_create_or_update_kyc_check
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "id":"d8839721-ad8f-45dd-9f78-2080414b93f9",
       "customerNumber":"5987953",
       "date":"2020-01-27T00:00:00Z",
       "how":"",
       "staffUserId":"",
       "mStaffName":"string",
       "mSatisfied":true,
       "comments":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":"gh.29.uk",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "idKycCheck":"string",
         "customerNumber":"5987953",
         "date":"2020-01-27T00:00:00Z",
         "how":"",
         "staffUserId":"",
         "staffName":"",
         "satisfied":true,
         "comments":""
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_kyc_document
DROP PROCEDURE IF EXISTS obp_create_or_update_kyc_document;
GO
-- create procedure obp_create_or_update_kyc_document
CREATE PROCEDURE obp_create_or_update_kyc_document
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "id":"d8839721-ad8f-45dd-9f78-2080414b93f9",
       "customerNumber":"5987953",
       "type":"",
       "number":"",
       "issueDate":"2020-01-27T00:00:00Z",
       "issuePlace":"",
       "expiryDate":"2021-01-27T00:00:00Z"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":"gh.29.uk",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "idKycDocument":"string",
         "customerNumber":"5987953",
         "type":"",
         "number":"",
         "issueDate":"2020-01-27T00:00:00Z",
         "issuePlace":"",
         "expiryDate":"2021-01-27T00:00:00Z"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_kyc_media
DROP PROCEDURE IF EXISTS obp_create_or_update_kyc_media;
GO
-- create procedure obp_create_or_update_kyc_media
CREATE PROCEDURE obp_create_or_update_kyc_media
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "id":"d8839721-ad8f-45dd-9f78-2080414b93f9",
       "customerNumber":"5987953",
       "type":"",
       "url":"http://www.example.com/id-docs/123/image.png",
       "date":"2020-01-27T00:00:00Z",
       "relatesToKycDocumentId":"",
       "relatesToKycCheckId":""
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":"gh.29.uk",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "idKycMedia":"string",
         "customerNumber":"5987953",
         "type":"",
         "url":"http://www.example.com/id-docs/123/image.png",
         "date":"2020-01-27T00:00:00Z",
         "relatesToKycDocumentId":"",
         "relatesToKycCheckId":""
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_or_update_kyc_status
DROP PROCEDURE IF EXISTS obp_create_or_update_kyc_status;
GO
-- create procedure obp_create_or_update_kyc_status
CREATE PROCEDURE obp_create_or_update_kyc_status
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "customerNumber":"5987953",
       "ok":true,
       "date":"2020-01-27T00:00:00Z"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "bankId":"gh.29.uk",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "customerNumber":"5987953",
         "ok":true,
         "date":"2020-01-27T00:00:00Z"
       }
     }'
	);
GO

 
 


-- drop procedure obp_get_kyc_checks
DROP PROCEDURE IF EXISTS obp_get_kyc_checks;
GO
-- create procedure obp_get_kyc_checks
CREATE PROCEDURE obp_get_kyc_checks
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":"gh.29.uk",
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "idKycCheck":"string",
           "customerNumber":"5987953",
           "date":"2020-01-27T00:00:00Z",
           "how":"",
           "staffUserId":"",
           "staffName":"",
           "satisfied":true,
           "comments":""
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_kyc_documents
DROP PROCEDURE IF EXISTS obp_get_kyc_documents;
GO
-- create procedure obp_get_kyc_documents
CREATE PROCEDURE obp_get_kyc_documents
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":"gh.29.uk",
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "idKycDocument":"string",
           "customerNumber":"5987953",
           "type":"",
           "number":"",
           "issueDate":"2020-01-27T00:00:00Z",
           "issuePlace":"",
           "expiryDate":"2021-01-27T00:00:00Z"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_kyc_medias
DROP PROCEDURE IF EXISTS obp_get_kyc_medias;
GO
-- create procedure obp_get_kyc_medias
CREATE PROCEDURE obp_get_kyc_medias
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":"gh.29.uk",
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "idKycMedia":"string",
           "customerNumber":"5987953",
           "type":"",
           "url":"http://www.example.com/id-docs/123/image.png",
           "date":"2020-01-27T00:00:00Z",
           "relatesToKycDocumentId":"",
           "relatesToKycCheckId":""
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_get_kyc_statuses
DROP PROCEDURE IF EXISTS obp_get_kyc_statuses;
GO
-- create procedure obp_get_kyc_statuses
CREATE PROCEDURE obp_get_kyc_statuses
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":[
         {
           "bankId":"gh.29.uk",
           "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
           "customerNumber":"5987953",
           "ok":true,
           "date":"2020-01-27T00:00:00Z"
         }
       ]
     }'
	);
GO

 
 


-- drop procedure obp_create_message
DROP PROCEDURE IF EXISTS obp_create_message;
GO
-- create procedure obp_create_message
CREATE PROCEDURE obp_create_message
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "user":{
         "userPrimaryKey":{
           "value":123
         },
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "idGivenByProvider":"string",
         "provider":"ETHEREUM",
         "emailAddress":"",
         "name":"felixsmith",
         "createdByConsentId":"string",
         "createdByUserInvitationId":"string",
         "isDeleted":true,
         "lastMarketingAgreementSignedDate":"2020-01-27T00:00:00Z"
       },
       "bankId":{
         "value":"gh.29.uk"
       },
       "message":"123456",
       "fromDepartment":"Open Bank",
       "fromPerson":"Tom"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "messageId":"string",
         "date":"2020-01-27T00:00:00Z",
         "message":"123456",
         "fromDepartment":"Open Bank",
         "fromPerson":"Tom"
       }
     }'
	);
GO

 
 


-- drop procedure obp_make_historical_payment
DROP PROCEDURE IF EXISTS obp_make_historical_payment;
GO
-- create procedure obp_make_historical_payment
CREATE PROCEDURE obp_make_historical_payment
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "fromAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "toAccount":{
         "accountId":{
           "value":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"
         },
         "accountType":"AC",
         "balance":"10",
         "currency":"EUR",
         "name":"bankAccount name string",
         "label":"My Account",
         "number":"bankAccount number string",
         "bankId":{
           "value":"gh.29.uk"
         },
         "lastUpdate":"2018-03-09T00:00:00Z",
         "branchId":"DERBY6",
         "accountRoutings":[
           {
             "scheme":"IBAN",
             "address":"DE91 1000 0000 0123 4567 89"
           }
         ],
         "accountRules":[
           {
             "scheme":"AccountRule scheme string",
             "value":"AccountRule value string"
           }
         ],
         "accountHolder":"bankAccount accountHolder string",
         "attributes":[
           {
             "name":"STATUS",
             "type":"STRING",
             "value":"closed"
           }
         ]
       },
       "posted":"2020-01-27T00:00:00Z",
       "completed":"2020-01-27T00:00:00Z",
       "amount":"10.12",
       "currency":"EUR",
       "description":"This an optional field. Maximum length is 2000. It can be any characters here.",
       "transactionRequestType":"SEPA",
       "chargePolicy":"SHARED"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "value":"2fg8a7e4-6d02-40e3-a129-0b2bf89de8ub"
       }
     }'
	);
GO

 
 


-- drop procedure obp_create_direct_debit
DROP PROCEDURE IF EXISTS obp_create_direct_debit;
GO
-- create procedure obp_create_direct_debit
CREATE PROCEDURE obp_create_direct_debit
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "bankId":"gh.29.uk",
       "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
       "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
       "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
       "dateSigned":"2020-01-27T00:00:00Z",
       "dateStarts":"2020-01-27T00:00:00Z",
       "dateExpires":"2021-01-27T00:00:00Z"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "directDebitId":"",
         "bankId":"gh.29.uk",
         "accountId":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
         "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
         "counterpartyId":"9fg8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "dateSigned":"2020-01-27T00:00:00Z",
         "dateCancelled":"2020-01-27T00:00:00Z",
         "dateStarts":"2020-01-27T00:00:00Z",
         "dateExpires":"2021-01-27T00:00:00Z",
         "active":true
       }
     }'
	);
GO

 
 


-- drop procedure obp_delete_customer_attribute
DROP PROCEDURE IF EXISTS obp_delete_customer_attribute;
GO
-- create procedure obp_delete_customer_attribute
CREATE PROCEDURE obp_delete_customer_attribute
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "customerAttributeId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":true
     }'
	);
GO

 
 


-- drop procedure obp_dynamic_entity_process
DROP PROCEDURE IF EXISTS obp_dynamic_entity_process;
GO
-- create procedure obp_dynamic_entity_process
CREATE PROCEDURE obp_dynamic_entity_process
   @outbound_json NVARCHAR(MAX),
   @inbound_json NVARCHAR(MAX) OUT
   AS
	  SET nocount on

-- replace the follow example to real logic
/*
this is example of parameter @outbound_json
     N'{
       "outboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "consumerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ],
         "outboundAdapterAuthInfo":{
           "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
           "username":"felixsmith",
           "linkedCustomers":[
             {
               "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
               "customerNumber":"5987953",
               "legalName":"Eveline Tripman"
             }
           ],
           "userAuthContext":[
             {
               "key":"CustomerNumber",
               "value":"5987953"
             }
           ],
           "authViews":[
             {
               "view":{
                 "id":"owner",
                 "name":"Owner",
                 "description":"This view is for the owner for the account."
               },
               "account":{
                 "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
                 "accountRoutings":[
                   {
                     "scheme":"IBAN",
                     "address":"DE91 1000 0000 0123 4567 89"
                   }
                 ],
                 "customerOwners":[
                   {
                     "bankId":"gh.29.uk",
                     "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
                     "customerNumber":"5987953",
                     "legalName":"Eveline Tripman",
                     "dateOfBirth":"2018-03-09T00:00:00Z"
                   }
                 ],
                 "userOwners":[
                   {
                     "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
                     "emailAddress":"felixsmith@example.com",
                     "name":"felixsmith"
                   }
                 ]
               }
             }
           ]
         }
       },
       "operation":"UPDATE",
       "entityName":"FooBar",
       "requestBody":{
         "name":"James Brown",
         "number":1234567890
       },
       "entityId":"foobar-id-value"
     }'
*/

-- return example value
	SELECT @inbound_json = (
		SELECT
     N'{
       "inboundAdapterCallContext":{
         "correlationId":"1flssoftxq0cr1nssr68u0mioj",
         "sessionId":"b4e0352a-9a0f-4bfa-b30b-9003aa467f50",
         "generalContext":[
           {
             "key":"CustomerNumber",
             "value":"5987953"
           }
         ]
       },
       "status":{
         "errorCode":"",
         "backendMessages":[
           {
             "source":"",
             "status":"",
             "errorCode":"",
             "text":"",
             "duration":"5.123"
           }
         ]
       },
       "data":{
         "name":"James Brown",
         "number":1234567890,
         "fooBarId":"foobar-id-value"
       }
     }'
	);
GO

