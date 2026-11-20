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
- creación de eventos de automatización con causa, modo y primer dispositivo conectado;
- mapeo de iconos de los modos aplicados;
- ventana horaria, incluida la franja que cruza medianoche;
- cálculo del siguiente inicio, siguiente fin y siguiente transición del horario;
- estado de automatización todo el día, dentro y fuera del horario;
- retención de eventos y días configurables;
- minimización de pares de eventos;
- fusión de dispositivos emparejados y conectados;
- inclusión de dispositivos seleccionados aunque no estén emparejados ni conectados, con la dirección como nombre de reserva;
- acoplamiento de las opciones de segundo plano;
- gating del `MonitorBroadcastHandler` entre servicio y receiver;
- encolado único de la automatización diferida con WorkManager;
- despacho del worker de automatización a las operaciones del coordinador;
- filtrado y borrado en `EventsViewModel`;
- reevaluación de la automatización al iniciar la app en `MainViewModel`;
- codificación y decodificación del JSON de copia de seguridad, incluido el modo de los eventos de automatización;
- restauración selectiva de eventos y ajustes;
- aplicación de la retención después de restaurar;
- limpieza de la tabla al restaurar una copia sin eventos;
- recreación de la Activity al restaurar ajustes y no al restaurar solo eventos;
- mapeo de preferencias y valores por defecto.

### Resultado actual

- **95 pruebas unitarias** ejecutadas.
- **0 fallos**.
- **0 omitidas**.

## 4. Pruebas instrumentadas

Cubren:

- navegación inferior entre Eventos y Ajustes, con Ajustes como pantalla inicial;
- renderizado de una fila de evento con nombre de dispositivo y tipo;
- renderizado del texto de un evento de automatización con modo y dispositivo;
- persistencia de un evento Bluetooth ON a través de `EventProcessor` y Room.

### Resultado actual

- **3 pruebas instrumentadas** ejecutadas previamente en `Pixel_10_no_Google`.
- La nueva prueba instrumentada de eventos de automatización queda pendiente de ejecutar en dispositivo/emulador.
- Android 17 / API 37.
- **0 fallos**.

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
- reevaluación de la automatización al iniciar la app, con Bluetooth encendido y apagado.

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
