#!/bin/bash
#
# Post-image script for macOS jpackage builds.
# Patches the Info.plist to add UTImportedTypeDeclarations so that
# macOS displays custom document icons for .md and .textbundle files.
# Also copies the document icon .icns files into the app's Resources folder.
#
set -euo pipefail

APP_BUNDLE="PurplePlatypus.app"
INFO_PLIST="${APP_BUNDLE}/Contents/Info.plist"
RESOURCES_DIR="${APP_BUNDLE}/Contents/Resources"

if [ ! -f "$INFO_PLIST" ]; then
    echo "ERROR: Info.plist not found at $INFO_PLIST"
    exit 1
fi

echo "Patching Info.plist with UTImportedTypeDeclarations..."

# Copy document icon files into the app bundle Resources directory
cp src/main/resources/doc_icon.icns "${RESOURCES_DIR}/doc_icon.icns"
cp src/main/resources/textbundle_icon.icns "${RESOURCES_DIR}/textbundle_icon.icns"

# Add UTImportedTypeDeclarations to Info.plist using PlistBuddy
# This declares the UTIs for markdown and textbundle so macOS knows how to display their icons.

PLISTBUDDY="/usr/libexec/PlistBuddy"

# --- Add UTImportedTypeDeclarations array ---
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations array" "$INFO_PLIST"

# Entry 0: net.daringfireball.markdown (for .md and .markdown files)
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0 dict" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeIdentifier string net.daringfireball.markdown" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeDescription string Markdown Document" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeIconFile string doc_icon.icns" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeConformsTo array" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeConformsTo:0 string public.plain-text" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeTagSpecification dict" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeTagSpecification:public.filename-extension array" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeTagSpecification:public.filename-extension:0 string md" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeTagSpecification:public.filename-extension:1 string markdown" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeTagSpecification:public.mime-type array" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:0:UTTypeTagSpecification:public.mime-type:0 string text/markdown" "$INFO_PLIST"

# Entry 1: org.textbundle.package (for .textbundle directories)
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1 dict" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeIdentifier string org.textbundle.package" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeDescription string TextBundle Document" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeIconFile string textbundle_icon.icns" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeConformsTo array" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeConformsTo:0 string com.apple.package" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeTagSpecification dict" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeTagSpecification:public.filename-extension array" "$INFO_PLIST"
$PLISTBUDDY -c "Add :UTImportedTypeDeclarations:1:UTTypeTagSpecification:public.filename-extension:0 string textbundle" "$INFO_PLIST"

# --- Patch CFBundleDocumentTypes to add LSItemContentTypes and LSHandlerRank ---
# jpackage generates CFBundleDocumentTypes but doesn't include LSItemContentTypes,
# which is required for macOS to link the document type to the UTI declaration.

# Find which index corresponds to each extension and patch it
DOC_TYPES_COUNT=$($PLISTBUDDY -c "Print :CFBundleDocumentTypes" "$INFO_PLIST" | grep -c "Dict")

for ((i=0; i<DOC_TYPES_COUNT; i++)); do
    # Get the extensions for this document type
    EXTENSIONS=$($PLISTBUDDY -c "Print :CFBundleDocumentTypes:${i}:CFBundleTypeExtensions" "$INFO_PLIST" 2>/dev/null || echo "")

    # Both .md and .markdown use the same UTI: net.daringfireball.markdown
    if echo "$EXTENSIONS" | grep -q "md"; then
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:LSItemContentTypes array" "$INFO_PLIST" 2>/dev/null || true
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:LSItemContentTypes:0 string net.daringfireball.markdown" "$INFO_PLIST" 2>/dev/null || true
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:LSHandlerRank string Owner" "$INFO_PLIST" 2>/dev/null || true
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:CFBundleTypeRole string Editor" "$INFO_PLIST" 2>/dev/null || true
        echo "  Patched CFBundleDocumentTypes[$i] (markdown) with LSItemContentTypes"
    fi

    if echo "$EXTENSIONS" | grep -qw "textbundle"; then
        # TextBundle type - add LSItemContentTypes and LSTypeIsPackage
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:LSItemContentTypes array" "$INFO_PLIST" 2>/dev/null || true
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:LSItemContentTypes:0 string org.textbundle.package" "$INFO_PLIST" 2>/dev/null || true
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:LSTypeIsPackage bool true" "$INFO_PLIST" 2>/dev/null || true
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:LSHandlerRank string Owner" "$INFO_PLIST" 2>/dev/null || true
        $PLISTBUDDY -c "Add :CFBundleDocumentTypes:${i}:CFBundleTypeRole string Editor" "$INFO_PLIST" 2>/dev/null || true
        echo "  Patched CFBundleDocumentTypes[$i] (textbundle) with LSItemContentTypes + LSTypeIsPackage"
    fi
done

echo "Info.plist patching complete."
echo "Document icons copied to ${RESOURCES_DIR}:"
ls -la "${RESOURCES_DIR}/"*icon* 2>/dev/null || true
