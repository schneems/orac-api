package io.elegans.orac.entities

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 31/10/17.
  */

case class Item (
  id: String,
  name: String,
  `type`: String,
  description: Option[String] = Option.empty,
  properties: Option[OracProperties] = Option.empty
)

case class Items (
                 items: List[Item]
                )