package io.elegans.orac.entities

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 31/10/17.
  */

case class OracUser(
  id: String,
  birthdate: Option[Long],
  name: Option[String],
  gender: Option[String],
  email: Option[String],
  phone: Option[String],
  tags: Option[List[String]]
)
