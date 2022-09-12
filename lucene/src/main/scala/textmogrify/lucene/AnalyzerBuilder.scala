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

import cats.effect.kernel.{Resource, Sync}
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.analysis.en.PorterStemFilter
import org.apache.lucene.analysis.fr.FrenchLightStemFilter
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.StopFilter
import org.apache.lucene.analysis.TokenStream

final case class Config(
    lowerCase: Boolean,
    foldASCII: Boolean,
    stopWords: Set[String],
) {

  /** Adds a lowercasing stage to the analyzer pipeline */
  def withLowerCasing: Config =
    copy(lowerCase = true)

  /** Adds an ASCII folding stage to the analyzer pipeline
    * ASCII folding converts alphanumeric and symbolic Unicode characters into
    * their ASCII equivalents, if one exists.
    */
  def withASCIIFolding: Config =
    copy(foldASCII = true)

  /** Adds a stop filter stage to analyzer pipeline for non-empty sets.
    */
  def withStopWords(words: Set[String]): Config =
    copy(stopWords = words)

}
object Config {
  def empty: Config = Config(false, false, Set.empty)
}

/** Build an Analyzer or tokenizer function
  */
sealed abstract class AnalyzerBuilder private[lucene] (config: Config) {

  def withConfig(config: Config): AnalyzerBuilder

  /** Adds a lowercasing stage to the analyzer pipeline */
  def withLowerCasing: AnalyzerBuilder =
    withConfig(config.withLowerCasing)

  /** Adds an ASCII folding stage to the analyzer pipeline
    * ASCII folding converts alphanumeric and symbolic Unicode characters into
    * their ASCII equivalents, if one exists.
    */
  def withASCIIFolding: AnalyzerBuilder =
    withConfig(config.withASCIIFolding)

  /** Adds a stop filter stage to analyzer pipeline for non-empty sets.
    */
  def withStopWords(words: Set[String]): AnalyzerBuilder =
    withConfig(config.withStopWords(words))

  /** Build the Analyzer wrapped inside a Resource.
    */
  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer]

  /** Directly construct a tokenizing function
    */
  def tokenizer[F[_]](implicit F: Sync[F]): Resource[F, String => F[Vector[String]]] =
    build.map(a => Tokenizer.vectorTokenizer(a))

  private[lucene] def mkFromStandardTokenizer[F[_]](
      config: Config
  )(extras: TokenStream => TokenStream)(implicit F: Sync[F]): Resource[F, Analyzer] =
    Resource.make(F.delay(new Analyzer {
      protected def createComponents(fieldName: String): TokenStreamComponents = {
        val source = new StandardTokenizer()
        var tokens = if (config.lowerCase) new LowerCaseFilter(source) else source
        tokens = if (config.foldASCII) new ASCIIFoldingFilter(tokens) else tokens
        tokens =
          if (config.stopWords.isEmpty) tokens
          else {
            val stopSet = new CharArraySet(config.stopWords.size, true)
            config.stopWords.foreach(w => stopSet.add(w))
            new StopFilter(tokens, stopSet)
          }
        new TokenStreamComponents(source, extras(tokens))
      }
    }))(analyzer => F.delay(analyzer.close()))

}
object AnalyzerBuilder {
  def english: EnglishAnalyzerBuilder = new EnglishAnalyzerBuilder(
    config = Config.empty,
    stemmer = false,
  )
  def french: FrenchAnalyzerBuilder = new FrenchAnalyzerBuilder(
    config = Config.empty,
    stemmer = false,
  )
}

/** Build an Analyzer or tokenizer function
  */
final class EnglishAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): EnglishAnalyzerBuilder =
    new EnglishAnalyzerBuilder(
      config = newConfig,
      stemmer = stemmer,
    )

  def withConfig(newConfig: Config): EnglishAnalyzerBuilder =
    copy(newConfig = newConfig)

  /** Adds the Porter Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required for the Lucene PorterStemFilter.
    */
  def withPorterStemmer: EnglishAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  /** Build the Analyzer wrapped inside a Resource.
    */
  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config)(ts => if (self.stemmer) new PorterStemFilter(ts) else ts)
}

/** Build an Analyzer or tokenizer function
  */
final class FrenchAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): FrenchAnalyzerBuilder =
    new FrenchAnalyzerBuilder(
      config = newConfig,
      stemmer = stemmer,
    )

  def withConfig(newConfig: Config): FrenchAnalyzerBuilder =
    copy(newConfig = newConfig)

  /** Adds the FrenchLight Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required for the Lucene FrenchLightStemFilter.
    */
  def withFrenchLightStemmer: FrenchAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  /** Build the Analyzer wrapped inside a Resource.
    */
  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config)(ts => if (self.stemmer) new FrenchLightStemFilter(ts) else ts)
}
