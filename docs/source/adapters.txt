Adapters
==============

Connecting Open Bank Project to a core bank system required an adapter to be 
written. 

Typically, this will involve the development of a Kafka consumer, which reads
api requests sent to Open Bank Project, and then the adapter responds by 
putting a reply message into a corresponding topic. 

