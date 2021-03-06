/*                                                                           *\
*    ____                   ____  ___ __                                      *
*   / __ \____  ___  ____  / __ \/ (_) /_____  _____                          *
*  / / / / __ \/ _ \/ __ \/ / / / / / __/ __ \/ ___/   OpenOlitor             *
* / /_/ / /_/ /  __/ / / / /_/ / / / /_/ /_/ / /       contributed by tegonal *
* \____/ .___/\___/_/ /_/\____/_/_/\__/\____/_/        http://openolitor.ch   *
*     /_/                                                                     *
*                                                                             *
* This program is free software: you can redistribute it and/or modify it     *
* under the terms of the GNU General Public License as published by           *
* the Free Software Foundation, either version 3 of the License,              *
* or (at your option) any later version.                                      *
*                                                                             *
* This program is distributed in the hope that it will be useful, but         *
* WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY  *
* or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for *
* more details.                                                               *
*                                                                             *
* You should have received a copy of the GNU General Public License along     *
* with this program. If not, see http://www.gnu.org/licenses/                 *
*                                                                             *
\*                                                                           */
package ch.openolitor.core.models

import java.util.UUID
import org.joda.time.DateTime
import ch.openolitor.util.IdUtil

sealed trait EvolutionStatus
case object Applying extends EvolutionStatus
case object Done extends EvolutionStatus

object EvolutionStatus {
  val AllStatus = Vector(Applying, Done)

  def apply(value: String): EvolutionStatus = AllStatus.find(_.toString == value).getOrElse(Applying)
}

case class DBSchemaId(id: Long = IdUtil.positiveRandomId) extends BaseId
case class DBSchema(id: DBSchemaId, revision: Int, status: EvolutionStatus,
  //modification flags
  erstelldat: DateTime,
  ersteller: PersonId,
  modifidat: DateTime,
  modifikator: PersonId) extends BaseEntity[DBSchemaId]