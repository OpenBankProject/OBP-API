Authentication
==============



.. sidebar:: Consumer Keys
    :subtitle: A **Consumer Key** allows you to register multiple application under one account.

    * `Create an account <https://api.openbankproject.com/user_mgt/sign_up>`_
    * Register a `new application <https://api.openbankproject.com/consumer-registration>`_ to generate your consumer key


``Open Bank Project`` offers multiple authentication methods. The most easy is 
called 'Direct Login'.

- OAuth 1.0a
- JSON Web Tokens (JWT) 

JSON Web Tokens (JWT)
---------------------

First you must 
`create an account <https://api.openbankproject.com/user_mgt/sign_up>`_ on Open
Bank Project. Then, `register a new apllication <https://api.openbankproject.com/consumer-registration>`_
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

