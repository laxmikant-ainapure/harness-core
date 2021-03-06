package software.wings.beans.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.artifact.ArtifactStreamType.SFTP;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@JsonTypeName("SFTP")
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpArtifactStream extends ArtifactStream {
  @NotEmpty private List<String> artifactPaths;

  public SftpArtifactStream() {
    super(SFTP.name());
    super.setMetadataOnly(true);
  }

  @Builder
  public SftpArtifactStream(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, String sourceName, String settingId,
      String name, boolean autoPopulate, String serviceId, List<String> artifactPaths, String accountId,
      Set<String> keywords, boolean sample) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath, SFTP.name(), sourceName,
        settingId, name, autoPopulate, serviceId, true, accountId, keywords, sample);
    this.artifactPaths = artifactPaths;
  }

  @Override
  public String fetchArtifactDisplayName(String buildNum) {
    return isBlank(getSourceName())
        ? format("%s_%s_%s", getSourceName(), buildNum, new SimpleDateFormat(dateFormat).format(new Date()))
        : format("%s_%s", buildNum, new SimpleDateFormat(dateFormat).format(new Date()));
  }

  @Override
  public ArtifactStreamAttributes fetchArtifactStreamAttributes() {
    return ArtifactStreamAttributes.builder()
        .artifactStreamType(getArtifactStreamType())
        .artifactName(artifactPaths == null ? "" : artifactPaths.get(0))
        .build();
  }

  @Override
  public String generateSourceName() {
    return getArtifactPaths().stream().map(artifactPath -> artifactPath + "").collect(joining(""));
  }

  @Override
  public boolean checkIfStreamParameterized() {
    if (isNotEmpty(artifactPaths)) {
      return validateParameters(artifactPaths.get(0));
    }
    return false;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends ArtifactStream.Yaml {
    private List<String> artifactPaths;

    @lombok.Builder
    public Yaml(String harnessApiVersion, String serverName, List<String> artifactPaths) {
      super(SFTP.name(), harnessApiVersion, serverName);
      this.artifactPaths = artifactPaths;
    }
  }
}
