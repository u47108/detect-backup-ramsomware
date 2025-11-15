#!/bin/bash

# Script para generar diagrama de arquitectura desde Mermaid
# Genera PNG y SVG del diagrama de arquitectura

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MMD_FILE="${SCRIPT_DIR}/architecture.mmd"
PNG_FILE="${SCRIPT_DIR}/architecture.png"
SVG_FILE="${SCRIPT_DIR}/architecture.svg"

echo "🔧 Generando diagrama de arquitectura..."

# Verificar si el archivo Mermaid existe
if [ ! -f "$MMD_FILE" ]; then
    echo "❌ Error: No se encuentra el archivo $MMD_FILE"
    exit 1
fi

# Función para verificar si Docker está disponible
check_docker() {
    if command -v docker &> /dev/null; then
        if docker ps &> /dev/null; then
            return 0
        fi
    fi
    return 1
}

# Función para verificar si Mermaid CLI está disponible
check_mermaid_cli() {
    if command -v mmdc &> /dev/null; then
        return 0
    fi
    return 1
}

# Función para generar con Docker (preferido)
generate_with_docker() {
    echo "📦 Usando Docker para generar diagrama..."
    
    # Determinar el usuario actual
    USER_ID=$(id -u)
    GROUP_ID=$(id -g)
    
    # Intentar ejecutar con permisos del usuario actual
    if docker run --rm \
        -u "${USER_ID}:${GROUP_ID}" \
        -v "${SCRIPT_DIR}:/data" \
        minlag/mermaid-cli \
        -i /data/architecture.mmd \
        -o /data/architecture.png \
        -b transparent \
        -w 2800 \
        -H 2000 2>/dev/null; then
        echo "✅ PNG generado exitosamente con Docker"
    elif sudo docker run --rm \
        -u "${USER_ID}:${GROUP_ID}" \
        -v "${SCRIPT_DIR}:/data" \
        minlag/mermaid-cli \
        -i /data/architecture.mmd \
        -o /data/architecture.png \
        -b transparent \
        -w 2800 \
        -H 2000 2>/dev/null; then
        echo "✅ PNG generado exitosamente con Docker (sudo)"
        # Asegurar que el archivo tenga los permisos correctos
        sudo chown "${USER_ID}:${GROUP_ID}" "$PNG_FILE"
    else
        echo "⚠️  Error al generar PNG con Docker"
        return 1
    fi
    
    # Generar SVG
    if docker run --rm \
        -u "${USER_ID}:${GROUP_ID}" \
        -v "${SCRIPT_DIR}:/data" \
        minlag/mermaid-cli \
        -i /data/architecture.mmd \
        -o /data/architecture.svg \
        -b transparent 2>/dev/null; then
        echo "✅ SVG generado exitosamente con Docker"
    elif sudo docker run --rm \
        -u "${USER_ID}:${GROUP_ID}" \
        -v "${SCRIPT_DIR}:/data" \
        minlag/mermaid-cli \
        -i /data/architecture.mmd \
        -o /data/architecture.svg \
        -b transparent 2>/dev/null; then
        echo "✅ SVG generado exitosamente con Docker (sudo)"
        sudo chown "${USER_ID}:${GROUP_ID}" "$SVG_FILE"
    else
        echo "⚠️  Error al generar SVG con Docker"
        return 1
    fi
}

# Función para generar con Mermaid CLI local
generate_with_cli() {
    echo "📦 Usando Mermaid CLI local para generar diagrama..."
    
    if mmdc -i "$MMD_FILE" -o "$PNG_FILE" -b transparent -w 2800 -H 2000; then
        echo "✅ PNG generado exitosamente con Mermaid CLI"
    else
        echo "❌ Error al generar PNG con Mermaid CLI"
        return 1
    fi
    
    if mmdc -i "$MMD_FILE" -o "$SVG_FILE" -b transparent; then
        echo "✅ SVG generado exitosamente con Mermaid CLI"
    else
        echo "❌ Error al generar SVG con Mermaid CLI"
        return 1
    fi
}

# Intentar generar con diferentes métodos
if check_docker; then
    if generate_with_docker; then
        echo ""
        echo "✅ Diagrama generado exitosamente:"
        echo "   📄 PNG: $PNG_FILE"
        echo "   📄 SVG: $SVG_FILE"
        exit 0
    fi
fi

if check_mermaid_cli; then
    if generate_with_cli; then
        echo ""
        echo "✅ Diagrama generado exitosamente:"
        echo "   📄 PNG: $PNG_FILE"
        echo "   📄 SVG: $SVG_FILE"
        exit 0
    fi
fi

echo ""
echo "❌ Error: No se pudo generar el diagrama."
echo ""
echo "💡 Opciones disponibles:"
echo "   1. Instalar Docker: https://docs.docker.com/get-docker/"
echo "   2. Instalar Mermaid CLI: npm install -g @mermaid-js/mermaid-cli"
echo "   3. Usar herramienta online: https://mermaid.live/"
echo ""
echo "   O ejecutar manualmente con Docker:"
echo "   docker run --rm -v \"$(pwd):/data\" minlag/mermaid-cli \\"
echo "     -i /data/diagrams/architecture.mmd \\"
echo "     -o /data/diagrams/architecture.png \\"
echo "     -b transparent -w 2800 -H 2000"
exit 1

