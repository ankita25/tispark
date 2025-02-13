/*
 * Copyright 2020 PingCAP, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pingcap.tispark.datasource

import com.pingcap.tikv.exception.TiBatchWriteException
import org.apache.spark.sql.Row

import scala.util.Random

/**
 * Ignore the test in this suit, because we don't support them anymore.
 * - as for select: use tidb_catalog to read
 * - as for insert: use write api to write
 * Insert statement in tidb_catalog is not able to support in two reasons:
 * 1. options can't be passed by sql
 * 2. insert statement will involve with catalyst，which may conflict with write logical. such as data type conversion
 */
class BasicSQLSuite extends BaseBatchWriteWithoutDropTableTest("test_datasource_sql") {
  private val row1 = Row(null, "Hello")
  private val row2 = Row(2, "TiDB")
  private val row3 = Row(3, "Spark")
  private val row4 = Row(4, null)

  override def beforeAll(): Unit = {
    super.beforeAll()
    jdbcUpdate(s"create table $dbtable(i int, s varchar(128))")
    jdbcUpdate(s"insert into $dbtable values(null, 'Hello'), (2, 'TiDB')")
  }

  ignore("Test Select") {
    testSelectSQL(Seq(row1, row2))
  }

  ignore("Test Insert Into") {
    val tmpTable =
      if (validateCatalog) "spark_catalog.default.testInsert" else "default.testInsert"
    sqlContext.sql(s"""
                      |CREATE TABLE $tmpTable
                      |USING tidb
                      |OPTIONS (
                      |  database '$database',
                      |  table '$table',
                      |  tidb.addr '$tidbAddr',
                      |  tidb.password '$tidbPassword',
                      |  tidb.port '$tidbPort',
                      |  tidb.user '$tidbUser',
                      |  spark.tispark.pd.addresses '$pdAddresses'
                      |)
       """.stripMargin)

    sqlContext.sql(s"""
                      |insert into $tmpTable values (3, 'Spark'), (4, null)
      """.stripMargin)

    testSelectSQL(Seq(row1, row2, row3, row4))
  }

  ignore("Test Insert Overwrite") {
    val tmpTable =
      if (validateCatalog) "spark_catalog.default.testOverwrite" else "default.testOverwrite"
    sqlContext.sql(s"""
                      |CREATE TABLE $tmpTable
                      |USING tidb
                      |OPTIONS (
                      |  database '$database',
                      |  table '$table',
                      |  tidb.addr '$tidbAddr',
                      |  tidb.password '$tidbPassword',
                      |  tidb.port '$tidbPort',
                      |  tidb.user '$tidbUser',
                      |  spark.tispark.pd.addresses '$pdAddresses'
                      |)
       """.stripMargin)

    val caught = intercept[TiBatchWriteException] {
      sqlContext.sql(s"""
                        |insert overwrite table $tmpTable values (3, 'Spark'), (4, null)
      """.stripMargin)
    }

    assert(
      caught.getMessage
        .equals("SaveMode: Overwrite is not supported. TiSpark only support SaveMode.Append."))
  }

  private def testSelectSQL(expectedAnswer: Seq[Row]): Unit = {
    val tmpTable =
      if (validateCatalog)
        s"spark_catalog.default.`testSelect_${Math.abs(Random.nextLong())}_${System.currentTimeMillis()}`"
      else s"default.`testSelect_${Math.abs(Random.nextLong())}_${System.currentTimeMillis()}`"

    sql(s"""
           |CREATE TABLE $tmpTable
           |USING tidb
           |OPTIONS (
           |  database '$database',
           |  table '$table',
           |  tidb.addr '$tidbAddr',
           |  tidb.password '$tidbPassword',
           |  tidb.port '$tidbPort',
           |  tidb.user '$tidbUser',
           |  spark.tispark.pd.addresses '$pdAddresses'
           |)
       """.stripMargin)
    val df = sql(s"select * from $tmpTable sort by i")
    checkAnswer(df, expectedAnswer)
  }
}
