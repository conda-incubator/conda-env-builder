package com.github.nh13.condaenvbuilder.tools

import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.sopt.{arg, clp}
import com.github.nh13.condaenvbuilder.CondaEnvironmentBuilderDef.{FilePath, PathToYaml}
import com.github.nh13.condaenvbuilder.api._
import com.github.nh13.condaenvbuilder.cmdline.{ClpGroups, CondaEnvironmentBuilderTool}
import com.github.nh13.condaenvbuilder.io.SpecParser


@clp(description =
  """
    |Tabulates a YAML configuration file.
    |
    |The following columns will be output:
    |- group: the group of the environment
    |- name: the name of the environment
    |- value: the value [1]
    |- source: the source of the value (either conda, pip, or custom command).
    |
    |[1] The value is specific to the type of step:
    |- the requirement specification for conda and pip steps.  One row will be output per requirement
    |- the custom command for that step.  One row will be output per command.  The commands may optionally be double-quoted.
    |
    |This tool may be useful for downstream software that ingests tabular data, such as the command line or Excel.
    |
    |""",
  group=ClpGroups.Util)
class Tabulate
(@arg(flag='c', doc="Input YAML configuration file.") val config: PathToYaml,
 @arg(flag='o', doc="Output delimited data file.") val output: FilePath = Io.StdOut,
 @arg(flag='d', doc="The delimiter to use.") val delimiter: String = "\t"
) extends CondaEnvironmentBuilderTool {

  Io.assertReadable(config)
  Io.assertCanWriteFile(output)

  override def execute(): Unit = {
    val spec: Spec = SpecParser(config).compiled

    val envs: Iterator[Environment] = spec
      .specs
      .groupBy(_.environment.group)
      .toSeq
      .sortBy(_._1)
      .iterator
      .flatMap(_._2)
      .map(_.environment)

    val rows   = envs.iterator.flatMap { env: Environment =>
      env.steps.iterator.flatMap { step =>
        toRows(
          environment = env,
          step        = step
        )
      }
    }

    val writer = Io.toWriter(output)
    writer.write(Row.header.productIterator.mkString(delimiter))
    writer.write('\n')
    rows
      .map(_.columns.productIterator.mkString(delimiter))
      .foreach { line => writer.write(line); writer.write('\n') }
    writer.close()
  }

  private def toRows(environment: Environment, step: Step): Seq[Row] = {
    step match {
      case conda: CondaStep =>
        conda.requirements.map { requirement =>
           CondaRow(
             environment = environment,
             requirement = requirement
           )
        }
      case pip: PipStep     =>
        pip.requirements.map { requirement =>
          CondaRow(
            environment = environment,
            requirement = requirement
          )
        }
      case code: CodeStep   =>
        code.commands.map { command =>
          CodeRow(
            environment = environment,
            command     = command
          )
        }
      case _                => throw new IllegalArgumentException(s"Could not write the step: $step")
    }
  }
}

private object Row {
  def header: (String, String, String, String) = ("group", "name", "value", "source")
}

private sealed trait Row {
  def environment: Environment
  protected def value: String
  def source: String
  final def columns: (String, String, String, String) = {
    (environment.group, environment.name, value, source)
  }
}

private trait RequirementRow extends Row {
  def requirement: Requirement
  final protected def value: String = requirement.toString
}

private case class CondaRow(environment: Environment, requirement: Requirement) extends RequirementRow {
  def source: String = "conda"
}

private case class PipRow(environment: Environment, requirement: Requirement) extends RequirementRow {
  def source: String = "pip"
}

private case class CodeRow(environment: Environment, command: String) extends Row {
  def value: String = command
  def source: String = "custom command"
}
