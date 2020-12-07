package ConektaApp

import scala.collection.IterableOnce.iterableOnceExtensionMethods

/**
 * NO CONSIDERAMOS AL CERO EN EL CONJUNTO DE LOS NUMEROS NATURALES
 */
abstract class  Conjunto {
    def contains(x: Int): Boolean;
}

class Singleton (natural: Int)  extends Conjunto {
    val contenido  = Set(natural)
    def contains(x: Int): Boolean = {
      this.contenido contains x
    }
}

class Naturales extends Conjunto {
  val cota = 100
  var lista = Set(1)
  for (i <- 1 to   (cota+1) ) lista = lista + i

  def contains(x: Int): Boolean = {
    this.lista contains x
  }

  def Extract ( x: Singleton): Unit = {
    this.lista = this.lista diff x.contenido
    println("El número introducido fue " + x.contenido.toList)
    println("La lista resultante es: ")
    println(this.lista.toList.sorted)
    println("Vamos por otra ronda .. ")

  }



}

object Main extends App {
  println("Bienvenido a la eliminación de números de una lista, cuando se aburra introduzca el número 0")
  val N = new Naturales()
  while (scala.io.StdIn.readLine() != 0) {
    try {
      println("Introduzca el entero que desea eliminar del conjunto {1, …, 100} : ")
      val entrada_consola = scala.io.StdIn.readInt()
      if (entrada_consola > 0 && entrada_consola <= 100) {
        val entrada = new Singleton(entrada_consola)
        println("-------------------------")
        println("El conjunto original es:")
        println(N.lista.toList.sorted)
        N.Extract(entrada)


      } else {
        throw new Exception("Solo se admiten enteros naturales entre 1 y 100, intenta nuevamente")
      }
    } catch {
      case e: Exception => println("Solo se admiten enteros naturales entre 1 y 100, intenta nuevamente")
    }
  }


}