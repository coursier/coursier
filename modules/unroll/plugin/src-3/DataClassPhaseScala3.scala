// SPDX-License-Identifier: MIT

// Local addition, not part of upstream com-lihaoyi/unroll.

package unroll

import dotty.tools.dotc.*
import plugins.*
import core.*
import Contexts.*
import Symbols.*
import Flags.*
import Decorators.*
import DenotTransformers.IdentityDenotTransformer
import ast.Trees.*
import ast.tpd
import StdNames.nme
import Names.*
import Constants.Constant

/**
 * Implements some arguments of the data-class `@data` annotation on Scala 3, where `@data` is
 * otherwise a no-op on case classes (see modules/unroll/dataclass):
 *  - `cachedHashCode = true`: like the data-class macro on Scala 2, the `hashCode` of the
 *    annotated case class gets cached in a private field, 0 standing for "not computed yet"
 *    (a computed 0 is stored as 1);
 *  - `settersCallApply = true`: the compiler-generated `copy` and `fromProduct` create instances
 *    via the companion `apply` with the constructor's signature rather than via the constructor,
 *    so that a hand-written `apply` (interning instances, say) sees every instance creation
 *    going through these. The unroll forwarders of `copy` go through it too, as they call it.
 */
class DataClassPhaseScala3 extends PluginPhase with IdentityDenotTransformer {
  import tpd.*

  val phaseName = "dataclass"

  override val runsAfter  = Set(dotty.tools.dotc.transform.Pickler.name)
  // fromProduct gets rewritten here before unroll2 expands it
  override val runsBefore = Set("unroll2")

  private val dataAnnotationName = "dataclass.data"

  private def isTrue(tree: Tree): Boolean = tree match {
    case Literal(Constant(true)) => true
    case _                       => false
  }

  /** Whether the `@data` annotation of `cls`, if any, sets its `param` boolean argument */
  private def dataArgument(cls: Symbol, param: String)(using Context): Boolean =
    cls.annotations.exists { annot =>
      annot.symbol.fullName.toString == dataAnnotationName && {
        val args = annot.tree match {
          case Apply(_, args)           => args
          case Block(_, Apply(_, args)) => args
          case _                        => Nil
        }
        val named = args.exists {
          case NamedArg(name, arg) => name.toString == param && isTrue(arg)
          case _                   => false
        }
        // after typing, named arguments are usually reordered into positional ones
        val paramNames = annot.symbol.primaryConstructor.info.firstParamNames
        val idx        = paramNames.indexWhere(_.toString == param)
        named || (idx >= 0 && args.lift(idx).exists(isTrue))
      }
    }

  override def transformTemplate(tmpl: Template)(using Context): Tree = {
    val cls = tmpl.constr.symbol.owner
    if (!cls.isClass || !cls.is(CaseClass) || cls.is(Module)) tmpl
    else {
      val tmpl0 =
        if (dataArgument(cls, "settersCallApply")) constructViaApply(cls.asClass, tmpl)
        else tmpl
      if (dataArgument(cls, "cachedHashCode")) cacheHashCode(cls.asClass, tmpl0)
      else tmpl0
    }
  }

  /** The hand-written companion `apply` with the constructor's signature, if any */
  private def constructorLikeApply(cls: ClassSymbol)(using Context): Option[Symbol] = {
    val constructor = cls.primaryConstructor
    cls.companionModule.info.decls.lookupAll(nme.apply).find { s =>
      s.is(Method) && !s.is(Synthetic) && s.info.matches(constructor.info)
    }
  }

  /** Replaces a `new C[T](args)(moreArgs)` call by `C.apply[T](args)(moreArgs)` */
  private def viaApply(tree: Tree, cls: ClassSymbol, apply: Symbol)(using Context): Option[Tree] =
    tree match {
      case Apply(fn, args)       => viaApply(fn, cls, apply).map(_.appliedToArgs(args))
      case TypeApply(fn, targs)  => viaApply(fn, cls, apply).map(_.appliedToTypeTrees(targs))
      case Select(New(_), nme.CONSTRUCTOR) => Some(ref(cls.companionModule).select(apply))
      case _                     => None
    }

  private def constructViaApply(cls: ClassSymbol, tmpl: Template)(using Context): Template =
    constructorLikeApply(cls) match {
      case None => tmpl
      case Some(apply) =>
        val rewrittenTemplate = cpy.Template(tmpl)(body = tmpl.body.map {
          case dd: DefDef if dd.symbol.is(Synthetic) && dd.name == nme.copy =>
            viaApply(dd.rhs, cls, apply).map(rhs => cpy.DefDef(dd)(rhs = rhs)).getOrElse(dd)
          case t => t
        })
        rewrittenTemplate
    }

  override def transformDefDef(dd: DefDef)(using Context): Tree = {
    // fromProduct lives in the companion of the case class
    val owner = dd.symbol.owner
    val cls   = owner.companionClass
    if (
      dd.name == nme.fromProduct && dd.symbol.is(Synthetic) && owner.is(Module) &&
      cls.exists && cls.is(CaseClass) && dataArgument(cls, "settersCallApply")
    )
      constructorLikeApply(cls.asClass) match {
        case None => dd
        case Some(apply) =>
          // `new C(p.productElement(0).asInstanceOf[...], ...)`, possibly in a block
          val rhs = dd.rhs match {
            case Block(stats, expr) =>
              viaApply(expr, cls.asClass, apply).map(e => cpy.Block(dd.rhs)(stats, e))
            case expr =>
              viaApply(expr, cls.asClass, apply)
          }
          rhs.map(r => cpy.DefDef(dd)(rhs = r)).getOrElse(dd)
      }
    else dd
  }

  private def cacheHashCode(clazz: ClassSymbol, tmpl: Template)(using Context): Template = {
    val cls = clazz
    {
      // only the compiler-generated hashCode gets cached, a hand-written one is left alone
      val syntheticHashCode = tmpl.body.collectFirst {
        case dd: DefDef if dd.name == nme.hashCode_ && dd.symbol.is(Synthetic) => dd
      }
      syntheticHashCode match {
        case None => tmpl
        case Some(hashDef) =>
          val field = newSymbol(
            clazz,
            termName("hashCode$cached"),
            Private | Local | Mutable | Synthetic,
            defn.IntType,
            coord = hashDef.span
          ).enteredAfter(this).asTerm
          val fieldDef = ValDef(field, Literal(Constant(0)))

          val computed =
            newSymbol(hashDef.symbol, termName("computed"), Synthetic, defn.IntType, coord = hashDef.span).asTerm
          def fieldRef        = This(clazz).select(field)
          def isZero(t: Tree) = t.select(defn.Int_==).appliedTo(Literal(Constant(0)))

          val rhs = Block(
            List(
              If(
                isZero(fieldRef),
                Block(
                  List(ValDef(computed, hashDef.rhs)),
                  Assign(fieldRef, If(isZero(ref(computed)), Literal(Constant(1)), ref(computed)))
                ),
                unitLiteral
              )
            ),
            fieldRef
          )
          val cachedHashDef = cpy.DefDef(hashDef)(rhs = rhs)
          cpy.Template(tmpl)(body = fieldDef :: tmpl.body.map(t => if (t eq hashDef) cachedHashDef else t))
      }
    }
  }
}
