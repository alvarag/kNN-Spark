package instanceSelection.lshis

import java.util.Random
import java.util.logging.Level
import java.util.logging.Logger

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions

import instanceSelection.abstr.TraitIS

/**
 *
 * Implementación del algoritmo Locality Sensitive Hashing Instance Selection
 * (LSH IS).
 *
 * LSH-IS es un algoritmo de selección de instancias apoyado en el uso de LSH.
 * La idea es aplicar un  algoritmo de LSH sobre el conjunto de instancias
 * inicial, de manera que podamos agrupar en un mismo bucket
 * aquellas instancias con un alto grado de similitud.
 * Posteriormente, de cada uno de esos buckets seleccionaremos una
 * instancia de cada clase, que pasará a formar parte del conjunto
 * de instancias final.
 *
 * Participante en el patrón de diseño "Strategy" en el que actúa con el
 * rol de estrategia concreta ("concrete strategies"). Hereda de la clase que
 * participa como estrategia ("Strategy")
 * [[instanceSelection.abstr.TraitIS]].
 *
 * @constructor Crea un nuevo algoritmo LSHIS con los parámetros por defecto.
 *
 * @author Alejandro González Rogel
 * @version 1.2.0
 */
class LSHIS extends TraitIS {

  /**
   * Archivo que contiene la frases de log.
   */
  private val bundleName = "resources.loggerStrings.stringsLSHIS";

  /**
   * Logger del algoritmo.
   */
  private val logger = Logger.getLogger(this.getClass.getName(), bundleName);

  /**
   * Número de funciones-AND.
   */
  var ANDs: Int = 10

  /**
   * Número de funciones-OR.
   */
  var ORs: Int = 1

  /**
   * Tamaño de los "buckets".
   */
  var width: Double = 1

  /**
   * Semilla para los números aleatorios.
   */
  var seed: Long = 1

  override def instSelection(
    parsedData: RDD[LabeledPoint]): RDD[LabeledPoint] = {

    parsedData.persist()

    parsedData.name = "TrainInLSHIS"

    val r = new Random(seed)

    val andTables = createANDTables(parsedData.first().features.size, r)

    // Variable para almacenar el resultado final
    var finalResult: RDD[LabeledPoint] = null

    for { i <- 0 until ORs } {
      val andTable = andTables(i)

      // Transformamos la RDD para generar tuplas de (bucket asignado, clase)
      // e instancia
      val keyInstRDD = parsedData.map { instancia =>
        ((andTable.hash(instancia), instancia.label), instancia)
      }
      // Seleccionamos una instancia por cada par (bucket,clase)
      val partialResult = keyInstRDD.reduceByKey { (inst1, inst2) => inst1 }

      if (i == 0) { // Si es la primera iteración del bucle for
        finalResult = partialResult.values.persist
        finalResult.name = "PartialResult"
      } else {
        // Recalculamos los buckets para las instancias ya seleccionadas
        // en otras iteraciones.
        val alreadySelectedInst = finalResult.map { instancia =>
          ((andTable.hash(instancia), instancia.label), instancia)
        }
        // Sobre la RDD de la iteración, seleccionamos aquellas las instancia
        // cuya key no esté repetida en el resultado final.
        val keyClassRDDGroupBy = partialResult.subtractByKey(alreadySelectedInst)
        val selectedInstances = keyClassRDDGroupBy.map[LabeledPoint] {
          case (tupla, instancia) => instancia
        }

        // Unimos el resultado de la iteración con el resultado parcial ya
        // almacenado
        finalResult = finalResult.union(selectedInstances).persist
      }
    }

    finalResult
  }

  /**
   * Genera un array de tablas AND, cada una de ellas con funciones hash de
   * dimensión indicada por parametro.
   *
   * @param  dim  Dimensión de las funciones hash
   * @param  r  Generador de números aleatorios.
   * @return Array con todas las tablas instanciadas
   */
  protected def createANDTables(dim: Int, r: Random): ArrayBuffer[ANDsTable] = {

    // Creamos tantos componentes AND como sean requeridos.
    var andTables: ArrayBuffer[ANDsTable] = new ArrayBuffer[ANDsTable]
    for { i <- 0 until ORs } {
      andTables += new ANDsTable(ANDs, dim, width, r.nextInt)
    }
    andTables
  } // end createANDTables

  override def setParameters(args: Array[String]): Unit = {

    // Comprobamos primero si tenemos el número de atributos correcto.
    if (args.size % 2 != 0) {
      logger.log(Level.SEVERE, "LSHISPairNumberParamError",
        this.getClass.getName)
      throw new IllegalArgumentException()
    }

    for { i <- 0 until args.size by 2 } {

      try {
        val identifier = args(i)
        val value = args(i + 1)
        assignValToParam(identifier, value)
      } catch {
        case ex: NumberFormatException =>
          logger.log(Level.SEVERE, "LSHISNoNumberError", args(i + 1))
          throw new IllegalArgumentException()
      }
    }

    // Si las variables no han sido asignadas con un valor correcto.
    if (ANDs <= 0 || ORs <= 0 || width <= 0) {
      logger.log(Level.SEVERE, "LSHISWrongArgsValuesError")
      logger.log(Level.SEVERE, "LSHISPossibleArgs")
      throw new IllegalArgumentException()
    }

  } // end readArgs

  protected override def assignValToParam(identifier: String,
                                          value: String): Unit = {
    identifier match {
      case "-and" => ANDs = value.toInt
      case "-w"   => width = value.toDouble
      case "-s" => {
        seed = value.toInt
      }
      case "-or" => ORs = value.toInt
      case somethingElse: Any =>
        logger.log(Level.SEVERE, "LSHISWrongArgsError", somethingElse.toString())
        logger.log(Level.SEVERE, "LSHISPossibleArgs")
        throw new IllegalArgumentException()
    }
  }

}
