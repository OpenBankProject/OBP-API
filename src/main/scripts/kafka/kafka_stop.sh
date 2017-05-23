#!/bin/bash

DIR=$(dirname $(readlink -f $0))
cd $DIR

TMPDIR=${DIR}/tmp

KAFKA_PID_FILE="${TMPDIR}/kafka.pid"
ZOOKEEPER_PID_FILE="${TMPDIR}/zookeeper.pid"


if [ -e "$KAFKA_PID_FILE" ]; then
        PID=`/bin/cat ${KAFKA_PID_FILE}`
        echo "Killing kafka process ${PID}..."
        /bin/kill -9 ${PID} 
	/bin/rm ${KAFKA_PID_FILE}
else
        echo "No PID file found at ${KAFKA_PID_FILE}"
fi


if [ -e "$ZOOKEEPER_PID_FILE" ]; then
        PID=`/bin/cat ${ZOOKEEPER_PID_FILE}`
        echo "Killing zookeeper process ${PID}..."
        /bin/kill -9 ${PID} 
	/bin/rm ${ZOOKEEPER_PID_FILE}
else
        echo "No PID file found at ${ZOOKEEPER_PID_FILE}"
fi

        PIDS=`/bin/ps auxw | grep kafka | grep -v grep | grep -v kafka_st | grep -v kafka_mo | grep -v kafka_cl | cut -b 10-16`
        if [ ${#PIDS[@]} -le 1 ]
	then
        	echo "No zombie processes found"
	fi
        for PID in ${PIDS}
	do
        	echo "Killing zombie process ${PID}..."
		/bin/kill -9 ${PID}
	done

cd - 
