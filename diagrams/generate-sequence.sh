#!/bin/bash

# Script para generar diagrama de secuencia PlantUML
# Genera PNG y SVG del diagrama de secuencia

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PUML_FILE="${SCRIPT_DIR}/sequence.puml"
PNG_FILE="${SCRIPT_DIR}/sequence.png"
SVG_FILE="${SCRIPT_DIR}/sequence.svg"

echo "🔧 Generando diagrama de secuencia PlantUML..."

# Verificar si el archivo PlantUML existe
if [ ! -f "$PUML_FILE" ]; then
    echo "❌ Error: No se encuentra el archivo $PUML_FILE"
    exit 1
fi

# Función para verificar si Docker está disponible
check_docker() {
    if command -v docker &> /dev/null; then
        if docker ps &> /dev/null 2>&1 || sudo docker ps &> /dev/null 2>&1; then
            return 0
        fi
    fi
    return 1
}

# Función para verificar si PlantUML CLI está disponible
check_plantuml_cli() {
    if command -v plantuml &> /dev/null; then
        return 0
    fi
    return 1
}

# Función para verificar si Java está disponible
check_java() {
    if command -v java &> /dev/null; then
        return 0
    fi
    return 1
}

# Función para generar con Docker
generate_with_docker() {
    echo "📦 Usando Docker para generar diagrama..."
    
    USER_ID=$(id -u)
    GROUP_ID=$(id -g)
    
    # Intentar sin sudo primero
    if docker run --rm \
        -v "${SCRIPT_DIR}:/data" \
        plantuml/plantuml:latest \
        -tpng /data/sequence.puml -o /data 2>/dev/null; then
        echo "✅ PNG generado exitosamente con Docker"
        docker run --rm \
            -v "${SCRIPT_DIR}:/data" \
            plantuml/plantuml:latest \
            -tsvg /data/sequence.puml -o /data 2>/dev/null && echo "✅ SVG generado exitosamente con Docker"
        return 0
    fi
    
    # Intentar con sudo
    if sudo docker run --rm \
        -v "${SCRIPT_DIR}:/data" \
        plantuml/plantuml:latest \
        -tpng /data/sequence.puml -o /data 2>/dev/null; then
        echo "✅ PNG generado exitosamente con Docker (sudo)"
        sudo docker run --rm \
            -v "${SCRIPT_DIR}:/data" \
            plantuml/plantuml:latest \
            -tsvg /data/sequence.puml -o /data 2>/dev/null && echo "✅ SVG generado exitosamente con Docker (sudo)"
        sudo chown "${USER_ID}:${GROUP_ID}" "$PNG_FILE" "$SVG_FILE" 2>/dev/null
        return 0
    fi
    
    return 1
}

# Función para generar con PlantUML CLI
generate_with_cli() {
    echo "📦 Usando PlantUML CLI local para generar diagrama..."
    
    if plantuml -tpng "$PUML_FILE" -o "$SCRIPT_DIR" 2>/dev/null; then
        echo "✅ PNG generado exitosamente con PlantUML CLI"
    else
        echo "❌ Error al generar PNG con PlantUML CLI"
        return 1
    fi
    
    if plantuml -tsvg "$PUML_FILE" -o "$SCRIPT_DIR" 2>/dev/null; then
        echo "✅ SVG generado exitosamente con PlantUML CLI"
    else
        echo "❌ Error al generar SVG con PlantUML CLI"
        return 1
    fi
    
    return 0
}

# Función para generar con PlantUML JAR
generate_with_jar() {
    echo "📦 Intentando usar PlantUML JAR..."
    
    PLANTUML_JAR="${SCRIPT_DIR}/plantuml.jar"
    
    # Intentar descargar PlantUML JAR si no existe
    if [ ! -f "$PLANTUML_JAR" ]; then
        echo "📥 Descargando PlantUML JAR..."
        if command -v wget &> /dev/null; then
            wget -q -O "$PLANTUML_JAR" https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar
        elif command -v curl &> /dev/null; then
            curl -L -o "$PLANTUML_JAR" https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar
        else
            echo "❌ Error: wget o curl no están disponibles para descargar PlantUML JAR"
            return 1
        fi
    fi
    
    if java -jar "$PLANTUML_JAR" -tpng "$PUML_FILE" -o "$SCRIPT_DIR" 2>/dev/null; then
        echo "✅ PNG generado exitosamente con PlantUML JAR"
        java -jar "$PLANTUML_JAR" -tsvg "$PUML_FILE" -o "$SCRIPT_DIR" 2>/dev/null && echo "✅ SVG generado exitosamente con PlantUML JAR"
        return 0
    fi
    
    return 1
}

# Intentar generar con diferentes métodos
SUCCESS=false

if check_docker; then
    if generate_with_docker; then
        SUCCESS=true
    fi
fi

if [ "$SUCCESS" = false ] && check_plantuml_cli; then
    if generate_with_cli; then
        SUCCESS=true
    fi
fi

if [ "$SUCCESS" = false ] && check_java; then
    if generate_with_jar; then
        SUCCESS=true
    fi
fi

if [ "$SUCCESS" = true ]; then
    echo ""
    echo "✅ Diagrama generado exitosamente:"
    echo "   📄 PNG: $PNG_FILE"
    echo "   📄 SVG: $SVG_FILE"
    exit 0
fi

echo ""
echo "❌ Error: No se pudo generar el diagrama automáticamente."
echo ""
echo "💡 Opciones disponibles:"
echo ""
echo "1. **Herramienta Online (Más fácil)**:"
echo "   - Abre http://www.plantuml.com/plantuml/uml/"
echo "   - Copia el contenido de $PUML_FILE"
echo "   - Pega en el editor y descarga como PNG/SVG"
echo ""
echo "2. **Docker**:"
echo "   docker run --rm -v \"\$(pwd):/data\" plantuml/plantuml:latest \\"
echo "     -tpng /data/sequence.puml -o /data"
echo ""
echo "3. **Instalar PlantUML CLI**:"
echo "   sudo apt-get install plantuml  # Ubuntu/Debian"
echo "   brew install plantuml          # macOS"
echo ""
echo "4. **PlantUML JAR**:"
echo "   - Descargar: https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar"
echo "   - Ejecutar: java -jar plantuml.jar -tpng sequence.puml"
exit 1

