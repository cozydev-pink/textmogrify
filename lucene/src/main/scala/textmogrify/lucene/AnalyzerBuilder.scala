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

import textmogrify.Config
import scala.jdk.CollectionConverters._

import cats.effect.kernel.{Resource, Sync}
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.snowball.SnowballFilter
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.analysis.en.PorterStemFilter
import org.apache.lucene.analysis.es.SpanishLightStemFilter
import org.apache.lucene.analysis.fr.FrenchLightStemFilter
import org.apache.lucene.analysis.it.ItalianLightStemFilter
import org.apache.lucene.analysis.de.GermanLightStemFilter
import org.apache.lucene.analysis.pt.PortugueseLightStemFilter
import org.apache.lucene.analysis.br.BrazilianStemFilter
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.StopFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.en.EnglishAnalyzer.{getDefaultStopSet => getEnglishStopSet}
import org.apache.lucene.analysis.fr.FrenchAnalyzer.{getDefaultStopSet => getFrenchStopSet}
import org.apache.lucene.analysis.es.SpanishAnalyzer.{getDefaultStopSet => getSpanishStopSet}
import org.apache.lucene.analysis.it.ItalianAnalyzer.{getDefaultStopSet => getItalianStopSet}
import org.apache.lucene.analysis.de.GermanAnalyzer.{getDefaultStopSet => getGermanStopSet}
import org.apache.lucene.analysis.nl.DutchAnalyzer.{getDefaultStopSet => getDutchStopSet}
import org.apache.lucene.analysis.pt.PortugueseAnalyzer.{getDefaultStopSet => getPortugueseStopSet}
import org.apache.lucene.analysis.br.BrazilianAnalyzer.{
  getDefaultStopSet => getBrazilianPortugueseStopSet
}

/** Build an Analyzer or tokenizer function */
sealed abstract class AnalyzerBuilder private[lucene] (config: Config) {
  type Builder <: AnalyzerBuilder

  private[lucene] def toSet(cs: CharArraySet): Set[String] =
    cs.asScala.map(ca => String.valueOf(ca.asInstanceOf[Array[Char]])).toSet

  /** A convenience value for debugging or investigating, to inspect the Lucene default stop words.
    * This set is immutable, and unused; it is the underlying Lucene `CharArraySet` that we use to
    * build the default StopFilter
    */
  def defaultStopWords: Set[String]

  def withConfig(config: Config): Builder

  /** Adds a lowercasing stage to the analyzer pipeline */
  def withLowerCasing: Builder =
    withConfig(config.withLowerCasing)

  /** Adds an ASCII folding stage to the analyzer pipeline
    * ASCII folding converts alphanumeric and symbolic Unicode characters into
    * their ASCII equivalents, if one exists.
    */
  def withASCIIFolding: Builder =
    withConfig(config.withASCIIFolding)

  def withDefaultStopWords: Builder =
    withConfig(config.withDefaultStopWords)

  /** Adds a stop filter stage to analyzer pipeline for non-empty sets. */
  def withCustomStopWords(words: Set[String]): Builder =
    withConfig(config.withCustomStopWords(words))

  /** Build the Analyzer wrapped inside a Resource. */
  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer]

  /** Build a tokenizing function that uses the Analyzer and collects tokens in a vector */
  def tokenizer[F[_]](implicit F: Sync[F]): Resource[F, String => F[Vector[String]]] =
    Tokenizer.vectorTokenizer(build)

  private[lucene] def mkFromStandardTokenizer[F[_]](
      config: Config
  )(extras: TokenStream => TokenStream)(implicit F: Sync[F]): Resource[F, Analyzer] =
    Resource.make(F.delay(new Analyzer {
      protected def createComponents(fieldName: String): TokenStreamComponents = {
        val source = new StandardTokenizer()
        var tokens = if (config.lowerCase) new LowerCaseFilter(source) else source
        tokens = if (config.foldASCII) new ASCIIFoldingFilter(tokens) else tokens
        tokens =
          if (config.customStopWords.isEmpty) tokens
          else {
            val stopSet = new CharArraySet(config.customStopWords.size, true)
            config.customStopWords.foreach(w => stopSet.add(w))
            new StopFilter(tokens, stopSet)
          }
        new TokenStreamComponents(source, extras(tokens))
      }
    }))(analyzer => F.delay(analyzer.close()))

}
object AnalyzerBuilder {
  def default: DefaultAnalyzerBuilder =
    new DefaultAnalyzerBuilder(Config.empty)
  def english: EnglishAnalyzerBuilder =
    new EnglishAnalyzerBuilder(Config.empty, false)
  def french: FrenchAnalyzerBuilder =
    new FrenchAnalyzerBuilder(Config.empty, false)
  def german: GermanAnalyzerBuilder =
    new GermanAnalyzerBuilder(Config.empty, false)
  def dutch: DutchAnalyzerBuilder =
    new DutchAnalyzerBuilder(Config.empty, false)
  def brazilianPortuguese: BrazilianPortugueseAnalyzerBuilder =
    new BrazilianPortugueseAnalyzerBuilder(Config.empty, false)
  def portuguese: PortugueseAnalyzerBuilder =
    new PortugueseAnalyzerBuilder(Config.empty, false)
  def italian: ItalianAnalyzerBuilder =
    new ItalianAnalyzerBuilder(Config.empty, false)
  def spanish: SpanishAnalyzerBuilder =
    new SpanishAnalyzerBuilder(Config.empty, false)
}

final class DefaultAnalyzerBuilder private[lucene] (config: Config)
    extends AnalyzerBuilder(config) { self =>
  type Builder = DefaultAnalyzerBuilder

  val defaultStopWords: Set[String] = Set.empty

  def withConfig(newConfig: Config): DefaultAnalyzerBuilder =
    new DefaultAnalyzerBuilder(newConfig)

  def english: EnglishAnalyzerBuilder =
    new EnglishAnalyzerBuilder(config, false)

  def french: FrenchAnalyzerBuilder =
    new FrenchAnalyzerBuilder(config, false)

  def german: GermanAnalyzerBuilder =
    new GermanAnalyzerBuilder(config, false)

  def dutch: DutchAnalyzerBuilder =
    new DutchAnalyzerBuilder(config, false)

  def brazilianPortuguese: BrazilianPortugueseAnalyzerBuilder =
    new BrazilianPortugueseAnalyzerBuilder(config, false)

  def portuguese: PortugueseAnalyzerBuilder =
    new PortugueseAnalyzerBuilder(config, false)

  def italian: ItalianAnalyzerBuilder =
    new ItalianAnalyzerBuilder(config, false)

  def spanish: SpanishAnalyzerBuilder =
    new SpanishAnalyzerBuilder(config, false)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config)(identity)
}

final class EnglishAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = EnglishAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): EnglishAnalyzerBuilder =
    new EnglishAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): EnglishAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getEnglishStopSet())

  /** Adds the Porter Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withPorterStemmer: EnglishAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens =
        if (self.config.defaultStopWords) new StopFilter(ts, getEnglishStopSet()) else ts
      if (self.stemmer) new PorterStemFilter(tokens) else tokens
    }
}

final class FrenchAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = FrenchAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): FrenchAnalyzerBuilder =
    new FrenchAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): FrenchAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getFrenchStopSet())

  /** Adds the FrenchLight Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withFrenchLightStemmer: FrenchAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens = if (self.config.defaultStopWords) new StopFilter(ts, getFrenchStopSet()) else ts
      if (self.stemmer) new FrenchLightStemFilter(tokens) else tokens
    }
}

final class SpanishAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = SpanishAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): SpanishAnalyzerBuilder =
    new SpanishAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): SpanishAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getSpanishStopSet())

  /** Adds the SpanishLight Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withSpanishLightStemmer: SpanishAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens = if (self.config.defaultStopWords) new StopFilter(ts, getSpanishStopSet()) else ts
      if (self.stemmer) new SpanishLightStemFilter(tokens) else tokens
    }
}

final class ItalianAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = ItalianAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): ItalianAnalyzerBuilder =
    new ItalianAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): ItalianAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getItalianStopSet())

  /** Adds the ItalianLight Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withItalianLightStemmer: ItalianAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens = if (self.config.defaultStopWords) new StopFilter(ts, getItalianStopSet()) else ts
      if (self.stemmer) new ItalianLightStemFilter(tokens) else tokens
    }
}

final class GermanAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = GermanAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): GermanAnalyzerBuilder =
    new GermanAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): GermanAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getGermanStopSet())

  /** Adds the GermanLight Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withGermanLightStemmer: GermanAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens = if (self.config.defaultStopWords) new StopFilter(ts, getGermanStopSet()) else ts
      if (self.stemmer) new GermanLightStemFilter(tokens) else tokens
    }
}

final class DutchAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = DutchAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): DutchAnalyzerBuilder =
    new DutchAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): DutchAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getDutchStopSet())

  /** Adds the Dutch Snowball Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withDutchStemmer: DutchAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens = if (self.config.defaultStopWords) new StopFilter(ts, getDutchStopSet()) else ts
      if (self.stemmer) {
        new SnowballFilter(ts, new org.tartarus.snowball.ext.DutchStemmer())
      } else tokens
    }
}

final class PortugueseAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = PortugueseAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): PortugueseAnalyzerBuilder =
    new PortugueseAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): PortugueseAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getPortugueseStopSet())

  /** Adds the PortugueseLight Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withPortugueseLightStemmer: PortugueseAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens =
        if (self.config.defaultStopWords) new StopFilter(ts, getPortugueseStopSet()) else ts
      if (self.stemmer) new PortugueseLightStemFilter(tokens) else tokens
    }
}

final class BrazilianPortugueseAnalyzerBuilder private[lucene] (
    config: Config,
    stemmer: Boolean,
) extends AnalyzerBuilder(config) { self =>
  type Builder = BrazilianPortugueseAnalyzerBuilder

  private def copy(
      newConfig: Config,
      stemmer: Boolean = self.stemmer,
  ): BrazilianPortugueseAnalyzerBuilder =
    new BrazilianPortugueseAnalyzerBuilder(newConfig, stemmer)

  def withConfig(newConfig: Config): BrazilianPortugueseAnalyzerBuilder =
    copy(newConfig = newConfig)

  lazy val defaultStopWords: Set[String] = toSet(getBrazilianPortugueseStopSet())

  /** Adds the Brazilian Stemmer to the end of the analyzer pipeline and enables lowercasing.
    * Stemming reduces words like `jumping` and `jumps` to their root word `jump`.
    * NOTE: Lowercasing is forced as it is required by most Lucene stemmers.
    */
  def withBrazilianStemmer: BrazilianPortugueseAnalyzerBuilder =
    copy(config.copy(lowerCase = true), stemmer = true)

  def build[F[_]](implicit F: Sync[F]): Resource[F, Analyzer] =
    mkFromStandardTokenizer(config) { ts =>
      val tokens =
        if (self.config.defaultStopWords) new StopFilter(ts, getBrazilianPortugueseStopSet())
        else ts
      if (self.stemmer) new BrazilianStemFilter(tokens) else tokens
    }
}
