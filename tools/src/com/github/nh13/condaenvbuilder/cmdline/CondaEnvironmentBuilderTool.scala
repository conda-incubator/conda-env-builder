package com.github.nh13.condaenvbuilder.cmdline

import com.fulcrumgenomics.commons.util.LazyLogging
import com.fulcrumgenomics.sopt.cmdline.ValidationException
import com.github.nh13.condaenvbuilder.cmdline.CondaEnvironmentBuilderMain.FailureException

object CondaEnvironmentBuilderTool {
  /** True to use `mamba` instead of `conda`, false otherwise. */
  var UseMamba: Boolean = false
}


/** The trait that all `conda-env-builder` com.github.nh13.condaenvbuilder.tools should extend. */
trait CondaEnvironmentBuilderTool extends LazyLogging {
  def execute(): Unit

  /** Fail with just an exit code. */
  def fail(exit: Int) = throw new FailureException(exit=exit)

  /** Fail with the default exit code and a message. */
  def fail(message: String) = throw new FailureException(message=Some(message))

  /** Fail with a specific error code and message. */
  def fail(exit: Int, message: String) = throw new FailureException(exit=exit, message=Some(message))

  /** Generates a new validation exception with the given message. */
  def invalid(message: String) = throw new ValidationException(message)

  /** Generates a validation exception if the test value is false. */
  def validate(test: Boolean, message: => String): Unit = if (!test) throw new ValidationException(message)

  /** Returns the conda executable to use. */
  protected def condaExecutable: String = if (CondaEnvironmentBuilderTool.UseMamba) "mamba" else "conda"
}

