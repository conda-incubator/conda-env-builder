package com.github.condaincubator.condaenvbuilder.api

import com.github.condaincubator.condaenvbuilder.testing.UnitSpec

class EnvironmentTest extends UnitSpec {

  private val emptyEnv: Environment = Environment(name="", group="")

  private def inheritFrom(environment: Environment, step: Step*): Environment = {
    environment.inheritFrom(environment=emptyEnv.copy(steps=step.toIndexedSeq))
  }

  "Environment.inheritFrom" should "return the current environment if no environments are given" in {
    emptyEnv.inheritFrom() shouldBe emptyEnv
  }

  it should "copy an inherited step if a step of that type is not present" in {
    val step = new DummyStep
    val pip  = PipStep()
    inheritFrom(emptyEnv, step) shouldBe emptyEnv.copy(steps=Seq(step))
    val env  = emptyEnv.copy(steps=Seq(pip))
    inheritFrom(env, step) shouldBe emptyEnv.copy(steps=Seq(pip, step))
  }

  it should "have steps of the same type inherit from each other" in {
    val step    = new DummyStep
    val pip1    = PipStep(args=Seq("arg1"))
    val pip2    = PipStep(args=Seq("arg3"))
    val pip3    = PipStep(args=Seq("arg1", "arg2"))
    val pip4    = PipStep(args=Seq("arg3", "arg4"))
    val child1  = emptyEnv.copy(steps=Seq(step, pip1))
    val child2  = emptyEnv.copy(steps=Seq(step, pip1, pip2))
    val parent1 = emptyEnv.copy(steps=Seq(pip3))
    val parent2 = emptyEnv.copy(steps=Seq(pip3, pip4))
    child1.inheritFrom(environment=parent1) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg1", "arg2"))))
    child1.inheritFrom(environment=parent2) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg1", "arg2", "arg3", "arg4"))))
    child2.inheritFrom(environment=parent1) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg1", "arg3", "arg2"))))
    child2.inheritFrom(environment=parent2) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg1", "arg3", "arg2", "arg4"))))
  }

  "Environment.withDefaults" should "" in {
    val step     = new DummyStep
    val pip1     = PipStep(args=Seq("arg1"))
    val pip2     = PipStep(args=Seq("arg3"))
    val pip3     = PipStep(args=Seq("arg1", "arg2"))
    val pip4     = PipStep(args=Seq("arg3", "arg4"))
    val env1     = emptyEnv.copy(steps=Seq(step, pip1))
    val env2     = emptyEnv.copy(steps=Seq(step, pip1, pip2))
    val default1 = emptyEnv.copy(steps=Seq(pip3))
    val default2 = emptyEnv.copy(steps=Seq(pip3, pip4))
    env1.withDefaults(default1) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg1", "arg2"))))
    env1.withDefaults(default2) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg3", "arg4", "arg1", "arg2"))))
    env2.withDefaults(default1) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg1", "arg2")), PipStep(args=Seq("arg1", "arg2", "arg3"))))
    env2.withDefaults(default2) shouldBe emptyEnv.copy(steps=Seq(step, PipStep(args=Seq("arg3", "arg4", "arg1", "arg2"))))
  }
}