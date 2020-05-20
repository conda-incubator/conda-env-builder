package com.github.nh13.condaenvbuilder.api

import com.fulcrumgenomics.commons.util.LazyLogging
import com.github.nh13.condaenvbuilder.api.Requirement.{DefaultRelation, DefaultVersion}
import io.circe.{Decoder, Encoder, Json}

import scala.util.Try

/** Represents a package requirement.
  *
  * @param name the name of the package
  * @param version the version
  * @param relation the relationship between the name and the version
  */
case class Requirement(name: String, version: String, relation: String) {

  require(name.nonEmpty, s"Name is empty")
  require(version.nonEmpty, s"Version is empty")
  require(relation.nonEmpty, s"Relation is empty")
  require(Requirement.Relations.contains(relation), s"Relation invalid: $relation")
  require(
    version != DefaultVersion || relation == DefaultRelation,
    s"Must use the relation '$DefaultRelation' when using the default version: '$DefaultVersion'"
  )

  /** Applies the list of default requirements.
    *
    * Only updated if the version is the default version
    *
    * @param defaults the list of default requirements
    * @return
    */
  def withDefaults(defaults: Seq[Requirement]): Requirement = {
    if (this.version != Requirement.DefaultVersion) this
    else defaults.find(_.name == this.name).getOrElse(this)
  }

  /** Applies the list of default requirements.
    *
    * Only updated if the version is the default version
    *
    * @param defaultsMap mapping of defaults from requirement name to requirement
    */
  def withDefaults(defaultsMap: Map[String, Requirement]): Requirement = {
    if (this.version != Requirement.DefaultVersion) this
    else defaultsMap.getOrElse(this.name, this)
  }

  /** Converts this requirement to specify that it should use the version from the defaults environment. */
  def toDefault: Requirement = this.copy(version=Requirement.DefaultVersion, relation=Requirement.DefaultRelation)

  /** The string representation (in a conda environments YAML) of this requirement. */
  override def toString: String = f"$name$relation$version"
}

object Requirement extends LazyLogging {
  /** The version to specify that the default verison in the default environment should be used. */
  val DefaultVersion: String = "default"

  /** The default relationship (equality). */
  val DefaultRelation: String = "=="

  /** Legal relationships. */
  val Relations: Seq[String] = Seq("!=", "==", ">=", "<=", ">", "<", "=")

  /** Builds a new [[Requirement]] from a string. */
  def apply(value: String): Requirement = {
    Relations.find(value.contains) match {
      case None           => Requirement(name=value, version=DefaultVersion, relation=DefaultRelation)
      case Some(relation) =>
        val Seq(name, version) = value.split(relation, 2).toSeq
        Requirement(name=name, version=version, relation=relation)
    }
  }

  /** Applies the list of default requirements to a list of requirements.
    *
    * Only requirements with the default version are updated.
    *
    * @param requirements the list of requirements to which to apply the defaults
    * @param defaultsMap mapping of defaults from requirement name to requirement
    */
  def withDefaults(requirements: Seq[Requirement], defaultsMap: Map[String, Requirement]): Seq[Requirement] = {
    val pkgs = requirements.map { pkg => pkg.withDefaults(defaultsMap=defaultsMap) }
    pkgs.find(_.version == Requirement.DefaultVersion).foreach { pkg =>
      throw new IllegalStateException(f"No default could be found for package: $pkg")
    }
    pkgs
  }

  /** Applies the list of default requirements to a list of requirements.
    *
    * Only requirements with the default version are updated.
    *
    * @param requirements the list of requirements to which to apply the defaults
    * @param defaults the list of default requirements
    * @return
    */
  def withDefaults(requirements: Seq[Requirement], defaults: Seq[Requirement]): Seq[Requirement] = {
    withDefaults(requirements=requirements, defaultsMap=defaults.map(default => default.name -> default).toMap)
  }

  /** Merges the sequence of parent requirements with the sequence of child requirements.
    *
    * First, child requirements with default versions are discarded if the parent has a requirement with the same name.
    * Next, the parent and child requirements are concatenated and made unique.  If there are two requirements with the
    * same name, an exception is thrown.
    *
    * @param parent the parent sequence of requirements
    * @param child the child sequence of requirements
    * @return the merged sequence of requirements
    */
  def join(parent: Seq[Requirement], child: Seq[Requirement]): Seq[Requirement] = {
    // find all requirements in the right list that have no version that are also in the left list
    val childrenToExamine = child.filterNot { r =>
      r.version == Requirement.DefaultVersion && parent.exists(_.name == r.name)
    }
    // Developer note: consider at some in point in the future to do package version comparison.  For now, just log it
    val requirements = (parent ++ childrenToExamine).distinct
    requirements.groupBy(_.name).iterator.filter(_._2.length > 1).foreach { case (name, pkgs) =>
      logger.debug(f"Found ${pkgs.length} package versions for '$name'")
    }
    requirements
  }

  /** Returns an YAML encoder for [[Requirement]] */
  def encoder: Encoder[Requirement] = new Encoder[Requirement] {
    final def apply(requirement: Requirement): Json = Json.fromString(requirement.toString)
  }

  /** Returns a YAML decoder for [[Requirement]] */
  def decoder: Decoder[Requirement] = Decoder.decodeString.emapTry { str =>
    Try(Requirement(str))
  }
}