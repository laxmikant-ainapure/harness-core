/**
 *
 */

package software.wings.beans;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.MoreObjects;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The type Ssh execution credential.
 *
 * @author Rishi
 */
@JsonTypeName("SSH")
@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(Module._970_API_SERVICES_BEANS)
public class SSHExecutionCredential extends ExecutionCredential {
  private String sshUser;
  private char[] sshPassword;
  private String appAccount;
  private char[] appAccountPassword;
  private char[] keyPassphrase;

  /**
   * Instantiates a new Ssh execution credential.
   */
  public SSHExecutionCredential() {
    super(ExecutionType.SSH);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("sshUser", sshUser).add("appAccount", appAccount).toString();
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String sshUser;
    private char[] sshPassword;
    private String appAccount;
    private char[] appAccountPassword;
    private char[] keyPassphrase;
    private ExecutionType executionType;

    private Builder() {}

    /**
     * A ssh execution credential builder.
     *
     * @return the builder
     */
    public static Builder aSSHExecutionCredential() {
      return new Builder();
    }

    /**
     * With ssh user builder.
     *
     * @param sshUser the ssh user
     * @return the builder
     */
    public Builder withSshUser(String sshUser) {
      this.sshUser = sshUser;
      return this;
    }

    /**
     * With ssh password builder.
     *
     * @param sshPassword the ssh password
     * @return the builder
     */
    public Builder withSshPassword(char[] sshPassword) {
      this.sshPassword = sshPassword == null ? null : sshPassword.clone();
      return this;
    }

    /**
     * With app account builder.
     *
     * @param appAccount the app account
     * @return the builder
     */
    public Builder withAppAccount(String appAccount) {
      this.appAccount = appAccount;
      return this;
    }

    /**
     * With app account password builder.
     *
     * @param appAccountPassword the app account password
     * @return the builder
     */
    public Builder withAppAccountPassword(char[] appAccountPassword) {
      this.appAccountPassword = appAccountPassword == null ? null : appAccountPassword.clone();
      return this;
    }

    /**
     * With key passphrase builder.
     *
     * @param keyPassphrase the key passphrase
     * @return the builder
     */
    public Builder withKeyPassphrase(char[] keyPassphrase) {
      this.keyPassphrase = keyPassphrase == null ? null : keyPassphrase.clone();
      return this;
    }

    /**
     * With execution type builder.
     *
     * @param executionType the execution type
     * @return the builder
     */
    public Builder withExecutionType(ExecutionType executionType) {
      this.executionType = executionType;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aSSHExecutionCredential()
          .withSshUser(sshUser)
          .withSshPassword(sshPassword)
          .withAppAccount(appAccount)
          .withAppAccountPassword(appAccountPassword)
          .withKeyPassphrase(keyPassphrase)
          .withExecutionType(executionType);
    }

    /**
     * Build ssh execution credential.
     *
     * @return the ssh execution credential
     */
    public SSHExecutionCredential build() {
      SSHExecutionCredential sSHExecutionCredential = new SSHExecutionCredential();
      sSHExecutionCredential.setSshUser(sshUser);
      sSHExecutionCredential.setSshPassword(sshPassword);
      sSHExecutionCredential.setAppAccount(appAccount);
      sSHExecutionCredential.setAppAccountPassword(appAccountPassword);
      sSHExecutionCredential.setKeyPassphrase(keyPassphrase);
      sSHExecutionCredential.setExecutionType(executionType);
      return sSHExecutionCredential;
    }
  }
}
