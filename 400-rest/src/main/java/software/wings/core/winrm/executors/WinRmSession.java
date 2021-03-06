package software.wings.core.winrm.executors;

import static io.harness.windows.CmdUtils.escapeEnvValueSpecialChars;

import static java.lang.String.format;

import io.harness.delegate.configuration.InstallUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;
import io.harness.ssh.SshHelperUtils;

import software.wings.beans.WinRmConnectionAttributes.AuthenticationScheme;

import com.google.common.annotations.VisibleForTesting;
import com.jcraft.jsch.JSchException;
import io.cloudsoft.winrm4j.client.ShellCommand;
import io.cloudsoft.winrm4j.client.WinRmClient;
import io.cloudsoft.winrm4j.client.WinRmClientBuilder;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WinRmSession implements AutoCloseable {
  private static final int retryCount = 1;
  @VisibleForTesting static final String COMMAND_PLACEHOLDER = "%s %s";

  private final ShellCommand shell;
  private final WinRmTool winRmTool;
  private final LogCallback logCallback;
  private PyWinrmArgs args;

  public WinRmSession(WinRmSessionConfig config, LogCallback logCallback) throws JSchException {
    Map<String, String> processedEnvironmentMap = new HashMap<>();
    if (config.getEnvironment() != null) {
      for (Entry<String, String> entry : config.getEnvironment().entrySet()) {
        processedEnvironmentMap.put(entry.getKey(), escapeEnvValueSpecialChars(entry.getValue()));
      }
    }
    this.logCallback = logCallback;
    if (config.getAuthenticationScheme() == AuthenticationScheme.KERBEROS) {
      args = PyWinrmArgs.builder()
                 .hostname(getEndpoint(config.getHostname(), config.getPort(), config.isUseSSL()))
                 .username(getUserPrincipal(config.getUsername(), config.getDomain()))
                 .environmentMap(processedEnvironmentMap)
                 .workingDir(config.getWorkingDirectory())
                 .timeout(config.getTimeout())
                 .build();
      SshHelperUtils.generateTGT(getUserPrincipal(config.getUsername(), config.getDomain()), config.getPassword(),
          config.getKeyTabFilePath(), logCallback);
      shell = null;
      winRmTool = null;
      return;
    }

    WinRmClientBuilder clientBuilder =
        WinRmClient.builder(getEndpoint(config.getHostname(), config.getPort(), config.isUseSSL()))
            .disableCertificateChecks(config.isSkipCertChecks())
            .authenticationScheme(getAuthSchemeString(config.getAuthenticationScheme()))
            .credentials(config.getDomain(), config.getUsername(), config.getPassword())
            .workingDirectory(config.getWorkingDirectory())
            .environment(processedEnvironmentMap)
            .retriesForConnectionFailures(retryCount)
            .operationTimeout(config.getTimeout());
    WinRmClient client = clientBuilder.build();

    WinRmClientContext context = WinRmClientContext.newInstance();

    winRmTool = WinRmTool.Builder.builder(config.getHostname(), config.getUsername(), config.getPassword())
                    .disableCertificateChecks(config.isSkipCertChecks())
                    .authenticationScheme(getAuthSchemeString(config.getAuthenticationScheme()))
                    .workingDirectory(config.getWorkingDirectory())
                    .environment(processedEnvironmentMap)
                    .port(config.getPort())
                    .useHttps(config.isUseSSL())
                    .context(context)
                    .build();

    shell = client.createShell();
  }

  public int executeCommandString(String command, Writer output, Writer error, boolean isOutputWriter) {
    if (args != null) {
      try {
        File commandFile = File.createTempFile("winrm-kerberos-command", null);
        byte[] buff = command.getBytes(StandardCharsets.UTF_8);
        Files.write(Paths.get(commandFile.getPath()), buff);

        return SshHelperUtils.executeLocalCommand(format(COMMAND_PLACEHOLDER, InstallUtils.getHarnessPywinrmToolPath(),
                                                      args.getArgs(commandFile.getAbsolutePath())),
                   logCallback, output, isOutputWriter)
            ? 0
            : 1;
      } catch (IOException e) {
        log.error(format("Error while creating temporary file: %s", e));
        logCallback.saveExecutionLog("Error while creating temporary file");
        return 1;
      }
    }
    return shell.execute(command, output, error);
  }

  public int executeCommandsList(List<List<String>> commandList, Writer output, Writer error, boolean isOutputWriter)
      throws IOException {
    WinRmToolResponse winRmToolResponse = null;
    if (commandList.isEmpty()) {
      return -1;
    }
    int statusCode = 0;
    if (args != null) {
      for (List<String> list : commandList) {
        String command = String.join(" & ", list);
        statusCode = executeCommandString(command, output, error, isOutputWriter);
        if (statusCode != 0) {
          return statusCode;
        }
      }
    } else {
      for (List<String> list : commandList) {
        winRmToolResponse = winRmTool.executeCommand(list);
        if (!winRmToolResponse.getStdOut().isEmpty()) {
          output.write(winRmToolResponse.getStdOut());
        }

        if (!winRmToolResponse.getStdErr().isEmpty()) {
          error.write(winRmToolResponse.getStdErr());
        }
        statusCode = winRmToolResponse.getStatusCode();
        if (statusCode != 0) {
          return statusCode;
        }
      }
    }

    return statusCode;
  }

  private static String getEndpoint(String hostname, int port, boolean useHttps) {
    return format("%s://%s:%d/wsman", useHttps ? "https" : "http", hostname, port);
  }

  private static String getAuthSchemeString(AuthenticationScheme authenticationScheme) {
    switch (authenticationScheme) {
      case BASIC:
        return "Basic";
      case NTLM:
        return "NTLM";
      case KERBEROS:
        return "Kerberos";
      default:
        return "Unknown";
    }
  }

  @Override
  public void close() {
    if (shell != null) {
      shell.close();
    }
  }

  @VisibleForTesting
  String getUserPrincipal(String username, String domain) {
    if (username == null || domain == null) {
      throw new InvalidRequestException("Username or domain cannot be null", WingsException.USER);
    }
    if (username.contains("@")) {
      username = username.substring(0, username.indexOf('@'));
    }
    return format("%s@%s", username, domain.toUpperCase());
  }
}
