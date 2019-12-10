package votbot.model

object DB {

  final case class Quote(id: Long, key: String, sourceUri: String, txt: String, author: Option[String])
}
