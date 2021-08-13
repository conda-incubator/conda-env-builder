package com.github.nh13.condaenvbuilder.api

import com.github.nh13.condaenvbuilder.testing.UnitSpec
import org.scalatest.OptionValues

class SpecTest extends UnitSpec with OptionValues {

  "Spec" should "only apply defaults if no package was specified in the case of inheritance" in {
    // Defaults has a default version for foo, parent does not, and child has a different version than the default.
    // So when compiled, parent should have version 1.1 and child 1.2 for foo.

    val defaults = Seq(CondaStep(requirements=Seq("foo=1.1").reqs))

    val parent   = EnvironmentSpec(environment=Environment(
      name  = "parent",
      steps = Seq(CondaStep(requirements=Seq("foo").reqs)),
      group = ""
    ))

    val child   = EnvironmentSpec(inherits=Seq("parent"), environment=Environment(
      name  = "child",
      steps = Seq(CondaStep(requirements=Seq("foo=1.2").reqs)),
      group = ""
    ))

    val environments = Spec.compile(
      specs        = Seq(parent, child),
      defaults     = defaults,
      environments = Seq.empty
    )

    val compiledParent = environments.find(_.name == "parent").value

    // Compiled parent should  have the default
    compiledParent.steps should contain theSameElementsInOrderAs Seq(CondaStep(requirements=Seq("foo=1.1").reqs))

    // Compiled child should not have the default, which has been applied to the parent, applied to itself
    val compiledChild = environments.find(_.name == "child").value
    compiledChild.steps should contain theSameElementsInOrderAs Seq(CondaStep(requirements=Seq("foo=1.2").reqs))
  }
}
