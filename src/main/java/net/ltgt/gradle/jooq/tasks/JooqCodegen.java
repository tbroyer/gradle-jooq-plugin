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

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

@UntrackedTask(because = "Depends on the database")
public abstract class JooqCodegen extends DefaultTask {

  @Inject
  protected abstract WorkerExecutor getWorkerExecutor();

  @Inject
  protected abstract FileSystemOperations getFileSystemOperations();

  /**
   * The classpath for executing the jOOQ code generator.
   *
   * <p>Defaults to the {@code jooqCodegenClasspath} configuration, itself extending the {@code
   * jooqCodegen} configuration.
   */
  @Classpath
  public abstract ConfigurableFileCollection getClasspath();

  /**
   * The jdbc url to use to connect to the database.
   *
   * <p>This will override any {@code jdbc.url} set in the {@linkplain #getConfigurationFile()
   * configuration file}.
   */
  @Input
  @Option(option = "url", description = "Configures the database JDBC URL")
  public abstract Property<String> getUrl();

  /**
   * The user to use to connect to the database.
   *
   * <p>This will override any {@code jdbc.user} set in the {@linkplain #getConfigurationFile()
   * configuration file}.
   */
  @Input
  @Optional
  @Option(option = "user", description = "Configures the database user")
  public abstract Property<String> getUser();

  /**
   * The password to use to connect to the database.
   *
   * <p>This will override any {@code jdbc.password} set in the {@linkplain #getConfigurationFile()
   * configuration file}.
   */
  @Input
  @Optional
  @Option(option = "password", description = "Configures the database password")
  public abstract Property<String> getPassword();

  /**
   * The encoding of the generated files.
   *
   * <p>This will override any {@code generator.target.encoding} set in the {@linkplain
   * #getConfigurationFile() configuration file}.
   *
   * <p>When the {@code java} plugin is applied, it defaults to the {@code compileJava} task's
   * {@link CompileOptions#getEncoding() options.encoding}.
   *
   * <p>When the {@code kotlin("jvm")} plugin is applied, it defaults to UTF-8.
   *
   * <p>If not configured, it will default to jOOQ's default encoding, which is UTF-8.
   */
  @Input
  @Optional
  public abstract Property<String> getEncoding();

  /**
   * The jOOQ code generation configuration file.
   *
   * <p>When the {@code java} plugin is applied, it defaults to {@code src/jooq-codegen.xml}.
   */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getConfigurationFile();

  /**
   * The directory where jOOQ will generate the code.
   *
   * <p>This will override any {@code generator.target.directory} set in the {@linkplain
   * #getConfigurationFile() configuration file}.
   *
   * <p>When the {@code java} plugin is applied, it defaults to {@code src/main/jooq}.
   */
  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  /**
   * Configures the java executable to be used to run the jOOQ code generator.
   *
   * <p>If it is the same as the one used to run Gradle, then the jOOQ code generator will run
   * <i>in-process</i>.
   *
   * <p>When the {@code java-base} plugin is applied, it defaults to using the toolchain {@link
   * JavaPluginExtension#getToolchain() configured at the project level}.
   */
  @Nested
  @Optional
  public abstract Property<JavaLauncher> getJavaLauncher();

  @TaskAction
  void run() {
    getFileSystemOperations().delete(spec -> spec.delete(getOutputDirectory()));

    final WorkQueue workQueue;
    JavaLauncher javaLauncher = getJavaLauncher().getOrNull();
    if (javaLauncher == null || javaLauncher.getMetadata().isCurrentJvm()) {
      workQueue =
          getWorkerExecutor()
              .classLoaderIsolation(spec -> spec.getClasspath().from(getClasspath()));
    } else {
      workQueue =
          getWorkerExecutor()
              .processIsolation(
                  spec -> {
                    spec.getClasspath().from(getClasspath());
                    spec.forkOptions(
                        forkOptions -> forkOptions.setExecutable(javaLauncher.getExecutablePath()));
                  });
    }
    workQueue.submit(
        JooqCodegenWorkAction.class,
        params -> {
          params.getUrl().set(getUrl());
          params.getUser().set(getUser());
          params.getPassword().set(getPassword());
          params.getConfigurationFile().set(getConfigurationFile());
          params.getOutputDirectory().set(getOutputDirectory());
          params.getEncoding().set(getEncoding());
        });
  }
}
