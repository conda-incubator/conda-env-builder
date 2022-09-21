package com.github.condaincubator.condaenvbuilder.api

import com.github.condaincubator.condaenvbuilder.api
import com.github.condaincubator.condaenvbuilder.testing.UnitSpec
import org.scalatest.OptionValues

class SpecTest extends UnitSpec with OptionValues {

  "Spec.compile" should "only apply defaults if no package was specified in the case of inheritance" in {
    // Defaults has a default version for foo, parent does not, and child has a different version than the default.
    // So when compiled, parent should have version 1.1 and child 1.2 for foo.

    val defaults = Seq(api.CondaStep(requirements=Seq("foo=1.1").reqs))

    val parent   = EnvironmentSpec(environment=Environment(
      name  = "parent",
      steps = Seq(api.CondaStep(requirements=Seq("foo").reqs)),
      group = ""
    ))

    val child   = EnvironmentSpec(inherits=Seq("parent"), environment=Environment(
      name  = "child",
      steps = Seq(api.CondaStep(requirements=Seq("foo=1.2").reqs)),
      group = ""
    ))

    val environments = Spec.compile(
      specs        = Seq(parent, child),
      defaults     = defaults,
      environments = Seq.empty
    )

    val compiledParent = environments.find(_.name == "parent").value

    // Compiled parent should  have the default
    compiledParent.steps should contain theSameElementsInOrderAs Seq(api.CondaStep(requirements=Seq("foo=1.1").reqs))

    // Compiled child should not have the default, which has been applied to the parent, applied to itself
    val compiledChild = environments.find(_.name == "child").value
    compiledChild.steps should contain theSameElementsInOrderAs Seq(api.CondaStep(requirements=Seq("foo=1.2").reqs))
  }

  it should "throw an exception when it detects a cyclical dependency in the environment inheritance" in {
    // simple cycle
    {
      val parent = EnvironmentSpec(inherits = Seq("child"), environment = Environment(
        name  = "parent",
        steps = Seq(api.CondaStep(requirements = Seq("foo").reqs)),
        group = "",
      ))

      val child = EnvironmentSpec(inherits = Seq("parent"), environment = Environment(
        name  = "child",
        steps = Seq(api.CondaStep(requirements = Seq("foo=1.2").reqs)),
        group = ""
      ))

      // validate we find the components correctly
      Spec.findConnectedComponents(Seq(parent, child)) contains theSameElementsInOrderAs(Seq(IndexedSeq(parent, child)))

      val ex = intercept[IllegalArgumentException] {
        Spec.compile(
          specs        = Seq(parent, child),
          defaults     = Seq.empty,
          environments = Seq.empty
        )
      }

      ex.getMessage should include ("Found a cyclical dependency in the environment inheritance")
    }

    // three-step cycle
    {
      val parent = EnvironmentSpec(inherits = Seq("grandchild"), environment = Environment(
        name  = "parent",
        steps = Seq(api.CondaStep(requirements = Seq("foo").reqs)),
        group = "",
      ))

      val child = EnvironmentSpec(inherits = Seq("parent"), environment = Environment(
        name  = "child",
        steps = Seq(api.CondaStep(requirements = Seq("foo=1.2").reqs)),
        group = ""
      ))

      val grandchild = EnvironmentSpec(inherits = Seq("child"), environment = Environment(
        name  = "grandchild",
        steps = Seq(api.CondaStep(requirements = Seq("foo=1.2").reqs)),
        group = ""
      ))

      // validate we find the components correctly
      Spec.findConnectedComponents(Seq(parent, child, grandchild)) contains theSameElementsInOrderAs(Seq(
        IndexedSeq(parent, child, grandchild))
      )

      val ex = intercept[IllegalArgumentException] {
        Spec.compile(
          specs        = Seq(parent, child, grandchild),
          defaults     = Seq.empty,
          environments = Seq.empty
        )
      }

      ex.getMessage should include ("Found a cyclical dependency in the environment inheritance")
    }
  }

  it should "throw an exception when an environment inherits from a non-existent environment" in {
    val foo = EnvironmentSpec(inherits = Seq.empty, environment = Environment(
      name  = "foo",
      steps = Seq(api.CondaStep(requirements = Seq("foo").reqs)),
      group = "",
    ))

    val bar = EnvironmentSpec(inherits = Seq("blah"), environment = Environment(
      name  = "bar",
      steps = Seq(api.CondaStep(requirements = Seq("bar=1.2").reqs)),
      group = ""
    ))

    val ex = intercept[IllegalArgumentException] {
      Spec.compile(
        specs        = Seq(foo, bar),
        defaults     = Seq.empty,
        environments = Seq.empty
      )
    }

    ex.getMessage should include ("The environment 'bar' inherits from a non-exist environment: 'blah'")
  }
}
