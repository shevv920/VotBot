package votbot.model

object DB {

  final case class Quote(id: Int, key: String, sourceUrl: String, txt: String, author: Option[String])
}
