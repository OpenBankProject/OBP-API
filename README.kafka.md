## Kafka Quickstart
    
  Note that obp with kafka connector will also need a bank backend connected to the kafka that implements the following: https://apiexplorersandbox.openbankproject.com/glossary#Adapter.Kafka.Intro
  
  Otherwise obp will display anything but adapter errors.
  
#####Configuration

* Edit the OBP-API/obp-api/src/main/resources/props/default.props so that it contains the following lines:

        connector=kafka_vMay2019
        # kafka server location, plaintext for quickstart
        kafka.host=localhost:9092
        # next 2 lines for legacy resons
        kafka.request_topic=Request
        kafka.response_topic=Response
        # number of partitions available on kafka. Must match the kafka configuration!!!!!!.
        kafka.partitions=1
        # no ssl for quickstart
        kafka.use.ssl=false
        # start with 1 for the first instance, set to 2 for the second instance etc
        api_instance_id=1
        

        

