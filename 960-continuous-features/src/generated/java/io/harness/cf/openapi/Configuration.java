/*
 * Harness feature flag service
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 1.0.0
 * Contact: ff@harness.io
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

package io.harness.cf.openapi;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen",
    date = "2021-03-24T19:32:06.834-07:00[America/Los_Angeles]")
public class Configuration {
  private static io.harness.cf.openapi.ApiClient defaultApiClient = new io.harness.cf.openapi.ApiClient();

  /**
   * Get the default API client, which would be used when creating API
   * instances without providing an API client.
   *
   * @return Default API client
   */
  public static io.harness.cf.openapi.ApiClient getDefaultApiClient() {
    return defaultApiClient;
  }

  /**
   * Set the default API client, which would be used when creating API
   * instances without providing an API client.
   *
   * @param apiClient API client
   */
  public static void setDefaultApiClient(ApiClient apiClient) {
    defaultApiClient = apiClient;
  }
}
