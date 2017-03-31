#!/bin/sh

# Deprecated option -XX:MaxPermSize=256m is kept
# just in case someone still uses java 1.7
export MAVEN_OPTS="-Xmx1024m -Xms1024m -Xss2048k -XX:MaxPermSize=1024m"

mvn $1 $2 $3 $4
