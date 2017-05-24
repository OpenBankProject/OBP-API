#!/bin/sh

export SBT_OPTS="-Xmx1024m -Xms1024m -Xss2048k -XX:MaxPermSize=1024m"
echo "Running standalone remotedata server"
sbt "run standalone"

