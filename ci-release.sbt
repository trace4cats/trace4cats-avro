ThisBuild / scalaVersion := Dependencies.Versions.scala213
ThisBuild / crossScalaVersions := Seq(
  Dependencies.Versions.scala213,
  Dependencies.Versions.scala212,
  Dependencies.Versions.scala3
)
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("8"), JavaSpec.temurin("17"))

ThisBuild / githubWorkflowBuildPreamble += WorkflowStep.Sbt(
  List("scalafmtCheckAll", "scalafmtSbtCheck"),
  name = Some("Check formatting")
)

ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.Equals(Ref.Branch("master")))
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ciReleaseSonatype"),
    name = Some("Publish artifacts"),
    env = Map(
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASS }}",
      "PGP_SECRET" -> "${{ secrets.GPG_PRIVATE_KEY }}",
    )
  )
)
ThisBuild / githubWorkflowPublishCond := Some("github.actor != 'mergify[bot]'")
//ThisBuild / githubWorkflowPublishPreamble += WorkflowStep.Use(
//  ref = UseRef.Public("crazy-max", "ghaction-import-gpg", "v6"),
//  id = Some("import_gpg"),
//  name = Some("Import GPG key"),
//  params = Map("gpg_private_key" -> "${{ secrets.GPG_PRIVATE_KEY }}", "passphrase" -> "${{ secrets.PGP_PASS }}")
//)

ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
ThisBuild / developers := List(
  Developer(
    "janstenpickle",
    "Chris Jansen",
    "janstenpickle@users.noreply.github.com",
    url = url("https://github.com/janstepickle")
  ),
  Developer(
    "catostrophe",
    "λoλcat",
    "catostrophe@users.noreply.github.com",
    url = url("https://github.com/catostrophe")
  )
)
ThisBuild / homepage := Some(url("https://github.com/trace4cats"))
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/trace4cats/trace4cats-avro"), "scm:git:git@github.com:trace4cats/trace4cats-avro.git")
)
ThisBuild / organization := "io.janstenpickle"
ThisBuild / organizationName := "trace4cats"
