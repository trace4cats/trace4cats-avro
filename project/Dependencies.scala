import sbt._

object Dependencies {
  object Versions {
    val scala212 = "2.12.16"
    val scala213 = "2.13.8"
    val scala3 = "3.1.3"

    val trace4cats = "0.14.0"

    val fs2 = "3.2.14"
    val log4cats = "2.4.0"
    val logback = "1.5.10"
    val vulcan = "1.8.3"
    val slf4j = "1.7.36"

    val kindProjector = "0.13.2"
    val betterMonadicFor = "0.3.1"
  }

  lazy val trace4catsKernel = "io.janstenpickle"  %% "trace4cats-kernel"  % Versions.trace4cats
  lazy val trace4catsCore = "io.janstenpickle"    %% "trace4cats-core"    % Versions.trace4cats
  lazy val trace4catsTestkit = "io.janstenpickle" %% "trace4cats-testkit" % Versions.trace4cats

  lazy val fs2Io = "co.fs2"                  %% "fs2-io"          % Versions.fs2
  lazy val log4cats = "org.typelevel"        %% "log4cats-slf4j"  % Versions.log4cats
  lazy val logback = "ch.qos.logback"         % "logback-classic" % Versions.logback
  lazy val vulcan = "com.github.fd4s"        %% "vulcan"          % Versions.vulcan
  lazy val vulcanGeneric = "com.github.fd4s" %% "vulcan-generic"  % Versions.vulcan
  lazy val slf4jNop = "org.slf4j"             % "slf4j-nop"       % Versions.slf4j

  lazy val kindProjector = ("org.typelevel" % "kind-projector"     % Versions.kindProjector).cross(CrossVersion.full)
  lazy val betterMonadicFor = "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor
}
