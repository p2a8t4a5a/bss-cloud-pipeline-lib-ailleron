#!/usr/bin/env bats

load helpers

setup() {
    teardown
    merge_configs
    . $LIBRARY_SCRIPTS/common.sh   
    #export PATH=$BATS_TEST_DIRNAME/bin:$PATH
     . shellmock
    shellmock_clean
}

teardown() {
    cleanup_configs
    if [ -z "$TEST_FUNCTION" ]; then
        . shellmock
        shellmock_clean
    fi
}

@test "Mock configuration of sample project with app" {
    shellmock_expect oc --type partial --match ""
    run $LIBRARY_SCRIPTS/configure-project.sh foobar-test sample
    echo "$output" >&3
    [ "$status" -eq 0 ]
}

@test "Mock deployment of sample app" {
    # required for parsing replicas output
    shellmock_expect oc --type partial --match ""  --output "# sample 0 0 0 0"

    run $LIBRARY_SCRIPTS/deploy-app.sh foobar-prod sample latest

    shellmock_verify
    params_order_result=0
    for c in "${capture[@]}";do
        # look for "oc process" stub
        if echo "$c"| grep -q '^oc-stub process';then
            xparams=$(echo "$c"|xargs -n1| grep '\(CONFIGMAP\|SECRET\)_NAME=')
            echo "$xparams" >&3
            for p in "SYS_CONFIGMAP_NAME=system-config" \
                "SYS_SECRET_NAME=system-secret" \
                "PROJ_CONFIGMAP_NAME=proj-config" \
                "PROJ_SECRET_NAME=proj-secret" \
                "APP_GLOBAL_CONFIGMAP_NAME=sample-global-config" \
                "APP_GLOBAL_SECRET_NAME=sample-global-secret" \
                "APP_CONFIGMAP_NAME=sample-config" \
                "APP_SECRET_NAME=sample-secret"
                do
                if ! echo "$xparams"|grep -q "$p";then
                    echo "ERROR: Param $p not found" >&3
                    params_order_result=1
                fi
            done
        fi
    done
    # echo "shellmock capture: ${capture[@]}" >&3

    echo "$output" >&3
    echo "status: $status, params_order_result=$params_order_result" >&3
    [ "$status" -eq 0 ]
    [ "$params_order_result" -eq 0 ]

}

@test "Mock deployment of sample2 app without global app configs" {
    # required for parsing replicas output
    shellmock_expect oc --type partial --match ""  --output "# sample2 0 0 0 0"

    run $LIBRARY_SCRIPTS/deploy-app.sh foobar-prod sample2 latest

    shellmock_verify
    params_order_result=0
    for c in "${capture[@]}";do
        # look for "oc process" stub
        if echo "$c"| grep -q '^oc-stub process';then
            xparams=$(echo "$c"|xargs -n1| grep '\(CONFIGMAP\|SECRET\)_NAME=')
            echo "$xparams" >&3
            for p in "SYS_CONFIGMAP_NAME=system-config" \
                "SYS_SECRET_NAME=system-secret" \
                "PROJ_CONFIGMAP_NAME=proj-config" \
                "PROJ_SECRET_NAME=proj-secret" \
                "APP_GLOBAL_CONFIGMAP_NAME=proj-config" \
                "APP_GLOBAL_SECRET_NAME=proj-secret" \
                "APP_CONFIGMAP_NAME=proj-config" \
                "APP_SECRET_NAME=proj-secret"
                do
                if ! echo "$xparams"|grep -q "$p";then
                    echo "ERROR: Param $p not found" >&3
                    params_order_result=1
                fi
            done
        fi
    done
    # echo "shellmock capture: ${capture[@]}" >&3

    echo "$output" >&3
    echo "status: $status, params_order_result=$params_order_result" >&3
    [ "$status" -eq 0 ]
    [ "$params_order_result" -eq 0 ]

}


@test "Mock deployment of jenkins with custom project name" {
    skip "Not ready yet..."
    shellmock_expect oc --type partial --match ""
    # shellmock_expect oc --type partial --match "get namespace foobar-custom-cicd -ogo-template" --output "type:preview"
    run $LIBRARY_SCRIPTS/deploy-jenkins.sh foobar foobar-custom

    shellmock_verify
    echo "${capture[@]}" >&3

    echo "$output" >&3
    [ "$status" -eq 0 ]
}

@test "Mock deployment of jenkins" {
    shellmock_expect oc --type partial --match ""
    run $LIBRARY_SCRIPTS/deploy-jenkins.sh foobar
    echo "$output" >&3
    [ "$status" -eq 0 ]
}

@test "Mock deployment of cicd configs" {
    shellmock_expect oc --type partial --match ""
    run $LIBRARY_SCRIPTS/deploy-cicd.sh sample
    echo "$output" >&3
    [ "$status" -eq 0 ]
}
