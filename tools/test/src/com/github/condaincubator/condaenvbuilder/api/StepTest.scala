package com.github.condaincubator.condaenvbuilder.api

import com.github.condaincubator.condaenvbuilder.testing.UnitSpec

import java.nio.file.Paths

class StepTest extends UnitSpec {

  "Step.canInheritFrom" should "return true only if the step is the same class but different object" in {
    def build(): Seq[Step] = {
      val step: Step       = new DummyStep
      val code: CodeStep   = CodeStep(path=Paths.get("."))
      val conda: CondaStep = CondaStep()
      val pip: PipStep     = PipStep()
      Seq(step, code, conda, pip)
    }

    val leftSteps  = build()
    val rightSteps = build()

    leftSteps.zipWithIndex.foreach { case (leftStep: Step, leftIdx: Int) =>
      rightSteps.zipWithIndex.foreach { case (rightStep: Step, rightIdx: Int) =>
        leftStep.canInheritFrom(rightStep) shouldBe leftIdx == rightIdx
        rightStep.canInheritFrom(leftStep) shouldBe leftIdx == rightIdx
        leftStep.canInheritFrom(leftStep) shouldBe false
        rightStep.canInheritFrom(rightStep) shouldBe false
      }
    }
  }
}
