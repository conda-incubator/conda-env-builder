package com.github.condaincubator.condaenvbuilder.util

import com.fulcrumgenomics.commons.util.Logger

import scala.sys.process.{ProcessBuilder, ProcessLogger}

object Process {
  /** The exit code of any interrupted execution of a task. */
  private val InterruptedExitCode = 255

  /** Runs the given processes(es). */
  def run(logger: Logger, processBuilder: ProcessBuilder): Unit = {
    val processOutput: StringBuilder = new StringBuilder()
    val (process: Option[scala.sys.process.Process], exitCode: Int, throwable: Option[Throwable]) = {
      try {
        val _process = processBuilder.run(ProcessLogger(fn = (line: String) => processOutput.append(line + "\n")))
        (Some(_process), _process.exitValue(), None)
      } catch {
        case e: InterruptedException => (None, InterruptedExitCode, Some(e))
        case t: Throwable => (None, 1, Some(t))
      }
    }

    // destroy the process regardless
    process.foreach(p => p.destroy())

    // throw the exception if something happened
    throwable.foreach { thr =>
      logger.error(processOutput)
      throw thr
    }

    if (exitCode != 0) {
      logger.error(processOutput)
      throw new IllegalStateException(s"Command exited with exit code '$exitCode': $processBuilder")
    }
  }

}
