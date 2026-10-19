// Shared library of ready-made ArchUnit rules for Clean / Hexagonal /
// Screaming / Framework-Isolation architectures.
//
// Published independently from the Gradle plugin so a team can:
//   - adopt the plugin only (policy-file-driven, enforced at `check`)
//   - adopt these rules only (ArchUnit-driven, enforced in unit tests)
//   - use both — the two gates are complementary, not duplicative.
//
// Published as:
//   maven coords: io.archguard.gradle:archunit-rules:<version>

plugins {
    `java-library`
    `maven-publish`
}

description = "Composable ArchUnit rule factories for Clean, Hexagonal, Screaming and Framework-Isolation architectures."

dependencies {
    // api{} because consumers write their own ArchUnit tests and need to see
    // the ArchRule / JavaClasses types we return.
    api(rootProject.libs.archunit)

    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter)
    testImplementation(rootProject.libs.archunit.junit5)
    testImplementation(rootProject.libs.assertj)
    // Fixture packages deliberately `import` these so the corresponding rules
    // can be observed firing. Scoped to tests only so published consumers do
    // not inherit them.
    testImplementation(rootProject.libs.jackson.annotations)
    testImplementation("org.slf4j:slf4j-api:2.0.16")
}

// Same GitHub Packages repository as the Gradle plugin.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("ArchGuard — ArchUnit Rules")
                description.set(project.description)
                url.set("https://github.com/dmishra2022/archguard-gradle-plugin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url  = uri("https://maven.pkg.github.com/dmishra2022/archguard-gradle-plugin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
