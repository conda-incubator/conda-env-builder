import $ivy.`org.scalaj::scalaj-http:2.4.2`

import $file.ci.upload
import java.util.jar.Attributes.Name.{IMPLEMENTATION_VERSION => ImplementationVersion}

import ammonite.ops._
import coursier.maven.MavenRepository
import mill._
import mill.api.Loose
import mill.define.{Input, Target}
import mill.scalalib._
import mill.scalalib.publish.{License, PomSettings, _}
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`
import mill.contrib.scoverage.ScoverageModule
import $ivy.`org.scalaj::scalaj-http:2.4.2`

import scala.util.{Failure, Success, Try}

private val fgCommonsVersion          = "1.1.0-f1f68f5-SNAPSHOT"
private val fgSoptVersion             = "1.1.0-e565813-SNAPSHOT"
private val circeYamlVersion          = "0.13.0"
private val circeGenericVersion       = "0.13.0"
private val circeGenericExtrasVersion = "0.13.0"

/** The ScalaTest settings. */
trait ScalaTest extends TestModule {

  /** Ivy dependencies. */
  override def ivyDeps = Agg(
    ivy"org.scalatest::scalatest::3.1.2".excludeOrg(organizations="org.junit"),
    ivy"org.scalamock::scalamock::4.4.0"
  )

  /** Test frameworks. */
  override def testFrameworks: Target[Seq[String]] = Seq("org.scalatest.tools.Framework")

  /** Run a single test with `scalatest`. */
  def testOne(args: String*): mill.define.Command[Unit] = T.command {
    super.runMain(mainClass = "org.scalatest.run", args: _*)
  }
}

/** A base trait for versioning modules. */
trait ReleaseModule extends ScalaModule {

  /** Execute Git arguments and return the standard output. */
  private def git(args: String*): String = %%("git", args)(pwd).out.string.trim

  /** Get the commit hash at the HEAD of this branch. */
  def gitHead: String = git("rev-parse", "HEAD")

  /** Get the commit shorthash at the HEAD of this branch .*/
  private def shortHash: String = gitHead.take(7)

  /** The current tag of the currently checked out commit, if any. */
  def currentTag: Try[String] = Try(git("describe", "--exact-match", "--tags", "--always", gitHead))

  /** The hash of the last tagged commit. */
  private def hashOfLastTag: Try[String] = Try(git("rev-list", "--tags", "--max-count=1"))

  /** The last tag of the currently checked out branch, if any. */
  def lastTag: Try[String] = hashOfLastTag match {
    case Success(hash) => Try(git("describe", "--abbrev=0", "--tags", "--always", hash))
    case Failure(e)    => Failure(e)
  }

  /** If the Git repository is left in a dirty state. */
  private def dirty: Boolean = git("status", "--porcelain").nonEmpty

  /** The implementation version. */
  def implementationVersion: Input[String] = T.input {
    val prefix: String = (currentTag, lastTag) match {
      case (Success(_currentTag), _)       => _currentTag
      case (Failure(_), Success(_lastTag)) => _lastTag + "-" + shortHash
      case (_, _)                          => shortHash
    }
    prefix + (if (dirty) "-dirty" else "")
  }

  /** The version string `Target`. */
  def version = T { println(implementationVersion()) }

  /** The JAR manifest. */
  override def manifest = T { super.manifest().add(ImplementationVersion.toString -> implementationVersion()) }

  /** The publish version. Currently set to the implementation version. */
  def publishVersion: T[String] = implementationVersion

  /** POM Settings. */
  def pomSettings: T[PomSettings] = PomSettings(
    description    = "TODO",
    organization   = "com.github.nh13.condanevbuilder",
    url            = "https://github.com/nh13/conda-env-builder",
    licenses       = Seq(License.MIT),
    versionControl = VersionControl.github(owner = "nh13", repo = "conda-env-builder"),
    developers     = Seq(Developer("nh13", "Nils Homer", "https://github.com/nh13"))
  )
}

/** The common module mixin for all of our projects. */
trait CommonModule extends ScalaModule with ReleaseModule with ScoverageModule {
  def scalaVersion     = "2.13.1"
  def scoverageVersion = "1.4.1"

  override def repositories: Seq[coursier.Repository] = super.repositories ++ Seq(
    MavenRepository("https://oss.sonatype.org/content/repositories/public"),
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://jcenter.bintray.com/"),
    MavenRepository("https://artifactory.broadinstitute.org/artifactory/libs-snapshot/")
  )

  def localJar(assembly: PathRef, jarName: String): PathRef= {
    mkdir(pwd / 'jars)
    cp.over(assembly.path, pwd / 'jars / jarName)
    PathRef(pwd / 'jars / jarName)
  }

  def testModulesDeps: Seq[TestModule] = Nil

  object test extends Tests with ScalaTest with ScoverageTests {
    override def moduleDeps: Seq[JavaModule]        = super.moduleDeps ++ testModulesDeps
    override def ivyDeps: Target[Loose.Agg[Dep]]    = super.ivyDeps()
    override def runIvyDeps: Target[Loose.Agg[Dep]] = super.runIvyDeps()
  }
}

/** The commons project. */
object tools extends CommonModule {

  /** The artifact name. */
  override def artifactName: T[String] = "conda-env-builder"

  /** Scala compiler options. */
  override def scalacOptions: Target[Seq[String]] = T { Seq("-target:jvm-1.8", "-deprecation", "-feature") }

  /** Ivy dependencies. */
  override def ivyDeps = Agg(
    ivy"com.fulcrumgenomics::commons::$fgCommonsVersion",
    ivy"com.fulcrumgenomics::sopt::$fgSoptVersion",
    ivy"io.circe::circe-yaml::$circeYamlVersion",
    ivy"io.circe::circe-generic::$circeGenericVersion",
    ivy"io.circe::circe-generic-extras::$circeGenericExtrasVersion",
    ivy"org.slf4j:slf4j-nop:1.7.6"  // For logging silence: https://www.slf4j.org/codes.html#StaticLoggerBinder
  )

  /** Build a JAR file from the com.github.nh13.condaenvbuilder.tools project. */
  def localJar = T { super.localJar(assembly(), jarName = "conda-env-builder.jar") }
}

def publishVersion = T.input {
  tools.currentTag match {
    case Success(tag) => (tag, tag)
    case Failure(ex)  =>
      val dirtySuffix = os.proc('git, 'diff).call().out.trim match {
        case "" => ""
        case s  => "-dirty" + Integer.toHexString(s.hashCode)
      }
      tools.lastTag match {
        case Failure(ex)                  => throw new IllegalStateException("No tags!", ex)
        case Success(latestTaggedVersion) =>
          val commitsSinceLastTag = os.proc('git, "rev-list", tools.gitHead, "--not", latestTaggedVersion, "--count").call().out.trim.toInt
          val label = s"$latestTaggedVersion-$commitsSinceLastTag-${tools.gitHead.take(6)}$dirtySuffix"
          (latestTaggedVersion, label)
      }
  }
}

def uploadToGithub(authKey: String) = T.command {
  val (releaseTag, label) = publishVersion()

  // Determine if we should make a new release, or not
  if (releaseTag == label){
    println("Creating a new release")
    scalaj.http.Http("https://api.github.com/repos/nh13/conda-env-builder/releases")
      .postData(
        ujson.write(
          ujson.Obj(
            "tag_name" -> releaseTag,
            "name" -> releaseTag
          )
        )
      )
      .header("Authorization", "token " + authKey)
      .asString
  }

  val jar  = tools.localJar().path
  val dest = f"conda-env-builder-${label}.jar"
  println(f"Uploading JAR $jar to $dest")
  upload.apply(jar, releaseTag, dest, authKey)
}
