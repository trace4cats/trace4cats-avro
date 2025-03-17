lazy val commonSettings = Seq(
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(compilerPlugin(Dependencies.kindProjector), compilerPlugin(Dependencies.betterMonadicFor))
      case _ => Seq.empty
    }
  },
  scalacOptions += {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => "-Wconf:any:wv"
      case _ => "-Wconf:any:v"
    }
  },
  Test / fork := true,
  resolvers += Resolver.sonatypeRepo("releases"),
)

lazy val noPublishSettings =
  commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None, publish / skip := true)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  Test / publishArtifact := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "Trace4Cats Avro")
  .aggregate(avro, `avro-exporter`, `avro-server`, `avro-test`)

lazy val avro =
  (project in file("modules/avro"))
    .settings(publishSettings)
    .settings(name := "trace4cats-avro", libraryDependencies ++= Dependencies.`trace4cats-avro`)

lazy val `avro-exporter` =
  (project in file("modules/avro-exporter"))
    .settings(publishSettings)
    .settings(name := "trace4cats-avro-exporter", libraryDependencies ++= Dependencies.`avro-exporter`)
    .dependsOn(avro)

lazy val `avro-server` =
  (project in file("modules/avro-server"))
    .settings(publishSettings)
    .settings(name := "trace4cats-avro-server", libraryDependencies ++= Dependencies.`avro-server`)
    .dependsOn(avro)

lazy val `avro-test` = (project in file("modules/avro-test"))
  .settings(noPublishSettings)
  .settings(name := "trace4cats-avro-test", libraryDependencies ++= Dependencies.`avro-test`)
  .dependsOn(`avro-exporter`, `avro-server`)
