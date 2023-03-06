package com.github.condaincubator.condaenvbuilder

import com.fulcrumgenomics.commons.CommonsDef

import java.nio.file.Path

/**
  * Object that is designed to be imported with `import CondaEnvironmentBuilderDef._` in any/all classes
  * much like the way that scala.PreDef is imported in all files automatically.
  *
  * New methods, types and objects should not be added to this class lightly as they
  * will pollute the namespace of any classes which import it.
  */
object CondaEnvironmentBuilderDef extends CommonsDef {

  //////////////////////////////////////////////////////////////////////////////////////////
  // Path or String-like type definitions that hint at what the path or string are used for.
  //////////////////////////////////////////////////////////////////////////////////////////

  /** Represents a path to a YAML file. */
  type PathToYaml = Path

}
