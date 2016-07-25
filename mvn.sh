#!/bin/sh

export MAVEN_OPTS="-Xmx512m -Xms512m"

mvn $1 $2 $3 $4
