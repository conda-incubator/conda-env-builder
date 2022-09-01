package com.github.condaincubator.condaenvbuilder.cmdline

import com.fulcrumgenomics.sopt.cmdline.ClpGroup

/** The command line groups. */
object ClpGroups {

  class _Util extends ClpGroup {
    override val name: String = "Utilities"
    override val description: String = "Utility programs."
  }

  final val Util = classOf[_Util]
}
