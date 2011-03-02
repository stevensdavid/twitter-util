import sbt._
import com.twitter.sbt._

class Project(info: ProjectInfo) extends StandardParentProject(info) with SubversionPublisher {

  override def subversionRepository = Some("http://svn.local.twitter.com/maven-public")

  val twitterRepo = "twitter.com" at "http://maven.twttr.com"


  // Projects
  // util-core: extensions with no external dependency requirements
  val coreProject = project("util-core", "util", new CoreProject(_))

  class CoreProject(info: ProjectInfo) extends StandardProject(info)
    with ProjectDefaults with SubversionPublisher
  {
    val scalaTools = "org.scala-lang" % "scala-compiler" % "2.8.1" % "compile"
    override def filterScalaJars = false

    val guava = "com.google.guava" % "guava" % "r06"
    val commonsCollections = "commons-collections" % "commons-collections" % "3.2.1"
    val cglib = "cglib" % "cglib" % "2.2"
  }


  trait ProjectDefaults extends StandardProject {
    val specs   = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5" % "test" withSources()
    val mockito = "org.mockito"             % "mockito-all" % "1.8.5" % "test" withSources()
    val junit   = "junit"                   %       "junit" % "3.8.2" % "test"

    override def compileOptions = super.compileOptions ++ Seq(Unchecked) ++
      compileOptions("-encoding", "utf8") ++
      compileOptions("-deprecation")
  }
}
