package com.github.condaincubator.condaenvbuilder.cmdline

import java.io.IOException
import java.net.InetAddress
import java.nio.file.Paths
import java.text.DecimalFormat

import com.fulcrumgenomics.commons.CommonsDef._
import com.fulcrumgenomics.commons.util.{LazyLogging, LogLevel, Logger}
import com.fulcrumgenomics.sopt.cmdline.CommandLineProgramParserStrings
import com.fulcrumgenomics.sopt.{Sopt, arg}
import CondaEnvironmentBuilderMain.FailureException


/**
  * Main program for conda-env-builder that loads everything up and runs the appropriate sub-command
  */
object CondaEnvironmentBuilderMain {
  /** The main method */
  def main(args: Array[String]): Unit = new CondaEnvironmentBuilderMain().makeItSoAndExit(args)

  /**
    * Exception class intended to be used by [[CondaEnvironmentBuilderMain]] and [[CondaEnvironmentBuilderTool]] to communicate
    * non-exceptional(!) failures when running a tool.
    */
  case class FailureException private[cmdline] (exit:Int = 1, message:Option[String] = None) extends RuntimeException
}

class CondaEnvironmentBuilderCommonArgs
( @arg(doc="Directory to use for temporary files.") val tmpDir: DirPath  = Paths.get(System.getProperty("java.io.tmpdir")),
  @arg(doc="Minimum severity log-level to emit.")   val logLevel: LogLevel = LogLevel.Info,
  @arg(doc="Use mamba instead of conda.")           val mamba: Boolean = false,
  @arg(doc="File extension to use for YAML files (no period).") val extension: String = "yml"
) {

  assert(!extension.startsWith("."), s"File extension should not start with a period: '$extension'")

  System.setProperty("java.io.tmpdir", tmpDir.toAbsolutePath.toString)

  Logger.level = this.logLevel
  CondaEnvironmentBuilderTool.UseMamba = mamba
  CondaEnvironmentBuilderTool.FileExtension = extension
}

class CondaEnvironmentBuilderMain extends LazyLogging {
  /** A main method that invokes System.exit with the exit code. */
  def makeItSoAndExit(args: Array[String]): Unit = System.exit(makeItSo(args))

  /** A main method that returns an exit code instead of exiting. */
  def makeItSo(args: Array[String]): Int = {
    val startTime = System.currentTimeMillis()
    val exit      = Sopt.parseCommandAndSubCommand[CondaEnvironmentBuilderCommonArgs,CondaEnvironmentBuilderTool](name, args.toIndexedSeq, Sopt.find[CondaEnvironmentBuilderTool](packageList)) match {
      case Sopt.Failure(usage) =>
        System.err.print(usage())
        1
      case Sopt.CommandSuccess(cmd) =>
        unreachable("CommandSuccess should never be returned by parseCommandAndSubCommand.")
      case Sopt.SubcommandSuccess(commonArgs, subcommand) =>
        val name = subcommand.getClass.getSimpleName
        try {
          printStartupLines(name, args, commonArgs)
          subcommand.execute()
          printEndingLines(startTime, name, true)
          0
        }
        catch {
          case ex: FailureException =>
            val banner = "#" * ex.message.map(_.length).getOrElse(80)
            logger.fatal(banner)
            logger.fatal("Execution failed!")
            ex.message.foreach(msg => msg.linesIterator.foreach(logger.fatal))
            logger.fatal(banner)
            printEndingLines(startTime, name, false)
            ex.exit
          case ex: IOException if Option(ex.getMessage).exists(_.toLowerCase.contains("broken pipe")) =>
            printEndingLines(startTime, name, false)
            System.err.println(ex)
            1
          case ex: Throwable =>
            printEndingLines(startTime, name, false)
            throw ex
        }
    }

    exit
  }

  /** The name of the toolkit, used in printing usage and status lines. */
  def name: String = "conda-env-builder"

  /** Prints a line of useful information when a tool starts executing. */
  protected def printStartupLines(tool: String, args: Array[String], commonArgs: CondaEnvironmentBuilderCommonArgs): Unit = {
    val version    = CommandLineProgramParserStrings.version(getClass, color=false).replace("Version: ", "")
    val host       = try { InetAddress.getLocalHost.getHostName } catch { case _: Exception => "unknown-host" }
    val user       = System.getProperty("user.name")
    val jreVersion = System.getProperty("java.runtime.version")
    logger.info(s"Executing $tool from $name version $version as $user@$host on JRE $jreVersion")
  }

  /** Prints a line of useful information when a tool stops executing. */
  protected def printEndingLines(startTime: Long, name: String, success: Boolean): Unit = {
    val elapsedMinutes: Double = (System.currentTimeMillis() - startTime) / (1000d * 60d)
    val elapsedString: String = new DecimalFormat("#,##0.00").format(elapsedMinutes)
    val verb = if (success) "completed" else "failed"
    logger.info(s"$name $verb. Elapsed time: $elapsedString minutes.")
  }

  /** The packages we wish to include in our command line **/
  protected def packageList: List[String] = List[String]("com.github.condaincubator.condaenvbuilder")
}

