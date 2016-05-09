package software.wings.helpers.ext;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.junit.Test;
import software.wings.WingsBaseUnitTest;
import software.wings.beans.JenkinsArtifactSource;
import software.wings.beans.Release;
import software.wings.utils.JsonUtils;

import javax.inject.Inject;

public class JsonTest extends WingsBaseUnitTest {
  @Inject private JsonUtils jsonUtils;

  @Test
  public void testJson() {
    BaseA baseA = new BaseA();
    String jsona = jsonUtils.asJson(baseA);
    System.out.println(jsona);
    BaseB baseB = new BaseB();
    String jsonb = jsonUtils.asJson(baseB);
    System.out.println(jsonb);

    Base baseA2 = jsonUtils.asObject(jsona, Base.class);
    Base baseB2 = jsonUtils.asObject(jsonb, Base.class);

    System.out.println(baseB2.getBaseType());
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "baseType")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = BaseA.class, name = "A")
    , @JsonSubTypes.Type(value = BaseB.class, name = "B"), @JsonSubTypes.Type(value = BaseC.class, name = "C")
  })
  public static class Base {
    private BaseType baseType;

    public BaseType getBaseType() {
      return baseType;
    }

    public void setBaseType(BaseType baseType) {
      this.baseType = baseType;
    }

    public enum BaseType { A, B, C }
  }

  public static class BaseA extends Base {
    private String name = BaseA.class.getName();

    public BaseA() {
      super();
      setBaseType(BaseType.A);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class BaseB extends Base {
    private String name = BaseB.class.getName();

    public BaseB() {
      super();
      setBaseType(BaseType.B);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class BaseC extends Base {
    private String name = BaseC.class.getName();

    public BaseC() {
      super();
      setBaseType(BaseType.C);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
