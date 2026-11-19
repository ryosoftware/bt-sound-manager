# AGENTS.md

## 1. Identidad del proyecto

- **Nombre visible:** BT Sound Manager
- **applicationId:** `com.ryosoftware.bt_sound_manager`
- **namespace:** `com.ryosoftware.bt_sound_manager`
- **versionName:** `1.0`
- **versionCode:** `1`
- **minSdk:** `26`
- **compileSdk / targetSdk:** `37`
- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose + Material Design 3

Aplicación Android para monitorizar conexiones Bluetooth, registrar eventos y automatizar el modo de interrupción del dispositivo.

## 2. Alcance funcional

La app incluye:

1. Monitorización global de dispositivos Bluetooth.
2. Notificaciones de conexión y desconexión de cualquier dispositivo, y de Bluetooth encendido/apagado.
3. Historial de eventos Bluetooth con agrupación por día.
4. Filtros de eventos por tipo y dispositivo.
5. Borrado de eventos visibles, por día o completos.
6. Nombre de dispositivo resuelto desde el nombre actual guardado, el nombre del evento o la dirección.
7. Automatización del modo de interrupción:
   - activación y desactivación
   - horario o todo el día
   - todos los dispositivos o selección
   - modo cuando hay dispositivos conectados
   - modo cuando no hay dispositivos conectados
   - forzar el modo de interrupción
   - restaurar el volumen multimedia
8. Servicio en primer plano para detectar eventos Bluetooth, DND y cambios de usuario.
9. Reactivación al volver el usuario, con intento de activación de Bluetooth y notificación accionable cuando el sistema lo requiera.
10. Ajustes de notificación del sistema.
11. Tema del sistema, claro u oscuro.
12. Color dinámico Material You en Android 12+.
13. Aviso de optimización de batería.
14. Selector de dispositivos para la automatización, con nombres actualizados al entrar en la app.

La aplicación no realiza peticiones de red ni declara el permiso `INTERNET`.

## 3. Stack técnico

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- StateFlow y Coroutines
- Hilt
- Room
- DataStore Preferences
- WorkManager
- Kotlin Serialization cuando sea necesario

Versiones centralizadas en `gradle/libs.versions.toml`.

No añadir dependencias de red, monetización, analítica o publicidad sin una necesidad funcional aprobada.

## 4. Arquitectura

Un único módulo `:app` con separación por capas:

```text
com.ryosoftware.bt_sound_manager
├── Main.kt
├── MainActivity.kt
├── di/
├── core/
│   ├── model/
│   ├── time/
│   └── util/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── local/db/
│   ├── local/prefs/
│   ├── bluetooth/
│   ├── notifications/
│   └── repository/
├── presentation/
│   ├── navigation/
│   ├── theme/
│   ├── components/
│   ├── events/
│   ├── settings/
│   ├── devices/
│   └── automation/
├── service/
└── receiver/
```

Reglas:

- La lógica de negocio no va en composables.
- Los composables no acceden a Room, DataStore ni Bluetooth directamente.
- Los ViewModels exponen `StateFlow<UiState>` y eventos de un solo uso.
- Los repositorios son la única puerta de entrada a datos y sistema.
- Los servicios y receptores delegan en casos de uso o procesadores inyectados.
- Evitar dependencias circulares entre capas.

## 5. Modelo de datos

### Room

Base de datos: `bt_sound_manager.db`.

Tablas:

- `bluetooth_events`
  - `id`
  - `time_utc`
  - `event_type`
  - `device_address`
  - `device_name`
- `device_names`
  - `address` (clave primaria)
  - `name`

`time_utc` almacena milisegundos UTC. La presentación convierte a la zona horaria del sistema.

### DataStore

Archivo: `bt_sound_manager_settings`.

Incluye:

- automatización completa
- servicio en segundo plano
- tema y color dinámico
- retención del historial de eventos (3 a 90 días, 7 por defecto)
- estado interno de automatización:
  - último modo aplicado
  - último tiempo de ejecución
  - volumen multimedia previo

## 6. Reglas de negocio

### Notificaciones

- Se notifica cualquier conexión o desconexión de dispositivo, sin filtro por dispositivo.
- Las direcciones se normalizan en mayúsculas.
- Las notificaciones de conexión y desconexión usan canales independientes.
- Al conectarse un dispositivo se oculta la notificación de desconexión, y viceversa.
- Al encenderse Bluetooth se muestra una notificación informativa.
- Al apagarse Bluetooth se oculta la notificación de conexión y se muestra una notificación accionable para activarlo.

### Eventos

- Tipos: Bluetooth ON, Bluetooth OFF, dispositivo conectado, dispositivo desconectado.
- Se conservan los eventos según la retención configurada, entre 3 y 90 días (7 por defecto).
- La retención se calcula desde la medianoche local actual menos los días configurados.
- Se eliminan pares de conexión/desconexión separados por menos de 1 segundo para la visualización.
- El nombre del dispositivo se resuelve con este orden: nombre actual guardado, nombre del evento, dirección.
- Al entrar en la app se refrescan los nombres de dispositivos emparejados y conectados en Room.
- Sin servicio activo, el receiver de manifiesto procesa los broadcasts que el sistema entregue sin exigir la detección activa; el servicio en primer plano es la vía fiable en segundo plano.

### Automatización

- Solo se aplica si está activada y hay dispositivos configurados.
- Si está dentro del horario:
  - hay al menos un dispositivo configurado conectado → modo configurado para conectados
  - no hay dispositivos conectados → modo configurado para sin conexión
- Fuera del horario no se cambia el modo.
- El cambio se aplica con un retraso de 3 segundos tras conexión/desconexión.
- Si el servicio no está activo, el receiver encola el cambio con WorkManager con un retraso mínimo de 3 segundos; puede ejecutarse algo después según el sistema.
- Se descarta el evento si el estado del dispositivo cambió de nuevo.
- Si el modo actual ya es el deseado, no se vuelve a aplicar.
- Si el usuario cambia manualmente el modo y `forceInterruptMode` está activo, se reaplica.
- Antes de cambiar el modo se guarda el volumen multimedia; se restaura si la opción está activa.
- La automatización se reevalúa en:
  - arranque del dispositivo, con un retraso de 15 segundos
  - actualización de la app
  - inicio de la app
  - cambio de usuario
  - cambio del filtro de interrupción
  - alarma de inicio de horario
  - conexión/desconexión de dispositivos
- Al reevaluar nunca se activa Bluetooth: si está apagado se aplica el modo configurado para “sin dispositivos conectados”.
- La reevaluación se ejecuta siempre en segundo plano.

### Modos de interrupción

- Normal → `INTERRUPTION_FILTER_ALL`
- No molestar → `INTERRUPTION_FILTER_PRIORITY`
- Solo alarmas → `INTERRUPTION_FILTER_ALARMS`
- Silencio total → `INTERRUPTION_FILTER_NONE`

## 7. Componentes Android

### Permisos

```text
POST_NOTIFICATIONS
RECEIVE_BOOT_COMPLETED
VIBRATE
BLUETOOTH (maxSdk 30)
BLUETOOTH_ADMIN (maxSdk 30)
BLUETOOTH_SCAN (neverForLocation)
BLUETOOTH_CONNECT
ACCESS_NOTIFICATION_POLICY
FOREGROUND_SERVICE
FOREGROUND_SERVICE_SPECIAL_USE
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
SCHEDULE_EXACT_ALARM
MODIFY_AUDIO_SETTINGS
```

No se declaran permisos de red, cuentas, superposición, almacenamiento ni publicidad.

### Servicios y receptores

- `MonitorForegroundService`: monitorización continua con notificación persistente. Tipo `specialUse`.
- `SystemEventReceiver`: eventos Bluetooth, DND, usuario y apagado.
- `BootReceiver`: arranque y actualización de la app.
- `NotificationActionReceiver`: acciones de notificaciones.
- `AutomationAlarmReceiver`: inicio del horario de automatización.
- `BluetoothEnableProxyActivity`: solicitud de activación de Bluetooth desde una notificación.

### Alarmas

- Se usa `AlarmManager`.
- Se comprueba `canScheduleExactAlarms()`.
- Si no hay permiso de alarmas exactas, se usa un fallback inexacto.
- La alarma se reprograma en cada evaluación.

## 8. Interfaz y navegación

Bottom navigation con dos destinos:

- **Eventos:** historial agrupado, filtros y borrado.
- **Ajustes:** estado y control de Bluetooth, permisos, notificaciones, automatización, retención del historial, segundo plano y apariencia.

La app abre en **Ajustes**. La card de estado de Bluetooth solo se muestra cuando el permiso de Bluetooth está concedido.

Subpantallas:

- Selección de dispositivos para la automatización.
- Configuración de automatización.

Tema:

- `Sistema`, `Claro`, `Oscuro`.
- Color dinámico activable.
- Paleta de respaldo azul.
- Tipografía y componentes Material 3.

Accesibilidad:

- Descripciones de contenido.
- Objetivos táctiles de al menos 48 dp.
- Contraste adecuado.
- Soporte de fuente dinámica, RTL y tamaños de pantalla grandes.
- Navigation rail en pantallas anchas.

## 9. Build y verificación

Requisitos:

- JDK 21
- Android SDK 37
- `local.properties` con `sdk.dir`

Comandos:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

La verificación de Bluetooth real, DND, activación desde notificación y comportamiento de fabricantes requiere dispositivo físico.

## 10. Testing

- Unitarias para reglas de negocio puras.
- ViewModel con repositorios fake y Turbine.
- Repositorios con Room en memoria y DataStore temporal.
- Compose UI tests para estados, navegación y acciones principales.
- Instrumentadas para persistencia y flujos básicos.
- No afirmar que una función está probada si no se ha ejecutado.

## 11. Convenciones de código

- Kotlin idiomático.
- Inmutabilidad siempre que sea posible.
- Funciones pequeñas y responsabilidades claras.
- Coroutines y Flow; nada bloqueante en el hilo principal.
- Sin fugas de ciclo de vida.
- Recursos de texto localizados en `strings.xml`.
- Sin secretos ni claves en el repositorio.
- Sin `TODO` sin documentar.
- Sin comentarios innecesarios.
- No duplicar componentes de UI.

## 12. Flujo de trabajo para agentes

1. Leer `AGENTS.md` antes de modificar código.
2. Revisar la estructura y los contratos existentes.
3. Planificar cambios antes de implementar.
4. Implementar por capas y mantener la compilación.
5. Ejecutar `assembleDebug`, tests y lint.
6. Corregir errores antes de continuar.
7. Actualizar este documento si cambian decisiones o comandos.
8. No hacer commit salvo petición explícita.

## 13. Documentación

- `docs/ARCHITECTURE.md`
- `docs/FEATURES.md`
- `docs/TESTING.md`
