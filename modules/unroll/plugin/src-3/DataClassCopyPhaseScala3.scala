// SPDX-License-Identifier: MIT

// Local addition, not part of upstream com-lihaoyi/unroll.

package unroll

import dotty.tools.dotc.*
import plugins.*
import core.*
import Contexts.*
import Contexts.atPhase
import Symbols.*
import Flags.*
import Decorators.*
import DenotTransformers.IdentityDenotTransformer
import NameKinds.DefaultGetterName
import Names.*
import StdNames.nme
import Types.*
import ast.Trees.*
import ast.{TreeTypeMap, tpd}

import scala.collection.mutable
import scala.language.implicitConversions

/**
 * Makes the compiler-generated `copy` of a `@data` case class with type parameters match the one
 * the data-class macro generates on Scala 2: it keeps the type parameters of the class, rather
 * than taking fresh ones of its own.
 *
 * The `copy` Scala 3 generates for
 * {{{
 *   @data case class FileCache[F[_]](location: File, …)(implicit val sync: Sync[F])
 * }}}
 * is `def copy[F[_]](location: File, …)(implicit sync: Sync[F]): FileCache[F]`, whose `F` nothing
 * constrains at call sites: `fileCache.copy(location = dir)` fails with "No given instance of type
 * Sync[F] was found", however precise the type of `fileCache` is. The data-class `copy` is
 * `def copy(location: File, …)(implicit sync: Sync[F]): FileCache[F]` instead, with the `F` of the
 * class, that `Sync[F]` is then looked up for.
 *
 * `copy$default$n`, that holds the default value of the n-th parameter of `copy`, gets the same
 * treatment, and the type arguments the type checker passed to both are dropped.
 *
 * The parameters of a trailing implicit clause also get the corresponding field as default value,
 * as they do on Scala 2, so that `copy` can be called where no implicit is in scope at all.
 *
 * Unlike the forwarders `unroll2` adds, this changes a signature callers see, so the phase runs
 * before the pickler rather than after it.
 */
class DataClassCopyPhaseScala3 extends PluginPhase with IdentityDenotTransformer {
  import tpd.*

  val phaseName = "dataclassCopy"

  override val runsAfter  = Set(dotty.tools.dotc.transform.PostTyper.name)
  override val runsBefore = Set(dotty.tools.dotc.transform.Pickler.name)

  // `copy$default$n` methods are added for the parameters of a trailing implicit clause
  override def changesMembers: Boolean = true

  private val dataAnnotationName = "dataclass.data"

  /**
   * The `copy` methods this run made monomorphic.
   *
   * Type arguments are only dropped for these: a `@data` case class read from a class path
   * compiled without this phase keeps a polymorphic `copy`, that its callers still pass type
   * arguments to.
   */
  private val monomorphized = new mutable.HashSet[Symbol]

  /** The `copy$default$n` methods this run adds, for the parameters of a trailing implicit clause */
  private val addedDefaultGetters = new mutable.HashMap[Symbol, List[tpd.DefDef]]

  private def hasDataAnnotation(cls: Symbol)(using Context): Boolean =
    cls.annotations.exists(_.symbol.fullName.toString == dataAnnotationName)

  /**
   * Whether `sym` is a `copy` (or `copy$default$n`) this phase makes monomorphic.
   *
   * Only depends on things this phase leaves alone, so that it holds both before and after the
   * signatures are changed.
   */
  private def isCopyOfDataClass(sym: Symbol)(using Context): Boolean =
    sym.exists && sym.isTerm && sym.is(Synthetic) && {
      val cls = sym.owner
      cls.isClass && cls.is(CaseClass) && !cls.is(Module) &&
      cls.typeParams.nonEmpty && hasDataAnnotation(cls) &&
      (sym.name == nme.copy ||
      (sym.name.is(DefaultGetterName) && sym.name.exclude(DefaultGetterName) == nme.copy))
    }

  /** The leading type parameter clause of `dd`, if it is the one of its class */
  private def classTypeParams(dd: DefDef)(using Context): Option[List[Symbol]] =
    dd.paramss.headOption
      .filter(ps => ps.headOption.exists(_.isInstanceOf[TypeDef]))
      .map(_.map(_.symbol))
      .filter(_.length == dd.symbol.owner.typeParams.length)

  private def toDrop(dd: DefDef)(using Context): Option[List[Symbol]] =
    if (isCopyOfDataClass(dd.symbol)) classTypeParams(dd) else None

  /** The type parameters of the class of `dd`, as seen from inside it */
  private def classTypeParamRefs(dd: DefDef)(using Context): List[Type] =
    dd.symbol.owner.typeParams.map(_.typeRef)

  // Signatures are changed for the whole run first: the trees the second pass rewrites are typed
  // against them, and a `copy` can be called from a unit compiled before the one defining it.
  override def runOn(units: List[CompilationUnit])(using Context): List[CompilationUnit] = {
    monomorphized.clear()
    addedDefaultGetters.clear()
    units.foreach(unit => new SignatureInstaller().traverse(unit.tpdTree))
    super.runOn(units)
  }

  override def run(using Context): Unit = {
    val unit = ctx.compilationUnit
    unit.tpdTree = atPhase(next)(new Rewriter().transform(unit.tpdTree))
  }

  /** The type `dd` gets once its type parameters are replaced by the `to` of its class */
  private def newInfo(dd: DefDef, to: List[Type])(using Context): Type =
    dd.symbol.info match {
      // `copy$default$n` is nullary, and instantiating its `PolyType` leaves a plain type: it has
      // to stay a method type, or the symbol stops being a method
      case pt: PolyType =>
        pt.instantiate(to) match {
          case methodic: MethodicType => methodic
          case result                 => ExprType(result)
        }
      case other => other
    }

  /**
   * Gives the parameters of the trailing implicit clause of `copy` the corresponding field as
   * default value, like the data-class macro does on Scala 2, so that calling `copy` doesn't
   * require an implicit to be in scope.
   *
   * Nothing is done for a parameter whose field the class doesn't keep.
   */
  private def addImplicitDefaults(dd: DefDef, copyInfo: Type)(using Context): Unit =
    copyInfo match {
      case mt: MethodType => mt.resType match {
          // only the shape the data-class macro handles: one plain clause, then an implicit one
          case implicits: MethodType
              if implicits.isImplicitMethod && !implicits.resType.isInstanceOf[MethodType] =>
            val cls     = dd.symbol.owner.asClass
            val getters = List.newBuilder[tpd.DefDef]
            for ((param, i) <- implicits.paramNames.zipWithIndex)
              fieldOf(cls, param).foreach { field =>
                // like any default of a parameter of a later clause, the getter takes the
                // parameters of the earlier ones
                val getterType = MethodType(mt.paramNames)(
                  getter => mt.paramInfos.map(_.substParams(mt, getter.paramRefs)),
                  getter => implicits.paramInfos(i).substParams(mt, getter.paramRefs)
                )
                val getter = Symbols.newSymbol(
                  cls,
                  DefaultGetterName(nme.copy, mt.paramNames.length + i),
                  Synthetic | Method,
                  getterType,
                  coord = dd.span
                ).enteredAfter(this).asTerm
                getters += tpd.DefDef(getter, _ => This(cls).select(field))
                val paramSymbol = dd.paramss.last(i).symbol
                paramSymbol.copySymDenotation(initFlags = paramSymbol.flags | HasDefault)
                  .installAfter(this)
              }
            val added = getters.result()
            if (added.nonEmpty) addedDefaultGetters(cls) = added
          case _ =>
        }
      case _ =>
    }

  /** The field of `cls` holding the constructor parameter named `name`, if it keeps one */
  private def fieldOf(cls: ClassSymbol, name: Name)(using Context): Option[Symbol] = {
    val field = cls.info.decls.lookup(name)
    Option.when(field.exists && field.isTerm && field.is(ParamAccessor) && !field.is(Method))(field)
  }

  /** Turns the `PolyType` of a `copy` into the method type it has once monomorphic */
  private class SignatureInstaller extends TreeTraverser {
    def traverse(tree: Tree)(using Context): Unit = {
      tree match {
        case dd: DefDef =>
          toDrop(dd).foreach { typeParams =>
            val phase      = DataClassCopyPhaseScala3.this
            val to         = classTypeParamRefs(dd)
            val paramSymss = dd.paramss.tail.map(_.map(_.symbol))
            for (params <- paramSymss; param <- params)
              param.copySymDenotation(info = param.info.subst(typeParams, to)).installAfter(phase)
            val info = newInfo(dd, to)
            dd.symbol.copySymDenotation(info = info, rawParamss = paramSymss).installAfter(phase)
            monomorphized += dd.symbol
            if (dd.name == nme.copy) addImplicitDefaults(dd, info)
          }
        case _ =>
      }
      traverseChildren(tree)
    }
  }

  /** Drops the type parameter clause of the `copy` methods, and the type arguments passed to them */
  private class Rewriter extends TreeMap {
    override def transform(tree: Tree)(using Context): Tree = tree match {
      case dd: DefDef if toDrop(dd).nonEmpty =>
        dropTypeParams(dd)
      case ta: TypeApply
          if monomorphized.contains(ta.fun.symbol) &&
          ta.args.length == ta.fun.symbol.owner.typeParams.length =>
        transform(ta.fun)
      case tmpl: Template if addedDefaultGetters.contains(tmpl.constr.symbol.owner) =>
        val transformed = super.transform(tmpl).asInstanceOf[Template]
        cpy.Template(transformed)(
          body = transformed.body ++ addedDefaultGetters(tmpl.constr.symbol.owner)
        )
      case _ =>
        super.transform(tree)
    }

    private def dropTypeParams(dd: DefDef)(using Context): DefDef = {
      val typeParams = toDrop(dd).get
      val to         = classTypeParamRefs(dd)
      val typeMap    = new TreeTypeMap(typeMap = _.subst(typeParams, to))
      def mapTree(tree: Tree): Tree = if (tree.isEmpty) tree else typeMap.transform(tree)

      val newParamss: List[ParamClause] = dd.paramss.tail.map { ps =>
        ps.map { p =>
          val param = p.asInstanceOf[ValDef]
          cpy.ValDef(param)(tpt = mapTree(param.tpt), rhs = mapTree(param.rhs))
        }
      }

      cpy.DefDef(dd)(
        paramss = newParamss,
        tpt = mapTree(dd.tpt),
        rhs = mapTree(transform(dd.rhs))
      )
    }
  }
}
