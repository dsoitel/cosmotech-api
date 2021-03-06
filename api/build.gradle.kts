import com.google.cloud.tools.jib.gradle.JibTask
import com.rameshkp.openapi.merger.gradle.task.OpenApiMergerTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins { id("com.rameshkp.openapi-merger-gradle-plugin") version "1.0.4" }

dependencies {
  api(project(":cosmotech-api-common"))
  implementation(project(":cosmotech-connector-api"))
  implementation(project(":cosmotech-dataset-api"))
  implementation(project(":cosmotech-organization-api"))
  implementation(project(":cosmotech-platform-api"))
  implementation(project(":cosmotech-scenario-api"))
  implementation(project(":cosmotech-scenariorun-api"))
  implementation(project(":cosmotech-solution-api"))
  implementation(project(":cosmotech-user-api"))
  implementation(project(":cosmotech-workspace-api"))
}

tasks.getByName<Delete>("clean") { delete("$rootDir/openapi/openapi.yaml") }

tasks.withType<JibTask> {
  // Need to depend on all sub-projects Jar tasks
  val jarTasks =
      parent
          ?.subprojects
          ?.filter { it.name != project.name && it.name != "cosmotech-api-common" }
          ?.flatMap { it.tasks.withType<Jar>() }
          ?.toList()
  logger.debug("jibTask ${this.name} needs to depend on : $jarTasks")
  if (jarTasks?.isNotEmpty() == true) {
    dependsOn(*jarTasks.toTypedArray())
  }
}

gradle.projectsEvaluated {
  tasks.register<Copy>("copySubProjectsOpenAPIFiles") {
    // By convention, we expect OpenAPI files for sub-projects to be named and placed as follows:
    // <subproject>/src/main/openapi/<subproject>s.yaml
    // For example: organization/src/main/openapi/organizations.yaml
    val sourcePaths =
        configurations
            .implementation
            .get()
            .allDependencies
            .withType<ProjectDependency>()
            .asSequence()
            .filter {
              logger.debug("Found project dependency: $it")
              it.name.matches("^cosmotech-[a-zA-Z]+-api$".toRegex())
            }
            .map { it.dependencyProject.projectDir }
            .map { file("${it}/src/main/openapi/${it.relativeTo(rootDir)}s.yaml") }
            .filter { it.exists() }
            .map { it.absolutePath }
            .toMutableList()
    // If you need to reference a non-conventional path, feel free to add it below to the
    // sourcePaths local variable, like so: sourcePaths.add("my/path/to/another/openapi.yaml")

    logger.debug("sourcePaths for 'copySubProjectsOpenAPIFiles' task: $sourcePaths")
    if (sourcePaths.isNotEmpty()) {
      from(*sourcePaths.toTypedArray())
      into("$buildDir/tmp/openapi")
    } else {
      logger.warn(
          "Unable to find OpenAPI definitions in project dependencies => 'copySubProjectsOpenAPIFiles' not configured!")
    }
  }
}

openApiMerger {
  openApi {
    openApiVersion.set("3.0.3")
    info {
      title.set("Cosmo Tech Plaform API")
      description.set("Cosmo Tech Platform API")
      version.set(project.version.toString())
      //      termsOfService.set("http://openapimerger.com/terms-of-service")
      contact {
        name.set("Repository")
        // email.set("openapi@sample.com")
        url.set("https://github.com/Cosmo-Tech/cosmotech-api")
      }
      license {
        name.set("MIT License")
        url.set("https://github.com/Cosmo-Tech/cosmotech-api/blob/main/LICENSE")
      }
    }
    servers {
      register("production") {
        url.set("https://api.azure.cosmo-platform.com")
        description.set("Production")
      }
      register("dev") {
        url.set("http://localhost:8080")
        description.set("Local dev environment")
      }
    }
    externalDocs {
      description.set("Portal")
      url.set("https://portal.cosmotech.com/")
    }
  }
}

tasks.getByName<OpenApiMergerTask>("mergeOpenApiFiles") {
  dependsOn("copySubProjectsOpenAPIFiles")
  inputDirectory.set(file("$buildDir/tmp/openapi"))
  outputFileProperty.set(file("$rootDir/openapi/openapi.yaml"))
}

tasks.register<GenerateTask>("openApiJSGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/javascript")
  generatorName.set("javascript")
  additionalProperties.set(
      mapOf(
          "projectName" to "@cosmotech/api",
          "projectDescription" to "Cosmo Tech Platform API client",
          "moduleName" to "CosmotechApi"))
}

tasks.register<Copy>("copyJSGitPushScript") {
  dependsOn("openApiJSGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/javascript")
}

tasks.register<Copy>("copyJSLicense") {
  dependsOn("openApiJSGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/javascript")
}

tasks.register("generateJSClient") { dependsOn("copyJSGitPushScript", "copyJSLicense") }

tasks.register<GenerateTask>("openApiPythonGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/python")
  generatorName.set("python")
  additionalProperties.set(
      mapOf("projectName" to "cosmotech-api", "packageName" to "cosmotech_api"))
}

tasks.register<Copy>("copyPythonGitPushScript") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/python")
}

tasks.register<Copy>("copyPythonLicense") {
  dependsOn("openApiPythonGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/python")
}

tasks.register("generatePythonClient") { dependsOn("copyPythonGitPushScript", "copyPythonLicense") }

tasks.register<GenerateTask>("openApiJavaGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/java")
  generatorName.set("java")
  additionalProperties.set(
      mapOf(
          "apiPackage" to "com.cosmotech.client.api",
          "artifactDescription" to "Cosmo Tech API Java Client",
          "artifactId" to "cosmotech-api-java-client",
          "artifactUrl" to "https://github.com/Cosmo-Tech/cosmotech-api-java-client",
          "developerEmail" to "team.engineering@cosmotech.com",
          "developerName" to "Cosmo Tech",
          "developerOrganization" to "Cosmo Tech",
          "developerOrganizationUrl" to "https://cosmotech.com/",
          "groupId" to "com.cosmotech",
          "invokerPackage" to "com.cosmotech.client",
          "licenseName" to "MIT",
          "licenseUrl" to
              "https://github.com/Cosmo-Tech/cosmotech-api-java-client/blob/master/LICENSE",
          "modelPackage" to "com.cosmotech.client.model",
          "scmConnection" to "scm:git:git@github.com:Cosmo-Tech/cosmotech-api-java-client",
          "scmDeveloperConnection" to "scm:git:git@github.com:Cosmo-Tech/cosmotech-api-java-client",
          "scmUrl" to "https://github.com/Cosmo-Tech/cosmotech-api-java-client"))
}

tasks.register<Copy>("copyJavaGitPushScript") {
  dependsOn("openApiJavaGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/java")
}

tasks.register<Copy>("copyJavaLicense") {
  dependsOn("openApiJavaGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/java")
}

tasks.register("generateJavaClient") { dependsOn("copyJavaGitPushScript", "copyJavaLicense") }

tasks.register<GenerateTask>("openApiCSharpGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$buildDir/generated-sources/csharp")
  generatorName.set("csharp-netcore")
  additionalProperties.set(mapOf("packageName" to "Com.Cosmotech"))
}

tasks.register<Copy>("copyCSharpGitPushScript") {
  dependsOn("openApiCSharpGenerate")
  from("${rootDir}/scripts/clients/build_override/git_push.sh")
  into("$buildDir/generated-sources/csharp")
}

tasks.register<Copy>("copyCSharpLicense") {
  dependsOn("openApiCSharpGenerate")
  from("${rootDir}/scripts/clients/build_override/LICENSE")
  into("$buildDir/generated-sources/csharp")
}

tasks.register("generateCSharpClient") { dependsOn("copyCSharpGitPushScript", "copyCSharpLicense") }

tasks.register<GenerateTask>("openApiUmlGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$rootDir/openapi/plantuml")
  generatorName.set("plantuml")
}

tasks.register<GenerateTask>("openApiMarkdownGenerate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
  outputDir.set("$rootDir/doc")
  generatorName.set("markdown")
}

tasks.getByName<GenerateTask>("openApiGenerate") { enabled = false }

tasks.getByName<ValidateTask>("openApiValidate") {
  dependsOn("mergeOpenApiFiles")
  inputSpec.set("${rootDir}/openapi/openapi.yaml")
}

tasks.register("generateClients") {
  dependsOn(
      "generateJSClient",
      "generatePythonClient",
      "generateJavaClient",
      "generateCSharpClient",
      "openApiUmlGenerate",
      "openApiMarkdownGenerate")
}

tasks.getByName<BootJar>("bootJar") { finalizedBy("generateClients") }

tasks.getByName<Copy>("copyOpenApiYamlToMainResources") { dependsOn("mergeOpenApiFiles") }

tasks.getByName<Copy>("copyOpenApiYamlToTestResources") { dependsOn("mergeOpenApiFiles") }
