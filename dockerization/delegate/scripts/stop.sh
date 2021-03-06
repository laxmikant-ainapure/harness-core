#!/bin/bash -e

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
  i=0
  stopped=0
  while [ "$i" -le 30 ]; do
    if `pgrep -f "\-Dwatchersourcedir=$DIR"> /dev/null`; then
      pgrep -f "\-Dwatchersourcedir=$DIR" | xargs kill
      if [ "$i" -gt 0 ]; then
        sleep 1
      fi
      i=$((i+1))
    else
      echo "Watcher stopped"
      stopped=1
      break
    fi
  done
  if [ "$stopped" -eq 0 ]; then
    echo "Unable to stop watcher in 30 seconds."
    exit 1
  fi
else
  echo "Watcher not running"
fi

if `pgrep -f "\-Ddelegatesourcedir=$DIR"> /dev/null`; then
  i=0
  while [ "$i" -le 30 ]; do
    if `pgrep -f "\-Ddelegatesourcedir=$DIR"> /dev/null`; then
      pgrep -f "\-Ddelegatesourcedir=$DIR" | xargs kill
      if [ "$i" -gt 0 ]; then
        sleep 1
      fi
      i=$((i+1))
    else
      echo "Delegate stopped"
      rm -rf msg
      exit 0
    fi
  done
  echo "Unable to stop delegate in 30 seconds."
  exit 1
else
  echo "Delegate not running"
  rm -rf msg
  exit 0
fi
