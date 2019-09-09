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

check_sequence() {
    set -x
    local d=$1
    echo "# dir: $d"
    if [ -d $d ];then
        cur=$(ls $d/order*.txt)
        cur=${cur##*order-}
        cur=${cur%%.txt}
        echo "# sequence: cur=$cur prev=$prev"
        [ $cur -gt $prev ] || { echo "Invalid sequence: $cur <= $prev" >&2; return 1; }
        prev=$cur
    fi
    set -x
}

@test "Check if the sequence is properly read" {
    echo "# using config dir $CONFDIR"

    local prev=0
    local cur
    for d in $(get_config_dirs foobar-dummy sample);do
        # check_sequence $d
        echo "# dir: $d"
        [ -d $d ] || { echo "# $d doesn't exist - skipping" >&2; continue; }
        cur=$(ls $d/order*.txt)
        cur=${cur##*order-}
        cur=${cur%%.txt}
        echo "# sequence: cur=$cur prev=$prev"
        [ $cur -gt $prev ] || { echo "Invalid sequence: $cur <= $prev" >&2; return 1; }
        prev=$cur
    done

    return 0
}

@test "Check if the sequence is propely read - with labeled projects" {
    echo "# using config dir $CONFDIR"

    local prev=0
    local cur
    for d in $(get_config_dirs foobar-dummy sample type:preview,cluster:test);do
        # check_sequence $d
        echo "# dir: $d"
        [ -d $d ] || { echo "# $d doesn't exist - skipping" >&2; continue; }
        cur=$(ls $d/order*.txt)
        cur=${cur##*order-}
        cur=${cur%%.txt}
        echo "# sequence: cur=$cur prev=$prev"
        [ $cur -gt $prev ] || { echo "Invalid sequence: $cur <= $prev" >&2; return 1; }
        prev=$cur

    done

    return 0
}
