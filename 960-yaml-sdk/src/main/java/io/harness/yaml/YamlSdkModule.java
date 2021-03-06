package io.harness.yaml;

import io.harness.exception.GeneralException;
import io.harness.testing.TestExecution;
import io.harness.yaml.schema.AbstractSchemaChecker;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.snippets.AbstractSnippetChecker;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YamlSdkModule extends AbstractModule {
  private static volatile YamlSdkModule defaultInstance;

  public static YamlSdkModule getInstance() {
    if (defaultInstance == null) {
      defaultInstance = new YamlSdkModule();
    }
    return defaultInstance;
  }

  private YamlSdkModule() {}

  private void testSchemas(Provider<List<YamlSchemaRootClass>> yamlSchemaRootClasses) {
    final AbstractSchemaChecker abstractSchemaChecker = new AbstractSchemaChecker();
    try {
      abstractSchemaChecker.schemaTests(yamlSchemaRootClasses.get());
    } catch (Exception e) {
      throw new GeneralException(e.getLocalizedMessage());
    }
  }

  private void testSnippets(Provider<List<YamlSchemaRootClass>> yamlSchemaRootClasses) {
    final AbstractSnippetChecker abstractSnippetChecker = new AbstractSnippetChecker(yamlSchemaRootClasses.get());
    try {
      abstractSnippetChecker.snippetTests();
    } catch (Exception e) {
      throw new GeneralException(e.getLocalizedMessage());
    }
  }

  @Override
  protected void configure() {
    MapBinder<String, TestExecution> testExecutionMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TestExecution.class);
    if (!binder().currentStage().name().equals("TOOL")) {
      Provider<List<YamlSchemaRootClass>> providerClasses =
          getProvider(Key.get(new TypeLiteral<List<YamlSchemaRootClass>>() {}));
      // todo(abhinav): add auto discovery of schema classes if it becomes chaotic.
      //      testExecutionMapBinder.addBinding("YamlSchema test registration")
      //              .toInstance(() -> testAutomaticSearch(providerClasses));

      testExecutionMapBinder.addBinding("Yaml Schema test registrars").toInstance(() -> testSchemas(providerClasses));
      testExecutionMapBinder.addBinding("Yaml Snippet test registrars").toInstance(() -> testSnippets(providerClasses));
    }
  }
}
