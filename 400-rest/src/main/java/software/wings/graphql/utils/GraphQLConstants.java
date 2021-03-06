package software.wings.graphql.utils;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import lombok.experimental.UtilityClass;

@UtilityClass
@TargetModule(Module._380_CG_GRAPHQL)
public class GraphQLConstants {
  public static final String APP_ID_ARG = "applicationId";
  public static final String MAX_PAGE_SIZE_STR = "20";

  // Error messages
  public static final String FEATURE_NOT_ENABLED = "Feature not enabled";
  public static final String RATE_LIMIT_REACHED =
      "You've reached your account's rate limit for data queries. Please try again later.";
  public static final String INVALID_API_KEY = "Invalid Api Key";
  public static final String NOT_AUTHORIZED = "User not authorized";
  public static final String INVALID_TOKEN = "Invalid Token";
  public static final String CREATE_APPLICATION_API = "createApplication";
  public static final String DELETE_APPLICATION_API = "deleteApplication";
  public static final String HTTP_SERVLET_REQUEST = "httpServletRequest";
  public static final String GRAPHQL_QUERY_STRING = "graphqlQueryString";
}
