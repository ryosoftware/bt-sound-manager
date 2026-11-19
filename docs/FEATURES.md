# Funcionalidades

## 1. Control de Bluetooth en Ajustes

La app abre en Ajustes. Cuando el permiso de Bluetooth está concedido, la parte superior muestra:

- estado actual de Bluetooth;
- dispositivos conectados, con su nombre cuando está disponible;
- botón para activar o desactivar Bluetooth.

Si faltan permisos de Bluetooth, la card de control no se muestra y la sección de permisos permite solicitarlos.

## 2. Monitorización Bluetooth

- Se notifica cualquier conexión o desconexión, sin filtro por dispositivo.
- Las direcciones se normalizan en mayúsculas.
- La detección de dispositivos conectados combina la consulta por perfiles (`getProfileProxy`) con los eventos ACL, que mantienen el estado en vivo.
- Los nombres de dispositivos se guardan y se reutilizan cuando el sistema no proporciona uno.
- Al entrar en la app se actualizan los nombres de dispositivos emparejados y conectados.
- Sin servicio activo, el receiver de manifiesto procesa los broadcasts que el sistema entregue sin exigir la detección activa; el servicio en primer plano es la vía fiable en segundo plano.

## 3. Notificaciones

Canales independientes:

- dispositivos conectados;
- dispositivos desconectados;
- automatización;
- servicio en segundo plano.

Comportamiento:

- al conectarse un dispositivo se oculta la notificación de desconexión;
- al desconectarse se oculta la de conexión;
- al encenderse Bluetooth se muestra una notificación informativa;
- al apagarse Bluetooth se oculta la de conexión y se muestra una notificación con acción para activarlo;
- las notificaciones de dispositivo abren el historial;
- la notificación de automatización permite desactivarla, activar Bluetooth o abrir el acceso a No molestar.

## 4. Historial de eventos

Tipos de evento:

- Bluetooth activado;
- Bluetooth desactivado;
- dispositivo conectado;
- dispositivo desconectado.

Funciones:

- agrupación por día, con Hoy/Ayer/fecha;
- filtros por tipo de evento y por dispositivo;
- borrado de eventos visibles, de un día o de todo el historial;
- nombre resuelto por nombre actual guardado, nombre del evento o dirección;
- retención configurable entre 3 y 90 días, 7 por defecto, desde la medianoche local;
- ocultación de pares de conexión/desconexión separados por menos de un segundo.

## 5. Automatización del modo de sonido

Configuración:

- activar o desactivar la automatización;
- horario todo el día o franja de inicio/fin, incluida la franja que cruza medianoche;
- todos los dispositivos Bluetooth o una selección;
- modo cuando hay al menos un dispositivo configurado conectado;
- modo cuando no hay dispositivos conectados;
- forzar el modo si se cambia manualmente;
- restaurar el volumen multimedia tras el cambio.

Modos:

- Normal;
- No molestar;
- Solo alarmas;
- Silencio.

Reglas:

- la automatización solo se aplica si está activada y hay dispositivos configurados;
- fuera del horario no se cambia el modo;
- el cambio se aplica 3 segundos después de la conexión o desconexión;
- si el servicio no está activo, el receiver delega el cambio en WorkManager con un retraso mínimo de 3 segundos;
- se descarta el evento si el estado del dispositivo vuelve a cambiar;
- si el modo actual ya es el deseado, no se vuelve a aplicar;
- si el usuario cambia manualmente el modo y “forzar” está activo, se reaplica;
- se guarda el volumen multimedia antes de cambiar el modo y se restaura si la opción está activa;
- se reevalúa en arranque (con 15 segundos de retraso), actualización de la app, inicio de la app, cambio de usuario, cambio de filtro de interrupción, alarma de inicio del horario y eventos de conexión/desconexión;
- al reevaluar nunca se activa Bluetooth: si está apagado se aplica el modo configurado para “sin dispositivos conectados”.

## 6. Servicio de segundo plano

- Se inicia cuando está activa alguna de las opciones de detección.
- Se detiene cuando ambas están desactivadas.
- Muestra una notificación persistente mientras está en ejecución.
- Detecta Bluetooth, cambios de filtro de interrupción, cambios de usuario y apagado del dispositivo.
- Si el servicio está en ejecución, evita que el receptor del manifiesto procese los mismos eventos.

## 7. Reactivación al cambiar de usuario

- Se puede activar desde Ajustes; al activarla se activa también “Detección activa”.
- Al volver el usuario, se intenta aplicar la automatización.
- Se intenta activar Bluetooth cuando el sistema lo permite.
- Si la activación silenciosa no es posible, se muestra una notificación con acción para activarlo.
- No requiere permiso de superposición.

## 8. Ajustes

- estado y control de Bluetooth (visible solo con el permiso concedido);
- permisos de Bluetooth, notificaciones, acceso a No molestar, alarmas exactas y optimización de batería;
- automatización y su configuración;
- retención del historial de eventos (3 a 90 días);
- detección Bluetooth en segundo plano;
- reactivación al cambiar de usuario;
- tema Sistema/Claro/Oscuro;
- color dinámico;
- en Acerca de: enlace al repositorio de GitHub y acceso a la información de la app;
- versión de la app.

## 9. Copia de seguridad y restauración

- El menú de tres puntos de Ajustes permite **Copiar datos** y **Restaurar datos** mediante el selector de documentos del sistema, sin permisos de almacenamiento.
- La copia se guarda en un JSON legible con versión de formato, versión de la app, fecha, eventos y ajustes.
- No se copian los nombres de dispositivos (caché derivada), el estado interno de automatización ni los IDs de Room.
- Al restaurar se muestran dos opciones siempre disponibles: **Eventos** y **Ajustes**.
- Restaurar eventos borra la tabla actual y restaura los del archivo, aunque el archivo no contenga eventos.
- Restaurar ajustes sobrescribe todos los ajustes actuales.
- Después de restaurar se aplica la retención resultante y se eliminan los eventos que queden fuera del rango.
- Si se restauran ajustes, se sincroniza el servicio en segundo plano y se reevalúa la automatización.
- Después de restaurar ajustes se recrea la Activity y se reconstruye la UI de Compose para aplicar el tema y el resto de estado visual desde cero.

## 10. Selección de dispositivos

- lista dispositivos emparejados, conectados y con nombre guardado;
- búsqueda por nombre o dirección;
- indicador de conectado/emparejado actualizado en vivo mediante los eventos ACL;
- selección múltiple;
- guardado automático para la automatización al salir con la flecha de la barra superior o con el gesto/botón atrás.

## 11. Tema y accesibilidad

- Material 3.
- Tema claro, oscuro y automático.
- Color dinámico Material You en Android 12+.
- Paleta de respaldo azul.
- Contenido con descripciones.
- Objetivos táctiles de al menos 48 dp.
- Soporte de fuente dinámica y RTL.
- Navegación inferior en móvil con dos destinos (Eventos y Ajustes) y contenido adaptado a pantallas grandes.

## 12. Privacidad

- La app no declara el permiso `INTERNET`.
- No realiza peticiones de red.
- No incluye publicidad, analítica ni compras.
- No comparte datos con terceros.
- Los datos se guardan localmente en Room y DataStore.
