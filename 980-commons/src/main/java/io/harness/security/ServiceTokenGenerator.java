package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.common.base.Preconditions;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by rsingh on 9/17/18.
 */
@OwnedBy(PL)
public class ServiceTokenGenerator {
  public static final AtomicReference<String> VERIFICATION_SERVICE_SECRET = new AtomicReference<>();

  public static ServiceTokenGenerator newInstance() {
    return new ServiceTokenGenerator();
  }

  public String getVerificationServiceToken() {
    Preconditions.checkState(isNotEmpty(VERIFICATION_SERVICE_SECRET.get()),
        "could not read verification secret from system or env properties");
    return createNewToken(VERIFICATION_SERVICE_SECRET.get());
  }

  public String getServiceTokenWithDuration(String secretKey, Duration duration) {
    if (isNotEmpty(secretKey)) {
      return createNewToken(secretKey, duration);
    }
    throw new InvalidArgumentsException("secretKey cannot be empty", null);
  }

  public String getServiceToken(String secretKey) {
    return getServiceTokenWithDuration(secretKey, Duration.ofHours(4));
  }

  private String createNewToken(String serviceSecret, Duration tokenDuration) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(serviceSecret);

      JWTCreator.Builder jwtBuilder =
          JWT.create()
              .withIssuer("Harness Inc")
              .withIssuedAt(new Date())
              .withExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(tokenDuration.toHours())))
              .withNotBefore(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)))
              .withIssuedAt(new Date());

      addPrincipalToJWTBuilder(jwtBuilder);
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new InvalidRequestException("error creating jwt token", exception);
    }
  }

  private void addPrincipalToJWTBuilder(JWTCreator.Builder jwtBuilder) {
    Map<String, String> claims = new HashMap<>();
    if (SecurityContextBuilder.getPrincipal() != null) {
      claims = SecurityContextBuilder.getPrincipal().getJWTClaims();
    }
    if (claims.size() > 0) {
      claims.forEach(jwtBuilder::withClaim);
    }
  }

  private String createNewToken(String serviceSecret) {
    return createNewToken(serviceSecret, Duration.ofHours(4));
  }
}
