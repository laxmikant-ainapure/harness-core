package io.harness.artifacts.gcr.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.gcr.GcrRestClient;
import io.harness.artifacts.gcr.beans.GcrInternalConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * @author brett on 8/2/17
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class GcrApiServiceImpl implements GcrApiService {
  private static final int CONNECT_TIMEOUT = 5; // TODO:: read from config

  private GcrRestClient getGcrRestClient(String registryHostName) {
    String url = getUrl(registryHostName);
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(GcrRestClient.class);
  }

  public String getUrl(String gcrHostName) {
    return "https://" + gcrHostName + (gcrHostName.endsWith("/") ? "" : "/");
  }

  @Override
  public List<BuildDetailsInternal> getBuilds(GcrInternalConfig gcpConfig, String imageName, int maxNumberOfBuilds) {
    try {
      Response<GcrImageTagResponse> response = getGcrRestClient(gcpConfig.getRegistryHostname())
                                                   .listImageTags(gcpConfig.getBasicAuthHeader(), imageName)
                                                   .execute();
      checkValidImage(imageName, response);
      return processBuildResponse(gcpConfig.getRegistryHostname(), imageName, response.body());
    } catch (IOException e) {
      throw new WingsException(ErrorCode.DEFAULT_ERROR_CODE, USER, e).addParam("message", ExceptionUtils.getMessage(e));
    }
  }

  private void checkValidImage(String imageName, Response<GcrImageTagResponse> response) {
    if (response.code() == 404) { // Page not found
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
          .addParam("args", "Image name [" + imageName + "] does not exist in Google Container Registry.");
    }
  }

  private List<BuildDetailsInternal> processBuildResponse(
      String gcrUrl, String imageName, GcrImageTagResponse dockerImageTagResponse) {
    if (dockerImageTagResponse != null && dockerImageTagResponse.getTags() != null) {
      List<BuildDetailsInternal> buildDetails =
          dockerImageTagResponse.getTags()
              .stream()
              .map(tag -> {
                Map<String, String> metadata = new HashMap();
                metadata.put(ArtifactMetadataKeys.IMAGE,
                    (gcrUrl.endsWith("/") ? gcrUrl : gcrUrl.concat("/")) + imageName + ":" + tag);
                metadata.put(ArtifactMetadataKeys.TAG, tag);
                return BuildDetailsInternal.builder()
                    .uiDisplayName("Tag# " + tag)
                    .number(tag)
                    .metadata(metadata)
                    .build();
              })
              .collect(toList());
      // Sorting at build tag for docker artifacts.
      return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());
    }
    return emptyList();
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuild(GcrInternalConfig gcpConfig, String imageName) {
    return null;
  }

  @Override
  public boolean verifyImageName(GcrInternalConfig gcpConfig, String imageName) {
    try {
      Response<GcrImageTagResponse> response = getGcrRestClient(gcpConfig.getRegistryHostname())
                                                   .listImageTags(gcpConfig.getBasicAuthHeader(), imageName)
                                                   .execute();
      if (!isSuccessful(response)) {
        return validateImageName(imageName);
      }
    } catch (IOException e) {
      log.error(ExceptionUtils.getMessage(e), e);
      throw new WingsException(ErrorCode.REQUEST_TIMEOUT, USER).addParam("name", "Registry server");
    }
    return true;
  }

  private boolean validateImageName(String imageName) {
    // image not found or user doesn't have permission to list image tags
    log.warn("Image name [" + imageName + "] does not exist in Google Container Registry.");
    throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
        .addParam("args", "Image name [" + imageName + "] does not exist in Google Container Registry.");
  }

  @Override
  public boolean validateCredentials(GcrInternalConfig gcpConfig, String imageName) {
    try {
      GcrRestClient registryRestClient = getGcrRestClient(gcpConfig.getRegistryHostname());
      Response response = registryRestClient.listImageTags(gcpConfig.getBasicAuthHeader(), imageName).execute();
      return isSuccessful(response);
    } catch (IOException e) {
      throw new WingsException(ErrorCode.DEFAULT_ERROR_CODE, USER, e).addParam("message", ExceptionUtils.getMessage(e));
    }
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      GcrInternalConfig gcrInternalConfig, String imageName, String tagRegex) {
    List<BuildDetailsInternal> builds = getBuilds(gcrInternalConfig, imageName, MAX_NO_OF_TAGS_PER_IMAGE);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(toList());
    if (builds.isEmpty()) {
      throw new InvalidArtifactServerException(
          "There are no builds for this image: " + imageName + " and tagRegex: " + tagRegex, USER);
    }
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(GcrInternalConfig gcrInternalConfig, String imageName, String tag) {
    try {
      List<BuildDetailsInternal> builds = getBuilds(gcrInternalConfig, imageName, MAX_NO_OF_TAGS_PER_IMAGE);
      builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());
      if (builds.size() != 1) {
        throw new InvalidArtifactServerException("Didn't get build number", USER);
      }
      return builds.get(0);
    } catch (Exception e) {
      throw new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER);
    }
  }

  private boolean isSuccessful(Response<?> response) {
    int code = response.code();
    switch (code) {
      case 200:
        return true;
      case 404:
      case 400:
        log.info("Response code {} received. Mostly with Image does not exist", code);
        return false;
      case 403:
        log.info("Response code {} received. User not authorized to access GCR Storage", code);
        throw new InvalidArtifactServerException("User not authorized to access GCR Storage", USER);
      case 401:
        throw new InvalidArtifactServerException("Invalid Google Container Registry credentials", USER);
      default:
        unhandled(code);
    }
    return true;
  }

  /**
   * The type GCR image tag response.
   */
  public static class GcrImageTagResponse {
    private List<String> child;
    private String name;
    private List<String> tags;
    private Map manifest;

    public Map getManifest() {
      return manifest;
    }

    public void setManifest(Map manifest) {
      this.manifest = manifest;
    }

    public List<String> getChild() {
      return child;
    }

    public void setChild(List<String> child) {
      this.child = child;
    }

    /**
     * Gets name.
     *
     * @return the name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets name.
     *
     * @param name the name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Gets tags.
     *
     * @return the tags
     */
    public List<String> getTags() {
      return tags;
    }

    /**
     * Sets tags.
     *
     * @param tags the tags
     */
    public void setTags(List<String> tags) {
      this.tags = tags;
    }
  }
}