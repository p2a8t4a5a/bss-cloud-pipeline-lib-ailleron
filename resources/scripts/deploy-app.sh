#!/usr/bin/env bash

set -eo pipefail

BASEDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $BASEDIR/common.sh

if [ $# -lt 3 ];then
    cat <<- EOF
    Usage: $0 project-name app-name version

    where

    project-name - name of the project to deploy app to
    app-name     - application name
    version      - application version

EOF

    exit 2
fi

PROJ="$1"
APP_NAME="$2"
APP_VERSION="$3"

SYS_NAME=$(get_sys_for_app $APP_NAME)
SRC_PROJ="${SYS_NAME}-cicd"
APP_IMGTAG="${SRC_PROJ}/${APP_NAME}:${APP_VERSION}"
echo "Name of the generated source ImageStreamTag to be used: ${APP_IMGTAG}"

set -u

# declare and initialize map for secrets and configmap to be discovered and filled with proper names
sys_configmap=''
proj_configmap=''
app_configmap=''
app_global_configmap=''
sys_secret=''
proj_secret=''
app_secret=''
app_global_secret=''

# apply extra params if project.params exists in project config dir
EXTRA_PARAMS=""

# secrets for app if secref.params exists in application config dir
SECREF=""

config_dirs=$(get_config_dirs $PROJ $APP_NAME)
echo "Configuration directories found: "
echo $config_dirs

# check if particular configs and secrets exists on sys, proj and app hierarchy levels and set proper params for template
for d in $config_dirs; do
    echo "Loading configuration from: ${d}"
    # global level project.params and app.params loading
    [ -f $d/project.params ] && EXTRA_PARAMS="--param-file=${d}/project.params $EXTRA_PARAMS"
    [ -f $d/app.params ] && EXTRA_PARAMS="--param-file=${d}/app.params $EXTRA_PARAMS"
    
    # sys level
    # looking for system level confiuration map
    if [ -f $d/system-configmap.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/system-configmap.yaml)"
      if [ "$yaml_metadata_name" != "system-config" ];then
        echo "WARNING: $d/system-configmap.yaml has incorrect name - $yaml_metadata_name instead of system-config"
      else
        sys_configmap='system-config'
        app_global_configmap='system-config'
        proj_configmap='system-config'
        app_configmap='system-config'
      fi
    fi
    # looking for system level secrets
    if [ -f $d/system-secret.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/system-secret.yaml)"
      if [ "$yaml_metadata_name" != "system-secret" ];then
        echo "WARNING: $d/system-secret.yaml has incorrect name - $yaml_metadata_name instead of system-secret"
      else
        sys_secret='system-secret'
        app_global_secret='system-secret'
        proj_secret='system-secret'
        app_secret='system-secret'
      fi
    fi
    
    # proj level
    # overriding system level configuration with project level configuration
    if [ -f $d/proj-configmap.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/proj-configmap.yaml)"
      if [ "$yaml_metadata_name" != "proj-config" ];then
        echo "WARNING: $d/proj-configmap.yaml has incorrect name - $yaml_metadata_name instead of proj-config"
      else
        [ $sys_configmap ] || sys_configmap='proj-config'
        app_global_configmap='proj-config'
        proj_configmap='proj-config'
        app_configmap='proj-config'
      fi
    fi
    # echo "# DEBUG: $proj_configmap [$d]"
    # overriding system level secrets with project secrets
    if [ -f $d/proj-secret.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/proj-secret.yaml)"
      if [ "$yaml_metadata_name" != "proj-secret" ];then
        echo "WARNING: $d/proj-secret.yaml has incorrect name - $yaml_metadata_name instead of proj-secret"
      else
        [ $sys_secret ] || sys_secret='proj-secret'
        app_global_secret='proj-secret'
        proj_secret='proj-secret'
        app_secret='proj-secret'
      fi
    fi

    # global app settings level
    if [ -f $d/app-global-configmap.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/app-global-configmap.yaml)"
      if [ "$yaml_metadata_name" != "${APP_NAME}-global-config" ];then
        echo "WARNING: $d/app-global-configmap.yaml has incorrect name - $yaml_metadata_name instead of ${APP_NAME}-global-config"
      else 
        [ $sys_configmap ] || sys_configmap="${APP_NAME}-global-config"
        [ $proj_configmap ] || proj_configmap="${APP_NAME}-global-config"
        app_global_configmap="${APP_NAME}-global-config"
        app_configmap="${APP_NAME}-global-config"
      fi
    fi
    if [ -f $d/app-global-secret.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/app-global-secret.yaml)"
      # echo "YAML METADATA NAME: $yaml_metadata_name" >&2
      if [ "$yaml_metadata_name" != "${APP_NAME}-global-secret" ];then
        echo "WARNING: $d/app-global-secret.yaml has incorrect name - $yaml_metadata_name instead of ${APP_NAME}-global-secrets"
      else
        [ $sys_secret ] || sys_secret=${APP_NAME}"-global-secret"
        [ $proj_secret ] || proj_secret="${APP_NAME}-global-secret"
        app_global_secret="${APP_NAME}-global-secret"
        app_secret="${APP_NAME}-global-secret"
      fi
    fi


    # app level
    if [ -f $d/app-configmap.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/app-configmap.yaml)"
      if [ "$yaml_metadata_name" != "${APP_NAME}-config" ];then
        echo "WARNING: $d/app-configmap.yaml has incorrect name - $yaml_metadata_name instead of ${APP_NAME}-config"
      else
        [ $sys_configmap ] || sys_configmap="${APP_NAME}-config"
        [ $app_global_configmap ] || app_global_configmap="${APP_NAME}-config"
        [ $proj_configmap ] || proj_configmap="${APP_NAME}-config"
        app_configmap="${APP_NAME}-config"
      fi
    fi
    if [ -f $d/app-secret.yaml ];then
      yaml_metadata_name="$(get_yaml_metadata_name $d/app-secret.yaml)"
      if [ "$yaml_metadata_name" != "${APP_NAME}-secret" ];then
        echo "WARNING: $d/app-secret.yaml has incorrect name - $yaml_metadata_name instead of ${APP_NAME}-secret"
      else
        [ $sys_secret ] || sys_secret="${APP_NAME}-secret"
        [ $app_global_secret ] || app_global_secret="${APP_NAME}-secret"
        [ $proj_secret ] || proj_secret="${APP_NAME}-secret"
        app_secret="${APP_NAME}-secret"
      fi
    fi
    if [ -f $d/app-extensions.params ];then
      echo "Applying app-extensions.params in directory: ${d}"
      source $d/app-extensions.params
    fi
    
done

echo "
#
# Config hierarchy: 
#
#  ConfigMap: $sys_configmap $app_global_configmap $proj_configmap $app_configmap
#  Secret: $sys_secret $app_global_secret $proj_secret $app_secret
#
" >&2

EXTRA_PARAMS="$EXTRA_PARAMS --param=SYS_CONFIGMAP_NAME=$sys_configmap --param=SYS_SECRET_NAME=$sys_secret"
EXTRA_PARAMS="$EXTRA_PARAMS --param=APP_GLOBAL_CONFIGMAP_NAME=$app_global_configmap --param=APP_GLOBAL_SECRET_NAME=$app_global_secret"
EXTRA_PARAMS="$EXTRA_PARAMS --param=PROJ_CONFIGMAP_NAME=$proj_configmap --param=PROJ_SECRET_NAME=$proj_secret"
EXTRA_PARAMS="$EXTRA_PARAMS --param=APP_CONFIGMAP_NAME=$app_configmap --param=APP_SECRET_NAME=$app_secret"

echo "> Configuring project $PROJ for app $APP_NAME"
$BASEDIR/configure-project.sh $PROJ $APP_NAME

# TODO: remove after merge
APP_TEMPLATE_FILE="app-template.yaml"

if [ "${APP_TYPE:-x}x" != "xx" ] && [ -f $CONFDIR/../templates/${APP_TYPE}-template.yaml ];then
  echo "Using application type template - ${APP_TYPE}-template.yaml"
  APP_TEMPLATE_FILE="${APP_TYPE}-template.yaml"
fi

if [ -f $CONFDIR/../templates/${APP_NAME}-template.yaml ];then
  echo "Using application specific template - ${APP_NAME}-template.yaml"
  APP_TEMPLATE_FILE="${APP_NAME}-template.yaml"
fi

if [ "${APP_TEMPLATE:-x}x" != "xx" ];then
  echo "Using named template ($APP_TEMPLATE) instead of file"
  TEMPLATE_ARGS="$APP_TEMPLATE"
else
  echo "Using template file: $CONFDIR/../templates/${APP_TEMPLATE_FILE}"
  TEMPLATE_ARGS="-f $CONFDIR/../templates/${APP_TEMPLATE_FILE}"
fi

echo "> Deploying app using template"
set -x
oc process --ignore-unknown-parameters $TEMPLATE_ARGS \
                    --param=PROJECT=$PROJ \
                    --param=APP_NAME=$APP_NAME \
					$EXTRA_PARAMS \
		| oc apply -f- -n $PROJ

# check if var SECREF exists in file secref.params and set env
if [ -n "$SECREF" ];then
 echo "SECREF=$SECREF"
 for SECRET in $SECREF
       do
         PREFIX=$(echo $SECRET | sed 's/-/_/g' | tr [a-z] [A-Z])
         oc set env --from=secret/$SECRET --prefix=${PREFIX}_ dc/$APP_NAME -n $PROJ
 done
fi

if [ "${BUILD_NUMBER:-x}x" = "xx" ];then
  # not a jenkins job
  echo "> Deploying $APP_NAME ($APP_IMGTAG) to $PROJ using imagestreamtag $APP_IMGTAG"
  oc tag ${APP_IMGTAG} ${PROJ}/${APP_NAME}:${APP_VERSION}
fi

# increase replicas from 0 to 1  in deployment config
CURRENT_REPLICAS=$(oc get dc $APP_NAME -n $PROJ | grep $APP_NAME | awk '{print $4}')
echo "CURRENT REPLICAS=$CURRENT_REPLICAS"
[ $CURRENT_REPLICAS -ne 0 ] && echo "DC Replicas conf - OK" || oc scale --replicas=1 dc $APP_NAME -n $PROJ
