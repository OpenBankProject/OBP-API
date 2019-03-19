Authentication
==============



.. sidebar:: Consumer Keys
    :subtitle: A **Consumer Key** allows you to register multiple applications under one account.

    * `Create an account <https://api.openbankproject.com/user_mgt/sign_up>`_
    * Register a `new application <https://api.openbankproject.com/consumer-registration>`_ to generate your consumer key


``Open Bank Project`` offers multiple authentication methods:

- OAuth 1.0a
- JSON Web Tokens (JWT) 

JSON Web Tokens (JWT)
---------------------

First you must 
`create an account <https://api.openbankproject.com/user_mgt/sign_up>`_ on Open
Bank Project. Then, `register a new application <https://api.openbankproject.com/consumer-registration>`_
which gives you a `consumer-key`. You use your consumer key when generating a 
JWT login token. 


.. http:example:: curl wget httpie python-requests

   POST /my/logins/direct HTTP/1.1
   Host: api.openbankproject.com
   Accept: application/json
   Authorization: DirectLogin username="username", password="password", consumer_key="yourConsumerKey"


   HTTP/1.1 200 OK
   Content-Type: application/json

   {
     "token": "abc123"
   }

Verify Authentication
---------------------

You then use the token recieved from your DirectLogin request. 

For example, make an authenticated request using your token.

Get your current user infomation:


.. http:example:: curl wget httpie python-requests

   POST /obp/v3.1.0/users/current HTTP/1.1
   Host: YOUR-HOST
   Accept: application/json
   Authorization: DirectLogin token="abc123"


   HTTP/1.1 200 OK
   Content-Type: application/json

   {
     "user_id":"2ef35575-aae9-48fb-ad01-751755b3964f",
     "email":"Fred@example.com",
     "provider_id":"your-provider-id",
     "provider":"your-provider-name",
     "username":"fred",
     "entitlements":{"list":[]}
   }
