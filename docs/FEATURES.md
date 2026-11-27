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
- Requiere el permiso de Bluetooth: sin él la detección activa está deshabilitada, el servicio no se inicia y el receiver no procesa eventos Bluetooth.

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
- la notificación de automatización permite desactivarla, activar Bluetooth o abrir el acceso a No molestar;
- en Android 14 o anterior, al intentar un cambio se muestra una notificación de intento con el modo deseado;
- al confirmarse, la notificación se actualiza con el modo establecido y los dispositivos conectados;
- si el intento no se confirma o falla, se muestra una notificación de fallo;
- cuando el usuario u otra app cambia el modo de sonido, se registra un cambio externo y la notificación de automatización lo indica al recibirlo, aunque después se reaplique el modo;
- si el modo no se aplica porque el modo actual no lo permite, la notificación lo explica con el dispositivo implicado, el Bluetooth apagado o un texto genérico;
- al activar o desactivar un forzado no se muestra el aviso de modo bloqueado: se conserva la notificación anterior;
- al entrar y salir del horario de automatización se actualizan la notificación de automatización y la del servicio en segundo plano.

## 4. Historial de eventos

Tipos de evento:

- Bluetooth activado;
- Bluetooth desactivado;
- dispositivo conectado;
- dispositivo desconectado;
- modo automático intentado, aplicado o fallido por dispositivo monitorizado conectado;
- modo automático intentado, aplicado o fallido por ausencia de dispositivos conectados;
- modo automático intentado, aplicado o fallido por Bluetooth desactivado;
- modo automático fallido por permisos insuficientes;
- modo de sonido cambiado externamente por el usuario u otra app.

Funciones:

- agrupación por día, con Hoy/Ayer/fecha;
- filtros por categoría de evento (estado del Bluetooth y cambios de modo de audio) y por dispositivo, con una entrada por dispositivo que engloba todos sus eventos;
- borrado de eventos visibles, de un día o de todo el historial; al borrar los visibles se borran también los pares ocultos asociados, y borrar un día incluye sus eventos filtrados u ocultos;
- nombre resuelto por nombre actual guardado, nombre del evento o dirección;
- eventos de automatización con el texto «Se activa el modo “X” porque “Y” está conectado», «Se intenta establecer el modo “X”…» o «No se ha podido establecer el modo “X”…»;
- icono del modo en los eventos de automatización, con estilo atenuado para los intentos y de error para los fallos;
- retención configurable entre 3 y 90 días, 7 por defecto, desde la medianoche local;
- ocultación de pares de conexión/desconexión separados por menos de un segundo.

## 5. Automatización del modo de sonido

Configuración:

- activar o desactivar la automatización;
- horario todo el día o franja de inicio/fin, incluida la franja que cruza medianoche;
- todos los dispositivos Bluetooth o una selección;
- modo cuando hay al menos un dispositivo configurado conectado;
- modo cuando no hay dispositivos conectados;
- forzar el modo al conectar dispositivos y al no haber dispositivos conectados, cada dirección por separado (su efecto depende de la versión de Android; ver las reglas);
- restaurar el volumen multimedia tras el cambio;
- reactivación al cambiar de usuario, que requiere «Detección activa».

Modos:

- Normal;
- No molestar;
- Solo alarmas;
- Silencio.

Reglas comunes:

- la automatización solo se aplica si está activada y hay dispositivos configurados;
- fuera del horario no se cambia el modo;
- el cambio se aplica 3 segundos después de la conexión o desconexión;
- si el servicio no está activo, el receiver delega el cambio en WorkManager con un retraso mínimo de 3 segundos;
- se descarta el evento si el estado del dispositivo vuelve a cambiar;
- no es posible saber qué app realizó un cambio externo;
- al activar o desactivar un forzado se reevalúa la automatización y, si el modo queda bloqueado, se conserva la notificación anterior;
- si el cambio queda bloqueado, la notificación de automatización lo explica, con el nombre del dispositivo cuando el evento lo permite, y se registra un evento de fallo salvo que el mismo fallo sea consecutivo;
- los bloqueos por permisos también registran un evento de fallo;
- se reevalúa en arranque (con 15 segundos de retraso), actualización de la app, cambio de usuario, alarmas de inicio y fin del horario y eventos de conexión/desconexión; abrir la app no reevalúa la automatización, solo sincroniza el servicio en segundo plano;
- al reevaluar nunca se activa Bluetooth: si está apagado se aplica el modo configurado para “sin dispositivos conectados”;
- requiere el permiso de Bluetooth y el acceso a No molestar: si falta alguno, la automatización no se aplica, los controles aparecen deshabilitados y se conservan los valores guardados;
- los cambios externos se registran y notifican al recibirlos.

Android 14 o anterior (se cambia el modo global):

- si el modo actual ya es el deseado, no se vuelve a aplicar;
- al intentar el cambio se inserta un evento de intento con la causa y el modo deseado;
- al confirmar, el intento se sustituye por el evento aplicado con el modo y el primer dispositivo monitorizado conectado cuando corresponde;
- para abandonar el modo con dispositivos sin forzado, la app exige que sea suyo: solo considera suyo un modo que ella ha cambiado y confirmado; el intento pendiente se guarda para recuperarlo si el proceso termina antes de confirmar;
- si el modo deseado ya estaba activo, la app no pasa a considerarlo suyo; para entrar en el modo con dispositivos basta con que el modo sin dispositivos sea el último visto por la app;
- la app deja de considerar suyo el modo al detectar un cambio externo, y conserva esa información al reiniciar el dispositivo;
- si el intento no se confirma, se comprueba el modo actual al vencer el timeout: si coincide se confirma; si no, el intento se sustituye por un evento de fallo;
- si otro intento reemplaza al pendiente antes de confirmarse, el intento anterior permanece en el historial;
- se reaplica de inmediato si el usuario u otra app activa el modo configurado de una dirección y el forzado de la dirección contraria está activo;
- con el forzado de la dirección activo, el modo deseado se aplica aunque el modo actual no lo haya fijado la app; con el forzado desactivado, solo se cambia si está activo el modo contrario y fue fijado por la app;
- se guarda el volumen multimedia antes de cambiar el modo y, si la opción está activa, se restaura al confirmarse el cambio (hasta 15 segundos después del intento).

Android 15 o superior (reglas propias de No molestar):

- la app no cambia el modo global: activa una regla propia (una por modo restrictivo) o, para el modo normal, las desactiva todas;
- el resultado se conoce al momento: no hay intentos pendientes, y el evento aplicado se registra cuando la regla cambia;
- las restricciones del usuario o de otras apps se mantienen; si al pasar al modo normal queda alguna, la notificación lo indica sin marcar fallo;
- la regla se activa aunque hubiera otro modo puesto, y Android aplica la restricción más fuerte; no se usa la propiedad del modo;
- si la regla está desactivada en el sistema, se registra el fallo y se ofrece abrir sus ajustes;
- el forzado significa «reactivar si lo desactivo»: solo se ofrece para un modo que restrinja el sonido y se ignora para el modo normal, conservando su valor;
- sin forzado, si el usuario apaga la regla se respeta hasta que cambie el modo deseado o se reinicie el dispositivo; entretanto, una reevaluación puede indicar que el modo no se ha aplicado;
- con forzado, la reactivación es inmediata si al apagarla queda activo el modo configurado del otro lado (por ejemplo, Normal); en otro caso, ocurre en la siguiente reevaluación;
- si la opción está activa, el volumen multimedia se restaura justo después de cambiar la regla.

## 6. Servicio de segundo plano

- Se inicia cuando «Detección activa» está encendida y el permiso de Bluetooth está concedido.
- Se detiene al desactivarla o al retirar el permiso de Bluetooth, aunque «Reactivar al cambiar de usuario» siga marcado.
- Muestra una notificación persistente mientras está en ejecución.
- La notificación indica si la automatización está todo el día, dentro del horario o fuera del horario.
- Detecta Bluetooth, cambios de filtro de interrupción, cambios de usuario y apagado del dispositivo.
- Si el servicio está en ejecución, evita que el receptor del manifiesto procese los mismos eventos.

## 7. Reactivación al cambiar de usuario

- Se configura en Automatización → Acciones y solo está disponible con la automatización activa y «Detección activa» encendida; no activa la detección por sí sola.
- Si se desactiva «Detección activa», la opción queda marcada pero deshabilitada, el servicio se detiene y la reactivación no se ejecuta hasta volver a activar la detección.
- Al volver el usuario, se intenta aplicar la automatización.
- Se intenta activar Bluetooth cuando el sistema lo permite.
- Si la activación silenciosa no es posible, se muestra una notificación con acción para activarlo.
- No requiere permiso de superposición.

## 8. Ajustes

- estado y control de Bluetooth (visible solo con el permiso concedido);
- permisos de Bluetooth, notificaciones, acceso a No molestar, alarmas exactas y optimización de batería;
- automatización y su configuración, deshabilitada si falta el permiso de Bluetooth o el acceso a No molestar;
- retención del historial de eventos (3 a 90 días);
- detección Bluetooth en segundo plano, deshabilitada si falta el permiso de Bluetooth;
- tema Sistema/Claro/Oscuro;
- color dinámico;
- en Acerca de: enlace al repositorio de GitHub y acceso a la información de la app;
- versión de la app.

## 9. Copia de seguridad y restauración

- El menú de tres puntos de Ajustes permite **Copiar datos** y **Restaurar datos** mediante el selector de documentos del sistema, sin permisos de almacenamiento.
- La copia se guarda en un JSON legible con versión de formato (3), versión de la app, fecha, eventos y ajustes. La restauración acepta las versiones 1, 2 y 3.
- No se copian los nombres de dispositivos (caché derivada), el estado interno de automatización ni los IDs de Room.
- Al restaurar se muestran dos opciones siempre disponibles: **Eventos** y **Ajustes**.
- Restaurar eventos borra la tabla actual y restaura los del archivo, aunque el archivo no contenga eventos.
- Restaurar ajustes sobrescribe todos los ajustes actuales.
- Después de restaurar se aplica la retención resultante y se eliminan los eventos que queden fuera del rango.
- Si se restauran ajustes, se sincroniza el servicio en segundo plano y se reevalúa la automatización.
- Después de restaurar ajustes se recrea la Activity y se reconstruye la UI de Compose para aplicar el tema y el resto de estado visual desde cero.

## 10. Selección de dispositivos

- lista dispositivos emparejados, conectados y con nombre guardado;
- los dispositivos seleccionados se muestran aunque ya no estén emparejados ni conectados, usando la dirección MAC como nombre de reserva;
- búsqueda por nombre o dirección;
- indicador de conectado, emparejado o no disponible, actualizado en vivo mediante los eventos ACL;
- selección múltiple;
- guardado automático para la automatización al salir con la flecha de la barra superior o con el gesto/botón atrás; si la selección aún no se ha cargado, se sale sin guardar, y refrescar la lista conserva los cambios pendientes.

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
