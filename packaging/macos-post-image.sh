#!/bin/bash
#
# Post-image script for macOS jpackage builds.
# Patches the Info.plist to add UTImportedTypeDeclarations so that
# macOS displays custom document icons for .md and .textbundle files.
# Also copies the document icon .icns files into the app's Resources folder.
#
# jpackage generates CFBundleDocumentTypes with CFBundleTypeExtensions, but
# modern macOS requires LSItemContentTypes referencing proper UTIs for document
# icons to appear. This script replaces the jpackage-generated document types
# with correct entries using net.daringfireball.markdown and org.textbundle.package.
#
set -euo pipefail

APP_BUNDLE="PurplePlatypus.app"
INFO_PLIST="${APP_BUNDLE}/Contents/Info.plist"
RESOURCES_DIR="${APP_BUNDLE}/Contents/Resources"

if [ ! -f "$INFO_PLIST" ]; then
    echo "ERROR: Info.plist not found at $INFO_PLIST"
    exit 1
fi

echo "Patching Info.plist with UTI declarations..."

# Copy document icon files into the app bundle Resources directory
cp src/main/resources/doc_icon.icns "${RESOURCES_DIR}/doc_icon.icns"
cp src/main/resources/textbundle_icon.icns "${RESOURCES_DIR}/textbundle_icon.icns"

PLISTBUDDY="/usr/libexec/PlistBuddy"

# --- Replace CFBundleDocumentTypes entirely ---
# jpackage creates separate entries for each file-associations file using the old
# CFBundleTypeExtensions key. Modern macOS requires LSItemContentTypes with proper
# UTIs for document icons to work. We delete the jpackage-generated array and
# recreate it with the correct structure.

$PLISTBUDDY -c "Delete :CFBundleDocumentTypes" "$INFO_PLIST" 2>/dev/null || true
$PLISTBUDDY -c "Add :CFBundleDocumentTypes array" "$INFO_PLIST"

# Entry 0: Markdown files (.md, .markdown) using net.daringfireball.markdown
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:0 dict" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:0:CFBundleTypeIconFile string doc_icon.icns" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:0:CFBundleTypeName string Markdown Document" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:0:CFBundleTypeRole string Editor" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:0:LSHandlerRank string Owner" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:0:LSItemContentTypes array" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:0:LSItemContentTypes:0 string net.daringfireball.markdown" "$INFO_PLIST"

# Entry 1: TextBundle files (.textbundle) using org.textbundle.package
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1 dict" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1:CFBundleTypeIconFile string textbundle_icon.icns" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1:CFBundleTypeName string TextBundle Document" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1:CFBundleTypeRole string Editor" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1:LSHandlerRank string Owner" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1:LSTypeIsPackage bool true" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1:LSItemContentTypes array" "$INFO_PLIST"
$PLISTBUDDY -c "Add :CFBundleDocumentTypes:1:LSItemContentTypes:0 string org.textbundle.package" "$INFO_PLIST"

# --- Add UTImportedTypeDeclarations array ---
$PLISTBUDDY -c "Delete :UTImportedTypeDeclarations" "$INFO_PLIST" 2>/dev/null || true
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

# --- Remove any UTExportedTypeDeclarations (we only import, not export, these UTIs) ---
$PLISTBUDDY -c "Delete :UTExportedTypeDeclarations" "$INFO_PLIST" 2>/dev/null || true

echo "Info.plist patching complete."
echo ""
echo "CFBundleDocumentTypes:"
$PLISTBUDDY -c "Print :CFBundleDocumentTypes" "$INFO_PLIST"
echo ""
echo "UTImportedTypeDeclarations:"
$PLISTBUDDY -c "Print :UTImportedTypeDeclarations" "$INFO_PLIST"
echo ""
echo "Document icons copied to ${RESOURCES_DIR}:"
ls -la "${RESOURCES_DIR}/"*icon* 2>/dev/null || true
