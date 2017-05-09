#!/bin/bash

DIR=$(dirname $(readlink -f $0))
cd $DIR

LOGDIR=${DIR}/logs
TMPDIR=${DIR}/tmp

mkdir -p ${LOGDIR}
mkdir -p ${TMPDIR}

VERSION=$(find . -name "kafka_2*[0-9.][0-9]" | cut -d_ -f2)

KAFKA_PID_FILE="${TMPDIR}/kafka.pid"
ZOOKEEPER_PID_FILE="${TMPDIR}/zookeeper.pid"

echo "Starting zookeeper..."
nohup kafka_$VERSION/bin/zookeeper-server-start.sh config/zookeeper.properties >> ${LOGDIR}/zookeeper.nohup.out & 
echo $! > ${ZOOKEEPER_PID_FILE}

sleep 5 
echo "OK"

echo "Starting kafka..."
nohup kafka_$VERSION/bin/kafka-server-start.sh config/kafka.properties >> ${LOGDIR}/kafka.nohup.out &
echo $! > ${KAFKA_PID_FILE}

sleep 5 
echo "OK"

cd -
