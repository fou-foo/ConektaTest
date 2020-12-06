# Cómo leer este documento.

En este `README.md` se documenta la instalación y setup de las herramientas que se utilizan para el task 1, de igual manera en este archivo incluyo mis comentarios sobre elección de herramientas, buenas prácticas y comentarios adicionales los cuales aunque se relacionan con el código y pipeline que no se reflejan directamente en el código, es decir son comentarios que se darían entre personas o bien que se reflejarán en un Backlog o en un code review. 

Mis comentarios se presentan en *itálicas* mientras que la documentación en texto plano. En ambos casos utilizo __negritas__ para resaltar puntos que considero importantes.

Por otro lado el  notebook, `DataPipeline.ipynb`, contiene el código que realiza el task requerido en la prueba, decidí utilizar un notebook para incluir la documentación del código por practicidad.    

Disclaimer: Las instrucciones de instalación son documentadas para un OS Ubuntu 20 en una arquitectura de 64 bits, cuando algún paso es dependiente del OS se proporcionan fuentes donde consultar la instalación para otros sistemas operativos.   

## 1.1 Carga de Información 

*Elegí trabajar con el RDBMS PostgreSQL por las siguientes razones:*

1. *Es Open Source y la comunidad alrededor de ella es muy activa por lo que su documentación es extensa y nice (bien presentada). Además dentro de los RDBMS libres ya es un estándar en la industria, los principales cloud providers ya lo adoptaron y lo ofrecen como PAAS.* 

2. *El dataset de entrada es muy pequeño, además las operaciones que alteran el estado de la DB serán pocas (DDLs, DMLs y sobre todo DCLs) y la estructura de las entidades parece fijo o bien que no cambiará continuamente. Por lo que una DB NoSQL (que acepte operaciones ACID) aunque satisface las necesidades lo considero una solución over engineered para este caso de uso.* 

3. *Por sus features. Contra otros RDBMS Open Source como MariaDB, PostgreSQL tiene mayor número de tipos de datos, incluyendo geoespaciales y custom (definidos por nosotros mismos :D).  En general tiene más features como de seguridad, indexado y otros.*


*Nota: En caso de trabajar con alta concurrencia ACID y si el modelo de datos no tiene cambios replicaría la DB y distribuiría la carga de peticiones con un balanceador (y claro sincronizar las réplicas). En caso de alta concurrencia de peticiones de lectura evaluaría una herramienta que guarde en cache las solicitudes efectuadas.* 

### Setup de la DB

*Use el servicio Cloud SQL for PostgreSQL considerando un escenario de la vida real y me restan unos créditos gratis en GCP (no así en AWS).
Además los datos de identificación están enmascarados y no hay regulaciones legales que me impidan alojar los datos fuera del territorio nacional.*

Para crear cualquier tipo de recurso en GCP primero se debe crear un proyecto, lo cual tiene una ventaja, cada proyecto es una VPC por lo que la comunicación y transferencia de datos entre ellos es rápida. 

1. Crear un proyecto en GCP, *como es un paso sencillo refiero a la documentación directa del [provider](https://cloud.google.com/appengine/docs/standard/nodejs/building-app/creating-project)*, y lo llame `sandbox`. 

2. Acceder a la consola de [GCP](https://console.cloud.google.com/) y desde el Shell (para no instalar la SDK ya que solo utilizare 2 comandos en ella) levanto el RDBMS PostgreSQL con la siguiente configuración: 

```bash 
gcloud sql instances create conekta \
--no-backup --cpu=1 --memory=4 \
--database-version=POSTGRES_12 \
--region=us-central1  --storage-type=HDD --storage-size=10
```
*Las especificaciones anteriores son mínimas dado el volumen de datos con el que trabajaremos:*

+ *1 solo core, 4 GB de memoria y 10GB de HDD (constraints minimos en GCP).*
+ *Optamos por HDD frente al SSD por economía.* 
+ *La versión 12 de PostgreSQL debido a que es una versión madura en comparación a la 13.*

+ *Y la región de Iowa pues es la región de GCP que tiene menor latencia hacia la CDMX, como se puede consultar [aquí](https://gcping.com/).*  


*Como __buena práctica__ cambio la contraseña default del user* `postgres`.

```bash 
gcloud sql users set-password postgres --instance=conekta --password=conektafou
```

3. Habilitar la conexión hacia la base de datos: 

    3.1 Habilitar la Cloud SQL Admin API. Esto se hace con la consola web de GCP con un [click](https://console.cloud.google.com/flows/enableapi?apiid=sqladmin&redirect=https://console.cloud.google.com&_ga=2.56133611.1105447096.1607143925-1191023216.1607143925).
    *En mi caso hice la conexión desde mi local a la instancia con el RDBMS pero el procedimiento es análogo a una conexión server-server, solo cambia la ubicación del .json (credenciales) y la ubicación del proxy.*


    3.2 Instalar en la máquina cliente el Cloud SQL Proxy. Suponiendo que nos encontramos en el path `~Keys$` de nuestro local descargamos y hacemos ejecutable el proxy con las siguientes líneas:

    ```bash 
    wget https://dl.google.com/cloudsql/cloud_sql_proxy.linux.amd64 -O cloud_sql_proxy
    chmod +x cloud_sql_proxy
    ```

    Para otros OS diferentes a Ubuntu puede consultar la doc. del [provider](https://cloud.google.com/sql/docs/postgres/sql-proxy#authentication-options).

    
    3.3 Generar claves de autentificación server-server. 

    En GCP, se requiere crear una entidad (puede ser un usuario o grupo de usuarios o bien como en nuestro caso un solo servicio) a la que se le pueden asociar permisos y roles para el uso de los servicios cloud. Se llama `service account`, si bien se puede generar por medio de la SDK o el Shell, la manera más sencilla es vía la consola web, como se documenta [aqui](https://cloud.google.com/sql/docs/postgres/sql-proxy#create-service-account), es importante que en la creación del service account `conektaclient` se asignen solo los permisos mencionados en el enlace anterior (*para mantener una política de permisos mínimos y suficientes*) y la obtención del archivo `sandbox-289720-ebae2a778afb.json` con las claves de autentificación del service account y guardamos el archivo el archivo en la carpeta `Keys` (que se encuentra oculto en el repositorio en GitHub).

*Nota: Como el dataset es pequeño lo trabajo en mi local, si estuviera trabajando con archivos de GB o más, subiría los archivos en paralelo a la nube y todo el código que Python de las siguientes secciones lo trabajaría en una instancia en la misma región geográfica en donde están los datos para reducir los tiempos de traslado de la data, que suelen ser los cuellos de botella.*

# 1.2 Extracción 

*Decidí utilizar el lenguaje Python para este task, por las siguientes razones:*
1. *Es Open Source y tiene una amplia comunidad por lo que es muy conocido y es fácil compartir código desarrollado en él.*   
2. *Es un estándar en el área de DS y DE que se integra con muchas otras herramientas como AirFlow, Spark, etc.*
3. *El lenguaje está diseñado para ser fácil de entender y su uso se ha extendido a diversas áreas como front-end y back-end lo que facilita seguir mejores prácticas en el desarrollo de software y por ende productos de datos.* 
4. *Aunque no es eficiente utilizando la RAM no tiene tanta área de oportunidad como R u otros.*  
5. *El dataset es pequeño (de nuevo).*
6. *Tiene un package especializado para interactuar con PostgreSQL.* 

*Elegí extraer la información directamente del archivo csv, pues es un archivo pequeño.* 

### Setup del entorno para programación 

Opte por utilizar los Jupyter notebooks aprovechando que Ubuntu 20 incorpora Python 3, sin embargo requerí instalar algunas herramientas, packages y crear un environment virtual como buena práctica para que las instalaciones (y sus dependencias)  de este task no entren en conflicto con las de otros proyectos en la misma máquina. 
 
```bash 
sudo apt-get install python3-venv 
python3 -m venv conekta_test # creacion del ambiente virtual
source conekta_test/bin/activate #activacion del ambiente
sudo apt install python3-pip 
pip3 install psycopg2-binary
pip3 install numpy # solo por completes ya que es una dependencia de pandas
pip3 install pandas
pip3 install notebook
Keys/./cloud_sql_proxy -instances=sandbox-289720:us-central1:conekta=tcp:5432 \
-credential_file=/home/foo/Desktop/GitHub/ConektaTest/DataPipeline/Keys/sandbox-289720-ebae2a778afb.json & # activacion del proxy  
jupyter notebook  #actuivacion del notebook 
```
