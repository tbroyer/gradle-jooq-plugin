# gradle-jooq-plugin

This plugin is an alternative to [jOOQ](https://www.jooq.org/)'s [own Gradle plugin](https://www.jooq.org/doc/latest/manual/code-generation/codegen-execution/codegen-gradle/) and [Etienne Studer's plugin](https://plugins.gradle.org/plugin/nu.studer.jooq), that both put the jOOQ generator's configuration in the Gradle build script, which makes it less readable in my opinion.

It's tailored for my own needs:

 * Code generation is run against a _real_ database, generally Postgresql, but possibly Oracle or MySQL
 * Generated code is committed to the source tree
 * jOOQ generator's configuration is managed in an XML file

For years, I used a `JavaExec` task in my projects (or more recently a custom task using `ExecOperations`) to execute the jOOQ generator; this plugin aims at packaging this practice to make it easily reusable (and more maintainable for developers not well-versed into Gradle configuration), and improve it along the way.

<details>
<summary>Example (legacy) configuration</summary>

```kotlin
val jooqCodegen by configurations.creating

dependencies {
    jooqCodegen("org.jooq:jooq-codegen:$jooqVersion")
    jooqCodegen("org.postgresql:postgresql:$postgresqlVersion")
}

val jooqOutputDir = file("src/main/jooq")

tasks {
    register<JavaExec>("jooq") {
        doFirst {
            project.delete(jooqOutputDir.listFiles())
        }

        classpath = jooqCodegen
        main = "org.jooq.codegen.GenerationTool"
        // The XML file uses ${...} placeholders for those
        systemProperties = mapOf(
            "db.url" to dbUrl,
            "db.user" to dbUser,
            "db.password" to dbPassword,
            "outputdir" to jooqOutputDir.path
        )
        args(file("src/jooq-codegen.xml"))
    }
}

sourceSets {
    main {
        java {
            srcDir(jooqOutputDir)
        }
    }
}
idea {
    module {
        generatedSourceDirs.add(jooqOutputDir)
    }
}
```

</details>

## Compatibility

 * Gradle >= 8.4 (including Gradle 9)
 * Java >= 8 (though it's currently only tested with Java >= 11)
 * jOOQ >= 3.16 (but should be compatible with earlier versions, as long as jOOQ is backwards compatible), built and tested against jOOQ Open Source Edition but should be compatible with commercial edtions.

## Usage

1. Apply the plugin:

   ```kotlin
   plugins {
       id("net.ltgt.jooq") version "…"
   }
   ```

   If you're generating Kotlin rather than Java, then use the `net.ltgt.jooq-kotlin` plugin instead ([see below](#kotlin)).

2. Add dependencies to jOOQ generator and your JDBC driver:

   ```kotlin
   dependencies {
       jooqCodegen("org.jooq:jooq-codegen:$jooqVersion")
       jooqCodegen("org.postgresql:postgresql:$postgresqlVersion")
   }
   ```

3. Create the `src/jooq-codegen.xml` to configure the jOOQ generator.

   The only required configuration is `generator.target.packageName` (this will actually only warn, not fail, if not configured).

   Some elements should not be configured as they will always be overwritten by the plugin: `jdbc.url`, `jdbc.user`, `jdbc.password`, `generator.target.clean`, `generator.target.encoding`, and `generator.target.directory`.

4. Configure the `jooq` task (see [below](#configuration "Configuration") for the full list of properties)

   At a minimum you need to configure the `url` property, as well as the `user` and `password` properties if your database requires authentication (which it should):

   ```kotlin
   jooq {
     url = dbUrl
     user = dbUser
     password = dbPassword
   }
   ```

   But you can also pass those values at runtime when executing the task (this will overwrite any configuration in the build script):

   ```shell
   ./gradlew jooq --url=... --user=... --password=...
   ```

## The Kotlin plugin <a name="kotlin"></a>

If you're [generating Kotlin](https://www.jooq.org/doc/latest/manual/code-generation/codegen-output-languages/kotlingenerator/), use the `net.ltgt.jooq-kotlin` plugin instead of `net.ltgt.jooq`.

This plugin works the same but configures the build to compile the `src/main/jooq` directory with the `compileKotlin` task rather than the `compileJava` task.

## Recipes

See [the wiki](https://github.com/tbroyer/gradle-jooq-plugin/wiki) for recipes of using this plugin with other plugins such as Flyway or Spotless, or checking that generated code is up-to-date in CI builds.

## Configuration

 Property | Default value | Command-line option | Description
:---------|:--------------|:--------------------|:-----------
`url`                | | `--url` | The jdbc url to use to connect to the database.
`user`               | | `--user` | The user to use to connect to the database.
`password`           | | `--password` | The password to use to connect to the database.
`configurationFile`  | `src/jooq-codegen.xml`, if the `java` plugin is applied | | The configuration file to use
`encoding`           | the `options.encoding` value of the `compileJava` task, if the `java` plugin is applied; `UTF-8` otherwise (and as a fallback) | | The encoding of the generated files
`outputDirectory`    | `src/main/jooq`, if the `java` plugin is applied | | The directory where jOOQ will generate the code
