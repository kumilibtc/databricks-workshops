// Databricks notebook source
import org.apache.spark.sql.functions._

// COMMAND ----------

// MAGIC %md
// MAGIC ### 1.  Execute notebook with common/reusable functions 

// COMMAND ----------

// MAGIC %run "../01-General/2-CommonFunctions"

// COMMAND ----------

// MAGIC %md
// MAGIC ### 2.  Add extra columns to yellow taxi - to match green taxi
// MAGIC Because we are working with parquet, we cannot just throw in additional columns in a select statement.<BR>
// MAGIC We are systematically adding the three extra columns with default values in this section.

// COMMAND ----------

// MAGIC %sql
// MAGIC use taxi_db;
// MAGIC refresh table yellow_taxi_trips_curated;
// MAGIC refresh table green_taxi_trips_curated;

// COMMAND ----------

//Read source data
val yellowTaxiDF = sql("""
SELECT 
    taxi_type,
    vendor_id,
    pickup_datetime,
    dropoff_datetime,
    store_and_fwd_flag,
    rate_code_id,
    pickup_location_id,
    dropoff_location_id,
    pickup_longitude,
    pickup_latitude,
    dropoff_longitude,
    dropoff_latitude,
    passenger_count,
    trip_distance,
    fare_amount,
    extra,
    mta_tax,
    tip_amount,
    tolls_amount,
    improvement_surcharge,
    total_amount,
    payment_type,
    vendor_abbreviation,
    vendor_description,
    month_name_short,
    month_name_full,
    payment_type_description,
    rate_code_description,
    pickup_borough,
    pickup_zone,
    pickup_service_zone,
    dropoff_borough,
    dropoff_zone,
    dropoff_service_zone,
    pickup_year,
    pickup_month,
    pickup_day,
    pickup_hour,
    pickup_minute,
    pickup_second,
    dropoff_year,
    dropoff_month,
    dropoff_day,
    dropoff_hour,
    dropoff_minute,
    dropoff_second,
    trip_year,
    trip_month
  FROM taxi_db.yellow_taxi_trips_curated 
""")

//Add extra columns
val yellowTaxiDFHomogenized = yellowTaxiDF.withColumn("ehail_fee",lit(0.0))
                                          .withColumn("trip_type",lit(0))
                                          .withColumn("trip_type_description",lit("")).cache()
//Materialize
yellowTaxiDFHomogenized.count()

//Register temporary view
yellowTaxiDFHomogenized.createOrReplaceTempView("yellow_taxi_trips_unionable")

//Approximately 2 minutes

// COMMAND ----------

yellowTaxiDFHomogenized.printSchema

// COMMAND ----------

// MAGIC %md
// MAGIC ### 3.  Create materialized view

// COMMAND ----------

//Destination directory
val destDataDirRoot = "/mnt/workshop/curated/materialized-view" 

//Delete any residual data from prior executions for an idempotent run
dbutils.fs.rm(destDataDirRoot,recurse=true)

// COMMAND ----------

val matViewDF = sql("""
  SELECT DISTINCT  
    taxi_type,
    vendor_id,
    pickup_datetime,
    dropoff_datetime,
    store_and_fwd_flag,
    rate_code_id,
    pickup_location_id,
    dropoff_location_id,
    pickup_longitude,
    pickup_latitude,
    dropoff_longitude,
    dropoff_latitude,
    passenger_count,
    trip_distance,
    fare_amount,
    extra,
    mta_tax,
    tip_amount,
    tolls_amount,
    ehail_fee,
    improvement_surcharge,
    total_amount,
    payment_type,
    trip_type,
    vendor_abbreviation,
    vendor_description,
    trip_type_description,
    month_name_short,
    month_name_full,
    payment_type_description,
    rate_code_description,
    pickup_borough,
    pickup_zone,
    pickup_service_zone,
    dropoff_borough,
    dropoff_zone,
    dropoff_service_zone,
    pickup_year,
    pickup_month,
    pickup_day,
    pickup_hour,
    pickup_minute,
    pickup_second,
    dropoff_year,
    dropoff_month,
    dropoff_day,
    dropoff_hour,
    dropoff_minute,
    dropoff_second,
    trip_year,
    trip_month
  FROM yellow_taxi_trips_unionable 
UNION ALL
  SELECT DISTINCT 
    taxi_type,
    vendor_id,
    pickup_datetime,
    dropoff_datetime,
    store_and_fwd_flag,
    rate_code_id,
    pickup_location_id,
    dropoff_location_id,
    pickup_longitude,
    pickup_latitude,
    dropoff_longitude,
    dropoff_latitude,
    passenger_count,
    trip_distance,
    fare_amount,
    extra,
    mta_tax,
    tip_amount,
    tolls_amount,
    ehail_fee,
    improvement_surcharge,
    total_amount,
    payment_type,
    trip_type,
    vendor_abbreviation,
    vendor_description,
    trip_type_description,
    month_name_short,
    month_name_full,
    payment_type_description,
    rate_code_description,
    pickup_borough,
    pickup_zone,
    pickup_service_zone,
    dropoff_borough,
    dropoff_zone,
    dropoff_service_zone,
    pickup_year,
    pickup_month,
    pickup_day,
    pickup_hour,
    pickup_minute,
    pickup_second,
    dropoff_year,
    dropoff_month,
    dropoff_day,
    dropoff_hour,
    dropoff_minute,
    dropoff_second,
    trip_year,
    trip_month
  FROM taxi_db.green_taxi_trips_curated 
""").cache()


//Save as Delta
matViewDF
    .coalesce(15)
    .write
    .format("delta")
    .mode("append")
    .partitionBy("taxi_type","trip_year","trip_month")
    .save(destDataDirRoot)   

//Execution time: ~ 18 minutes for 2016/2017
//Execuction for all the data: 55.75 minutes

// COMMAND ----------

matViewDF.printSchema

// COMMAND ----------

// MAGIC %md
// MAGIC ### 4.  Create external table

// COMMAND ----------

// MAGIC %sql
// MAGIC use taxi_db;
// MAGIC 
// MAGIC DROP TABLE IF EXISTS taxi_trips_mat_view;
// MAGIC CREATE TABLE taxi_trips_mat_view
// MAGIC USING DELTA
// MAGIC partitioned by (taxi_type,trip_year,trip_month)
// MAGIC LOCATION '/mnt/workshop/curated/materialized-view';

// COMMAND ----------

// MAGIC %sql
// MAGIC select taxi_type,count(*) as trip_count from taxi_db.taxi_trips_mat_view group by taxi_type

// COMMAND ----------

// MAGIC %sql
// MAGIC select * from taxi_db.taxi_trips_mat_view where trip_year=2013