#!/usr/bin/env bash

set -eo pipefail

BASEDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $BASEDIR/common.sh

[ $# -lt 3 ] && { echo "Usage: $0 project-name app-name app-version" >&2; exit 2; }
PROJ="$1"
APP_NAME="$2"
APP_VERSION="$3"
SYS_NAME=$(get_sys_for_app $APP_NAME)

set -u

if oc project -q $PROJ &> /dev/null;then
    echo "Project $PROJ already exists. Use 'deploy-app.sh' to deploy an app to it." >&2
    exit 1
fi

echo "> Creating new preview env $PROJ for app $APP_NAME"
oc new-project $PROJ

echo "> Deploying $APP_NAME with version $APP_VERSION to $PROJ"
$BASEDIR/deploy-app.sh $PROJ $APP_NAME $APP_VERSION
