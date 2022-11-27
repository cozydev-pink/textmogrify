/*
 * Copyright 2022 Pig.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package textmogrify.lucene

import cats.syntax.all._
import cats.effect.{IO, IOApp, Resource, Sync}
import fs2.{Stream, Pipe, Pull, Chunk}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.memory.MemoryIndex
import org.apache.lucene.queryparser.classic.QueryParser

sealed trait Indexable[A] {
  def keyValues(a: A): List[(String, String)]
}

sealed trait InMemory[F[_], A] {
  def score(query: String): Stream[F, A] => Stream[F, (A, Float)]
  def filter(query: String, threshold: Float = 0.0f): Stream[F, A] => Stream[F, A]
  def filterWithScore(
      query: String,
      threshold: Float = 0.0f,
  ): Stream[F, A] => Stream[F, (A, Float)]
}

sealed abstract class InMemoryBuilder[F[_], A] private[lucene] (
    defaultField: String,
    analyzer: Resource[F, Analyzer],
)(implicit F: Sync[F], idx: Indexable[A]) {

  private def copy(
      defaultField: String = defaultField,
      analyzer: Resource[F, Analyzer] = analyzer,
  ): InMemoryBuilder[F, A] =
    new InMemoryBuilder[F, A](defaultField, analyzer) {}

  def withAnalyzer(analyzerR: Resource[F, Analyzer]): InMemoryBuilder[F, A] =
    copy(analyzer = analyzerR)

  def withDefaultField(field: String): InMemoryBuilder[F, A] =
    copy(defaultField = field)

  def build: Resource[F, InMemory[F, A]] = {
    val indexR = Resource.eval(F.delay(new MemoryIndex()))
    (analyzer, indexR).mapN { case (analyzer, index) =>
      new InMemory[F, A] {
        private[this] def reset() = index.reset()

        private[this] def goStream[B](
            s: Stream[F, A]
        )(goChunk: Chunk[A] => Chunk[B]): Pull[F, B, Unit] =
          s.pull.uncons.flatMap {
            case Some((hd, tl)) => Pull.output(goChunk(hd)) >> goStream(tl)(goChunk)
            case None => reset(); Pull.done
          }
        private[this] def indexAndScore(query: String)(d: A): Float = {
          val parser = new QueryParser(defaultField, analyzer) // Not thread safe
          idx.keyValues(d).foreach { case (k, v) => index.addField(k, v, analyzer) }
          val score = index.search(parser.parse(query))
          reset()
          score
        }
        private[this] def fromGoChunk[B](goChunk: Chunk[A] => Chunk[B]): Pipe[F, A, B] =
          in => goStream(in)(goChunk).stream

        def score(query: String): Stream[F, A] => Stream[F, (A, Float)] =
          fromGoChunk { c =>
            c.map { d =>
              (d, indexAndScore(query)(d))
            }
          }

        def filter(query: String, threshold: Float = 0.0f): Stream[F, A] => Stream[F, A] =
          fromGoChunk { c =>
            c.filter { d =>
              indexAndScore(query)(d) > threshold
            }
          }

        def filterWithScore(
            query: String,
            threshold: Float = 0.0f,
        ): Stream[F, A] => Stream[F, (A, Float)] =
          fromGoChunk { c =>
            c.mapFilter { d =>
              val score = indexAndScore(query)(d)
              if (score > threshold)
                Some((d, score))
              else None
            }
          }
      }
    }
  }
}
object InMemoryBuilder {
  def default[F[_], A: Indexable](fieldName: String)(implicit F: Sync[F]): InMemoryBuilder[F, A] =
    new InMemoryBuilder[F, A](fieldName, AnalyzerBuilder.default.build) {}
}

object InMemoryApp extends IOApp.Simple {

  case class Doc(author: String, title: String)
  object Doc {
    implicit val indexableDoc: Indexable[Doc] = new Indexable[Doc] {
      def keyValues(a: Doc): List[(String, String)] =
        ("title", a.title) :: ("author", a.author) :: Nil
    }
  }

  val docStream = Stream[IO, Doc](
    Doc("Tales of James", "Readings about Salmons and other select Alaska fishing Manuals"),
    Doc("Tales of Andrew", "Readings about Salmons and other select Alaska fishing Manuals"),
    Doc("james stuff", "salmon are great fish, learn cook em' in this manual"),
  )

  val memory = InMemoryBuilder
    .default[IO, Doc](fieldName = "title")
    .withAnalyzer(AnalyzerBuilder.english.withLowerCasing.build)
    .build

  val query = "author:james AND salmon~"

  val run =
    memory.use(mem => mem.filterWithScore(query)(docStream).evalMap(IO.println).compile.drain)
}
