/*
 * Copyright © 2025 Thomas Broyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ltgt.gradle.jooq.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.jooq.tools.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class JooqCodegenWorkAction implements WorkAction<JooqCodegenWorkAction.Parameters> {
  interface Parameters extends WorkParameters {

    Property<String> getUrl();

    Property<String> getUser();

    Property<String> getPassword();

    RegularFileProperty getConfigurationFile();

    DirectoryProperty getOutputDirectory();

    Property<String> getEncoding();
  }

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  public JooqCodegenWorkAction() {}

  @Override
  public void execute() {
    Configuration configuration;
    try (InputStream is =
        Files.newInputStream(getParameters().getConfigurationFile().get().getAsFile().toPath())) {
      configuration = GenerationTool.load(is);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    configureJdbc(configuration);
    configureTarget(configuration);
    try {
      GenerationTool.generate(configuration);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void configureJdbc(Configuration configuration) {
    Jdbc jdbc = configuration.getJdbc();
    if (jdbc == null) {
      jdbc = new Jdbc();
      configuration.setJdbc(jdbc);
    }

    set(jdbc.getUrl(), jdbc::setUrl, "jdbc.url", getParameters().getUrl().get());
    set(jdbc.getUser(), jdbc::setUser, "jdbc.user", getParameters().getUser().getOrNull());
    set(
        jdbc.getPassword(),
        jdbc::setPassword,
        "jdbc.password",
        getParameters().getPassword().getOrNull());
  }

  private void configureTarget(Configuration configuration) {
    Target target = configuration.getGenerator().getTarget();
    if (target == null) {
      target = new Target();
      configuration.getGenerator().setTarget(target);
    }

    if (Objects.equals(target.isClean(), false)) {
      logger.warn(
          "Configuration file ({}) has a configured generator.target.clean with value false that will be ignored (the plugin always clear the output directory)",
          getParameters().getConfigurationFile().get().getAsFile().getPath());
    }
    if (Objects.equals(target.getPackageName(), GenerationTool.DEFAULT_TARGET_PACKAGENAME)) {
      logger.warn(
          "Configuration file ({}) does not configure generator.target.packageName; this is likely an error. Code will be generated in package {}",
          getParameters().getConfigurationFile().get().getAsFile().getPath(),
          GenerationTool.DEFAULT_TARGET_PACKAGENAME);
    }

    if (Objects.equals(target.getDirectory(), GenerationTool.DEFAULT_TARGET_DIRECTORY)) {
      // To avoid a warning in the following call to set()
      target.setDirectory(null);
    }
    set(
        target.getDirectory(),
        target::setDirectory,
        "generator.target.directory",
        getParameters().getOutputDirectory().get().getAsFile().getPath());
    set(
        target.getEncoding(),
        target::setEncoding,
        "generator.target.encoding",
        getParameters().getEncoding().getOrElse(GenerationTool.DEFAULT_TARGET_ENCODING));
  }

  private void set(
      String configurationFileValue,
      Consumer<String> set,
      String property,
      @Nullable String value) {
    if (!StringUtils.isBlank(configurationFileValue) && !configurationFileValue.equals(value)) {
      logger.warn(
          "Configuration file ({}) has a configured {} ({}) that will be ignored (overridden by {})",
          getParameters().getConfigurationFile().get().getAsFile().getPath(),
          property,
          configurationFileValue,
          value);
    }
    set.accept(value);
  }
}
