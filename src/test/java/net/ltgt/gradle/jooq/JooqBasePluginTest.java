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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Correspondence;
import java.nio.file.Files;
import java.nio.file.Path;
import net.ltgt.gradle.jooq.tasks.JooqCodegen;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JooqBasePluginTest {
  private Project project;

  @BeforeEach
  void setup(@TempDir Path projectDir) throws Exception {
    project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "");
  }

  @Test
  void test() {
    project.getPluginManager().apply(JooqBasePlugin.class);

    var jooqCodegenConfiguration = project.getConfigurations().findByName("jooqCodegen");
    assertThat(jooqCodegenConfiguration).isInstanceOf(DependencyScopeConfiguration.class);
    assertThat(jooqCodegenConfiguration.getExtendsFrom()).isEmpty();

    var jooqCodegenClasspathConfiguration =
        project.getConfigurations().findByName("jooqCodegenClasspath");
    assertThat(jooqCodegenClasspathConfiguration).isInstanceOf(ResolvableConfiguration.class);
    assertThat(jooqCodegenClasspathConfiguration.getExtendsFrom())
        .containsExactly(jooqCodegenConfiguration);

    assertThat(project.getTasks().withType(JooqCodegen.class)).hasSize(1);

    var jooq = project.getTasks().withType(JooqCodegen.class).getByName("jooq");
    assertThat(jooq.getClasspath().getFrom())
        .comparingElementsUsing(
            Correspondence.transforming(
                input -> input instanceof Provider<?> provider ? provider.get() : input,
                "resolving providers"))
        .containsExactly(jooqCodegenClasspathConfiguration);
    assertThat(jooq.getUrl().isPresent()).isFalse();
    assertThat(jooq.getUser().isPresent()).isFalse();
    assertThat(jooq.getPassword().isPresent()).isFalse();
    assertThat(jooq.getEncoding().isPresent()).isFalse();
    assertThat(jooq.getConfigurationFile().isPresent()).isFalse();
    assertThat(jooq.getOutputDirectory().isPresent()).isFalse();
    assertThat(jooq.getJavaLauncher().isPresent()).isFalse();
  }

  @Test
  void javaBaseProject() {
    project.getPluginManager().apply(JooqBasePlugin.class);
    project.getPluginManager().apply(JavaBasePlugin.class);

    var jooq = project.getTasks().withType(JooqCodegen.class).getByName("jooq");
    assertThat(jooq.getUrl().isPresent()).isFalse();
    assertThat(jooq.getUser().isPresent()).isFalse();
    assertThat(jooq.getPassword().isPresent()).isFalse();
    assertThat(jooq.getEncoding().isPresent()).isFalse();
    assertThat(jooq.getConfigurationFile().isPresent()).isFalse();
    assertThat(jooq.getOutputDirectory().isPresent()).isFalse();
    // XXX: test that it's the "default" toolchain?
    assertThat(jooq.getJavaLauncher().isPresent()).isTrue();
  }

  @Test
  void javaProject() {
    project.getPluginManager().apply(JooqBasePlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);

    var jooq = project.getTasks().withType(JooqCodegen.class).getByName("jooq");
    assertThat(jooq.getUrl().isPresent()).isFalse();
    assertThat(jooq.getUser().isPresent()).isFalse();
    assertThat(jooq.getPassword().isPresent()).isFalse();
    assertThat(jooq.getEncoding().isPresent()).isFalse();
    assertThat(jooq.getConfigurationFile().getAsFile().getOrNull())
        .isEqualTo(project.file("src/jooq-codegen.xml"));
    assertThat(jooq.getOutputDirectory().getAsFile().getOrNull())
        .isEqualTo(project.file("src/main/jooq"));
    // XXX: test that it's the "default" toolchain?
    assertThat(jooq.getJavaLauncher().isPresent()).isTrue();

    var compileJava = project.getTasks().withType(JavaCompile.class).getByName("compileJava");
    assertThat(jooq.getTaskDependencies().getDependencies(jooq)).doesNotContain(compileJava);

    compileJava.getOptions().setEncoding("UTF-8");

    // Encoding is NOT linked to the compileJava task's encoding
    assertThat(jooq.getEncoding().isPresent()).isFalse();
  }
}
