package software.wings.service.impl.yaml.handler.setting.artifactserver;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.HarnessException;

import software.wings.beans.DockerConfig;
import software.wings.beans.DockerConfig.Yaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;

import com.google.inject.Singleton;
import java.util.List;

/**
 * @author rktummala on 11/19/17
 */
@OwnedBy(CDC)
@Singleton
public class DockerRegistryConfigYamlHandler extends ArtifactServerYamlHandler<Yaml, DockerConfig> {
  @Override
  public Yaml toYaml(SettingAttribute settingAttribute, String appId) {
    DockerConfig dockerConfig = (DockerConfig) settingAttribute.getValue();
    Yaml yaml;
    if (dockerConfig.hasCredentials()) {
      yaml = Yaml.builder()
                 .harnessApiVersion(getHarnessApiVersion())
                 .type(dockerConfig.getType())
                 .url(dockerConfig.getDockerRegistryUrl())
                 .username(dockerConfig.getUsername())
                 .password(getEncryptedYamlRef(dockerConfig.getAccountId(), dockerConfig.getEncryptedPassword()))
                 .build();
    } else {
      yaml = Yaml.builder()
                 .harnessApiVersion(getHarnessApiVersion())
                 .type(dockerConfig.getType())
                 .url(dockerConfig.getDockerRegistryUrl())
                 .build();
    }

    toYaml(yaml, settingAttribute, appId);
    return yaml;
  }

  @Override
  protected SettingAttribute toBean(SettingAttribute previous, ChangeContext<Yaml> changeContext,
      List<ChangeContext> changeSetContext) throws HarnessException {
    String uuid = previous != null ? previous.getUuid() : null;
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();

    DockerConfig config = DockerConfig.builder()
                              .accountId(accountId)
                              .dockerRegistryUrl(yaml.getUrl())
                              .encryptedPassword(yaml.getPassword())
                              .username(yaml.getUsername())
                              .build();
    return buildSettingAttribute(accountId, changeContext.getChange().getFilePath(), uuid, config);
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
