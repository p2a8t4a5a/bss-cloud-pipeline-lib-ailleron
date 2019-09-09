#!/usr/bin/env bash


BASEDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $BASEDIR/common.sh

APP=""
[ -z "$1" ] || PROJ="$1"
[ -z "$2" ] || APP="$2"
[ -z "$PROJ" ] && { echo "Usage: $0 project-name [app-name]" >&2; exit 2; }

PREV_PROJ="$(oc project -q || echo default)"

set -euo pipefail

echo "> Configuring $PROJ"

OC="oc -n $PROJ"

echo "> Fetching labels for project $PROJ"
PROJ_LABELS=$(get_labels_for_proj $PROJ)
if [ $? -ne 0 ];then
  echo "WARNING: Could not retrieve project labels. Settings based on labels will NOT be applied."
elif [ "${PROJ_LABELS:-x}x" = "xx" ];then
  echo "No labels found."
else
  echo "Labels found: $PROJ_LABELS"
fi

echo -e ">> Processing yamls"
for d in $(get_config_dirs $PROJ $APP $PROJ_LABELS);do
    if $(dir_has_yamls $d);then
        echo ">>> $d"
        for yaml in $d/*.yaml;do
          if grep -q '^apiVersion:' $yaml;then
            echo ">>>> $yaml"
            $OC apply -f $yaml
          else
            echo "$yaml is not a valid OpenShift/Kubernetes resource"
          fi
        done
    fi
    if [ -f $d/project-config.sh ];then
        if ! [ -x $d/project-config.sh ];then
            echo ">>> Script $d/project-config.sh exists, but is not executable. Skipping." >&2
            continue
        fi
        echo ">>> Executing additional config script: $d/project-config.sh"
        oc project $PROJ
        $d/project-config.sh
        oc project "$PREV_PROJ"
    fi
done
