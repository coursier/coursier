package coursier.maven

import coursier.core.{Classifier, Variant}
import utest._

object GradleModuleTests extends TestSuite {

  def createGradleModule(variants: Seq[GradleModule.Variant]): GradleModule =
    GradleModule(
      formatVersion = "1.1",
      component = GradleModule.Component(
        group = "org.example",
        module = "test-artifact",
        version = "1.0.0"
      ),
      variants = variants
    )

  def attrs(pairs: (String, String)*): Map[String, GradleModule.StringOrInt] =
    pairs.map { case (k, v) => k -> GradleModule.StringOrInt(v) }.toMap

  def file(name: String): GradleModule.ModuleFile =
    GradleModule.ModuleFile(name = name, url = name)

  val tests = Tests {

    test("variantPublications classifier extraction") {

      test("library variant should have no classifier") {
        val variant = GradleModule.Variant(
          name = "jvmRuntimeElements-published",
          attributes = attrs(
            "org.gradle.category" -> "library",
            "org.gradle.usage"    -> "java-runtime"
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("test-artifact-1.0.0.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val module  = createGradleModule(Seq(variant))
        val project = module.project(None)

        val publications =
          project.variantPublications.get(Variant.Attributes("jvmRuntimeElements-published"))
        assert(publications.isDefined)
        assert(publications.get.nonEmpty)

        val pub = publications.get.head
        assert(pub.classifier.isEmpty)
      }

      test("documentation variant with docstype=sources should have sources classifier") {
        val variant = GradleModule.Variant(
          name = "jvmSourcesElements-published",
          attributes = attrs(
            "org.gradle.category" -> "documentation",
            "org.gradle.docstype" -> "sources",
            "org.gradle.usage"    -> "java-runtime"
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("test-artifact-1.0.0-sources.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val module  = createGradleModule(Seq(variant))
        val project = module.project(None)

        val publications =
          project.variantPublications.get(Variant.Attributes("jvmSourcesElements-published"))
        assert(publications.isDefined)
        assert(publications.get.nonEmpty)

        val pub = publications.get.head
        assert(pub.classifier.contains(Classifier("sources")))
      }

      test("documentation variant with docstype=javadoc should have javadoc classifier") {
        val variant = GradleModule.Variant(
          name = "javadocElements",
          attributes = attrs(
            "org.gradle.category" -> "documentation",
            "org.gradle.docstype" -> "javadoc",
            "org.gradle.usage"    -> "java-runtime"
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("test-artifact-1.0.0-javadoc.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val module  = createGradleModule(Seq(variant))
        val project = module.project(None)

        val publications = project.variantPublications.get(Variant.Attributes("javadocElements"))
        assert(publications.isDefined)
        assert(publications.get.nonEmpty)

        val pub = publications.get.head
        assert(pub.classifier.contains(Classifier("javadoc")))
      }

      test("documentation variant with docstype=groovydoc should have groovydoc classifier") {
        val variant = GradleModule.Variant(
          name = "groovydocElements",
          attributes = attrs(
            "org.gradle.category" -> "documentation",
            "org.gradle.docstype" -> "groovydoc",
            "org.gradle.usage"    -> "java-runtime"
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("groovy-4.0.24-groovydoc.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val module  = createGradleModule(Seq(variant))
        val project = module.project(None)

        val publications = project.variantPublications.get(Variant.Attributes("groovydocElements"))
        assert(publications.isDefined)
        assert(publications.get.nonEmpty)

        val pub = publications.get.head
        assert(pub.classifier.contains(Classifier("groovydoc")))
      }

      test("documentation variant without docstype should have no classifier") {
        val variant = GradleModule.Variant(
          name = "docElements",
          attributes = attrs(
            "org.gradle.category" -> "documentation"
            // No docstype attribute
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("test-artifact-1.0.0-docs.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val module  = createGradleModule(Seq(variant))
        val project = module.project(None)

        val publications = project.variantPublications.get(Variant.Attributes("docElements"))
        assert(publications.isDefined)
        assert(publications.get.nonEmpty)

        val pub = publications.get.head
        assert(pub.classifier.isEmpty)
      }

      test("mixed variants should have correct classifiers") {
        val libraryVariant = GradleModule.Variant(
          name = "jvmRuntimeElements",
          attributes = attrs(
            "org.gradle.category" -> "library",
            "org.gradle.usage"    -> "java-runtime"
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("test-artifact-1.0.0.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val sourcesVariant = GradleModule.Variant(
          name = "jvmSourcesElements",
          attributes = attrs(
            "org.gradle.category" -> "documentation",
            "org.gradle.docstype" -> "sources"
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("test-artifact-1.0.0-sources.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val javadocVariant = GradleModule.Variant(
          name = "javadocElements",
          attributes = attrs(
            "org.gradle.category" -> "documentation",
            "org.gradle.docstype" -> "javadoc"
          ),
          dependencies = Nil,
          dependencyConstraints = Nil,
          files = Seq(file("test-artifact-1.0.0-javadoc.jar")),
          `available-at` = None,
          capabilities = Nil
        )

        val module  = createGradleModule(Seq(libraryVariant, sourcesVariant, javadocVariant))
        val project = module.project(None)

        // Library variant - no classifier
        val libPubs = project.variantPublications.get(Variant.Attributes("jvmRuntimeElements"))
        assert(libPubs.isDefined)
        assert(libPubs.get.head.classifier.isEmpty)

        // Sources variant - sources classifier
        val srcPubs = project.variantPublications.get(Variant.Attributes("jvmSourcesElements"))
        assert(srcPubs.isDefined)
        assert(srcPubs.get.head.classifier.contains(Classifier("sources")))

        // Javadoc variant - javadoc classifier
        val docPubs = project.variantPublications.get(Variant.Attributes("javadocElements"))
        assert(docPubs.isDefined)
        assert(docPubs.get.head.classifier.contains(Classifier("javadoc")))
      }
    }
  }
}
