#!/usr/bin/env bash

addTags(){
	path=$1
	tags=$2
	IFS=',' read -ra str_array <<< "$tags"
	for tag in "${str_array[@]}"
		do
	   	 	yq write -i /opt/harness/command-library-server-config.yml "$path[+]" "$tag"
		done
}

yq delete -i /opt/harness/command-library-server-config.yml server.adminConnectors
yq delete -i /opt/harness/command-library-server-config.yml server.applicationConnectors[0]

if [[ "" != "$LOGGING_LEVEL" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml logging.level "$LOGGING_LEVEL"
fi

if [[ "" != "$COMMAND_LIBRARY_SERVER_PORT" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml server.applicationConnectors[0].port "$COMMAND_LIBRARY_SERVER_PORT"
else
  yq write -i /opt/harness/command-library-server-config.yml server.applicationConnectors[0].port "7070"
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml mongo.uri "${MONGO_URI//\\&/&}"
fi

yq write -i /opt/harness/command-library-server-config.yml server.requestLog.appenders[0].type "console"
yq write -i /opt/harness/command-library-server-config.yml server.requestLog.appenders[0].threshold "TRACE"
yq write -i /opt/harness/command-library-server-config.yml server.requestLog.appenders[0].target "STDOUT"

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i /opt/harness/command-library-server-config.yml logging.appenders[0]
  yq write -i /opt/harness/command-library-server-config.yml logging.appenders[0].stackdriverLogEnabled "true"
else
  yq delete -i /opt/harness/command-library-server-config.yml logging.appenders[1]
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  yq write -i /opt/harness/command-library-server-config.yml serviceSecret.managerToCommandLibraryServiceSecret "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET"
fi

if [[ "" != "$ALLOWED_TAGS_TO_ADD" ]]; then
  addTags "tag.allowedTags" "$ALLOWED_TAGS_TO_ADD"
fi

if [[ "" != "$IMPORTANT_TAGS_TO_ADD" ]]; then
  addTags "tag.importantTags" "$IMPORTANT_TAGS_TO_ADD"
fi


