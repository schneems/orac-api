package io.elegans.orac.entities

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 31/10/17.
  */

case class UpdateItem (
  name: Option[String] = Option.empty,
  `type`: Option[String] = Option.empty,
  description: Option[String] = Option.empty,
  properties: Option[OracProperties] = Option.empty
)
