#!/bin/bash

PARTITIONS=10

DIR=$(dirname $(readlink -f $0))
cd $DIR

VERSION=$(find . -name "kafka_2*[0-9.][0-9]" | cut -d_ -f2)

./kafka_stop.sh 2>/dev/null

sleep 1

echo "Cleaning kafka logs..."
rm -rf ${DIR}/tmp/[kz][ao]*

./kafka_start.sh

sleep 5 

if [[ "$(kafka_$VERSION/bin/kafka-topics.sh --zookeeper=localhost:2181 --list | grep  Response)" != "Response" ]]; then
  kafka_$VERSION/bin/kafka-topics.sh --zookeeper=localhost:2181 --create --partitions ${PARTITIONS} --replication-factor 1 --topic Response
fi

if [[ "$(kafka_$VERSION/bin/kafka-topics.sh --zookeeper=localhost:2181 --list | grep  Request)" != "Request" ]]; then
  kafka_$VERSION/bin/kafka-topics.sh --zookeeper=localhost:2181 --create --partitions ${PARTITIONS} --replication-factor 1 --topic Request
fi

