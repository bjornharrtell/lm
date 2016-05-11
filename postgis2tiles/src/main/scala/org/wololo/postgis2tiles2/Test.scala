package org.wololo.postgis2tiles2

import java.io.IOException
import com.zaxxer.hikari.HikariDataSource
import scalikejdbc.ConnectionPool
import scalikejdbc.DataSourceConnectionPool

object Test {
  
  val ds = new HikariDataSource()

  ds.setJdbcUrl("jdbc:postgresql://localhost/lm")
  ds.setUsername("postgres")
  ds.setPassword("postgres")
  
  ConnectionPool.singleton(new DataSourceConnectionPool(ds))

  def main(args: Array[String]) {
    VectorTileStreamer.stream()
  }
}
