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

import java.nio.file.Files;
import java.nio.file.Path;
import net.ltgt.gradle.jooq.tasks.JooqCodegen;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JooqPluginTest {
  private Project project;

  @BeforeEach
  void setup(@TempDir Path projectDir) throws Exception {
    project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "");
  }

  @Test
  void test() {
    project.getPluginManager().apply(JooqPlugin.class);

    assertThat(project.getPluginManager().hasPlugin("java")).isFalse();

    project.getPluginManager().apply(JavaPlugin.class);

    var outputDir = project.file("src/main/jooq");
    var mainSourceSet =
        project
            .getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    assertThat(mainSourceSet.getJava().getSourceDirectories()).contains(outputDir);

    var jooq = project.getTasks().withType(JooqCodegen.class).getByName("jooq");
    assertThat(jooq.getUrl().isPresent()).isFalse();
    assertThat(jooq.getUser().isPresent()).isFalse();
    assertThat(jooq.getPassword().isPresent()).isFalse();
    assertThat(jooq.getEncoding().isPresent()).isFalse();
    assertThat(jooq.getConfigurationFile().getAsFile().getOrNull())
        .isEqualTo(project.file("src/jooq-codegen.xml"));
    assertThat(jooq.getOutputDirectory().getAsFile().getOrNull()).isEqualTo(outputDir);

    var compileJava = project.getTasks().withType(JavaCompile.class).getByName("compileJava");
    assertThat(jooq.getTaskDependencies().getDependencies(jooq)).doesNotContain(compileJava);
    assertThat(compileJava.getTaskDependencies().getDependencies(compileJava)).doesNotContain(jooq);

    compileJava.getOptions().setEncoding("UTF-8");

    // Encoding is NOT linked to the compileJava task's encoding
    assertThat(jooq.getEncoding().getOrNull()).isEqualTo("UTF-8");
  }

  @Test
  void reconfigureOutputDir() {
    project.getPluginManager().apply(JooqPlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);

    var outputDir = project.file("src/main/jooqCodegen");

    var jooq = project.getTasks().withType(JooqCodegen.class).getByName("jooq");
    jooq.getOutputDirectory().set(outputDir);

    var mainSourceSet =
        project
            .getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    assertThat(mainSourceSet.getJava().getSourceDirectories()).contains(outputDir);
    assertThat(mainSourceSet.getJava().getSourceDirectories())
        .doesNotContain(project.file("src/main/jooq"));
  }
}
