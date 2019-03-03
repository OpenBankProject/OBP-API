Welcome to Open Bank Project Guide!
===================================================


Guide:

.. toctree::
   :maxdepth: 2

..  http:example:: curl wget httpie python-requests

    POST /my/logins/direct HTTP/1.1
    Host: localhost:8080
    Accept: application/json
    Authorization: DirectLogin username="username", password="password", consumer_key="consumer-key"


    HTTP/1.1 200 OK
    Content-Type: application/json

    {
        "token": "abc123"
    }




Indices and tables
==================

* :ref:`genindex`
* :ref:`search`

