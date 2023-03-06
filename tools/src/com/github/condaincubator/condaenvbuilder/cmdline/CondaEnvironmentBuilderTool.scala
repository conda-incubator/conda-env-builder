package com.github.condaincubator.condaenvbuilder.cmdline

import com.fulcrumgenomics.commons.util.LazyLogging
import com.fulcrumgenomics.sopt.cmdline.ValidationException
import com.github.condaincubator.condaenvbuilder.cmdline.CondaEnvironmentBuilderMain.FailureException

object CondaEnvironmentBuilderTool {
  /** True to use `mamba` instead of `conda`, false otherwise. */
  var UseMamba: Boolean = false

  /** True to use `micromamba` instead of `conda` or `mamba`, false otherwise. Needed for testing in micromamba
   * environments*/
  var UseMicromamba: Boolean = false

  /** The file extension to use for YAML files. */
  var YamlFileExtension: String = "yml"
}


/** The trait that all `conda-env-builder` com.github.condaincubator.condaenvbuilder.tools should extend. */
trait CondaEnvironmentBuilderTool extends LazyLogging {
  def execute(): Unit

  /** Fail with just an exit code. */
  def fail(exit: Int) = throw FailureException(exit=exit)

  /** Fail with the default exit code and a message. */
  def fail(message: String) = throw FailureException(message=Some(message))

  /** Fail with a specific error code and message. */
  def fail(exit: Int, message: String) = throw FailureException(exit=exit, message=Some(message))

  /** Generates a new validation exception with the given message. */
  def invalid(message: String) = throw new ValidationException(message)

  /** Generates a validation exception if the test value is false. */
  def validate(test: Boolean, message: => String): Unit = if (!test) throw new ValidationException(message)

  /** Returns the conda executable to use. */
  protected def condaExecutable: String = {
    if (CondaEnvironmentBuilderTool.UseMamba) "micromamba"
    else if (CondaEnvironmentBuilderTool.UseMamba) "mamba"
    else "conda"
  }
}

