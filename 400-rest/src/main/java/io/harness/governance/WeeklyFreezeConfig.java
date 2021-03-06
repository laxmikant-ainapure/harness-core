package io.harness.governance;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;

import software.wings.resources.stats.model.WeeklyRange;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.codehaus.jackson.annotate.JsonCreator;

@Getter
@ToString
@EqualsAndHashCode
@ParametersAreNonnullByDefault
@TargetModule(Module._980_COMMONS)
public class WeeklyFreezeConfig extends GovernanceFreezeConfig {
  // if freezeForAllApps=true, ignore appIds
  private WeeklyRange weeklyRange;

  public WeeklyRange getWeeklyRange() {
    return weeklyRange;
  }

  @JsonCreator
  public WeeklyFreezeConfig(@JsonProperty("freezeForAllApps") boolean freezeForAllApps,
      @JsonProperty("appIds") List<String> appIds,
      @JsonProperty("environmentTypes") List<EnvironmentType> environmentTypes,
      @JsonProperty("weeklyRange") WeeklyRange weeklyRange, @JsonProperty("name") String name,
      @JsonProperty("description") String description, @JsonProperty("applicable") boolean applicable,
      @JsonProperty("appSelections") List<ApplicationFilter> appSelections,
      @JsonProperty("userGroups") List<String> userGroups, @JsonProperty("uuid") String uuid) {
    super(freezeForAllApps, appIds, environmentTypes, name, description, applicable, appSelections, userGroups, uuid);
    this.weeklyRange = weeklyRange;
  }

  @Override
  public long fetchEndTime() {
    // TODO: return correct end time
    return 0;
  }
}
