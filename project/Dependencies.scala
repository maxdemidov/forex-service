import sbt._

object Dependencies {

  object Versions {
    val scalaVersion        = "2.12.10"

    val shapeless           = "2.3.3"
    val cats                = "2.1.0"
    val catsEffect          = "2.0.0"
    val fs2                 = "2.1.0"
    val zio                 = "1.0.0-RC20"
    val monix               = "3.2.1"
    val http4s              = "0.20.15"
    val circe               = "0.11.1"
    val pureConfig          = "0.12.1"
    val scalaj              = "2.4.2"

    val kindProjector       = "0.9.10"
    val logback             = "1.2.3"
    val log4cats            = "1.1.1"
    val scalaCheck          = "1.14.3"
    val scalaTest           = "3.1.0"
    val catsScalaCheck      = "0.2.0"
  }

  object Libraries {
    def circe(artifact: String): ModuleID = "io.circe"    %% artifact % Versions.circe
    def http4s(artifact: String): ModuleID = "org.http4s" %% artifact % Versions.http4s

    lazy val scalaReflect        = "org.scala-lang"        % "scala-reflect"               % Versions.scalaVersion

    lazy val shapeless           = "com.chuusai"           %% "shapeless"                  % Versions.shapeless
    lazy val cats                = "org.typelevel"         %% "cats-core"                  % Versions.cats
    lazy val catsEffect          = "org.typelevel"         %% "cats-effect"                % Versions.catsEffect
    lazy val fs2                 = "co.fs2"                %% "fs2-core"                   % Versions.fs2
    lazy val zio                 = "dev.zio"               %% "zio"                        % Versions.zio
    lazy val monix               = "io.monix"              %% "monix"                      % Versions.monix

    lazy val http4sDsl           = http4s("http4s-dsl")
    lazy val http4sServer        = http4s("http4s-blaze-server")
    lazy val http4sCirce         = http4s("http4s-circe")
    lazy val circeCore           = circe("circe-core")
    lazy val circeGeneric        = circe("circe-generic")
    lazy val circeGenericExt     = circe("circe-generic-extras")
    lazy val circeParser         = circe("circe-parser")
    lazy val circeJava8          = circe("circe-java8")
    lazy val pureConfig          = "com.github.pureconfig" %% "pureconfig"                 % Versions.pureConfig

    lazy val scalaj              = "org.scalaj"            %% "scalaj-http"                % Versions.scalaj

    // Compiler plugins
    lazy val kindProjector       = "org.spire-math"        %% "kind-projector"             % Versions.kindProjector

    // Runtime
    lazy val logback             = "ch.qos.logback"        %  "logback-classic"            % Versions.logback

    lazy val log4catsCore        = "io.chrisdavenport"     %% "log4cats-core"              % Versions.log4cats
    lazy val log4catsSlf4j       = "io.chrisdavenport"     %% "log4cats-slf4j"             % Versions.log4cats

    // Test
    lazy val scalaTest           = "org.scalatest"         %% "scalatest"                  % Versions.scalaTest
    lazy val scalaCheck          = "org.scalacheck"        %% "scalacheck"                 % Versions.scalaCheck
    lazy val catsScalaCheck      = "io.chrisdavenport"     %% "cats-scalacheck"            % Versions.catsScalaCheck
  }
}
