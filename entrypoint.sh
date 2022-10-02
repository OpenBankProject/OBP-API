#!/bin/bash

# Dynamically set uid/gid (Openshift needs this)
# See: 
# - https://github.com/KarmaComputing/OBP-API/issues/9
# - https://blog.openshift.com/jupyter-on-openshift-part-6-running-as-an-assigned-user-id/

# We only do this if we're running in an openshift-like environment
# openshift-like simply means if the uid is > 10000 at container
# runtime (this is the (arbitary?) number chosen by openshift)
# "Simplicty is a prerequisite to reliability" is sadly not present here
if [[ $UID -ge 10000 ]]; then
    GID=$(id -g)
    sed -e "s/^jetty:x:[^:]*:[^:]*:/jetty:x:$UID:$GID:/" /etc/passwd > /tmp/passwd
    cat /tmp/passws > /etc/passwd
    rm /tmp/passwd
fi

# Start Jetty
java -jar start.jar
