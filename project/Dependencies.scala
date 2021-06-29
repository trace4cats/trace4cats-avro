import sbt._

object Dependencies {
  object Versions {
    val scala212 = "2.12.14"
    val scala213 = "2.13.6"

    val trace4cats = "0.12.0-RC1+188-05746b3c"

    val fs2 = "3.0.4"
    val log4cats = "2.1.1"
    val logback = "1.2.3"
    val vulcan = "1.7.1"

    val kindProjector = "0.13.0"
    val betterMonadicFor = "0.3.1"
  }

  lazy val trace4catsExporterCommon = "io.janstenpickle" %% "trace4cats-exporter-common" % Versions.trace4cats
  lazy val trace4catsModel = "io.janstenpickle"          %% "trace4cats-model"           % Versions.trace4cats
  lazy val trace4catsTestkit = "io.janstenpickle"        %% "trace4cats-testkit"         % Versions.trace4cats

  lazy val fs2Io = "co.fs2"                  %% "fs2-io"          % Versions.fs2
  lazy val log4cats = "org.typelevel"        %% "log4cats-slf4j"  % Versions.log4cats
  lazy val logback = "ch.qos.logback"         % "logback-classic" % Versions.logback
  lazy val vulcan = "com.github.fd4s"        %% "vulcan"          % Versions.vulcan
  lazy val vulcanGeneric = "com.github.fd4s" %% "vulcan-generic"  % Versions.vulcan

  lazy val kindProjector = ("org.typelevel" % "kind-projector"     % Versions.kindProjector).cross(CrossVersion.full)
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor
}
