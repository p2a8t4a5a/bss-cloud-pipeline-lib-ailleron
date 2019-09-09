#!/usr/bin/env bash


BASEDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $BASEDIR/common.sh

APP=""
[ -z "$1" ] || PROJ="$1"
[ -z "$2" ] || APP="$2"
[ -z "$PROJ" ] && { echo "Usage: $0 project-name [app-name]" >&2; exit 2; }

set -euo pipefail

i=1
echo "Config hierarchy for project $PROJ, APP=${APP:-(unset)}"
for d in $(get_config_dirs $PROJ $APP);do
    echo -n "[$i] $d"
    [ -d $d ] || echo -n " [DOESN'T EXIST]"
    echo
    i=$((i+1))
done
