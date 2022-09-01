package com.github.condaincubator.condaenvbuilder.api

import com.fulcrumgenomics.commons.util.{LogLevel, Logger}
import com.github.condaincubator.condaenvbuilder.testing.UnitSpec
import Requirement._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

class RequirementTest extends UnitSpec {

  "Requirement" should "fail if not using a valid relation" in {
    an[Exception] should be thrownBy Requirement("foo", "1", "?")
  }

  it should "fail if specifying the default version but not the default relation" in {
    an[Exception] should be thrownBy Requirement("foo", Requirement.DefaultVersion, ">")
  }

  it should "fail if name, version, or relation are empty" in {
    an[Exception] should be thrownBy Requirement("", "1", "==")
    an[Exception] should be thrownBy Requirement("foo", "", "==")
    an[Exception] should be thrownBy Requirement("foo", "1", "")
  }

  "Requirement.toString" should "return the conda package specification" in {
    Requirement("foo", "0.1.1", "==").toString shouldBe "foo==0.1.1"
    Requirement("openssl", "1.1.1g=h0b31af3_0", "=").toString shouldBe "openssl=1.1.1g=h0b31af3_0"
    Requirement("bzip2", "1.0.8", ">=").toString shouldBe "bzip2>=1.0.8"
  }

  "Requirement" should "build from a String" in {
    Requirement("foo==0.1.1").toString shouldBe "foo==0.1.1"
    Requirement("openssl=1.1.1g=h0b31af3_0").toString shouldBe "openssl=1.1.1g=h0b31af3_0"
    Requirement("bzip2>=1.0.8").toString shouldBe "bzip2>=1.0.8"
    Requirement(s"foo$DefaultRelation$DefaultVersion").toString shouldBe s"foo$DefaultRelation$DefaultVersion"
    Requirement("foo").toString shouldBe "foo==default"
  }

  it should "fail to build from a string if a relation is given but no version" in {
    an[Exception] should be thrownBy Requirement("foo==")
  }

  "Requirement.withDefaults" should "not change the requirements if not defaults are given" in {
    Requirement.withDefaults(requirements=Seq(Requirement("foo==0.1.1")), defaults=Seq.empty) shouldBe Seq(Requirement("foo==0.1.1"))
  }

  it should "not change the requirements if no requirements target a default" in {
    Requirement.withDefaults(
      requirements = Seq(Requirement("foo==1")),
      defaults     = Seq(Requirement("foo==2"))
    ) shouldBe Seq(Requirement("foo==1"))

    Requirement("foo==1").withDefaults(Seq(Requirement("foo==2"))) shouldBe Requirement("foo==1")
  }

  it should "change the requirements if a requirements targets a default" in {
    Requirement.withDefaults(
      requirements = Seq(Requirement("foo")),
      defaults     = Seq(Requirement("foo==2"))
    ) shouldBe Seq(Requirement("foo==2"))

    Requirement("foo").withDefaults(Seq(Requirement("foo==2"))) shouldBe Requirement("foo==2")
  }

  it should "fail if a default could not be found" in {
    an[Exception] should be thrownBy Requirement.withDefaults(requirements=Seq(Requirement("foo")), defaults=Seq.empty)
    an[Exception] should be thrownBy Requirement.withDefaults(requirements=Seq(Requirement("foo=default")), defaults=Seq.empty)
  }

  "Requirement.toDefault" should "convert a requirement to default" in {
    Requirement("foo", "0.1.1", ">=").toDefault.toString shouldBe s"foo$DefaultRelation$DefaultVersion"
    Requirement("foo", "0.1.1", "=").toDefault.toString shouldBe s"foo$DefaultRelation$DefaultVersion"
  }

  "Requirement.join" should "concatenate requirements when the parent and child requirements are distinct" in {
    Requirement.join(
      parent = Seq(Requirement("foo=1")),
      child  = Seq(Requirement("bar=1"))
    ) should contain theSameElementsInOrderAs Seq(Requirement("foo=1"), Requirement("bar=1"))
  }

  it should "discard child requirements with default versions if there is a parent requirement with the same name" in {
    Requirement.join(
      parent = Seq(Requirement("foo=1")),
      child  = Seq(Requirement("foo"))
    ) should contain theSameElementsInOrderAs Seq(Requirement("foo=1"))
  }

  it should "override parent requirements with a non-default child requirement with the same name" in {
    Requirement.join(
      parent = Seq(Requirement("foo=1")),
      child  = Seq(Requirement("foo=2"))
    ) should contain theSameElementsInOrderAs Seq(Requirement("foo=2"))
  }

  it should "log at warning level if there are two requirements for the same package" in {
    Logger.level = LogLevel.Warning
    val logging: LoggerString = captureLogger { () =>
      Requirement.join(
        parent = Seq(Requirement("foo=1")),
        child = Seq(Requirement("foo=2"))
      )
    }
    Logger.level = LogLevel.Info
    logging should include ("Overriding parent requirement foo=1 with child requirement foo=2")
  }

  it should "inherit the parent requirement when the child has a default requirement" in {
    Requirement.join(
      parent = Seq(Requirement("foo=1")),
      child  = Seq(Requirement("foo")) // non-default
    ) should contain theSameElementsInOrderAs Seq(Requirement("foo=1"))
  }


  "Requirement.encoder" should "return an encoder" in {
    implicit val encoder: Encoder[Requirement] = Requirement.encoder
    Requirement("foo").asJson.toString shouldBe """"foo==default""""
    Requirement("foo", "1.2", ">=").asJson.toString shouldBe """"foo>=1.2""""
  }

  "Requirement.decoder" should "return an decoder" in {
    implicit val decoder: Decoder[Requirement] = Requirement.decoder
    toJson("foo==default").as[Requirement].rightValue shouldBe Requirement("foo")
    toJson("foo>=1.2").as[Requirement].rightValue shouldBe Requirement("foo", "1.2", ">=")
  }
}
