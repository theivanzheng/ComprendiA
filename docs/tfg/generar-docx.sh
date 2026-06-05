#!/usr/bin/env bash
# Regenera el .docx de la memoria desde memoria.md.
# Si existe una plantilla oficial en .docx, se usa para heredar sus estilos.
cd "$(dirname "$0")"
REF=""
[ -f plantilla-upsa.docx ] && REF="--reference-doc=plantilla-upsa.docx"
pandoc memoria.md -o ComprendiA-TFG.docx --toc --toc-depth=3 $REF
echo "Generado: ComprendiA-TFG.docx"
