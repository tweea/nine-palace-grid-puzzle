#!/bin/bash

# Base on Spring Boot's launch.script, date 2019-09-22
# https://github.com/spring-projects/spring-boot/blob/master/spring-boot-project/spring-boot-tools/spring-boot-loader-tools/src/main/resources/org/springframework/boot/loader/tools/launch.script

[[ -n "$DEBUG" ]] && set -x

# ANSI Colors
echoRed() { echo $'\e[0;31m'"$1"$'\e[0m'; }
echoGreen() { echo $'\e[0;32m'"$1"$'\e[0m'; }
echoYellow() { echo $'\e[0;33m'"$1"$'\e[0m'; }

# Initialize variables
jarfile="$1"
[[ -z "$jarfile" ]] && echoRed "Missing argument jarfile." && exit 1
shift

# Find the real jar
cd "$(dirname "$jarfile")" || { echoRed "Cannot access directory $(dirname "$jarfile")."; exit 1; }
jarfolder=$(pwd)
jarfile=$(pwd)/$(basename "$jarfile")
! [[ -f "$jarfile" ]] && echoRed "$jarfile is not a file." && exit 1

# Initialize PID/LOG locations
PID_FOLDER="${jarfolder}/data"
LOG_FOLDER="${jarfolder}/data/logs"
! [[ -x "$PID_FOLDER" ]] && echoRed "PID_FOLDER $PID_FOLDER does not exist." && exit 1
! [[ -x "$LOG_FOLDER" ]] && echoRed "LOG_FOLDER $LOG_FOLDER does not exist." && exit 1

# Create an identity for log/pid files
identity=$(basename "${jarfile%.*}")

# Initialize log file name
[[ -z "$LOG_FILENAME" ]] && LOG_FILENAME="${identity}-run.log"

# Initialize stop wait time
[[ -z "$STOP_WAIT_TIME" ]] && STOP_WAIT_TIME="60"

# Utility functions
checkPermissions() {
  touch "$pid_file" &> /dev/null || { echoRed "Operation not permitted (cannot access pid file)"; return 4; }
  touch "$log_file" &> /dev/null || { echoRed "Operation not permitted (cannot access log file)"; return 4; }
}

isRunning() {
  ps -p "$1" &> /dev/null
}

# Determine the script action
action="$1"
shift

# Build the pid and log filenames
pid_file="$PID_FOLDER/${identity}.pid"
log_file="$LOG_FOLDER/$LOG_FILENAME"

# Determine the user to run as if we are root
# shellcheck disable=SC2012
[[ $(id -u) == "0" ]] && run_user=$(ls -ld "$jarfile" | awk '{print $3}')

# Run as user specified in RUN_AS_USER
if [[ -n "$RUN_AS_USER" ]]; then
    if ! [[ "$action" =~ ^(status|run)$ ]]; then
        id -u "$RUN_AS_USER" || {
            echoRed "Cannot run as '$RUN_AS_USER': no such user"
            exit 2
        }
        [[ $(id -u) == 0 ]] || {
            echoRed "Cannot run as '$RUN_AS_USER': current user is not root"
            exit 4
        }
    fi
    run_user="$RUN_AS_USER"
fi

# Issue a warning if the application will run as root
[[ $(id -u ${run_user}) == "0" ]] && { echoYellow "Application is running as root (UID 0). This is considered insecure."; }

# Find Java
if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
    javaexe="$JAVA_HOME/bin/java"
elif type -p java > /dev/null 2>&1; then
    javaexe=$(type -p java)
elif [[ -x "/usr/bin/java" ]];  then
    javaexe="/usr/bin/java"
else
    echo "Unable to find Java"
    exit 1
fi

[[ -z "$JAVA_OPTS" ]] && JAVA_OPTS="-Xms256m -Xmx512m -XX:MetaspaceSize=64m -XX:MaxMetaspaceSize=256m -XX:-OmitStackTraceInFastThrow"
export LOGGING_CONFIG="classpath:log4j2-docker.xml"
export LOGGING_PATH="data/logs"
export LOGGING_FILENAME="${identity}"
export SPRING_CONFIG_ADDITIONAL_LOCATION="file:./data/config/"
export SPRING_PROFILES_ACTIVE="docker"
arguments=(-Dsun.misc.URLClassPath.disableJarChecking=true -Duser.timezone=GMT+8 -Dloader.path="${jarfolder}/data/config" $JAVA_OPTS -jar "$jarfile" $RUN_ARGS "$@")

# Action functions
start() {
  if [[ -f "$pid_file" ]]; then
    pid=$(cat "$pid_file")
    isRunning "$pid" && { echoYellow "Already running [$pid]"; return 0; }
  fi
  do_start "$@"
}

do_start() {
  working_dir=$(dirname "$jarfile")
  pushd "$working_dir" > /dev/null
  if [[ ! -e "$PID_FOLDER" ]]; then
    mkdir -p "$PID_FOLDER" &> /dev/null
    if [[ -n "$run_user" ]]; then
      chown "$run_user" "$PID_FOLDER"
    fi
  fi
  if [[ ! -e "$log_file" ]]; then
    touch "$log_file" &> /dev/null
    if [[ -n "$run_user" ]]; then
      chown "$run_user" "$log_file"
    fi
  fi
  if [[ -n "$run_user" ]]; then
    checkPermissions || return $?
    su -s /bin/sh -c "$javaexe $(printf "\"%s\" " "${arguments[@]}") >> \"$log_file\" 2>&1 & echo \$!" "$run_user" > "$pid_file"
    pid=$(cat "$pid_file")
  else
    checkPermissions || return $?
    "$javaexe" "${arguments[@]}" >> "$log_file" 2>&1 &
    pid=$!
    disown $pid
    echo "$pid" > "$pid_file"
  fi
  [[ -z $pid ]] && { echoRed "Failed to start"; return 1; }
  echoGreen "Started $jarfile[$pid]"
}

stop() {
  working_dir=$(dirname "$jarfile")
  pushd "$working_dir" > /dev/null
  [[ -f $pid_file ]] || { echoYellow "Not running (pidfile not found)"; return 0; }
  pid=$(cat "$pid_file")
  isRunning "$pid" || { echoYellow "Not running (process ${pid}). Removing stale pid file."; rm -f "$pid_file"; return 0; }
  do_stop "$pid" "$pid_file"
}

do_stop() {
  kill "$1" &> /dev/null || { echoRed "Unable to kill process $1"; return 1; }
  for i in $(seq 1 $STOP_WAIT_TIME); do
    isRunning "$1" || { echoGreen "Stopped $jarfile[$1]"; rm -f "$2"; return 0; }
    [[ $i -eq STOP_WAIT_TIME/2 ]] && kill "$1" &> /dev/null
    sleep 1
  done
  echoRed "Unable to kill process $1";
  return 1;
}

force_stop() {
  [[ -f $pid_file ]] || { echoYellow "Not running (pidfile not found)"; return 0; }
  pid=$(cat "$pid_file")
  isRunning "$pid" || { echoYellow "Not running (process ${pid}). Removing stale pid file."; rm -f "$pid_file"; return 0; }
  do_force_stop "$pid" "$pid_file"
}

do_force_stop() {
  kill -9 "$1" &> /dev/null || { echoRed "Unable to kill process $1"; return 1; }
  for i in $(seq 1 $STOP_WAIT_TIME); do
    isRunning "$1" || { echoGreen "Stopped $jarfile[$1]"; rm -f "$2"; return 0; }
    [[ $i -eq STOP_WAIT_TIME/2 ]] && kill -9 "$1" &> /dev/null
    sleep 1
  done
  echoRed "Unable to kill process $1";
  return 1;
}

restart() {
  stop && start
}

force_reload() {
  working_dir=$(dirname "$jarfile")
  pushd "$working_dir" > /dev/null
  [[ -f $pid_file ]] || { echoRed "Not running (pidfile not found)"; return 7; }
  pid=$(cat "$pid_file")
  rm -f "$pid_file"
  isRunning "$pid" || { echoRed "Not running (process ${pid} not found)"; return 7; }
  do_stop "$pid" "$pid_file"
  do_start
}

status() {
  working_dir=$(dirname "$jarfile")
  pushd "$working_dir" > /dev/null
  [[ -f "$pid_file" ]] || { echoRed "Not running"; return 3; }
  pid=$(cat "$pid_file")
  isRunning "$pid" || { echoRed "Not running (process ${pid} not found)"; return 1; }
  echoGreen "Running $jarfile[$pid]"
  return 0
}

run() {
  pushd "$(dirname "$jarfile")" > /dev/null
  "$javaexe" "${arguments[@]}"
  result=$?
  popd > /dev/null
  return "$result"
}

# Call the appropriate action function
case "$action" in
start)
  start "$@"; exit $?;;
stop)
  stop "$@"; exit $?;;
force-stop)
  force_stop "$@"; exit $?;;
restart)
  restart "$@"; exit $?;;
force-reload)
  force_reload "$@"; exit $?;;
status)
  status "$@"; exit $?;;
run)
  run "$@"; exit $?;;
*)
  echo "Usage: $0 <jarfile> {start|stop|force-stop|restart|force-reload|status|run}"; exit 1;
esac

exit 0
