package com.github.nh13.condaenvbuilder.tools

import com.fulcrumgenomics.commons.io.Io
import com.fulcrumgenomics.sopt.{arg, clp}
import com.github.nh13.condaenvbuilder.CondaEnvironmentBuilderDef._
import com.github.nh13.condaenvbuilder.api.Spec
import com.github.nh13.condaenvbuilder.cmdline.{ClpGroups, CondaEnvironmentBuilderTool}
import com.github.nh13.condaenvbuilder.io.{SpecParser, SpecWriter}


@clp(description =
  """
    |Compiles a YAML Configuration file.
    |
    |- Updates default package requirements (both pip and conda) using the default environment.
    |- Applies inheritance to each environment.
    |
    |The output will have inheritance removed and no default environment.
    |""",
  group=ClpGroups.Util)
class Compile
( @arg(flag='c', doc="Input YAML configuration file.") val config: PathToYaml,
  @arg(flag='o', doc="Output YAML configuration file.") val output: PathToYaml = Io.StdOut,
) extends CondaEnvironmentBuilderTool {

  Io.assertReadable(config)
  Io.assertCanWriteFile(output)

  override def execute(): Unit = {
    val spec: Spec = SpecParser(config).compiled
    SpecWriter.write(spec=spec, config=output)
  }
}
