package com.example.pc1_grupal

/**
 * Objeto de constantes para manejar los nombres de colecciones, campos y estados de Firestore.
 * Esto evita errores tipográficos en las consultas a la base de datos.
 */
object FirestoreConstantes {
    const val COLECCION_SORTEOS = "sorteos"
    const val SORTEO_ID = "sorteo_prueba"
    const val SUBCOLECCION_JUGADAS = "jugadas"
    const val SUBCOLECCION_BOLILLAS = "bolillas"
    const val CAMPO_NOMBRE = "nombre"
    const val CAMPO_FECHA = "fecha"
    const val CAMPO_ESTADO = "estado"
    const val CAMPO_SORTEO_ID = "sorteoId"
    const val CAMPO_UID = "uid"
    const val CAMPO_NUMEROS = "numeros"
    const val ESTADO_ABIERTO = "abierto"
    const val ESTADO_EN_CURSO = "en_curso"
    const val ESTADO_FINALIZADO = "finalizado"
}
