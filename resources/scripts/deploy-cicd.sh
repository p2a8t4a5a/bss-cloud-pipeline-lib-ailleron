#!/usr/bin/env bash

set -eo pipefail

BASEDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $BASEDIR/common.sh

if [ $# -lt 1 ];then
    cat <<- EOF
    Usage: $0 app-name

    where:

    app-name - application name
EOF
    exit 2
fi

APP_NAME="$1"

set -u

SYS_NAME=$(get_sys_for_app $APP_NAME)
[ "${CICD_PROJ:-x}x" != "xx" ] || CICD_PROJ="${SYS_NAME}-cicd"

EXTRA_PARAMS="--param=PROJECT=$CICD_PROJ"
for d in $(get_config_dirs $CICD_PROJ $APP_NAME);do
    [ -f $d/cicd.params ] || continue
    echo "Adding cicd config from $d/cicd.params"
    . $d/cicd.params
    EXTRA_PARAMS="--param-file=${d}/cicd.params $EXTRA_PARAMS"
done

[ "${CICD_TEMPLATE:-x}" != "x" ] || CICD_TEMPLATE="cicd-template.yaml"
TEMPLATE_PATH=$BASEDIR/../templates/${CICD_TEMPLATE}

if [[ ${CICD_TEMPLATE##*.} != "yaml" && ${CICD_TEMPLATE##*.} != "yml" ]]; then
  echo "Unsupported template file format"
  exit 2
fi

if [ ! -f $TEMPLATE_PATH ]; then
  echo "File $TEMPLATE_PATH doesn't not exists"
  exit 2
fi

echo "> Applying cicd template ($CICD_TEMPLATE) for app $APP_NAME in $CICD_PROJ project"
echo "Applying template will look like: "
oc process --ignore-unknown-parameters -o describe -f $TEMPLATE_PATH \
        $EXTRA_PARAMS
        
oc process --ignore-unknown-parameters -f $TEMPLATE_PATH \
        $EXTRA_PARAMS \
    	| oc apply -f- -n $CICD_PROJ
