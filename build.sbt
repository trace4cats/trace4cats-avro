ThisBuild / crossScalaVersions     := Seq("2.12.20", "2.13.16", "3.3.5")
ThisBuild / organization           := "io.janstenpickle"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; publishLocal; +test")
addCommandAlias("ci-docs", "github; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val `trace4cats-avro` = module
  .settings(libraryDependencies ++= Dependencies.`trace4cats-avro`)

lazy val `trace4cats-avro-exporter` = module
  .settings(libraryDependencies ++= Dependencies.`avro-exporter`)
  .dependsOn(`trace4cats-avro`)

lazy val `trace4cats-avro-server` = module
  .settings(libraryDependencies ++= Dependencies.`avro-server`)
  .dependsOn(`trace4cats-avro`)

lazy val `trace4cats-avro-test` = module
  .settings(libraryDependencies ++= Dependencies.`avro-test`)
  .dependsOn(`trace4cats-avro-exporter`, `trace4cats-avro-server`)
