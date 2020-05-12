package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import com.google.inject.Singleton;

import io.harness.annotations.dev.OwnedBy;
import software.wings.beans.artifact.SftpArtifactStream;
import software.wings.beans.artifact.SftpArtifactStream.Yaml;
import software.wings.beans.yaml.ChangeContext;

@OwnedBy(CDC)
@Singleton
public class SftpArtifactStreamYamlHandler extends ArtifactStreamYamlHandler<Yaml, SftpArtifactStream> {
  @Override
  public Yaml toYaml(SftpArtifactStream bean, String appId) {
    Yaml yaml = Yaml.builder().build();
    super.toYaml(yaml, bean);
    yaml.setArtifactPaths(bean.getArtifactPaths());
    return yaml;
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  protected SftpArtifactStream getNewArtifactStreamObject() {
    return new SftpArtifactStream();
  }

  @Override
  protected void toBean(SftpArtifactStream bean, ChangeContext<Yaml> changeContext, String appId) {
    super.toBean(bean, changeContext, appId);
    Yaml yaml = changeContext.getYaml();
    bean.setArtifactPaths(yaml.getArtifactPaths());
  }
}