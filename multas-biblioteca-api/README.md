# Post-contenido — Unidad 7: Patrones Arquitectónicos I

## Descripción
Repositorio del post-contenido de la Unidad 7 de Patrones de Diseño
de Software. Un unico proyecto Spring Boot (multas-biblioteca-api)
para la gestion de multas de biblioteca, con dos partes: una API REST
en capas (Model, Repository, Service, Controller) sobre H2, y pago en
linea de multas con dos pasarelas intercambiables.

## Parte 1 — Arquitectura en Capas
MultaRepository extiende JpaRepository y agrega una consulta
agregada (countByEstudianteIdAndEstado). MultaService concentra las
reglas de negocio (limite de multas pendientes, generacion); el
calculo del monto vive en la propia entidad Multa
(Multa.calcularMonto). MultaController expone /api/multas. Ver
paquetes model/, repository/, service/ y controller/.

## Parte 2 — Pago en Linea con Dos Pasarelas
[Documentar aqui la opcion elegida entre A (rama condicional), B
(Strategy en service/) o C (puerto de dominio con adaptadores) y por
que. Si se eligio C: domain/port/PasarelaPagoPort y domain/ResultadoPago
son Java puro; infrastructure/pago/PagosUdesAdapter e
infrastructure/pago/WompiAdapter traducen cada formato HTTP externo
al mismo ResultadoPago; se seleccionan por app.pagos.proveedor sin
tocar MultaController ni el resto de MultaService.]

## Cómo ejecutar
```
$ cd multas-biblioteca-api && mvn spring-boot:run
```

## Herramientas utilizadas
- Java 17, Spring Boot 3.x, Spring Data JPA, H2, RestTemplate
- Apache Maven, Postman/curl, Git, GitHub


## Decisiones de diseño

### Punto de decisión 1: Ubicación del cálculo del monto de la multa

Se decidió implementar `Multa.calcularMonto` como un método estático de la entidad `Multa`, en lugar de ubicar el cálculo como un método privado de `MultaService`. Esta decisión se tomó porque calcular el monto de una multa, a partir de los días de atraso, el valor diario y el tope establecido, es una regla propia del dominio que no necesita consultar repositorios ni utilizar otros servicios. La alternativa de dejar el cálculo dentro de `MultaService` también funcionaría, pero convertiría a `Multa` en un modelo anémico, limitado a almacenar datos y sin comportamiento propio. Además, cualquier otro componente que necesitara realizar ese cálculo tendría que duplicar la fórmula o depender innecesariamente del servicio. Por esta razón, se consideró más adecuado mantener la regla junto al concepto de dominio al que pertenece.

### Punto de decisión 2: Consulta del número de multas pendientes

Se decidió utilizar `MultaRepository.countByEstudianteIdAndEstado` para obtener directamente la cantidad de multas pendientes de un estudiante, en lugar de recuperar todas sus multas mediante `findByEstudianteId` y filtrarlas posteriormente con streams de Java. Esta decisión separa las responsabilidades: el repositorio obtiene el dato mediante una consulta `COUNT` ejecutada en la base de datos, mientras que `MultaService` utiliza ese resultado para decidir si el estudiante puede generar una nueva multa. La alternativa de realizar el filtrado en memoria implicaría recuperar registros que no son necesarios para tomar la decisión, aumentando el consumo de memoria y el procesamiento a medida que crezca el historial de multas. Por ejemplo, con miles de registros por estudiante, esta estrategia supondría transferir y recorrer una colección completa cuando únicamente se necesita conocer una cantidad. Por ello, se eligió delegar la operación de conteo al motor de base de datos y mantener la decisión de negocio en el servicio.



### Punto de decisión 3: Selección del adaptador mediante configuración o resolución dinámica

Se decidió utilizar `@ConditionalOnProperty` para seleccionar el adaptador de pago durante el arranque de la aplicación, de acuerdo con la propiedad `app.pagos.proveedor`. De esta manera, Spring registra únicamente el bean correspondiente a la pasarela configurada, ya sea `UfpsAdapter` o `WompiAdapter`. `MultaService` recibe mediante inyección por constructor una única dependencia de tipo `PasarelaPagoPort`, sin necesitar `@Qualifier`, condiciones `if/switch` ni conocer las implementaciones concretas.

Se descartó la alternativa de inyectar un `Map<String, PasarelaPagoPort>` y seleccionar la implementación en tiempo de ejecución porque, aunque ofrece mayor flexibilidad para cambiar de proveedor sin reiniciar la aplicación, introduce una responsabilidad adicional en `MultaService`: conocer las claves de identificación de cada adaptador y decidir cuál utilizar. Para el escenario planteado, en el que la pasarela permanece fija por sede durante el piloto y los cambios se realizan mediante configuración, esa flexibilidad adicional no resulta necesaria. Por tanto, se priorizó mantener el servicio desacoplado de los proveedores y resolver su selección durante el arranque.

Esta decisión supone que cada sede dispone de su propia configuración de aplicación o contexto de ejecución. Si ambas sedes utilizaran una única instancia de la aplicación y necesitaran seleccionar simultáneamente pasarelas diferentes, `@ConditionalOnProperty` por sí solo no sería suficiente, porque selecciona los beans para todo el contexto de Spring y no individualmente por campus. En ese escenario sería necesario incorporar una resolución de adaptadores basada en el campus.

### Punto de decisión 4: Resultado común independiente de las pasarelas

Se decidió que ambos adaptadores, `UfpsAdapter` y `WompiAdapter`, traduzcan sus respectivas respuestas al mismo objeto de dominio `ResultadoPago` antes de devolver el resultado mediante `PasarelaPagoPort`. Aunque UFPS utiliza los campos `idTransaccion` y `estadoTransaccion`, mientras que Wompi trabaja con `reference` y `status`, estas diferencias quedan encapsuladas dentro de cada adaptador. Así, `MultaService` recibe una estructura uniforme y no necesita conocer las particularidades de los contratos HTTP externos.

La alternativa de devolver directamente los DTO específicos de cada proveedor o definir métodos diferentes dentro del puerto fue descartada porque obligaría a `MultaService` a distinguir entre las estructuras de UFPS y Wompi. Esto aumentaría el acoplamiento y provocaría que la incorporación de una tercera pasarela requiriera modificar el servicio, además de implementar su adaptador correspondiente.

También se decidió utilizar un atributo neutral como `referenciaExterna` dentro de `ResultadoPago`, en lugar de `idTransaccion`. Este último nombre representa un concepto específico del contrato de UFPS y no describe adecuadamente la referencia devuelta por Wompi. Si se utilizara `idTransaccion` como campo común, `WompiAdapter` tendría que introducir un nombre que no corresponde al dato original o realizar una conversión artificial. Por ello, `referenciaExterna` permite representar la información de ambos proveedores sin favorecer a una implementación concreta y mantiene el modelo de dominio independiente de las tecnologías externas.

### Trade-off considerado — Parte 2
Decisión elegida: Opción C — Puerto de dominio con dos adaptadores.

Se decidió implementar una interfaz PasarelaPagoPort dentro del paquete domain/, sin dependencias de Spring, junto con dos adaptadores en infrastructure/: uno para PagosUDES y otro para Wompi. Esta decisión se fundamenta en que ambas pasarelas ofrecen contratos HTTP diferentes: PagosUDES utiliza los campos idTransaccion y estadoTransaccion, mientras que Wompi trabaja con valores en centavos y devuelve reference y status. Por tanto, resulta necesario aislar estas diferencias para evitar que los detalles de los proveedores externos lleguen a MultaService.

El puerto establece un contrato común mediante un tipo propio del dominio, ResultadoPago, mientras que cada adaptador se encarga de traducir las solicitudes y respuestas de su respectiva pasarela. De esta manera, MultaService depende de una abstracción y no necesita conocer los formatos HTTP, las estructuras de respuesta ni las particularidades de cada proveedor. La selección de la pasarela correspondiente se resuelve mediante la configuración de cada campus y un mecanismo de selección en la infraestructura.

Se descartó la opción A porque incorporar condiciones if/switch dentro de MultaService obligaría a este servicio a conocer las particularidades de cada pasarela. Si posteriormente se agrega un tercer proveedor, sería necesario modificar nuevamente su código, aumentando el acoplamiento y poniendo en riesgo el comportamiento de una clase que ya fue probada en la Parte 1. También se descartó la opción B porque, aunque una interfaz Strategy en service/ permitiría intercambiar implementaciones, el contrato seguiría ubicado en la capa de servicio y no se establecería una separación tan clara entre el dominio y los sistemas externos.

La opción C implica crear interfaces, adaptadores y una estructura adicional de configuración, por lo que requiere más trabajo inicial que las otras alternativas. Sin embargo, ese costo se justifica por la posibilidad de agregar, sustituir o retirar pasarelas mediante nuevos adaptadores y ajustes de configuración, sin modificar el Controller ni la lógica de MultaService cada vez que cambie el proveedor. Además, esta decisión no implica migrar todo el proyecto a arquitectura hexagonal: únicamente se aplica a la integración de los pagos externos, evitando una transformación arquitectónica innecesaria.

## Conclusiones

El aprendizaje principal de ambas partes fue comprender que las decisiones de diseño deben basarse en las responsabilidades de cada componente y en las necesidades reales del sistema, evitando agregar complejidad innecesaria. En la primera parte, se diferenció entre las reglas propias del dominio, que pueden permanecer en la entidad `Multa`, y las operaciones que necesitan consultar información persistida, que deben delegarse al repositorio mientras el servicio conserva las decisiones de negocio. En la segunda parte, se aplicó arquitectura hexagonal de manera localizada para integrar las pasarelas de pago mediante un puerto y dos adaptadores, manteniendo `MultaService` independiente de los contratos externos. Lo más difícil fue decidir entre extender la arquitectura en capas mediante una interfaz Strategy o introducir un puerto de dominio, porque ambas alternativas permitían intercambiar las pasarelas, pero había que evaluar el costo de una mayor separación frente a la necesidad de aislar contratos HTTP diferentes y facilitar futuros cambios de proveedores. Finalmente, se comprendió que una buena solución no consiste en utilizar siempre la arquitectura más compleja, sino en elegir la estructura que resuelva el problema actual sin comprometer la evolución del sistema.