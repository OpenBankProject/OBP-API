#!/bin/sh

export SBT_OPTS="-Xmx1024m -Xms1024m -Xss2048k -XX:MaxPermSize=1024m"
echo "sbt $@"
sbt $@

#$1 $2 $3 $4
