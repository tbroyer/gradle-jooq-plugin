rootProject.name = "gradle-jooq-plugin"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        gradlePluginPortal()
    }
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
    components {
        withModule("com.google.truth:truth") {
            withVariant("compile") {
                withDependencies {
                    // junit is actually a runtime-only dependency
                    // See https://github.com/google/truth/issues/333
                    removeAll { it.group == "junit" }
                }
            }
        }
    }
}
