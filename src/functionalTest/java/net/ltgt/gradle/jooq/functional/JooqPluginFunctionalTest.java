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
package net.ltgt.gradle.jooq.functional;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JooqPluginFunctionalTest extends BaseFunctionalTest {

  @RegisterExtension DatabaseExtension database = new DatabaseExtension(() -> projectDir);

  @Test
  void test() throws Exception {
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        plugins {
            id("net.ltgt.jooq")
            java
        }

        dependencies {
          implementation("org.jooq:jooq:%1$s")

          jooqCodegen("org.jooq:jooq-codegen:%1$s")
          jooqCodegen("com.h2database:h2:%2$s")
        }
        """
            .formatted(jooqVersion, h2Version));
    Files.createDirectory(projectDir.resolve("src"));
    Files.writeString(
        projectDir.resolve("src/jooq-codegen.xml"),
        // language=xml
        """
        <configuration>
          <generator>
            <database>
              <inputSchema>TEST_SCHEMA</inputSchema>
            </database>
            <target>
              <packageName>test.jooq</packageName>
            </target>
          </generator>
        </configuration>
        """);

    database.createDb();

    var result = buildWithArgs("jooq", "--url", database.getURL());
    assertThat(requireNonNull(result.task(":jooq")).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput())
        .doesNotContainMatch(
            "Configuration file \\(.*?\\) has a configured [\\w.]+ \\(.*?\\) that will be ignored \\(overridden by .*?\\)");

    var outputDir = projectDir.resolve("src/main/jooq");
    try (var generatedFiles = Files.walk(outputDir)) {
      assertThat(generatedFiles.filter(path -> !Files.isDirectory(path)))
          .containsExactly(
              outputDir.resolve("test/jooq/DefaultCatalog.java"),
              outputDir.resolve("test/jooq/TestSchema.java"),
              outputDir.resolve("test/jooq/Tables.java"),
              outputDir.resolve("test/jooq/tables/TestTable.java"),
              outputDir.resolve("test/jooq/tables/records/TestTableRecord.java"));
    }

    // Task is never up to date
    result = buildWithArgs("jooq", "--url", database.getURL());
    assertThat(requireNonNull(result.task(":jooq")).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

    // Output is cleared even if the configuration file has generator.target.clean=false
    Files.writeString(
        projectDir.resolve("src/jooq-codegen.xml"),
        // language=xml
        """
        <configuration>
          <generator>
            <database>
              <inputSchema>TEST_SCHEMA</inputSchema>
            </database>
            <target>
              <packageName>test.db.jooq</packageName>
              <clean>false</clean>
            </target>
          </generator>
        </configuration>
        """);
    result = buildWithArgs("jooq", "--url", database.getURL());
    assertThat(requireNonNull(result.task(":jooq")).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput())
        .contains(
            "has a configured generator.target.clean with value false that will be ignored (the plugin always clear the output directory)");
  }
}
