import sbt._
import Keys._
import xml.Group

// Shell prompt which show the current project, git branch and build version
// git magic from Daniel Sobral, adapted by Ivan Porto Carrero to also work with git flow branches
object ShellPrompt {

  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
  }

  val current = """\*\s+([^\s]+)""".r

  def gitBranches = ("git branch --no-color" lines_! devnull mkString)

  val buildShellPrompt = {
    (state: State) => {
      val currBranch = current findFirstMatchIn gitBranches map (_ group(1)) getOrElse "-"
      val currProject = Project.extract (state).currentProject.id
      "%s:%s:%s> ".format (currBranch, currProject, LogbackAkkaSettings.buildVersion)
    }
  }
}

object LogbackAkkaSettings {
  val buildOrganization = "io.mojolly.logback"
  val buildScalaVersion = "2.9.1"
  val buildVersion      = "0.9.0"

  val description = SettingKey[String]("description")

  val buildSettings = Defaults.defaultSettings ++ Seq(
      name := "logback-ext",
      version := buildVersion,
      organization := buildOrganization,
      scalaVersion := buildScalaVersion,
      javacOptions ++= Seq("-Xlint:unchecked"),
      exportJars := true,
      scalacOptions ++= Seq(
        "-optimize",
        "-deprecation",
        "-unchecked",
        "-Xcheckinit",
        "-encoding", "utf8"),
      externalResolvers <<= resolvers map { rs => Resolver.withDefaultResolvers(rs, mavenCentral = true, scalaTools = false) },
      resolvers ++= Seq(
        "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
        "ScalaTools Snapshots" at "http://scala-tools.org/repo-snapshots",
        "TIM Group Repo" at "http://repo-1:8081/nexus/content/groups/public",
        "TIM Group Repo" at "http://repo-1:8081/nexus/content/repositories/yd-release-candidates"
      ),
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % "1.0.0",
        "joda-time" % "joda-time" % "2.1",
        "net.liftweb" %% "lift-json" % "2.4",
        "org.joda" % "joda-convert" %	"1.2",
        "org.slf4j" % "slf4j-api" % "1.6.4",
        "junit" % "junit" % "4.10" % "test",
        "org.specs2" %% "specs2" % "1.8.2" % "test"
      ),
      crossScalaVersions := Seq("2.9.1", "2.9.0-1"),
      autoCompilerPlugins := true,
      parallelExecution in Test := false,
      publishTo <<= (version) { version: String =>
        val nexus = "http://repo.youdevise.com:8081/nexus/content/repositories/"
        if (version.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus+"snapshots/") 
        else                                   Some("releases" at nexus+"yd-release-candidates/")
      },
      shellPrompt  := ShellPrompt.buildShellPrompt,
      testOptions := Seq(
        Tests.Argument("console", "junitxml")),
      testOptions <+= crossTarget map { ct =>
        Tests.Setup { () => System.setProperty("specs2.junit.outDir", new File(ct, "specs-reports").getAbsolutePath) }
      })

  val packageSettings = Seq (
    packageOptions <<= (packageOptions, name, version, organization) map {
      (opts, title, version, vendor) =>
         opts :+ Package.ManifestAttributes(
          "Created-By" -> "Simple Build Tool",
          "Built-By" -> System.getProperty("user.name"),
          "Build-Jdk" -> System.getProperty("java.version"),
          "Specification-Title" -> title,
          "Specification-Vendor" -> "Mojolly Ltd.",
          "Specification-Version" -> version,
          "Implementation-Title" -> title,
          "Implementation-Version" -> version,
          "Implementation-Vendor-Id" -> vendor,
          "Implementation-Vendor" -> "Mojolly Ltd.",
          "Implementation-Url" -> "https://backchat.io"
         )
    },
    homepage := Some(url("https://backchat.io")),
    startYear := Some(2010),
    licenses := Seq(("MIT", url("http://github.com/mojolly/logback-akka/raw/HEAD/LICENSE"))),
    pomExtra <<= (pomExtra, name, description) {(pom, name, desc) => pom ++ Group(
      <scm>
        <connection>scm:git:git://github.com/mojolly/logback-akka.git</connection>
        <developerConnection>scm:git:git@github.com:mojolly/logback-akka.git</developerConnection>
        <url>https://github.com/mojolly/logback-akka</url>
      </scm>
      <developers>
        <developer>
          <id>casualjim</id>
          <name>Ivan Porto Carrero</name>
          <url>http://flanders.co.nz/</url>
        </developer>
        <developer>
          <id>sdb</id>
          <name>Stefan De Boey</name>
          <url>http://ellefant.be</url>
        </developer>
      </developers>
    )},
    credentials += Credentials(new File("/etc/sbt/credentials")),
    publishMavenStyle := true,
    publishTo <<= version { (v: String) =>
      val nexus = "http://repo.youdevise.com:8081/nexus/content/repositories/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "snapshots")
      else
        Some("releases"  at nexus + "yd-release-candidates")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { x => false })

  val projectSettings = buildSettings ++ packageSettings
}

object LogbackAkkaBuild extends Build {

  import LogbackAkkaSettings._
  val buildShellPrompt =  ShellPrompt.buildShellPrompt

  lazy val root = Project ("logback-akka", file("."), settings = projectSettings ++ Seq(
    description := "A logstash layout for logback"))

}
