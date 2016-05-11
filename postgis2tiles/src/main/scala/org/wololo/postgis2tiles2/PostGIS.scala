package org.wololo.postgis2tiles2

import java.io.IOException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.ArrayList
import java.util.List
import java.util.Properties
import org.apache.commons.io.IOUtils
import org.geowebcache.grid.BoundingBox
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKBReader
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import scala.collection.JavaConversions._
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.ReadOnlyAutoSession
import scalikejdbc.DBSession
import scalikejdbc.ConnectionPool
import scalikejdbc.DataSourceConnectionPool

import scalikejdbc._

object PostGIS extends LazyLogging {
  
  def fetch(layerName: String, bbox: BoundingBox, optFields: String = "", optKkod: Array[Int] = Array.emptyIntArray)(implicit session: DBSession = ReadOnlyAutoSession) = {
    val tableName = SQLSyntax.createUnsafely(layerName)
    val minx = bbox.getMinX
    val miny = bbox.getMinY
    val maxx = bbox.getMaxX
    val maxy = bbox.getMaxY
    val optFields2 = if (optFields.length() > 0) SQLSyntax.createUnsafely("," + optFields) else SQLSyntax.createUnsafely("")
    
    val wherein = if (optKkod.length > 0) SQLSyntax.createUnsafely(s" and kkod in (${optKkod.mkString(",")})") else SQLSyntax.createUnsafely("")
    
    val q = sql"select kkod,ST_AsEWKB(geom) as geom${optFields2} from ${tableName} where ST_Intersects(geom, ST_MakeEnvelope(${minx},${miny},${maxx},${maxy},3006))${wherein}"
    q.fetchSize(100)
  }
}
