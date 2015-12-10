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
package ch.openolitor.stammdaten

import ch.openolitor.core.models._
import java.util.UUID
import scalikejdbc._
import scalikejdbc.async._
import scalikejdbc.async.FutureImplicits._
import scala.concurrent.ExecutionContext
import ch.openolitor.core.db._
import ch.openolitor.core.db.OOAsyncDB._
import ch.openolitor.core.repositories._
import ch.openolitor.core.repositories.BaseRepository._
import ch.openolitor.core.repositories.BaseWriteRepository
import scala.concurrent._
import akka.event.Logging
import ch.openolitor.stammdaten.models._
import com.typesafe.scalalogging.LazyLogging
import ch.openolitor.core.EventStream
import ch.openolitor.core.Boot
import akka.actor.ActorSystem
import ch.openolitor.stammdaten.models._

trait StammdatenReadRepository {
  def getAbotypDetail(id: AbotypId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[AbotypDetail]]
  def getAbotypen(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Abotyp]]
}

class StammdatenReadRepositoryImpl extends StammdatenReadRepository with LazyLogging with StammdatenDBMappings {

  lazy val aboTyp = abotypMapping.syntax("t")
  lazy val pl = postlieferungMapping.syntax("pl")
  lazy val dl = depotlieferungMapping.syntax("dl")
  lazy val d = depotMapping.syntax("d")
  lazy val t = tourMapping.syntax("tr")
  lazy val hl = heimlieferungMapping.syntax("hl")

  def getAbotypen(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[List[Abotyp]] = {
    withSQL {
      select
        .from(abotypMapping as aboTyp)
        .where.append(aboTyp.aktiv)
        .orderBy(aboTyp.name)
    }.map(abotypMapping(aboTyp)).list.future
  }

  override def getAbotypDetail(id: AbotypId)(implicit asyncCpContext: MultipleAsyncConnectionPoolContext): Future[Option[AbotypDetail]] = {
    withSQL {
      select
        .from(abotypMapping as aboTyp)
        .leftJoin(postlieferungMapping as pl).on(aboTyp.id, pl.abotypId)
        .leftJoin(heimlieferungMapping as hl).on(aboTyp.id, hl.abotypId)
        .leftJoin(depotlieferungMapping as dl).on(aboTyp.id, dl.abotypId)
        .leftJoin(depotMapping as d).on(dl.depotId, d.id)
        .leftJoin(tourMapping as t).on(hl.tourId, t.id)
        .where.eq(aboTyp.id, parameter(id))
    }.one(abotypMapping(aboTyp))
      .toManies(
        rs => postlieferungMapping.opt(pl)(rs),
        rs => heimlieferungMapping.opt(hl)(rs),
        rs => depotlieferungMapping.opt(dl)(rs),
        rs => depotMapping.opt(d)(rs),
        rs => tourMapping.opt(t)(rs))
      .map({ (abotyp, pls, hms, dls, depot, tour) =>
        val vertriebsarten =
          pls.map(pl => PostlieferungDetail(pl.liefertage)) ++
            hms.map(hm => HeimlieferungDetail(tour.head, hm.liefertage)) ++
            dls.map(dl => DepotlieferungDetail(depot.head, dl.liefertage))
        logger.debug(s"getAbottyp:$id, abotyp:$abotyp:$vertriebsarten")
        AbotypDetail(abotyp.id,
          abotyp.name,
          abotyp.beschreibung,
          abotyp.lieferrhythmus,
          abotyp.enddatum,
          abotyp.anzahlLieferungen,
          abotyp.anzahlAbwesenheiten,
          abotyp.preis,
          abotyp.preiseinheit,
          abotyp.aktiv,
          vertriebsarten.toSet,
          abotyp.anzahlAbonnenten,
          abotyp.letzteLieferung)
      })
      .single.future
  }
}

trait StammdatenWriteRepository extends BaseWriteRepository {
  def cleanupDatabase(implicit cpContext: ConnectionPoolContext)
}

class StammdatenWriteRepositoryImpl(val system: ActorSystem) extends StammdatenWriteRepository with LazyLogging with EventStream with StammdatenDBMappings {

  override def cleanupDatabase(implicit cpContext: ConnectionPoolContext) = {

    //drop all tables
    DB autoCommit { implicit session =>
      logger.debug(s"oo-system: cleanupDatabase - drop tables")

      sql"drop table if exists ${postlieferungMapping.table}".execute.apply()
      sql"drop table if exists ${depotlieferungMapping.table}".execute.apply()
      sql"drop table if exists ${heimlieferungMapping.table}".execute.apply()
      sql"drop table if exists ${depotMapping.table}".execute.apply()
      sql"drop table if exists ${tourMapping.table}".execute.apply()
      sql"drop table if exists ${abotypMapping.table}".execute.apply()

      logger.debug(s"oo-system: cleanupDatabase - create tables")
      //create tables

      sql"create table ${postlieferungMapping.table}  (id varchar(36) not null, abotyp_id int not null, liefertage varchar(256))".execute.apply()
      sql"create table ${depotlieferungMapping.table} (id varchar(36) not null, abotyp_id int not null, depot_id int not null, liefertage varchar(256))".execute.apply()
      sql"create table ${heimlieferungMapping.table} (id varchar(36) not null, abotyp_id int not null, tour_id int not null, liefertage varchar(256))".execute.apply()
      sql"create table ${depotMapping.table} (id varchar(36) not null, name varchar(50) not null, beschreibung varchar(256))".execute.apply()
      sql"create table ${tourMapping.table} (id varchar(36) not null, name varchar(50) not null, beschreibung varchar(256))".execute.apply()
      sql"create table ${abotypMapping.table} (id varchar(36) not null, name varchar(50) not null, beschreibung varchar(256), lieferrhythmus varchar(256), enddatum timestamp, anzahl_lieferungen int, anzahl_abwesenheiten int, preis NUMERIC not null, preiseinheit varchar(20) not null, aktiv bit, anzahl_abonnenten INT not null, letzte_lieferung timestamp, waehrung varchar(10))".execute.apply()

      logger.debug(s"oo-system: cleanupDatabase - end")
    }
  }

  def getById[E <: BaseEntity[I], I <: BaseId](syntax: BaseEntitySQLSyntaxSupport[E], id: I)(implicit session: DBSession,
    binder: SqlBinder[I]): Option[E] = {
    val alias = syntax.syntax("x")
    withSQL {
      select
        .from(syntax as alias)
        .where.eq(alias.id, parameter(id))
    }.map(syntax.apply(alias)).single.apply()
  }

  def insertEntity(entity: BaseEntity[_ <: BaseId])(implicit session: DBSession) = {
    entity match {
      case abotyp: Abotyp =>
        processInsert(abotyp)

      case depotlieferung: Depotlieferung =>
        processInsert(depotlieferung)
    }

    def processInsert[E <: BaseEntity[_ <: BaseId]](entity: E)(implicit syntaxSupport: BaseEntitySQLSyntaxSupport[E]): Unit = {
      val params = syntaxSupport.parameterMappings(entity)
      logger.debug(s"create entity with values:$entity")
      withSQL(insertInto(syntaxSupport).values(params: _*)).update.apply()

      publish(EntityCreated(Boot.systemUserId, entity))
    }
  }

  def updateEntity(entity: BaseEntity[_ <: BaseId])(implicit session: DBSession) = {

    entity match {
      case abotyp: Abotyp =>
        logger.debug(s"update abotyp:$abotyp")
        withSQL(update(abotypMapping).set(abotypMapping.column.name -> parameter(abotyp.name),
          abotypMapping.column.beschreibung -> parameter(abotyp.beschreibung),
          abotypMapping.column.lieferrhythmus -> parameter(abotyp.lieferrhythmus),
          abotypMapping.column.enddatum -> parameter(abotyp.enddatum),
          abotypMapping.column.anzahlLieferungen -> parameter(abotyp.anzahlLieferungen),
          abotypMapping.column.anzahlAbwesenheiten -> parameter(abotyp.anzahlAbwesenheiten),
          abotypMapping.column.preis -> parameter(abotyp.preis),
          abotypMapping.column.preiseinheit -> parameter(abotyp.preiseinheit),
          abotypMapping.column.aktiv -> parameter(abotyp.aktiv),
          abotypMapping.column.anzahlAbonnenten -> parameter(abotyp.anzahlAbonnenten),
          abotypMapping.column.letzteLieferung -> parameter(abotyp.letzteLieferung),
          abotypMapping.column.waehrung -> parameter(abotyp.waehrung)).where.eq(abotypMapping.column.id, parameter(abotyp.id))).update.apply()

        //publish event to stream
        //TODO: fetch real user when security gets integrated 
        publish(EntityModified(Boot.systemUserId, entity))
    }
  }

  def deleteEntity(id: BaseId)(implicit session: DBSession) = {
    id match {
      case abotypId: AbotypId =>
        logger.debug(s"delete from abotypen:$id")
        getById(abotypMapping, abotypId) map { abotyp =>
          withSQL(deleteFrom(abotypMapping).where.eq(abotypMapping.column.id, parameter(abotypId))).update.apply()

          //publish event to stream
          //TODO: fetch real user when security gets integrated 
          publish(EntityDeleted(Boot.systemUserId, abotyp))
        }

      case x =>
        logger.warn(s"Can't delete requested  entity:$x")
    }
  }
}
