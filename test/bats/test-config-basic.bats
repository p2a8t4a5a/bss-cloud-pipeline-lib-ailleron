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

@test "Check if we see all 3 levels for project only" {
    echo "# using config dir $CONFDIR"

    local n=0
    for d in $(get_config_dirs foobar-prod);do
        echo "# dir: $d"
        n=$((n+1))
    done

    [[ $n == 3 ]]
}

@test "Check if we see all 5 levels for project and app defined (without namespace levels)" {
    echo "# using config dir $CONFDIR"

    local n=0
    for d in $(get_config_dirs foobar-prod sample);do
        echo "# dir: $d"
        n=$((n+1))
    done

    [[ $n == 5 ]]
}

@test "Check if we see 3 levels for system, project and app defined (without namespace levels and global app config) - ITPBC-647" {
    echo "# using config dir $CONFDIR"

    local prev=0
    local cur

    local n=0
    for d in $(get_config_dirs foobar-itpbc647 app-itpbc647);do
        echo "# dir: $d"
        cur=$(ls $d/order*.txt 2> /dev/null || true)
        [ "${cur:-x}x" = "xx" ] && { echo "# no 'order-*.txt' file found - skipping"; continue; }
        cur=${cur##*order-}
        cur=${cur%%.txt}
        echo "# sequence: cur=$cur prev=$prev"
        [ $cur -gt $prev ] || { echo "Invalid sequence: $cur <= $prev" >&2; return 1; }
        n=$((n+1))
        prev=$cur
    done

    [[ $n == 4 ]]
}

@test "Check if the sequence is propely read" {
    echo "# using config dir $CONFDIR"

    local prev=0
    local cur
    for d in $(get_config_dirs foobar-dummy sample);do
        echo "# dir: $d"
        cur=$(ls $d/order*.txt 2>/dev/null)
        cur=${cur##*order-}
        cur=${cur%%.txt}
        echo "# sequence: cur=$cur prev=$prev"
        [ $cur -gt $prev ] || { echo "Invalid sequence: $cur <= $prev" >&2; return 1; }
        prev=$cur
    done

    return 0
}
