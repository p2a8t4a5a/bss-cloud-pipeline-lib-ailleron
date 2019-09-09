#!/usr/bin/env bash

set -eo pipefail

LIBBASEDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CONFDIR=${CONFDIR:-$LIBBASEDIR/../config}

# TODO: move it outside the file - e.g. ConfigMap
LABELS_ORDER="sys type name"

[ "${DEBUG:-x}x" = "xx" ] || { echo "Debug mode on"; set -x; }


# get available dirs in config hierarchy depending on available variables
# possible params:
#
# proj_name app_name proj_labels
# proj_name proj_labels
# proj_name app_name
# proj_name
#
# where
#   proj_name   - project namne
#   app_name    - application name
#   proj_labels - comma separated list of project labels e.g. type:preview,system:foobar
get_config_dirs() {
  local proj=$1
  local app
  local proj_labels

  if [ $# -ge 2 ];then
    # second param is projects labels list if it contains ":", otherwise it's app name
    if echo $2|grep -q '.*:.*';then
      proj_labels=$2
    else
      app=$2
    fi
  fi

  [ $# -eq 3 ] && proj_labels=$3
  local sys_name
  local config_dirs=""
  if [ "${app:-x}x" != "xx" ];then
    sys_name=$(get_sys_for_app $app)
  elif [ "${SYS_NAME:-x}x" != "xx" ];then
    echo "Using $SYS_NAME for system name from \$SYS_NAME variable" >&2
    sys_name="$SYS_NAME"
  else
    if get_sys_from_proj_label $proj &> /dev/null;then
      sys_name=$(get_sys_from_proj_label $proj)
      echo "Found system name in project label: $sys_name" >&2
    else
      sys_name=$(get_sys_for_proj $proj)
    fi
  fi
  [ $? -eq 0 ] || { echo "Failed to determine system name for project $proj" >&2; return 1; }
  
  local label_dirs=""
  if [ "${proj_labels:-x}x" != "xx" ];then
    for l in ${proj_labels//,/ };do
      label_dirs=" $label_dirs ${CONFDIR}/by-ns-label/${l//://}"
    done
  fi

  for d in  ${CONFDIR}/ \
            ${CONFDIR}/system/${sys_name} \
            "${label_dirs}" \
            ${CONFDIR}/system/${sys_name}/${proj}
            do
    config_dirs="$config_dirs $d"
  done

  if [ "${app:-x}" != "x" ];then
    for d in  ${CONFDIR}/applications/${app} \
              ${CONFDIR}/system/${sys_name}/${proj}/${app}
              do
      config_dirs="$config_dirs $d"
    done
    # search through labels directories as well
    for d in  ${label_dirs};do
      config_dirs="$config_dirs $d/${app}"
    done
  fi
  echo "$config_dirs"
}

# get available dirs in hierarchy for an app
get_config_app_dirs() {
  local app=$1
  local sys_name
  local config_dirs=""
  sys_name=$(get_sys_for_app $app)
  [ $? -eq 0 ] || { echo "Failed to determine system name for app $app" >&2; return 1; }

  for d in  ${CONFDIR}/system/${sys_name} \
            ${CONFDIR}/applications/${app}
            do
    config_dirs="$config_dirs $d"
  done
  echo "$config_dirs"
}

get_sys_for_proj() {
  local proj=$1
  local sys_name=$(find ${CONFDIR} -name "$proj" -type d|sed -e 's|.*/system/\(.*\)/.*|\1|')
  if [ -z "$sys_name" ];then
    return 1
  else
    echo $sys_name
  fi
}

get_sys_for_app() {
  local app=$1
  local sys_name=$(. ${CONFDIR}/applications/${app}/cicd.params && echo $SYS_NAME)
  if [ -z "$sys_name" ];then
    return 1
  else
    echo $sys_name
  fi
}

# return a list of labels separated with commas: key1:val1,key2:val2
get_labels_for_proj() {
  local proj=$1
  if ! oc get namespace $proj &> /dev/null;then 
    echo "Failed to get project $proj details.." >&2
    return 1
  fi
  local labels=$(oc get namespace $proj -ogo-template --template='{{range $k,$v := .metadata.labels}}{{$k}}:{{$v}}{{","}}{{end}}')
  if [ "${labels:-x}x" != "xx" ];then
    sort_label_list "$labels" "$LABELS_ORDER"
  fi
}

# return system name from project label
get_sys_from_proj_label() {
  local proj=$1
  local labels=$(get_labels_for_proj $proj)
  [ $? -ne 0 ] && return 1
  for i in $(echo name:foobar-tch2-stage,sys:foobar|tr "," " ");do 
    kv=( $(echo $i|tr ":" " ") )
    if [ ${kv[0]} = "sys" ];then
      echo ${kv[1]}
      return 0
    fi
  done
}


# returns true if dir $1 has yamls in it
dir_has_yamls() {
  local d=$1
  ls $d/*.yaml &> /dev/null
  local res=$?
  # echo $res >&2
  return $res
}

# https://gist.github.com/briantjacobs/7753bf850ca5e39be409
parse_yaml() {
    local prefix=''
    [ $# -ge 2 ] && prefix=$2
    local s
    local w
    local fs
    s='[[:space:]]*'
    w='[a-zA-Z0-9_]*'
    fs="$(echo @|tr @ '\034')"
    sed -ne "s|^\($s\)\($w\)$s:$s\"\(.*\)\"$s\$|\1$fs\2$fs\3|p" \
        -e "s|^\($s\)\($w\)$s[:-]$s\(.*\)$s\$|\1$fs\2$fs\3|p" "$1" |
    awk -F"$fs" '{
    indent = length($1)/2;
    vname[indent] = $2;
    for (i in vname) {if (i > indent) {delete vname[i]}}
        if (length($3) > 0) {
            vn=""; for (i=0; i<indent; i++) {vn=(vn)(vname[i])("_")}
            printf("%s%s%s=(\"%s\")\n", "'"$prefix"'",vn, $2, $3);
        }
    }' | sed 's/_=/+=/g'
}

# get yaml metadata.name attribute
# params:
# 
#  file - path to a yaml file
get_yaml_metadata_name() {
  parse_yaml $1 ''|awk -F= '/^metadata_name=/{print $2}'|tr -d '[()"]'
}

# sort label keys 
# arg 1 - key:value list separated with commas (e.g. "key1:val1,key2:val2,key3:val3")
# arg 2 - key order list separated witgh spaces (e.g. "key1 key2")
# returns sorted in the same format as arg 1
sort_label_list() {
  local labels="$1"
  local order="$2"
  local res=""
  local seen_keys=""

  for k in $order;do
      seen="$(echo $labels|tr "," "\n"|grep "^$k:" || true)"
      if [ "${seen:-x}x" != "xx" ];then
          res="$res${res:+,}$seen"
          seen_keys="$seen_keys $k"
      fi
  done

  for l in $(echo $labels|tr "," "\n");do
      for k in $seen_keys;do
          echo $l|grep -q "^$k:" && continue 2
      done
      res="$res,$l"
  done

  echo "$res"
}