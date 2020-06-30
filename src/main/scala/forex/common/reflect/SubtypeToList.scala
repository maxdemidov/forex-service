package forex.common.reflect

import reflect.runtime.universe
import reflect.runtime.currentMirror
import reflect.runtime.universe._

object SubtypeToList {

  def fromKnownSubtypes[T : TypeTag]: List[T] = {
    def run(symbol: universe.Symbol): List[T] = {
      if (symbol.isModuleClass) {
        val moduleMirror = currentMirror.reflectModule(symbol.asClass.module.asModule)
        List(moduleMirror.instance.asInstanceOf[T])
      } else if (symbol.isClass && symbol.asClass.isSealed) {
        symbol.asClass.knownDirectSubclasses.toList.flatMap(run)
      } else Nil
    }
    run(symbolOf[T])
  }
}
