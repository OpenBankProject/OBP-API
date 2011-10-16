import bootstrap.liftweb.Boot
import scala.tools.nsc.MainGenericRunner

object LiftConsole {
  def main(args : Array[String]) {
    // Instantiate your project's Boot file
    val b = new Boot()
    // Boot your project
    b.boot
    // Now run the MainGenericRunner to get your repl
    MainGenericRunner.main(args)
    // After the repl exits, then exit the scala script
    exit(0)
  }
}
