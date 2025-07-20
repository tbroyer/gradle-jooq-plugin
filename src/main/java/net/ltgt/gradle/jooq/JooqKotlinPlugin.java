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

import java.nio.charset.StandardCharsets;
import net.ltgt.gradle.jooq.tasks.JooqCodegen;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension;
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet;

class JooqKotlinPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(JooqBasePlugin.class);

    project
        .getPluginManager()
        .withPlugin(
            "net.ltgt.jooq",
            appliedPlugin -> {
              throw new IllegalStateException(
                  "The net.ltgt.jooq and net.ltgt.jooq-kotlin plugins are incompatible, only one of them can be applied at a time");
            });

    TaskProvider<JooqCodegen> jooqTask =
        project.getTasks().named(JooqBasePlugin.JOOQ_TASK_NAME, JooqCodegen.class);
    project
        .getPluginManager()
        .withPlugin(
            "org.jetbrains.kotlin.jvm",
            appliedPlugin -> configureKotlinDefaults(project, jooqTask));
  }

  private void configureKotlinDefaults(Project project, TaskProvider<JooqCodegen> task) {
    KotlinSourceSet mainSourceSet =
        project
            .getExtensions()
            .getByType(KotlinProjectExtension.class)
            .getSourceSets()
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

    // Using project.provider to *avoid* creating a task dependency (while allowing the task to be
    // reconfigured)
    mainSourceSet.getKotlin().srcDir(project.provider(() -> task.get().getOutputDirectory().get()));

    task.configure(
        jooqCodegen -> jooqCodegen.getEncoding().convention(StandardCharsets.UTF_8.name()));
  }
}
