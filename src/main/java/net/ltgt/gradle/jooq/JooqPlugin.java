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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

class JooqPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(JooqBasePlugin.class);

    TaskProvider<JooqCodegen> jooqTask =
        project.getTasks().named(JooqBasePlugin.JOOQ_TASK_NAME, JooqCodegen.class);
    project
        .getPluginManager()
        .withPlugin("java", appliedPlugin -> configureJavaDefaults(project, jooqTask));
  }

  private void configureJavaDefaults(Project project, TaskProvider<JooqCodegen> task) {
    SourceSet mainSourceSet =
        project
            .getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName(SourceSet.MAIN_SOURCE_SET_NAME);

    // Using project.provider to *avoid* creating a task dependency (while allowing the task to be
    // reconfigured)
    mainSourceSet.getJava().srcDir(project.provider(() -> task.get().getOutputDirectory().get()));

    task.configure(
        jooqCodegen ->
            jooqCodegen
                .getEncoding()
                .convention(
                    // Using project.provider to avoid creating a task dependency
                    project.provider(
                        () ->
                            project
                                .getTasks()
                                .named(mainSourceSet.getCompileJavaTaskName(), JavaCompile.class)
                                .get()
                                .getOptions()
                                .getEncoding())));
  }
}
