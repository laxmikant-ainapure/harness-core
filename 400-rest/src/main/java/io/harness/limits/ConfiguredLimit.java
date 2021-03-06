package io.harness.limits;

import io.harness.annotation.HarnessEntity;
import io.harness.limits.lib.Limit;
import io.harness.mongo.index.Field;
import io.harness.mongo.index.NgUniqueIndex;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.validation.Update;

import com.github.reinert.jjschema.SchemaIgnore;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Getter
@EqualsAndHashCode(exclude = "id", callSuper = false)
@Entity(value = "allowedLimits", noClassnameStored = true)
@NgUniqueIndex(name = "key_idx", fields = { @Field("key")
                                            , @Field("accountId") })
@FieldNameConstants(innerTypeName = "ConfiguredLimitKeys")
@HarnessEntity(exportable = true)
public class ConfiguredLimit<T extends Limit> implements PersistentEntity, AccountAccess {
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private ObjectId id;

  private String accountId;
  private String key;
  private T limit;

  public ConfiguredLimit(String accountId, T limit, ActionType actionType) {
    this.accountId = accountId;
    this.key = actionType.toString();
    this.limit = limit;
  }

  public T getLimit() {
    return limit;
  }

  // morphia expects an no-args constructor
  private ConfiguredLimit() {}
}
