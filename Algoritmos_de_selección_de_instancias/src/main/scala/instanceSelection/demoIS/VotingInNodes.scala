package instanceSelection.demoIS

import scala.collection.mutable.MutableList

import org.apache.spark.mllib.regression.LabeledPoint

import instanceSelection.seq.abstr.TraitSeqIS

/**
 * Algoritmo encargado de asignar "votos" a instancias en función de si han
 * sido seleccionadas o no.
 *
 * Forma parte del algoritmo [[instanceSelection.demoIS.DemoIS]] pero, a
 * diferencia de los otros componentes de dicho algoritmo, estas operaciones
 * requieren ser serializables entre la red de nodos.
 */
@SerialVersionUID(1L)
private class VotingInNodes extends Serializable {

  /**
   * Algoritmo de votación.
   *
   * Partiendo de un conjunto de instancias inicial, aplica un algoritmo de
   * selección de instancias. Posteriormente, y utilizando el resultado de esta
   * última operación, aumenta el contador de la instancia en un punto si no ha
   * sido seleccionada durante el filtrado.
   *
   * @param  instancesIterator  Iterador sobre el conjunto de instancias inicial,
   *   donde cada una de las instancias lleva asociado un contador
   *   (número de votos).
   * @param  linearIS  Algoritmo de selección de instancias secuencial
   * @return Iterador sobre un conjunto de (votos,instancia) una vez se han
   *   actualizado los valores de votación.
   *
   */
  def applyIterationPerPartition(
    instancesIterator: Iterator[(Long,(Int, LabeledPoint))],
    linearIS: TraitSeqIS): Iterator[(Long,(Int, LabeledPoint))] = {

    // Almacenamos todos los valores en listas
    var instancias = new MutableList[LabeledPoint]
    var myIterableCopy = new MutableList[(Long,(Int, LabeledPoint))]
    while (instancesIterator.hasNext) {
      var tmp = instancesIterator.next
      myIterableCopy += tmp
      instancias += tmp._2._2
    }

    // Ejecutamos el algoritmo
    var selected = linearIS.instSelection(instancias)

    // Actualizamos los contadores de las instancias no seleccionadas.
    var iter = myIterableCopy.iterator
    var actIndex = -1
    while (iter.hasNext) {
      actIndex += 1
      var actualInst = iter.next._2._2
      if (!selected.exists { inst => inst.eq(actualInst) }) {
        myIterableCopy.update(actIndex,
          (myIterableCopy.get(actIndex).get._1,(myIterableCopy.get(actIndex).get._2._1 + 1,
            myIterableCopy.get(actIndex).get._2._2)))
      }
    }
    myIterableCopy.iterator
  }

}
