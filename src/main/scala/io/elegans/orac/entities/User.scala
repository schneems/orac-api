package io.elegans.orac.entities

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 17/11/17.
  */

object Permissions extends Enumeration {
  type Permission = Value
  val read, write, admin, unknown = Value
  def getValue(permission: String) = values.find(_.toString == permission).getOrElse(unknown)
}

case class User(
                 id: String, /** user id */
                 password: String, /** user password */
                 salt: String, /** salt for password hashing */
                 permissions: Map[
                   String, /** index name */
                   Set[Permissions.Value] /** permissions granted for the index */
                   ]
               )
