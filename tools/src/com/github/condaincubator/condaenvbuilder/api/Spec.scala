package com.github.condaincubator.condaenvbuilder.api

import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._

import scala.annotation.tailrec

/** Stores the top level specification for the environment(s) to build.
  *
  * @param name the name of the specification
  * @param defaults the set of default steps
  * @param specs the conda environments to build
  */
case class Spec(name: String, defaults: Seq[Step] = Seq.empty, specs: Seq[EnvironmentSpec] = Seq.empty) {

  /** Returns the compiled version of this specification, with defaults and inherited steps applied. */
  def compiled: Spec = {
    val environments: Seq[Environment] = Spec.compile(
      specs        = specs,
      defaults     = defaults,
      environments = Seq.empty
    )
    this.copy(
      defaults = Seq.empty,
      specs    = environments.map(e => EnvironmentSpec(environment=e))
    )
  }
}

/** Example YAML encoding for [[Spec]]
  * {{{
  *   name: config_name
  *   environments:
  *     default:
  *       steps:
  *         ...
  *     snakemake:
  *       steps:
  *         ...
  *     fgbio:
  *       steps:
  *       - conda:
  *           channels:
  *           - conda-forge
  *           - bioconda
  *           requirements:
  *           - fgbio
  * }}}
  *
  * `name` is required.
  *
  */
object Spec {

  /** The name of the default environment. */
  val DefaultEnvironmentName: String = "defaults"

  import Encoders.EncodeEnvironment

  /** Returns an YAML encoder for [[Spec]] */
  def encoder: Encoder[Spec] = new Encoder[Spec] {
    final def apply(spec: Spec): Json = {
      if (spec.defaults.isEmpty) {
        Json.obj(
          ("name", Json.fromString(spec.name)),
          ("environments", Json.fromFields(spec.specs.map(s => (s.environment.name, s.environment.asJson))))
        )
      }
      else {
        Json.obj(
          ("name", Json.fromString(spec.name)),
          (DefaultEnvironmentName, Environment(name=DefaultEnvironmentName, steps=spec.defaults, group=DefaultEnvironmentName).asJson),
          ("environments", Json.fromFields(spec.specs.map(s => (s.environment.name, s.environment.asJson))))
        )
      }
    }
  }

  /** Returns a YAML decoder for [[Spec]] */
  def decoder: Decoder[Spec] = new Decoder[Spec] {
    import Decoders.{DecodeEnvironmentSpec, DecodeStep}

    final def apply(c: HCursor): Decoder.Result[Spec] = {
      // Get the keys at this level
      val keys: Seq[String] = c.keys.map(_.toSeq).getOrElse(Seq.empty)

      val nameResult: Result[String] = c.downField("name").as[String]

      val defaultsResult: Result[Seq[Step]] = {
        if (keys.contains(DefaultEnvironmentName)) c.downField(DefaultEnvironmentName).downField("steps").as[Seq[Step]]
        else Right(Seq.empty)
      }

      val specsResult: Result[Seq[EnvironmentSpec]] = {
        val cursor: ACursor                       = c.downField("environments")
        val names: Seq[String]                    = cursor.keys.map(_.toSeq).getOrElse(Seq.empty)
        val results: Seq[Result[EnvironmentSpec]] = names.map { name =>
          cursor.downField(name).as[EnvironmentSpec].map { environmentSpec: EnvironmentSpec =>
            // Update the name
            val group = {
              if (environmentSpec.environment.group == Environment.NotYetDecodedGroup) name
              else environmentSpec.environment.group
            }
            val environment = environmentSpec.environment.copy(name=name, group=group)
            environmentSpec.copy(environment=environment)
          }
        }

        // Convert the results into environments.  Look for the first failure, return that, otherwise return the root
        results.collectFirst { case left: Left[DecodingFailure, EnvironmentSpec] => left } match {
          case Some(Left(left)) => Left[DecodingFailure, Seq[EnvironmentSpec]](left)
          case None             => Right(results.collect { case Right(environment) => environment })
        }
      }

      for {
        name <- nameResult
        defaults <- defaultsResult
        specs <- specsResult
      } yield {
        specs.find(_.environment.name == DefaultEnvironmentName) match {
          case Some(environmentSpec) =>
            Spec(
              name     = name,
              defaults = environmentSpec.environment.steps ++ defaults,
              specs    = specs.filterNot(_.environment.name == DefaultEnvironmentName)
            )
          case None                  =>
            Spec(
              name     = name,
              defaults = defaults,
              specs    = specs
            )
        }
      }
    }
  }

  /** Compiles the list of environment specifications by applying the defaults and inherited steps.
    *
    * First searches for an environment specification that does not inherit from another.  Next, applies the defaults
    * to this environment.  Finally, inheritance is applied to other environment specifications yet to be compiled that
    * inherit from this environment specification.  This is continued until no more environments are left to be
    * compiled.
    *
    * @param specs the list of environment specifications yet to be compiled
    * @param defaults the default list of steps
    * @param environments the environments that have been compiled.
    * @throws IllegalArgumentException if an environment that does not inherit cannot be found.
    */
  @tailrec
  private[api] def compile(specs: Seq[EnvironmentSpec],
                           defaults: Seq[Step],
                           environments: Seq[Environment]): Seq[Environment] = {
    if (specs.isEmpty) environments else {
      specs.find(_.inherits.isEmpty) match {
        case None =>
          // NB: there are two possibilities here:
          // 1. There's an environment that inherits from a non-existent environment
          // 2. There's a cyclical dependency in the environment inheritance

          // 1. Find a spec that inherits from a non-existent environment
          val environmentNames = (specs.iterator.map(_.environment) ++ environments.iterator).map(_.name).toSet
          specs.foreach { spec =>
            spec.inherits.foreach { name =>
              if (!environmentNames.contains(name)) {
                throw new IllegalArgumentException(
                  s"The environment '${spec.environment.name}' inherits from a non-exist environment: '$name'"
                )
              }
            }
          }

          // 2. Find a cyclical inheritance
          val components = findConnectedComponents(specs)
          if (components.isEmpty) {
            throw new IllegalStateException("Bug: expected connected components, but did not find any, please report.")
          } else {
            val componentsString = components.map { component =>
              // NB: make sure to append the first spec to show the full cycle
              "\tcycle: " +
                (component :+ component.head).map(spec => s"'${spec.environment.name}'").mkString(" inherits from ")
            }.mkString("\n")
            throw new IllegalArgumentException(
              s"Found a cyclical dependency in the environment inheritance:\n$componentsString"
            )
          }
        case Some(spec) =>
          // Apply the defaults, if given, to the spec's environments
          val newEnvironment = if (defaults.isEmpty) spec.environment else spec.environment.withDefaults(defaults: _*)
          // Find all other specs that inherit from this spec, and apply inheritance
          val otherSpecs = specs.filterNot(_ == spec).map { _spec =>
            _spec.inheritFrom(environment=newEnvironment)
          }
          // Keep going
          compile(defaults=defaults, specs=otherSpecs, environments=newEnvironment +: environments)
      }
    }
  }

  private[api] def findConnectedComponents(specs: Seq[EnvironmentSpec]): Seq[IndexedSeq[EnvironmentSpec]] = {
    val specToInherits = specs.map { spec => spec -> spec.inherits }.toMap
    val envNameToSpec  = specs.map { spec => spec.environment.name -> spec }.toMap

    // The following finds connected components in a breadth first manner
    val visited    = scala.collection.mutable.HashSet[EnvironmentSpec]()
    val components = IndexedSeq.newBuilder[IndexedSeq[EnvironmentSpec]]
    specs.foreach { rootSpec =>
      if (!visited.contains(rootSpec)) {
        val remaining = scala.collection.mutable.Queue[EnvironmentSpec]()
        val component = Set.newBuilder[EnvironmentSpec]
        remaining.enqueue(rootSpec)
        while (remaining.nonEmpty) {
          // get the next pileup to examine
          val curSpec = remaining.dequeue()
          // add it to this component
          component += curSpec
          // find all unvisited neighbors
          val unvisited = specToInherits(curSpec).map(envNameToSpec(_)).filterNot(visited.contains)
          // add the unvisited neighbors to the queue
          remaining ++= unvisited
          // mark the unvisited neighbors as being visited so we don't enqueue them later
          unvisited.foreach(visited.add)
          // mark this pileup as visited
          visited.add(curSpec)
        }
        components += component.result().toIndexedSeq
      }
    }

    components.result()
  }
}
