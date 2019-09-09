#!/usr/bin/env bats


load helpers

teardown() {
    cleanup_configs
}

setup() {
    teardown
    merge_configs
    . $LIBRARY_SCRIPTS/common.sh   
}

@test "Check if we see all levels for project with single label" {
    echo "# using config dir $CONFDIR" 

    local n=0
    for d in $(get_config_dirs foobar-test sample cluster:test);do
        echo "# dir: $d" 
        n=$((n+1))
    done

    [[ $n == 7 ]]
}

@test "Check if we see all levels for project with single label without app defined" {
    echo "# using config dir $CONFDIR" 

    local n=0
    for d in $(get_config_dirs foobar-dummy cluster:test);do
        echo "# dir: $d" 
        n=$((n+1))
    done

    [[ $n == 4 ]]
}

@test "Check if we see all levels for project with 2 labels" {
    echo "# using config dir $CONFDIR" 

    local n=0
    for d in $(get_config_dirs foobar-test sample cluster:test,type:preview);do
        echo "# dir: $d"
        n=$((n+1))
    done

    [[ $n == 9 ]]
}

@test "Check if we see all levels for project with 3 labels in proper order" {
    echo "# using config dir $CONFDIR" 

    local n=0
    for d in $(get_config_dirs foobar-test sample cluster:test,type:preview);do
        echo "# dir: $d"
        n=$((n+1))
    done

    [[ $n == 9 ]]
}

@test "Read data for project without existing config dir with labels only" {
    echo "# using config dir $CONFDIR" 

    local n=0
    for d in $(get_config_dirs foobar-pr123 sample type:preview);do
        echo "# dir: $d" 
        if [ -f $d/cicd.params ];then
            echo "# reading from $d/cicd.params"
            . $d/cicd.params
        fi
    done

    [[ $FOO == "preview" ]]
}

