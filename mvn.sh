#!/bin/sh

export MAVEN_OPTS="-Xmx512m -Xms512m -XX:MaxPermSize=256m"

mvn $1 $2 $3 $4
