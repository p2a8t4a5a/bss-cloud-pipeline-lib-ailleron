#!/usr/bin/env bash -x

UPSTREAM_CONFIG="$BATS_TEST_DIRNAME/../resources/upstream-config"
DOWNSTREAM_CONFIG="$BATS_TEST_DIRNAME/../resources/downstream-config"

LIBRARY_SCRIPTS="$BATS_TEST_DIRNAME/../../resources/scripts"

merge_configs() {
    export CONFDIR=$(mktemp -d)
    cp -a $UPSTREAM_CONFIG/* $CONFDIR
    cp -a $DOWNSTREAM_CONFIG/* $CONFDIR

}

cleanup_configs() {
    if [ -d ${CONFDIR} ];then
        rm -fr $CONFDIR
    fi
}

