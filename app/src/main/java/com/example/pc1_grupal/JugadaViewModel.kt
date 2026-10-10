package com.example.pc1_grupal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Estado UI para la pantalla de Registro de Jugada.
 */
data class JugadaUiState(
    val correo: String = "",
    val nombreSorteo: String = "",
    val fechaSorteo: String = "",
    val estadoSorteo: String = "",
    val numerosSeleccionados: Set<Int> = emptySet(),
    val jugadaGuardada: List<Int>? = null,
    val mensajeError: String? = null,
    val mensajeExito: String? = null,
    val cargando: Boolean = true
)

/**
 * ViewModel encargado de gestionar la lógica del sorteo y la selección/guardado de jugadas en Firestore.
 */
class JugadaViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(JugadaUiState())
    val uiState: StateFlow<JugadaUiState> = _uiState.asStateFlow()

    init {
        cargarDatos()
    }

    /**
     * Carga el correo del usuario, la información del sorteo y verifica si el usuario ya tiene una jugada registrada.
     */
    fun cargarDatos() {
        val currentUser = auth.currentUser
        val correoUsuario = currentUser?.email ?: "Usuario desconocido"
        val uid = currentUser?.uid ?: ""

        _uiState.update {
            it.copy(
                correo = correoUsuario,
                cargando = true,
                mensajeError = null,
                mensajeExito = null
            )
        }

        viewModelScope.launch {
            try {
                // 1. Cargar el documento del sorteo
                val sorteoDoc = firestore.collection(FirestoreConstantes.COLECCION_SORTEOS)
                    .document(FirestoreConstantes.SORTEO_ID)
                    .get()
                    .await()

                if (!sorteoDoc.exists()) {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            mensajeError = "El sorteo '${FirestoreConstantes.SORTEO_ID}' no existe en Firestore"
                        )
                    }
                    return@launch
                }

                val nombre = sorteoDoc.getString(FirestoreConstantes.CAMPO_NOMBRE) ?: ""
                val fecha = sorteoDoc.getString(FirestoreConstantes.CAMPO_FECHA) ?: ""
                val estado = sorteoDoc.getString(FirestoreConstantes.CAMPO_ESTADO) ?: FirestoreConstantes.ESTADO_ABIERTO

                // 2. Verificar si el usuario ya tiene una jugada registrada
                var jugadaExistente: List<Int>? = null
                var mensajePrevio: String? = null

                if (uid.isNotEmpty()) {
                    val jugadaDoc = firestore.collection(FirestoreConstantes.COLECCION_SORTEOS)
                        .document(FirestoreConstantes.SORTEO_ID)
                        .collection(FirestoreConstantes.SUBCOLECCION_JUGADAS)
                        .document(uid)
                        .get()
                        .await()

                    if (jugadaDoc.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val numerosRaw = jugadaDoc.get(FirestoreConstantes.CAMPO_NUMEROS) as? List<Long>
                        jugadaExistente = numerosRaw?.map { it.toInt() }?.sorted()
                        mensajePrevio = "Ya registraste tu jugada"
                    }
                }

                _uiState.update {
                    it.copy(
                        nombreSorteo = nombre,
                        fechaSorteo = fecha,
                        estadoSorteo = estado,
                        jugadaGuardada = jugadaExistente,
                        mensajeExito = mensajePrevio,
                        cargando = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        mensajeError = "Error al cargar datos del sorteo: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Permite seleccionar o deseleccionar un número de la grilla (1 al 45).
     * Máximo 6 números.
     */
    fun toggleNumero(numero: Int) {
        val seleccionadosActuales = _uiState.value.numerosSeleccionados.toMutableSet()

        if (seleccionadosActuales.contains(numero)) {
            seleccionadosActuales.remove(numero)
            _uiState.update { it.copy(numerosSeleccionados = seleccionadosActuales, mensajeError = null) }
        } else {
            if (seleccionadosActuales.size >= 6) {
                _uiState.update { it.copy(mensajeError = "Solo puedes seleccionar exactamente 6 números") }
            } else {
                seleccionadosActuales.add(numero)
                _uiState.update { it.copy(numerosSeleccionados = seleccionadosActuales, mensajeError = null) }
            }
        }
    }

    /**
     * Valida y guarda la jugada en Firestore en sorteos/sorteo_prueba/jugadas/{uid}.
     */
    fun guardarJugada() {
        val estadoActual = _uiState.value.estadoSorteo
        if (estadoActual != FirestoreConstantes.ESTADO_ABIERTO) {
            _uiState.update { it.copy(mensajeError = "El sorteo ya inició, no se pueden registrar jugadas") }
            return
        }

        val seleccionados = _uiState.value.numerosSeleccionados
        // Validaciones requeridas
        if (seleccionados.size != 6) {
            _uiState.update { it.copy(mensajeError = "Debes seleccionar exactamente 6 números") }
            return
        }

        if (seleccionados.any { it < 1 || it > 45 }) {
            _uiState.update { it.copy(mensajeError = "Todos los números deben estar entre 1 y 45 inclusive") }
            return
        }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            _uiState.update { it.copy(mensajeError = "No hay un usuario autenticado") }
            return
        }

        _uiState.update { it.copy(cargando = true, mensajeError = null, mensajeExito = null) }

        viewModelScope.launch {
            try {
                // Verificar en tiempo real antes de guardar que el sorteo continúe 'abierto'
                val sorteoDoc = firestore.collection(FirestoreConstantes.COLECCION_SORTEOS)
                    .document(FirestoreConstantes.SORTEO_ID)
                    .get()
                    .await()

                val estadoSorteoServidor = sorteoDoc.getString(FirestoreConstantes.CAMPO_ESTADO) ?: FirestoreConstantes.ESTADO_ABIERTO
                if (estadoSorteoServidor != FirestoreConstantes.ESTADO_ABIERTO) {
                    _uiState.update {
                        it.copy(
                            estadoSorteo = estadoSorteoServidor,
                            cargando = false,
                            mensajeError = "El sorteo ya inició o finalizó, no se pueden registrar jugadas"
                        )
                    }
                    return@launch
                }

                // Verificar que no exista ya una jugada previa de este usuario
                val jugadaRef = firestore.collection(FirestoreConstantes.COLECCION_SORTEOS)
                    .document(FirestoreConstantes.SORTEO_ID)
                    .collection(FirestoreConstantes.SUBCOLECCION_JUGADAS)
                    .document(uid)

                val jugadaExistente = jugadaRef.get().await()
                if (jugadaExistente.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val numerosGuardados = (jugadaExistente.get(FirestoreConstantes.CAMPO_NUMEROS) as? List<Long>)?.map { it.toInt() }?.sorted()
                    _uiState.update {
                        it.copy(
                            jugadaGuardada = numerosGuardados,
                            cargando = false,
                            mensajeExito = "Ya registraste tu jugada previamente",
                            mensajeError = null
                        )
                    }
                    return@launch
                }

                // Guardar jugada con enteros ordenados de menor a mayor
                val numerosOrdenados = seleccionados.sorted()
                val datosJugada = hashMapOf(
                    FirestoreConstantes.CAMPO_SORTEO_ID to FirestoreConstantes.SORTEO_ID,
                    FirestoreConstantes.CAMPO_UID to uid,
                    FirestoreConstantes.CAMPO_NUMEROS to numerosOrdenados
                )

                jugadaRef.set(datosJugada).await()

                _uiState.update {
                    it.copy(
                        jugadaGuardada = numerosOrdenados,
                        cargando = false,
                        mensajeExito = "¡Jugada registrada con éxito!",
                        mensajeError = null
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        mensajeError = "Error al guardar la jugada: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
