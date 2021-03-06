package io.harness.tiserviceclient;

import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.exception.GeneralException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;

@Getter
@Setter
@Slf4j
@Singleton
public class TIServiceUtils {
  private final TIServiceClient tiServiceClient;
  private final TIServiceConfig tiServiceConfig;

  @Inject
  public TIServiceUtils(TIServiceClient tiServiceClient, TIServiceConfig tiServiceConfig) {
    this.tiServiceClient = tiServiceClient;
    this.tiServiceConfig = tiServiceConfig;
  }

  @NotNull
  public String getTIServiceToken(String accountID) {
    log.info("Initiating token request to TI service: {}", this.tiServiceConfig.getBaseUrl());
    Call<String> tokenCall = tiServiceClient.generateToken(accountID, this.tiServiceConfig.getGlobalToken());
    Response<String> response = null;
    try {
      response = tokenCall.execute();
    } catch (IOException e) {
      throw new GeneralException("Token request to TI service call failed", e);
    }

    // Received error from the server
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new GeneralException(String.format(
          "Could not fetch token from TI service. status code = %s, message = %s, response = %s", response.code(),
          response.message() == null ? "null" : response.message(), response.errorBody() == null ? "null" : errorBody));
    }
    return response.body();
  }
}
