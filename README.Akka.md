# Intro

Akka is a toolkit and runtime for building highly concurrent, distributed, and resilient message-driven applications on the JVM.
See http://akka.io


To use Akka, you want to have two different machines:
- One considered `remote` which stores the data
- One considered `local` which is the public facing side of the API, where your users will connect

Both run current versions of the Open Bank Project API, but configured and run differently.


## Remote models

Not all models have been ported yet to be retrieved via Akka. See `modelsRemotedata` in `src/main/scala/bootstrap/liftweb/Boot.scala` to get a current list.



# Remote side

- Configure `src/main/resources/default.props`: 

```ini
# Remote end gets data 'locally'
remotedata.enable=false
# Your remote's external IP address
remotedata.hostname=10.0.0.19  
# Arbitrary port of your choosing
remotedata.port=5448
# Arbitrary value used in order to assure us that remote and local sides are paired well
remotedata.secret=CHANGE_ME

# Optionally configure postgres, otherwise file-based H2 will be used 
remotedata.db.driver=org.postgresql.Driver
remotedata.db.url=jdbc:postgresql://localhost:5432/dbname?user=user&password=password
```

## Run

```bash
#!/bin/sh

cd ${HOME}/OBP-API/ && /usr/bin/nohup /usr/bin/mvn compile exec:java -Dexec.mainClass="code.remotedata.RemotedataActors" -Dexec.args="standalone" > ${HOME}/akka_remote_api.log &
```



# Local OBP API side

- Configure `src/main/resources/default.props`:

```ini
# Define is Akka transport layer used by OBP-API
# In case that property is not defined default value is set to false
use_akka=false
# Local end gets data remotely
remotedata.enable=true
# Your remote's public IP address
remotedata.hostname=10.0.0.19
# Arbitrary port of your choosing, has to match remote above
remotedata.port=5448
```

# Run

```bash
#!/bin/sh

cd ${HOME}/OBP-API/ && /usr/bin/nohup /usr/bin/mvn jetty:run -Djetty.port=8080 -DskipTests  > ${HOME}/akka_local_api.log &
```
