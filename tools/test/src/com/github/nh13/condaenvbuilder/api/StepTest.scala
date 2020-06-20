package com.github.nh13.condaenvbuilder.api

import java.nio.file.Paths

import com.github.nh13.condaenvbuilder.testing.UnitSpec

class StepTest extends UnitSpec {

  "Step.canInheritFrom" should "return true only if the step is the same class" in {
    val step: Step       = new DummyStep
    val code: CodeStep   = CodeStep(path=Paths.get("."))
    val conda: CondaStep = CondaStep()
    val pip: PipStep     = PipStep()
    val steps: Seq[Step] = Seq(step, code, conda, pip)
    steps.foreach { left =>
      steps.foreach { right =>
        left.canInheritFrom(right) shouldBe left == right
        right.canInheritFrom(left) shouldBe left == right
      }
    }
  }
}
