# Diagramas de Arquitectura

Este directorio contiene los diagramas de arquitectura del sistema **Detect Backup Ransomware Service**.

## 📊 Diagrama Principal

El diagrama principal (`architecture.mmd`) muestra la arquitectura completa del sistema:

- **Cloud SQL**: PostgreSQL 14 con Private Service Connect exportando backups
- **Cloud Storage**: Bucket privado donde se almacenan los backups
- **Cloud Run**: Servicio de detección de ransomware
- **Cloud DLP**: Inspección de datos sensibles encriptados
- **Cloud Monitoring**: Alertas y métricas
- **PostgreSQL Monitoring DB**: Base de datos para registro de backups
- **Pub/Sub**: Opcional, para mensajes de backup

## 🛠️ Generación de Diagramas

### Método 1: Script Automático (Recomendado)

```bash
cd diagrams
chmod +x generate-diagram.sh
./generate-diagram.sh
```

El script detectará automáticamente si tienes Docker o Mermaid CLI instalado y usará el método disponible.

### Método 2: Docker (Manual)

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

### Método 3: Mermaid CLI (si está instalado)

```bash
npm install -g @mermaid-js/mermaid-cli
cd diagrams
mmdc -i architecture.mmd -o architecture.png -b transparent -w 2800 -H 2000
mmdc -i architecture.mmd -o architecture.svg -b transparent
```

### Método 4: Herramienta Online

1. Abre https://mermaid.live/
2. Copia el contenido de `architecture.mmd`
3. Pega en el editor
4. Descarga como PNG o SVG

## 📁 Archivos Generados

- `architecture.mmd`: Definición del diagrama en formato Mermaid
- `architecture.png`: Imagen PNG del diagrama (generado)
- `architecture.svg`: Imagen SVG del diagrama (generado)

## 🔄 Actualizar el Diagrama

Si modificas `architecture.mmd`, ejecuta nuevamente el script de generación:

```bash
./generate-diagram.sh
```

Los archivos PNG y SVG se regenerarán automáticamente.

## 📝 Notas

- El diagrama muestra el flujo completo de detección de ransomware
- Los colores ayudan a identificar los diferentes componentes del sistema
- El diagrama está optimizado para visualización en README.md

