package votbot.database

import slick.jdbc.SQLiteProfile.api._
import slick.lifted.ProvenShape
import votbot.model.DB.Quote

object QuoteTable {

  class Quotes(tag: Tag) extends Table[Quote](tag, "quotes") {
    def id: Rep[Long]               = column[Long]("id", O.PrimaryKey)
    def key: Rep[String]            = column[String]("key")
    def sourceUri: Rep[String]      = column[String]("source_uri")
    def txt: Rep[String]            = column[String]("txt")
    def author: Rep[Option[String]] = column[Option[String]]("author")
    def * : ProvenShape[Quote]      = (id, key, sourceUri, txt, author) <> (Quote.tupled, Quote.unapply)
  }
}
