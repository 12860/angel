/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package com.tencent.angel.spark.ml.automl.tuner.config

import scala.collection.mutable.HashSet
import org.apache.spark.ml.linalg.{Vector, Vectors}
import com.tencent.angel.spark.ml.automl.tuner.parameter.ParamSpace
import org.apache.commons.logging.{Log, LogFactory}
import org.apache.spark.sql.types._

import scala.collection.mutable.ArrayBuffer

class ConfigurationSpace(
                          val name: String,
                          private var paramDict: Map[String, ParamSpace[Double]] = Map()) {

  val LOG: Log = LogFactory.getLog(classOf[ConfigurationSpace])

  var numParams: Int = paramDict.size

  var fields: ArrayBuffer[StructField] = new ArrayBuffer[StructField]()

  var param2Idx: Map[String, Int] = paramDict.keys.zipWithIndex.toMap
  var param2Doc: Map[String, String] = paramDict.map { case (k: String, v: ParamSpace[Double]) => (k, v.doc) }
  var idx2Param: Map[Int, String] = param2Idx.map(_.swap)

  // configurations tried
  var preX: HashSet[Vector] = HashSet[Vector]()

  def getParamNum: Int = numParams

  def addParams(params: List[ParamSpace[Double]]): Unit = {
    params.foreach(addParam)
  }

  def addParam(param: ParamSpace[Double]): Unit = {
    if (!paramDict.contains(param.name)) {
      fields += DataTypes.createStructField(param.name, DataTypes.DoubleType, false)
      paramDict += (param.name -> param)
      param2Idx += (param.name -> numParams)
      param2Doc += (param.name -> param.doc)
      idx2Param += (numParams -> param.name)
      numParams += 1
    }
    println(s"add param ${param.toString}, current params: ${paramDict.keySet.mkString(",")}")
  }

  def getFields: Array[StructField] = fields.toArray

  def getParams(): Array[ParamSpace[Double]] = paramDict.values.toArray

  def getParamByName(name: String): Option[ParamSpace[Double]] = paramDict.get(name)

  def getIdxByParam(name: String): Option[Int] = param2Idx.get(name)

  def getParamByIdx(idx: Int): Option[ParamSpace[Double]] = paramDict.get(idx2Param.getOrElse(idx, "none"))

  def getDocByName(name: String): Option[String] = param2Doc.get(name)

  def addHistories(vecs: Array[Vector]): Unit = preX ++= vecs

  def addHistory(vec: Vector): Unit = preX += vec

  def sample(size: Int): Array[Configuration] = {
    var configs: ArrayBuffer[Configuration] = new ArrayBuffer[Configuration]

    var missing: Int = 0
    do {
      missing = size - configs.length
      //println(s"num of params: $numParams")
      var vectors: Array[Vector] = Array.fill(missing)(Vectors.dense(new Array[Double](numParams)))
      param2Idx.foreach { case (paramName, paramIdx) =>
        paramDict.get(paramName) match {
          case Some(param) =>
            param.sample(missing).zipWithIndex.foreach { case (f: Double, i: Int) =>
              vectors(i).toArray(paramIdx) = f
            }
          case None => LOG.info(s"Cannot find $paramName.")
        }
      }
      vectors.filter(isValid).foreach{ vec =>
        configs += new Configuration(param2Idx, param2Doc, vec)
      }
    } while(configs.length < size)

    configs.toArray
  }

  def isValid(vec: Vector): Boolean = !preX.contains(vec)

}