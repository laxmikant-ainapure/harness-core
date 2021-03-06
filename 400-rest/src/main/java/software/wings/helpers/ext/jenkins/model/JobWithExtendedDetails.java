package software.wings.helpers.ext.jenkins.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class JobWithExtendedDetails extends JobWithDetails {
  @JsonProperty("property") List<JobProperty> properties;
  private String url;

  @Override
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
