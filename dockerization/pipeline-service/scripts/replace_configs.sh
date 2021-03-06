#!/usr/bin/env bash

CONFIG_FILE=/opt/harness/config.yml

yq delete -i $CONFIG_FILE grpcServerConfig.connectors[0]

if [[ "" != "$LOGGING_LEVEL" ]]; then
    yq write -i $CONFIG_FILE logging.level "$LOGGING_LEVEL"
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    yq write -i $CONFIG_FILE logging.loggers.[$LOGGER] "${LOGGER_LEVEL}"
  done
fi

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE mongo.uri "${MONGO_URI//\\&/&}"
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE mongo.connectTimeout $MONGO_CONNECT_TIMEOUT
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE mongo.serverSelectionTimeout $MONGO_SERVER_SELECTION_TIMEOUT
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  yq write -i $CONFIG_FILE mongo.maxConnectionIdleTime $MAX_CONNECTION_IDLE_TIME
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  yq write -i $CONFIG_FILE mongo.connectionsPerHost $MONGO_CONNECTIONS_PER_HOST
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE mongo.indexManagerMode $MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  yq write -i $CONFIG_FILE grpcServerConfig.connectors[0].port "$GRPC_SERVER_PORT"
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE managerTarget $MANAGER_TARGET
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE managerAuthority $MANAGER_AUTHORITY
fi

if [[ "" != "$MANAGER_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE managerClientConfig.baseUrl $MANAGER_BASE_URL
fi

if [[ "" != "$MANAGER_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE managerServiceSecret $MANAGER_SERVICE_SECRET
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE ngManagerServiceHttpClientConfig.baseUrl $NG_MANAGER_BASE_URL
fi

if [[ "" != "$NG_MANAGER_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE ngManagerServiceSecret $NG_MANAGER_SERVICE_SECRET
fi

if [[ "" != "$CI_MANAGER_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE yamlSchemaClientConfig.yamlSchemaHttpClientMap.ci.serviceHttpClientConfig.baseUrl $CI_MANAGER_BASE_URL
fi

if [[ "" != "$CI_MANAGER_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE yamlSchemaClientConfig.yamlSchemaHttpClientMap.ci.secret $CI_MANAGER_SERVICE_SECRET
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE yamlSchemaClientConfig.yamlSchemaHttpClientMap.cd.serviceHttpClientConfig.baseUrl $NG_MANAGER_BASE_URL
fi

if [[ "" != "$NG_MANAGER_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE yamlSchemaClientConfig.yamlSchemaHttpClientMap.cd.secret $NG_MANAGER_SERVICE_SECRET
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfigs.cd.target $NG_MANAGER_TARGET
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfigs.cd.authority $NG_MANAGER_AUTHORITY
fi

if [[ "" != "$CV_MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfigs.cv.target $CV_MANAGER_TARGET
fi

if [[ "" != "$CV_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfigs.cv.authority $CV_MANAGER_AUTHORITY
fi

if [[ "" != "$CI_MANAGER_TARGET" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfigs.ci.target $CI_MANAGER_TARGET
fi

if [[ "" != "$CI_MANAGER_AUTHORITY" ]]; then
  yq write -i $CONFIG_FILE grpcClientConfigs.ci.authority $CI_MANAGER_AUTHORITY
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  yq write -i $CONFIG_FILE scmConnectionConfig.url "$SCM_SERVICE_URI"
fi

if [[ "" != "$PIPELINE_SERVICE_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE pipelineServiceBaseUrl "$PIPELINE_SERVICE_BASE_URL"
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE logging.appenders[0]
  yq write -i $CONFIG_FILE logging.appenders[0].stackdriverLogEnabled "true"
else
  yq delete -i $CONFIG_FILE logging.appenders[1]
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  yq write -i $CONFIG_FILE jwtAuthSecret "$JWT_AUTH_SECRET"
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  yq write -i $CONFIG_FILE jwtIdentityServiceSecret "$JWT_IDENTITY_SERVICE_SECRET"
fi

if [[ "" != "$AUTH_ENABLED" ]]; then
  yq write -i $CONFIG_FILE enableAuth "$AUTH_ENABLED"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  yq write -i $CONFIG_FILE eventsFramework.redis.redisUrl "$EVENTS_FRAMEWORK_REDIS_URL"
fi

if [[ "" != "$EVENTS_FRAMEWORK_USE_SENTINEL" ]]; then
  yq write -i $CONFIG_FILE eventsFramework.redis.sentinel "$EVENTS_FRAMEWORK_USE_SENTINEL"
fi

if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
  yq write -i $CONFIG_FILE eventsFramework.redis.masterName "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME"
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    yq write -i $CONFIG_FILE eventsFramework.redis.sentinelUrls.[$INDEX] "${REDIS_SENTINEL_URL}"
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$NOTIFICATION_BASE_URL" ]]; then
  yq write -i $CONFIG_FILE notificationClient.httpClient.baseUrl "$NOTIFICATION_BASE_URL"
fi

if [[ "" != "$NOTIFICATION_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE notificationClient.messageBroker.uri "${NOTIFICATION_MONGO_URI//\\&/&}"
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  yq write -i $CONFIG_FILE managerClientConfig.baseUrl "$MANAGER_CLIENT_BASEURL"
fi
