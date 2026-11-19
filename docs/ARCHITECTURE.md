# Arquitectura

## 1. Visión general

La aplicación es un único módulo Android `:app` escrito en Kotlin. La separación se organiza por capas dentro del propio módulo para mantener la arquitectura proporcional al tamaño del producto:

```text
presentation → domain ← data
       ↑          ↑
   service/receiver
```

- **presentation:** Compose, navegación, ViewModels y estado de UI.
- **domain:** modelos, reglas de negocio puras y contratos de repositorio.
- **data:** Room, DataStore, Bluetooth, notificaciones, alarmas e implementaciones de repositorio.
- **service/receiver:** ejecución en segundo plano y entrada de eventos del sistema.
- **di:** módulos de Hilt que conectan contratos e implementaciones.

## 2. Estructura de paquetes

```text
com.ryosoftware.bt_sound_manager
├── Main.kt
├── MainActivity.kt
├── BluetoothEnableProxyActivity.kt
├── di/
│   └── AppModule.kt
├── core/
│   ├── time/AppClock.kt
│   └── util/
│       ├── Address.kt
│       ├── AppActions.kt
│       └── InterruptModeResources.kt
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

## 3. Flujo de datos

### Ajustes

```text
DataStore Preferences
        ↓ Flow<Preferences>
SettingsDataStore
        ↓ Flow<AppSettings> / snapshot()
SettingsRepository
        ↓
ViewModels / AutomationCoordinator / EventProcessor
```

Las escrituras pasan siempre por `SettingsRepository`; la UI nunca accede a DataStore directamente.

### Eventos Bluetooth

```text
Broadcast del sistema
        ↓
SystemEventReceiver / MonitorForegroundService
        ↓
EventProcessor
        ├── BluetoothEventRepository → Room
        ├── DeviceRepository → Room (nombres)
        ├── MonitorNotificationRepository → notificaciones
        └── AutomationCoordinator → modo de interrupción
```

`EventsViewModel` observa `BluetoothEventRepository.observeEvents()` y transforma el flujo en grupos por día, aplicando filtros en memoria.

### Automatización

```text
Evento / alarma / arranque / cambio de usuario
        ↓
AutomationCoordinator.apply()
        ├── SettingsRepository.snapshot()
        ├── BluetoothRepository.connectedDevices()
        ├── InterruptModeRepository.currentMode()
        ├── AutomationPlanner.plan()
        ├── InterruptModeRepository.setMode()
        ├── RuntimeStateRepository.saveAppliedMode()
        ├── MonitorNotificationRepository.showAutomationNotification()
        └── AutomationScheduler.scheduleNextStart()
```

`AutomationPlanner` es una regla pura y testeable. El coordinador se encarga de los efectos del sistema.

Cuando el servicio está activo, `AutomationCoordinator.applyDelayed()` mantiene el retraso de 3 segundos en el scope del servicio. Si el servicio no está activo y el evento lo procesa `SystemEventReceiver`, el trabajo de automatización no se espera dentro de `goAsync()`:

```text
SystemEventReceiver → MonitorBroadcastHandler (DEFERRED)
        → EventProcessor (Room, nombres y notificaciones)
        → AutomationApplyScheduler
        → WorkManager (retraso mínimo de 3 s)
        → AutomationApplyWorker
        → AutomationCoordinator.applyForEvent()
```

Así el receiver termina en pocos cientos de milisegundos y el sistema puede ejecutar el cambio algo después de los 3 segundos.

La reevaluación se dispara en arranque, actualización de la app, inicio de la app, cambio de usuario, cambio de filtro de interrupción, alarma de horario y conexión/desconexión. `AutomationCoordinator.apply()` se ejecuta en `Dispatchers.Default`, por lo que nunca bloquea el hilo principal. Nunca activa Bluetooth: si está apagado se evalúa como si no hubiera dispositivos conectados.

## 4. Inyección de dependencias

Hilt centraliza la construcción de:

- `DataStore<Preferences>` (`bt_sound_manager_settings`).
- `AppDatabase` (`bt_sound_manager.db`) y sus DAOs.
- Implementaciones de repositorios.
- `AppClock` y `AutomationScheduler`.
- `HiltWorkerFactory` para `MaintenanceWorker`.

`Main` implementa `Configuration.Provider` e inicializa WorkManager con el factory de Hilt. El inicializador por defecto de WorkManager se elimina en el manifiesto para evitar dobles inicializaciones.

## 5. Persistencia

### Room

Base de datos `bt_sound_manager.db`:

- `bluetooth_events`: id, tiempo UTC, tipo de evento, dirección y nombre del dispositivo.
- `device_names`: dirección como clave primaria y nombre guardado.

La retención se aplica al insertar un evento y al cambiar el ajuste: se eliminan los anteriores a la medianoche local actual menos los días configurados (3 a 90, 7 por defecto).

### DataStore

Archivo `bt_sound_manager_settings`:

- automatización completa;
- opciones de segundo plano;
- tema y color dinámico;
- días de retención del historial de eventos;
- estado interno de automatización: último modo, último tiempo de ejecución y volumen previo.

## 6. Bluetooth

`BluetoothRepositoryImpl`:

- comprueba `BLUETOOTH_CONNECT` antes de consultar dispositivos;
- obtiene dispositivos emparejados;
- obtiene dispositivos conectados de dos formas complementarias:
  - perfiles clásicos y BLE (`HEADSET`, `A2DP`, `SAP`, `HID_DEVICE`, `HEARING_AID`, `LE_AUDIO`) mediante `BluetoothAdapter.getProfileProxy` y `BluetoothProfile.getConnectedDevices`, en paralelo y con timeout;
  - `GATT`/`GATT_SERVER` mediante `BluetoothManager.getConnectedDevices`;
- mantiene un estado vivo de dispositivos conectados actualizado por los eventos ACL (`EventProcessor`), que se combina con la consulta de perfiles;
- captura `SecurityException` e `IllegalArgumentException` por perfiles no disponibles en algunos OEMs;
- expone un `Flow<BluetoothSnapshot>` combinando el estado del adaptador con el estado vivo de dispositivos conectados;
- normaliza direcciones a mayúsculas;
- permite solicitar un refresco inmediato del snapshot, que relanza la consulta de perfiles.

`RefreshDeviceNames` actualiza en Room los nombres de dispositivos emparejados y conectados cada vez que `MainActivity` vuelve a primer plano, y devuelve la lista combinada.

## 7. Notificaciones y DND

`MonitorNotificationRepositoryImpl` crea cuatro canales:

- dispositivos conectados;
- dispositivos desconectados;
- automatización;
- servicio en segundo plano.

Las notificaciones de dispositivo se emiten para cualquier dispositivo, sin filtro por selección. Bluetooth encendido y apagado también generan notificación; la de apagado incluye una acción para abrir el diálogo del sistema. Las notificaciones de dispositivo abren el historial. La notificación de automatización incluye acciones para desactivar la automatización, activar Bluetooth y, cuando faltan permisos, abrir los ajustes de acceso a No molestar.

`AndroidInterruptModeRepository` mapea los modos de la app a los filtros de Android y comprueba el acceso a la política de notificaciones.

## 8. Servicio y receptores

- `MonitorForegroundService`: servicio en primer plano de tipo `specialUse`; registra un receptor runtime para Bluetooth, DND, usuario y apagado.
- `MonitorBroadcastHandler`: interpreta de forma compartida los broadcasts de Bluetooth, DND, usuario y apagado, aplica las restricciones de settings y decide si el servicio debe detenerse; lo usan tanto el servicio como `SystemEventReceiver`.
- `SystemEventReceiver`: recibe eventos del manifiesto; cuando el servicio no está en ejecución procesa los broadcasts que el sistema entregue sin exigir la detección activa, y evita duplicarlos si el servicio está gestionando Bluetooth o usuario.
- `BootReceiver`: encola `MaintenanceWorker` tras arranque o actualización.
- `MaintenanceWorker`: en arranque espera 15 segundos antes de aplicar la automatización; después sincroniza el servicio.
- `AutomationApplyWorker`: ejecuta la automatización que el receiver encola cuando el servicio no está activo; aplica el retraso mínimo de 3 segundos mediante `WorkManager` y reintenta si falla.
- `NotificationActionReceiver`: gestiona acciones de notificación.
- `AutomationAlarmReceiver`: reevalúa la automatización al inicio del horario.
- `BluetoothEnableProxyActivity`: actividad transparente que solicita la activación de Bluetooth al usuario.

## 9. Alarmas

`AutomationSchedulerImpl` usa `AlarmManager` con `RTC_WAKEUP`.

- Si el permiso de alarmas exactas está disponible, usa `setExactAndAllowWhileIdle`.
- Si no, usa `setAndAllowWhileIdle`.
- Si el sistema lanza `SecurityException`, cae a `set`.
- La alarma se cancela cuando la automatización está desactivada o el horario es “todo el día”.

## 10. UI y tema

- Navegación: `NavHost` con bottom navigation para Eventos y Ajustes; la app abre en Ajustes.
- Ajustes muestra, cuando el permiso de Bluetooth está concedido, la card de estado con dispositivos conectados y control para activar o desactivar Bluetooth.
- Ajustes concentra la gestión de permisos: Bluetooth, notificaciones, acceso a No molestar, alarmas exactas, optimización de batería y acceso directo a la información de la app.
- `NavigationRail` en anchos de ventana de 600 dp o superiores.
- Subpantallas: automatización y selección de dispositivos para la automatización.
- Tema: `MonitorizeTheme` con modos Sistema/Claro/Oscuro y color dinámico en Android 12+.
- Paleta de respaldo azul.
- Componentes compartidos en `presentation/components`.
- ViewModels con `StateFlow<UiState>` y canales para eventos de un solo uso.
- Uso de `collectAsStateWithLifecycle` y `LifecycleEventEffect` para refrescar estado del sistema.

## 11. Manejo de errores

- Los repositorios capturan errores esperables del sistema (permisos, perfiles, notificaciones, alarmas).
- Las operaciones de persistencia y acciones de UI se ejecutan en coroutines.
- Los ViewModels emiten eventos para snackbars o diálogos.
- No se usa un manejador global de crashes; la app sigue el comportamiento estándar de Android.

## 12. Límites conocidos

- La reactivación automática de Bluetooth tras cambio de usuario depende de las restricciones de Android; en versiones modernas se ofrece una notificación accionable.
- El servicio `specialUse` requiere justificación en Play Console.
- Los permisos de alarmas exactas y de política de notificación dependen de la configuración del usuario.
- Cuando el receiver delega en WorkManager, el cambio de modo puede ejecutarse algo después de los 3 segundos según las restricciones del sistema.
- El comportamiento real de Bluetooth, DND y fabricantes requiere dispositivo físico.
