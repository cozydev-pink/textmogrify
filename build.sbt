// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "pink.cozydev"
ThisBuild / organizationName := "CozyDev"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("valencik", "Andrew Valencik")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

// publish snapshots from main branch
ThisBuild / tlCiReleaseBranches := Seq("main")

// use JDK 11
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))

val Scala213 = "2.13.15"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.4")
ThisBuild / scalaVersion := Scala213 // the default Scala

val catsV = "2.12.0"
val catsEffectV = "3.5.7"
val fs2V = "3.11.0"
val luceneV = "10.1.0"
val munitCatsEffectV = "2.0.0"

lazy val root = tlCrossRootProject.aggregate(lucene, example, unidocs, benchmarks)

lazy val lucene = project
  .in(file("lucene"))
  .settings(
    name := "textmogrify-lucene",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsV,
      "org.typelevel" %% "cats-effect" % catsEffectV,
      "org.typelevel" %% "cats-effect-kernel" % catsEffectV,
      "co.fs2" %% "fs2-core" % fs2V,
      "co.fs2" %% "fs2-io" % fs2V,
      "org.apache.lucene" % "lucene-core" % luceneV,
      "org.apache.lucene" % "lucene-analysis-common" % luceneV,
      "org.typelevel" %% "munit-cats-effect" % munitCatsEffectV % Test,
    ),
  )

lazy val example = project
  .in(file("example"))
  .enablePlugins(NoPublishPlugin)
  .dependsOn(lucene)

import laika.ast.Path.Root
import laika.helium.config.{IconLink, HeliumIcon, TextLink, ThemeNavigationSection}
import cats.data.NonEmptyList
lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(lucene)
  .settings(
    tlSiteApiPackage := Some("textmogrify"),
    tlSiteHelium := {
      tlSiteHelium.value.site.darkMode.disabled.site
        .topNavigationBar(
          homeLink = IconLink.external("https://github.com/valencik/textmogrify", HeliumIcon.home)
        )
        .site
        .mainNavigation(
          appendLinks = Seq(
            ThemeNavigationSection(
              "Related Projects",
              TextLink.external("https://lucene.apache.org/", "lucene"),
              TextLink.external("https://typelevel.org/cats-effect/", "cats-effect"),
              TextLink.external("https://fs2.io/", "fs2"),
            )
          )
        )
    },
  )

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    name := "textmogrify-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(lucene),
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(lucene)
  .settings(
    name := "textmogrify-benchmarks",
    libraryDependencies += "org.typelevel" %% "cats-effect" % catsEffectV,
  )
  .enablePlugins(NoPublishPlugin, JmhPlugin)
