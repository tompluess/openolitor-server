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
package ch.openolitor.kundenportal.repositories

import ch.openolitor.core.models._
import scalikejdbc._
import scalikejdbc.async._
import scala.concurrent.ExecutionContext
import ch.openolitor.core.db._
import ch.openolitor.core.db.OOAsyncDB._
import ch.openolitor.core.repositories._
import ch.openolitor.core.repositories.BaseRepository._
import scala.concurrent._
import ch.openolitor.stammdaten.models._
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.stammdaten.models._
import ch.openolitor.core.Macros._
import ch.openolitor.util.DateTimeUtil._
import org.joda.time.DateTime
import ch.openolitor.util.parsing.FilterExpr
import ch.openolitor.core.security.Subject

trait KundenportalReadRepository {
  def getProjekt(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[ProjektPublik]]

  def getAbos(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext, filter: Option[FilterExpr], owner: Subject): Future[List[AboDetail]]

  def getLieferungenDetails(aboId: AbotypId)(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext, filter: Option[FilterExpr], owner: Subject): Future[List[LieferungDetail]]

  def getLieferungenDetail(lieferungId: LieferungId)(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[LieferungDetail]]
}

class KundenportalReadRepositoryImpl extends KundenportalReadRepository with LazyLogging with KundenportalRepositoryQueries {
  def getProjekt(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[ProjektPublik]] = {
    getProjektQuery.future map (_ map (projekt => copyTo[Projekt, ProjektPublik](projekt)))
  }

  def getDepotlieferungAbos(implicit asyncCpContext: MultipleAsyncConnectionPoolContext, filter: Option[FilterExpr], owner: Subject): Future[List[DepotlieferungAboDetail]] = {
    getDepotlieferungAbosQuery(filter).future
  }

  def getHeimlieferungAbos(implicit asyncCpContext: MultipleAsyncConnectionPoolContext, filter: Option[FilterExpr], owner: Subject): Future[List[HeimlieferungAboDetail]] = {
    getHeimlieferungAbosQuery(filter).future
  }

  def getPostlieferungAbos(implicit asyncCpContext: MultipleAsyncConnectionPoolContext, filter: Option[FilterExpr], owner: Subject): Future[List[PostlieferungAboDetail]] = {
    getPostlieferungAbosQuery(filter).future
  }

  def getAbos(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext, filter: Option[FilterExpr], owner: Subject): Future[List[AboDetail]] = {
    for {
      d <- getDepotlieferungAbos
      h <- getHeimlieferungAbos
      p <- getPostlieferungAbos
    } yield {
      d ::: h ::: p
    }
  }

  def getLieferungenDetails(abotypId: AbotypId)(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext, filter: Option[FilterExpr], owner: Subject): Future[List[LieferungDetail]] = {
    getLieferungenByAbotypQuery(abotypId, filter).list.future
  }

  def getLieferungenDetail(lieferungId: LieferungId)(implicit context: ExecutionContext, asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[LieferungDetail]] = {
    getLieferungenDetailQuery(lieferungId).future
  }
}