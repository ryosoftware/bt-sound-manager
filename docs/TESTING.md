# Testing

## 1. Estrategia

La verificación se organiza en cuatro niveles:

1. **Reglas de negocio puras:** pruebas unitarias JVM.
2. **ViewModels:** flujos, filtros y eventos con repositorios fake y Turbine.
3. **Persistencia y mapeo:** Room/DataStore y mapeo de preferencias.
4. **UI e integración:** Compose UI Testing e instrumentadas en emulador.

## 2. Comandos

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

## 3. Pruebas unitarias

Cubren:

- planificación de la automatización;
- creación de eventos de intento, aplicados y de fallo con causa, modo y primer dispositivo conectado;
- creación de eventos de cambio externo de modo;
- evento de automatización pendiente hasta confirmar el modo, con caducidad;
- sustitución del intento por el evento aplicado al confirmar;
- sustitución del intento por el evento de fallo al vencer el timeout o fallar `setMode`;
- confirmación tardía cuando el modo ya coincide al vencer el timeout;
- guardado del modo aplicado solo al confirmar;
- deduplicación de fallos consecutivos por bloqueo o permisos;
- descarte del evento pendiente cuando ocurre un cambio externo;
- detección de cambios propios y externos con supresión de broadcasts repetidos;
- los forzados por dirección aplican el modo aunque el modo actual no lo haya fijado la app;
- sin forzar, solo se cambia cuando está activo el modo contrario y fue fijado por la app;
- los flags de forzado no afectan a la dirección contraria;
- la notificación bloqueada explica el motivo con dispositivo, Bluetooth apagado o texto genérico;
- un cambio externo al modo sin dispositivos reaplica el modo con dispositivos si su forzado está activo;
- un cambio externo al modo con dispositivos reaplica el modo sin dispositivos si su forzado está activo;
- la reaplicación inmediata externa respeta el horario;
- al cambiar los forzados se suprime el aviso bloqueado y se conserva la notificación anterior;
- los setters de forzado llaman a `apply()` con la supresión activa y el resto de ajustes no;
- mapeo de iconos de los modos aplicados;
- ventana horaria, incluida la franja que cruza medianoche;
- cálculo del siguiente inicio, siguiente fin y siguiente transición del horario;
- estado de automatización todo el día, dentro y fuera del horario;
- retención de eventos y días configurables;
- minimización de pares de eventos;
- fusión de dispositivos emparejados y conectados;
- inclusión de dispositivos seleccionados aunque no estén emparejados ni conectados, con la dirección como nombre de reserva;
- «Detección activa» como única opción que arranca el servicio y dependencia de la reactivación al cambiar de usuario;
- persistencia independiente de detección y reactivación, también en copias de seguridad;
- gateo de `USER_FOREGROUND` por detección activa;
- bloqueo de detección y automatización sin permiso de Bluetooth y de automatización sin acceso a No molestar;
- gating del `MonitorBroadcastHandler` entre servicio y receiver, incluido el permiso de Bluetooth;
- encolado único de la automatización diferida con WorkManager;
- despacho del worker de automatización a las operaciones del coordinador;
- filtrado por categorías de evento (estado del Bluetooth y cambios de modo de audio), por dispositivo y borrado en `EventsViewModel`;
- sincronización del servicio al iniciar la app en `MainViewModel` sin aplicar la automatización;
- codificación y decodificación del JSON de copia de seguridad, incluido el modo de los eventos de automatización, intento, fallo y cambio externo;
- compatibilidad de backups v1, v2 y v3 y migración de `forceMode` a las dos direcciones;
- restauración selectiva de eventos y ajustes;
- aplicación de la retención después de restaurar;
- limpieza de la tabla al restaurar una copia sin eventos;
- recreación de la Activity al restaurar ajustes y no al restaurar solo eventos;
- mapeo de preferencias y valores por defecto.

- encolado y cancelación del timeout de intento con WorkManager;
- despacho del worker de timeout al coordinador.

### Resultado actual

- **160 pruebas unitarias** ejecutadas.
- **0 fallos**.
- **0 omitidas**.

## 4. Pruebas instrumentadas

Cubren:

- navegación inferior entre Eventos y Ajustes, con Ajustes como pantalla inicial;
- renderizado de una fila de evento con nombre de dispositivo y tipo;
- renderizado del texto de un evento de automatización con modo y dispositivo;
- renderizado de un evento de intento y de un evento de fallo;
- renderizado de un evento de cambio externo de modo;
- persistencia de un evento Bluetooth ON a través de `EventProcessor` y Room;
- sustitución atómica del evento de intento por el confirmado en Room.

### Resultado actual

- **8 pruebas instrumentadas** definidas.
- **3 pruebas instrumentadas** ejecutadas previamente en `Pixel_10_no_Google`.
- Las nuevas pruebas instrumentadas de eventos de automatización, intento y fallo quedan pendientes de ejecutar en dispositivo/emulador.
- Android 17 / API 37.
- **0 fallos** en la última ejecución previa.

## 5. Lint

`./gradlew lintDebug` finaliza correctamente.

- **0 errores**.
- **0 fatales**.
- Se mantienen avisos informativos sobre versiones de dependencias, permisos de batería, atributos disponibles solo en API superiores y constantes inline de perfiles Bluetooth. Son avisos esperados y no bloquean la build.

## 6. Verificación manual realizada

En emulador:

- arranque de la aplicación en Ajustes;
- card de estado y control de Bluetooth en Ajustes (visible con permiso concedido);
- gestión de permisos desde Ajustes;
- navegación a Eventos y Ajustes;
- pantalla de Automatización y estados deshabilitados;
- activación de la automatización con acceso a No molestar;
- notificación de automatización;
- selección de dispositivos para la automatización;
- guardado de la selección de dispositivos con la flecha de la barra superior y con el gesto/botón atrás;
- inicio y parada del servicio en primer plano;
- servicio `specialUse` con tipo correcto;
- apertura de la app mediante extra de destino de notificación;
- actualización de nombres de dispositivos al volver a primer plano;
- puesta en marcha del servicio en segundo plano al iniciar la app, sin aplicar el modo de sonido.

## 7. Verificación pendiente en dispositivo físico

No se puede certificar en emulador:

- conexión y desconexión real de dispositivos Bluetooth;
- recepción pasiva de broadcasts Bluetooth sin detección activa en Android 17;
- detección multipérfil con auriculares, relojes y equipos de audio;
- estado “Conectado” en vivo en la pantalla de selección de dispositivos al conectar/desconectar un dispositivo clásico;
- aplicación de la automatización al conectar un dispositivo clásico;
- fallback del receiver con WorkManager cuando el servicio no está activo, incluido el retraso mínimo de 3 segundos;
- cambio de la retención de eventos con el slider y borrado inmediato de los eventos fuera del nuevo rango;
- cambio real del filtro de interrupción;
- restauración del volumen multimedia;
- notificación de modo no aplicado por el forzado desactivado, con nombre de dispositivo;
- intento visible al aplicar, sustitución por evento aplicado al confirmar y por evento de fallo al vencer el timeout;
- comprobación de timeout real con WorkManager y notificación de fallo;
- activación de Bluetooth desde una notificación;
- comportamiento tras reinicio del dispositivo;
- cambio de usuario real;
- diferencias de fabricantes (MIUI, Samsung, etc.);
- alarmas exactas con el dispositivo en reposo;
- consumo de batería real;
- creación y restauración de copias de seguridad mediante el selector de documentos;
- aplicación de la retención tras restaurar una copia con eventos antiguos;
- recreación visible de la Activity y de la UI después de restaurar ajustes.

Estas comprobaciones deben realizarse en un dispositivo físico antes de considerar la app lista para producción.

## 8. Criterios de aceptación

- La build debug y release compilan.
- Las pruebas unitarias pasan.
- Las pruebas instrumentadas pasan en emulador.
- Lint no contiene errores.
- La persistencia sobrevive a reinicios del proceso.
- La navegación y el back stack funcionan.
- Los temas claro, oscuro y dinámico se aplican correctamente.
- La automatización respeta horario, dispositivos y permisos.
- La copia de seguridad se crea y se restaura por secciones, aplicando la retención.
- Las notificaciones y sus acciones funcionan según lo esperado.
