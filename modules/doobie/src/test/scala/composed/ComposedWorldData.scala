// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package composed

import cats.Monad
import cats.data.Ior
import cats.effect.Sync
import cats.implicits._
import doobie.Transactor

import edu.gemini.grackle._, doobie._
import Query._, Predicate._, Value._
import QueryCompiler._
import QueryInterpreter.mkErrorResult
import DoobiePredicate._

/* Currency component */

object CurrencyData {
  case class Currency(
    code: String,
    exchangeRate: Double,
    countryCode: String
  )

  val BRL = Currency("BRL", 0.25, "BRA")
  val EUR = Currency("EUR", 1.12, "NLD")
  val GBP = Currency("GBP", 1.25, "GBR")

  val currencies = List(BRL, EUR, GBP)

}

class CurrencyMapping[F[_] : Monad] extends ValueMapping[F] {
  import CurrencyData._

  val schema =
    Schema(
      """
        type Query {
          allCurrencies: [Currency!]!
        }
        type Currency {
          code: String!
          exchangeRate: Float!
          countryCode: String!
        }
      """
    ).right.get

  val QueryType = schema.ref("Query")
  val CurrencyType = schema.ref("Currency")

  val typeMappings =
    List(
      ObjectMapping(
        tpe = QueryType,
        fieldMappings =
          List(
            ValueRoot("allCurrencies", currencies)
          )
      ),
      ValueObjectMapping[Currency](
        tpe = CurrencyType,
        fieldMappings =
          List(
            ValueField("code", _.code),
            ValueField("exchangeRate", _.exchangeRate),
            ValueField("countryCode", _.countryCode)
          )
      )
  )
}

object CurrencyMapping {
  def apply[F[_] : Monad]: CurrencyMapping[F] = new CurrencyMapping[F]
}

/* World component */

trait WorldMapping[F[_]] extends DoobieMapping[F] {
  import DoobieFieldMapping._

  val schema =
    Schema(
      """
        type Query {
          cities(namePattern: String = "%"): [City!]
          country(code: String): Country
          countries: [Country!]
        }
        type City {
          name: String!
          country: Country!
          district: String!
          population: Int!
        }
        type Language {
          language: String!
          isOfficial: Boolean!
          percentage: Float!
          countries: [Country!]!
        }
        type Country {
          name: String!
          continent: String!
          region: String!
          surfacearea: Float!
          indepyear: Int
          population: Int!
          lifeexpectancy: Float
          gnp: String
          gnpold: String
          localname: String!
          governmentform: String!
          headofstate: String
          capitalId: Int
          code2: String!
          cities: [City!]!
          languages: [Language!]!
        }
      """
    ).right.get

  val QueryType = schema.ref("Query")
  val CountryType = schema.ref("Country")
  val CityType = schema.ref("City")
  val LanguageType = schema.ref("Language")

  val typeMappings =
    List(
      ObjectMapping(
        tpe = QueryType,
        fieldMappings =
          List(
            DoobieRoot("cities"),
            DoobieRoot("country"),
            DoobieRoot("countries")
          )
      ),
      ObjectMapping(
        tpe = CountryType,
        fieldMappings =
          List(
            DoobieAttribute[String]("code", ColumnRef("country", "code"), key = true),
            DoobieField("name", ColumnRef("country", "name")),
            DoobieField("continent", ColumnRef("country", "continent")),
            DoobieField("region", ColumnRef("country", "region")),
            DoobieField("surfacearea", ColumnRef("country", "surfacearea")),
            DoobieField("indepyear", ColumnRef("country", "indepyear")),
            DoobieField("population", ColumnRef("country", "population")),
            DoobieField("lifeexpectancy", ColumnRef("country", "lifeexpectancy")),
            DoobieField("gnp", ColumnRef("country", "gnp")),
            DoobieField("gnpold", ColumnRef("country", "gnpold")),
            DoobieField("localname", ColumnRef("country", "localname")),
            DoobieField("governmentform", ColumnRef("country", "governmentform")),
            DoobieField("headofstate", ColumnRef("country", "headofstate")),
            DoobieField("capitalId", ColumnRef("country", "capitalId")),
            DoobieField("code2", ColumnRef("country", "code2")),
            DoobieObject("cities", Subobject(
              List(Join(ColumnRef("country", "code"), ColumnRef("city", "countrycode"))))),
            DoobieObject("languages", Subobject(
              List(Join(ColumnRef("country", "code"), ColumnRef("countryLanguage", "countrycode")))))
          )
      ),
      ObjectMapping(
        tpe = CityType,
        fieldMappings =
          List(
            DoobieAttribute[Int]("id", ColumnRef("city", "id"), key = true),
            DoobieAttribute[String]("countrycode", ColumnRef("city", "countrycode")),
            DoobieField("name", ColumnRef("city", "name")),
            DoobieObject("country", Subobject(
              List(Join(ColumnRef("city", "countrycode"), ColumnRef("country", "code"))))),
            DoobieField("district", ColumnRef("city", "district")),
            DoobieField("population", ColumnRef("city", "population"))
          )
      ),
      ObjectMapping(
        tpe = LanguageType,
        fieldMappings =
          List(
            DoobieField("language", ColumnRef("countryLanguage", "language"), key = true),
            DoobieField("isOfficial", ColumnRef("countryLanguage", "isOfficial")),
            DoobieField("percentage", ColumnRef("countryLanguage", "percentage")),
            DoobieAttribute[String]("countrycode", ColumnRef("countryLanguage", "countrycode")),
            DoobieObject("countries", Subobject(
              List(Join(ColumnRef("countryLanguage", "countrycode"), ColumnRef("country", "code")))))
          )
      )
    )
}

object WorldMapping extends DoobieMappingCompanion {
  def mkMapping[F[_] : Sync](transactor: Transactor[F], monitor: DoobieMonitor[F]): WorldMapping[F] =
    new DoobieMapping(transactor, monitor) with WorldMapping[F]
}

/* Composition */

class ComposedMapping[F[_] : Monad]
  (world: Mapping[F], currency: Mapping[F]) extends Mapping[F] {
  val schema =
    Schema(
      """
        type Query {
          cities(namePattern: String = "%"): [City!]
          country(code: String): Country
          countries: [Country!]
          currencies: [Currency!]!
        }
        type City {
          name: String!
          country: Country!
          district: String!
          population: Int!
        }
        type Language {
          language: String!
          isOfficial: Boolean!
          percentage: Float!
          countries: [Country!]!
        }
        type Currency {
          code: String!
          exchangeRate: Float!
          countryCode: String!
        }
        type Country {
          name: String!
          continent: String!
          region: String!
          surfacearea: Float!
          indepyear: Int
          population: Int!
          lifeexpectancy: Float
          gnp: String
          gnpold: String
          localname: String!
          governmentform: String!
          headofstate: String
          capitalId: Int
          code2: String!
          cities: [City!]!
          languages: [Language!]!
          currencies: [Currency!]!
        }
      """
    ).right.get

  val QueryType = schema.ref("Query")
  val CountryType = schema.ref("Country")

  val typeMappings =
    List(
      ObjectMapping(
        tpe = QueryType,
        fieldMappings =
          List(
            Delegate("country", world),
            Delegate("countries", world),
            Delegate("cities", world)
          )
      ),
      ObjectMapping(
        tpe = CountryType,
        fieldMappings =
          List(
            Delegate("currencies", currency, countryCurrencyJoin)
          )
      )
    )

  def countryCurrencyJoin(c: Cursor, q: Query): Result[Query] =
    (c.attribute("code"), q) match {
      case (Ior.Right(countryCode: String), Select("currencies", _, child)) =>
        Select("allCurrencies", Nil, Filter(Eql(FieldPath(List("countryCode")), Const(countryCode)), child)).rightIor
      case _ => mkErrorResult(s"Expected 'code' attribute at ${c.tpe}")
    }

  override val selectElaborator =  new SelectElaborator(Map(
    QueryType -> {
      case Select("country", List(Binding("code", StringValue(code))), child) =>
        Select("country", Nil, Unique(Eql(AttrPath(List("code")), Const(code)), child)).rightIor
      case Select("countries", _, child) =>
        Select("countries", Nil, child).rightIor
      case Select("cities", List(Binding("namePattern", StringValue(namePattern))), child) =>
        Select("cities", Nil, Filter(Like(FieldPath(List("name")), namePattern, true), child)).rightIor
    }
  ))
}

object ComposedMapping {
  def fromTransactor[F[_] : Sync](xa: Transactor[F]): ComposedMapping[F] =
    new ComposedMapping[F](WorldMapping.fromTransactor(xa), CurrencyMapping[F])
}
