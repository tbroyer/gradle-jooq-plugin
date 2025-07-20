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

import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.gradle.api.JavaVersion;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public class BaseFunctionalTest {
  public static final GradleVersion testGradleVersion =
      Optional.ofNullable(System.getProperty("test.gradle-version"))
          .map(GradleVersion::version)
          .orElseGet(GradleVersion::current);

  public static final JavaVersion testJavaVersion =
      Optional.ofNullable(System.getProperty("test.java-version"))
          .map(JavaVersion::toVersion)
          .orElseGet(JavaVersion::current);
  public static final String testJavaHome =
      System.getProperty("test.java-home", System.getProperty("java.home"));

  public static String jooqVersion = computeJvmCompatibleJooqVersion();

  private static String computeJvmCompatibleJooqVersion() {
    // Based on https://www.jooq.org/download/support-matrix-jdk
    if (testJavaVersion.isCompatibleWith(JavaVersion.VERSION_21)) {
      // This should be the latest stable version, the one the plugin has been compiled against.
      return requireNonNull(System.getProperty("test.jooq-version"));
    }
    if (testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
      // 3.19 is the maximum version compatible with JDK 17
      return "3.19.24";
    }
    // 3.16 is the maximum version compatible with JDK 11
    return "3.16.23";
  }

  public static final String h2Version = requireNonNull(System.getProperty("test.h2-version"));

  @TempDir protected Path projectDir;

  protected Properties gradleProperties;

  protected final Path getSettingsFile() {
    return projectDir.resolve("settings.gradle.kts");
  }

  protected final Path getBuildFile() {
    return projectDir.resolve("build.gradle.kts");
  }

  @BeforeEach
  void setupProject() throws Exception {
    assumeCompatibleGradleAndJavaVersions();

    jooqVersion = computeJvmCompatibleJooqVersion();

    gradleProperties = new Properties();
    gradleProperties.setProperty("org.gradle.java.home", testJavaHome);
    gradleProperties.setProperty("org.gradle.configuration-cache", "true");
    Files.writeString(
        getSettingsFile(),
        """
        dependencyResolutionManagement {
            repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
            repositories {
                mavenCentral()
            }
        }
        """);
  }

  protected final BuildResult buildWithArgs(String... args) throws Exception {
    return prepareBuild(args).build();
  }

  protected final BuildResult buildWithArgsAndFail(String... args) throws Exception {
    return prepareBuild(args).buildAndFail();
  }

  protected final GradleRunner prepareBuild(String... args) throws Exception {
    try (var os = Files.newOutputStream(projectDir.resolve("gradle.properties"))) {
      gradleProperties.store(os, null);
    }
    return GradleRunner.create()
        .withGradleVersion(testGradleVersion.getVersion())
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(args)
        .forwardOutput();
  }

  // Based on https://docs.gradle.org/current/userguide/compatibility.html#java_runtime
  private static final Map<JavaVersion, GradleVersion> COMPATIBLE_GRADLE_VERSIONS =
      Map.of(
          JavaVersion.VERSION_16, GradleVersion.version("7.0"),
          JavaVersion.VERSION_17, GradleVersion.version("7.3"),
          JavaVersion.VERSION_18, GradleVersion.version("7.5"),
          JavaVersion.VERSION_19, GradleVersion.version("7.6"),
          JavaVersion.VERSION_20, GradleVersion.version("8.3"),
          JavaVersion.VERSION_21, GradleVersion.version("8.5"),
          JavaVersion.VERSION_22, GradleVersion.version("8.8"),
          JavaVersion.VERSION_23, GradleVersion.version("8.10"),
          JavaVersion.VERSION_24, GradleVersion.version("8.14"));

  private static void assumeCompatibleGradleAndJavaVersions() {
    assume()
        .that(testGradleVersion)
        .isAtLeast(
            COMPATIBLE_GRADLE_VERSIONS.getOrDefault(testJavaVersion, GradleVersion.version("6.8")));
  }
}
