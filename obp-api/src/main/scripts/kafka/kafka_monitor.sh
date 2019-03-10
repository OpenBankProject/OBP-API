#!/bin/bash

trap ctrl_c INT
function ctrl_c() {
  echo
  kill -9 ${pids}
  exit 
}

DIR=$(dirname $(readlink -f $0))
cd $DIR

VERSION=$(find . -name "kafka_2*[0-9.][0-9]" | cut -d_ -f2)

topic_list=$(kafka_$VERSION/bin/kafka-topics.sh --list --zookeeper=localhost:2181)
echo "Monitoring topics:"

pids=

for topic in ${topic_list}
do
  if [ "${topic}" != "__consumer_offsets" ]
  then
    echo ${topic}
    kafka_$VERSION/bin/kafka-console-consumer.sh --bootstrap-server=localhost:9092 --topic=${topic} &
    pids=${pids}\ $!
  fi
done
echo "------------------"

read keypress

echo ${pids}


kill -9 ${pids}
exit 

