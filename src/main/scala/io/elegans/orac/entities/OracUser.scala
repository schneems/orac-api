package io.elegans.orac.entities

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 31/10/17.
  */

case class OracUser(
                     id: String,
                     name: Option[String] = Option.empty,
                     email: Option[String] = Option.empty,
                     phone: Option[String] = Option.empty,
                     properties: Option[OracProperties] = Option.empty
)

case class OracUsers (
                   items: List[OracUser]
                 )