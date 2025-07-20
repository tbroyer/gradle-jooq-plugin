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
package net.ltgt.gradle.jooq;

import net.ltgt.gradle.jooq.tasks.JooqCodegen;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugins.ide.idea.model.IdeaModel;

class JooqBasePlugin implements Plugin<Project> {

  static final String JOOQ_TASK_NAME = "jooq";

  @Override
  public void apply(Project project) {
    @SuppressWarnings("UnstableApiUsage")
    NamedDomainObjectProvider<ResolvableConfiguration> jooqCodegenClasspathConfiguration =
        registerConfigurations(project);

    TaskProvider<JooqCodegen> jooqTask = registerTask(project, jooqCodegenClasspathConfiguration);

    project
        .getPluginManager()
        .withPlugin("java-base", appliedPlugin -> configureToolchain(project));

    project
        .getPluginManager()
        .withPlugin("java", appliedPlugin -> configureMainSourceSetDefaults(project, jooqTask));
  }

  @SuppressWarnings("UnstableApiUsage")
  private NamedDomainObjectProvider<ResolvableConfiguration> registerConfigurations(
      Project project) {
    NamedDomainObjectProvider<DependencyScopeConfiguration> jooqCodegenConfiguration =
        project.getConfigurations().dependencyScope("jooqCodegen");
    return project
        .getConfigurations()
        .resolvable(
            "jooqCodegenClasspath",
            configuration -> configuration.extendsFrom(jooqCodegenConfiguration.get()));
  }

  private TaskProvider<JooqCodegen> registerTask(
      Project project,
      @SuppressWarnings("UnstableApiUsage")
          NamedDomainObjectProvider<ResolvableConfiguration> jooqCodegenClasspathConfiguration) {
    return project
        .getTasks()
        .register(
            JOOQ_TASK_NAME,
            JooqCodegen.class,
            jooqCodegen -> jooqCodegen.getClasspath().from(jooqCodegenClasspathConfiguration));
  }

  private void configureToolchain(Project project) {
    Provider<JavaLauncher> javaLauncher =
        project
            .getExtensions()
            .getByType(JavaToolchainService.class)
            .launcherFor(
                project.getExtensions().getByType(JavaPluginExtension.class).getToolchain());
    project
        .getTasks()
        .withType(JooqCodegen.class)
        .configureEach(jooqCodegen -> jooqCodegen.getJavaLauncher().convention(javaLauncher));
  }

  private void configureMainSourceSetDefaults(Project project, TaskProvider<JooqCodegen> task) {
    Directory outputDirectory = project.getLayout().getProjectDirectory().dir("src/main/jooq");

    task.configure(
        jooqCodegen -> {
          jooqCodegen
              .getConfigurationFile()
              .convention(project.getLayout().getProjectDirectory().file("src/jooq-codegen.xml"));
          jooqCodegen.getOutputDirectory().convention(outputDirectory);
        });

    project
        .getPluginManager()
        .withPlugin(
            "idea",
            appliedPlugin -> {
              IdeaModel idea = project.getExtensions().getByType(IdeaModel.class);
              // The API isn't lazy, so to avoid using afterEvaluate and realizing the task, we just
              // configure the default output directory.
              idea.getModule().getGeneratedSourceDirs().add(outputDirectory.getAsFile());
            });
  }
}
