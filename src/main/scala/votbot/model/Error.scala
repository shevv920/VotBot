package votbot.model

sealed trait Error extends Throwable

final case class DBError(description: String, throwable: Throwable) extends Error
