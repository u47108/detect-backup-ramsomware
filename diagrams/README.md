# Diagramas de Arquitectura

Este directorio contiene los diagramas de arquitectura y secuencia del sistema **Detect Backup Ransomware Service**.

## 📊 Diagramas Disponibles

### 1. Diagrama de Arquitectura (`architecture.mmd`)

El diagrama principal muestra la arquitectura completa del sistema:

- **Cloud SQL**: PostgreSQL 14 con Private Service Connect exportando backups
- **Cloud Storage**: Bucket privado donde se almacenan los backups
- **Cloud Run**: Servicio de detección de ransomware
- **Cloud DLP**: Inspección de datos sensibles encriptados
- **Cloud Monitoring**: Alertas y métricas
- **PostgreSQL Monitoring DB**: Base de datos para registro de backups
- **Pub/Sub**: Opcional, para mensajes de backup

### 2. Diagrama de Secuencia (`sequence.puml`)

El diagrama de secuencia muestra el flujo detallado de interacciones entre los componentes:

1. **Inicio del Proceso**: Usuario inicia backup → Cloud SQL exporta a Cloud Storage
2. **Verificación**: Servicio verifica backup disponible
3. **Detección**: Cloud DLP inspecciona datos sensibles
4. **Alertas**: Si detecta ransomware, genera alertas
5. **Verificación de DB**: Verifica cambios en la base de datos
6. **Restauración**: Si está comprometida, restaura backup anterior

## 🛠️ Generación de Diagramas

### Diagrama de Arquitectura (Mermaid)

#### Método 1: Script Automático (Recomendado)

```bash
cd diagrams
chmod +x generate-diagram.sh
./generate-diagram.sh
```

El script detectará automáticamente si tienes Docker o Mermaid CLI instalado y usará el método disponible.

#### Método 2: Docker (Manual)

```bash
cd diagrams
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -v "$(pwd):/data" \
  minlag/mermaid-cli \
  -i /data/architecture.mmd \
  -o /data/architecture.png \
  -b transparent \
  -w 2800 \
  -H 2000
```

Si necesitas usar `sudo`:

```bash
cd diagrams
sudo docker run --rm \
  -u "$(id -u):$(id -g)" \
  -v "$(pwd):/data" \
  minlag/mermaid-cli \
  -i /data/architecture.mmd \
  -o /data/architecture.png \
  -b transparent \
  -w 2800 \
  -H 2000
sudo chown "$(id -u):$(id -g)" architecture.png
```

#### Método 3: Mermaid CLI (si está instalado)

```bash
npm install -g @mermaid-js/mermaid-cli
cd diagrams
mmdc -i architecture.mmd -o architecture.png -b transparent -w 2800 -H 2000
mmdc -i architecture.mmd -o architecture.svg -b transparent
```

#### Método 4: Herramienta Online

1. Abre https://mermaid.live/
2. Copia el contenido de `architecture.mmd`
3. Pega en el editor
4. Descarga como PNG o SVG

### Diagrama de Secuencia (PlantUML)

#### Método 1: Script Automático (Recomendado)

```bash
cd diagrams
chmod +x generate-sequence.sh
./generate-sequence.sh
```

El script detectará automáticamente si tienes Docker, PlantUML CLI o Java instalado y usará el método disponible.

#### Método 2: Herramienta Online (Más fácil)

1. Abre http://www.plantuml.com/plantuml/uml/
2. Copia el contenido de `sequence.puml`
3. Pega en el editor
4. Descarga como PNG o SVG

#### Método 3: PlantUML CLI (si está instalado)

```bash
# Instalar PlantUML (requiere Java)
# En Ubuntu/Debian:
sudo apt-get install plantuml

# Generar imagen
cd diagrams
plantuml sequence.puml

# Generar con formato específico
plantuml -tpng sequence.puml
plantuml -tsvg sequence.puml
```

#### Método 4: Docker

```bash
cd diagrams
docker run --rm \
  -v "$(pwd):/data" \
  plantuml/plantuml:latest \
  -tpng /data/sequence.puml -o /data

# O generar SVG
docker run --rm \
  -v "$(pwd):/data" \
  plantuml/plantuml:latest \
  -tsvg /data/sequence.puml -o /data
```

#### Método 5: PlantUML JAR

```bash
# Descargar PlantUML JAR
cd diagrams
wget https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar

# Generar imágenes (requiere Java)
java -jar plantuml.jar -tpng sequence.puml
java -jar plantuml.jar -tsvg sequence.puml
```

## 📁 Archivos Disponibles

### Diagrama de Arquitectura (Mermaid)
- `architecture.mmd`: Definición del diagrama en formato Mermaid
- `architecture.png`: Imagen PNG del diagrama (generado)
- `architecture.svg`: Imagen SVG del diagrama (generado)
- `generate-diagram.sh`: Script de generación automática

### Diagrama de Secuencia (PlantUML)
- `sequence.puml`: Definición del diagrama en formato PlantUML

## 🔄 Actualizar los Diagramas

### Arquitectura (Mermaid)

Si modificas `architecture.mmd`, ejecuta nuevamente el script de generación:

```bash
./generate-diagram.sh
```

Los archivos PNG y SVG se regenerarán automáticamente.

### Secuencia (PlantUML)

Si modificas `sequence.puml`, usa uno de los métodos de generación mencionados arriba para crear la imagen.

## 📝 Notas

- Los diagramas muestran el flujo completo de detección de ransomware
- Los colores ayudan a identificar los diferentes componentes del sistema
- Los diagramas están optimizados para visualización en README.md
- El diagrama de secuencia sigue el flujo exacto del sistema UML original

## 🔗 Enlaces Útiles

- **Mermaid**: https://mermaid.js.org/
- **Mermaid Live Editor**: https://mermaid.live/
- **PlantUML**: https://plantuml.com/
- **PlantUML Online**: http://www.plantuml.com/plantuml/uml/
