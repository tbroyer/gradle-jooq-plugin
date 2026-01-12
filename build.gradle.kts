import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.nullaway)
}

group = "net.ltgt.gradle"

dependencies {
    errorprone(libs.errorprone)
    errorprone(libs.nullaway)

    compileOnly(embeddedKotlin("gradle-plugin"))
    compileOnly(libs.jooq.codegen)
}

nullaway {
    onlyNullMarked = true
}
tasks {
    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all,-options"))
        options.errorprone {
            nullaway {
                isJSpecifyMode = true
            }
        }
    }
    compileJava {
        options.release = 8
        options.errorprone {
            // Gradle uses javax.inject annotations in a special way.
            disable("InjectOnConstructorOfAbstractClass")
            disable("JavaxInjectOnAbstractMethod")
        }
    }
    javadoc {
        (options as CoreJavadocOptions).addBooleanOption("Xdoclint:all,-missing", true)
    }
    check {
        dependsOn(testing.suites)
    }
}

testing {
    suites {
        withType(JvmTestSuite::class).configureEach {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                implementation(libs.truth)
            }
            targets.configureEach {
                testTask {
                    testLogging {
                        showExceptions = true
                        showStackTraces = true
                        exceptionFormat = TestExceptionFormat.FULL
                    }
                }
            }
        }

        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation(project.dependencies.embeddedKotlin("gradle-plugin") as String)
            }
        }

        val functionalTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(gradleTestKit())
                implementation(libs.h2)
            }

            // make plugin-under-test-metadata.properties accessible to TestKit
            gradlePlugin.testSourceSet(sources)

            targets.configureEach {
                testTask {
                    shouldRunAfter(test)

                    val testJavaToolchain = project.findProperty("test.java-toolchain")
                    testJavaToolchain?.also {
                        val launcher =
                            project.javaToolchains.launcherFor {
                                languageVersion.set(JavaLanguageVersion.of(testJavaToolchain.toString()))
                            }
                        val metadata = launcher.get().metadata
                        systemProperty("test.java-version", metadata.languageVersion.asInt())
                        systemProperty("test.java-home", metadata.installationPath.asFile.canonicalPath)
                    }

                    val testGradleVersion = project.findProperty("test.gradle-version")
                    testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }

                    systemProperty("test.jooq-version", libs.versions.jooq.get())
                    systemProperty("test.h2-version", libs.versions.h2.get())
                }
            }
        }
    }
}

gradlePlugin {
    website.set("https://github.com/tbroyer/gradle-jooq-plugin")
    vcsUrl.set("https://github.com/tbroyer/gradle-jooq-plugin")
    plugins {
        register("jooq") {
            id = "net.ltgt.jooq"
            implementationClass = "net.ltgt.gradle.jooq.JooqPlugin"
            displayName = "Gradle plugin for jOOQ code generation (for Java projects)"
            description = "Adds a Gradle task to generate Java code from a database schema using jOOQ"
            tags.addAll("jooq", "jooq-codegen")
        }
        register("jooq-kotlin") {
            id = "net.ltgt.jooq-kotlin"
            implementationClass = "net.ltgt.gradle.jooq.JooqKotlinPlugin"
            displayName = "Gradle plugin for jOOQ code generation (for Kotlin projects)"
            description = "Adds a Gradle task to generate Kotlin code from a database schema using jOOQ"
            tags.addAll("jooq", "jooq-codegen")
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Gradle plugin for jOOQ code generation")
            description.set("Adds Gradle tasks to generate code from a database schema using jOOQ")
            url.set("https://github.com/tbroyer/gradle-jooq-plugin")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    name.set("Thomas Broyer")
                    email.set("t.broyer@ltgt.net")
                }
            }
            scm {
                connection.set("https://github.com/tbroyer/gradle-jooq-plugin.git")
                developerConnection.set("scm:git:ssh://github.com:tbroyer/gradle-jooq-plugin.git")
                url.set("https://github.com/tbroyer/gradle-jooq-plugin")
            }
        }
    }
}

spotless {
    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
    }
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        licenseHeaderFile("LICENSE.header")
    }
}
