#!/usr/bin/env bash

set -eo pipefail

BASEDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $BASEDIR/common.sh

if [ $# -lt 1 ];then
    cat <<- EOF
    Usage: $0 sys-name [suffix]

    where:

    sys-name  - system name
    suffix    - suffix to be appended to project name (none by default)
EOF
    exit 2
fi

export SYS_NAME="$1"

CICD_PROJ="${SYS_NAME}-cicd"
PROJ_SUFFIX=
if [ $# -eq 2 ];then
    PROJ_SUFFIX="$2"
    CICD_PROJ="${SYS_NAME}-${PROJ_SUFFIX}-cicd"
fi

set -u



EXTRA_PARAMS="--param=PROJECT=$CICD_PROJ --param=SYS_NAME=$SYS_NAME"
JENKINS_ENVS=""
for d in $(get_config_dirs $CICD_PROJ);do
    [ -f $d/jenkins.envs ] && JENKINS_ENVS="$JENKINS_ENVS $d/jenkins.envs"
    [ -f $d/jenkins.params ] || continue
    echo "Adding jenkins params from $d/jenkins.params"
    . $d/jenkins.params
    EXTRA_PARAMS="--param-file=${d}/jenkins.params $EXTRA_PARAMS"
done

# itpbc-241
# get secret with proper label and convert them to variables for casc
oc get secret -n $CICD_PROJ -lvarname.casc.jenkins.p4.int -Lvarname.casc.jenkins.p4.int --no-headers|awk "{print \$5\"=$CICD_PROJ-\"\$1}" > $PWD/casc.envs
JENKINS_ENVS="$JENKINS_ENVS $PWD/casc.envs"

# configure common objects firs including git ssh secret
echo "> Deploying common cicd objects from jenkins-config-template.yaml in $CICD_PROJ project"
oc process --ignore-unknown-parameters -f $BASEDIR/../templates/jenkins-config-template.yaml \
        $EXTRA_PARAMS \
    	| oc apply -f- -n $CICD_PROJ

# configure project - we need configmaps and other objects before deploying jenkins
echo "> Configuring project $CICD_PROJ"
$BASEDIR/configure-project.sh $CICD_PROJ

# first deploy
echo "> Deploying jenkins template for system $SYS_NAME in $CICD_PROJ project"
oc process --ignore-unknown-parameters openshift//jenkins-${JENKINS_TYPE} \
        $EXTRA_PARAMS \
    	| oc apply -f- -n $CICD_PROJ


echo "> Setting jenkins permissions using jenkins-permissions-template.yaml"
for p in ${SYS_NAME}${PROJ_SUFFIX:+-}${PROJ_SUFFIX}-{cicd,test,stage,prod};do
  echo ">> project: $p"
  oc process --ignore-unknown-parameters -f $BASEDIR/../templates/jenkins-permissions-template.yaml \
              --param=SYS_NAME=${SYS_NAME}${PROJ_SUFFIX:+-}${PROJ_SUFFIX} \
          	| oc apply -f- -n $p
done

echo "> Setting jenkins environment variables for $CICD_PROJ"
echo $JENKINS_ENVS | xargs cat | tee -a /dev/stderr | oc set env -e - dc/jenkins -n $CICD_PROJ

