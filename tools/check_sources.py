#!/usr/bin/env python3
"""Offline sanity checks for the ARCHIVE project (no Android SDK required).

1. Parses every .java file with tree-sitter-java and reports syntax errors.
2. Parses every XML resource and checks that @string/@drawable/@style/@dimen/@color/@layout/@menu
   references resolve to a real resource, and that layout ids referenced from Java exist.
Usage: python3 tools/check_sources.py
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res')
JAVA = os.path.join(ROOT, 'app/src/main/java')

ANDROID_ATTR = re.compile(r'^@(android:|\*android:)')
REF = re.compile(r'@(string|drawable|style|dimen|color|layout|menu|mipmap|xml|raw|array|integer|bool|plurals|anim|attr)/([A-Za-z0-9_.]+)')
JAVA_RES = re.compile(r'\bR\.(string|drawable|style|dimen|color|layout|menu|mipmap|xml|raw|array|integer|bool|plurals|attr)\.([A-Za-z0-9_]+)')

# Attributes/styles that come from AndroidX / Material rather than this project.
LIBRARY_PREFIXES = ('Widget.Material3', 'Widget.AppCompat', 'Widget.Material', 'Theme.Material3',
                    'TextAppearance.Material3', 'TextAppearance.AppCompat', 'ShapeAppearance.Material3',
                    'ThemeOverlay.Material3', 'Base.', 'Platform.')
LIBRARY_ATTRS = {'colorPrimary', 'colorOnPrimary', 'colorSurface', 'colorOnSurface', 'colorOutline',
                 'colorSecondary', 'colorOnSurfaceVariant', 'colorSurfaceVariant', 'colorControlNormal',
                 'colorBackground', 'colorOnBackground', 'actionBarSize', 'selectableItemBackground',
                 'selectableItemBackgroundBorderless', 'colorPrimaryContainer', 'colorOnPrimaryContainer',
                 'colorSecondaryContainer', 'colorOnSecondaryContainer', 'windowActionModeOverlay',
                 'materialButtonOutlinedStyle', 'materialButtonStyle', 'materialCardViewStyle',
                 'bottomSheetDialogTheme', 'materialAlertDialogTheme', 'switchStyle'}


def collect_resources():
    found = set()
    for path in glob.glob(os.path.join(RES, '**', '*.xml'), recursive=True):
        folder = os.path.basename(os.path.dirname(path))
        name = os.path.basename(path)[:-4]
        if folder.startswith('values'):
            found.add(('values-file', name))
            continue
        kind = folder.split('-')[0]
        found.add((kind, name))
        # ids declared in this file
    return found


def values_entries():
    """Collects names defined inside values/*.xml by tag."""
    entries = set()
    for path in glob.glob(os.path.join(RES, 'values*', '*.xml')):
        try:
            tree = ET.parse(path)
        except ET.ParseError as exc:
            print(f'XML PARSE ERROR {path}: {exc}')
            continue
        for node in tree.getroot():
            tag = node.tag
            name = node.get('name')
            if tag == 'item' and node.get('type'):
                entries.add((node.get('type'), name))
                continue
            if tag in ('string', 'color', 'dimen', 'style', 'bool', 'integer', 'array', 'plurals', 'attr'):
                entries.add((tag, name))
    return entries


def layout_ids():
    ids = set()
    for path in glob.glob(os.path.join(RES, '**', '*.xml'), recursive=True):
        try:
            text = open(path).read()
        except OSError:
            continue
        ids.update(re.findall(r'@\+id/([A-Za-z0-9_]+)', text))
    return ids


def check():
    problems = 0
    # --- Java syntax
    try:
        import tree_sitter_java
        from tree_sitter import Language, Parser
        parser = Parser(Language(tree_sitter_java.language()))
    except Exception as exc:  # pragma: no cover
        parser = None
        print('WARN: tree-sitter-java not installed, skipping syntax check:', exc)

    java_files = sorted(glob.glob(os.path.join(JAVA, '**', '*.java'), recursive=True))
    test_files = sorted(glob.glob(os.path.join(ROOT, 'app/src/test/java', '**', '*.java'),
                                  recursive=True))
    syntax_files = java_files + test_files
    if parser:
        for path in syntax_files:
            src = open(path, 'rb').read()
            tree = parser.parse(src)
            stack = [tree.root_node]
            bad = []
            while stack:
                node = stack.pop()
                if node.type == 'ERROR' or node.is_missing:
                    bad.append((node.start_point, node.type))
                stack.extend(node.children)
            if bad:
                problems += 1
                print(f'JAVA SYNTAX {os.path.relpath(path, ROOT)}: {bad[:3]}')

    # --- resource references
    values = values_entries()
    available = {(kind, name) for kind, name in values}
    for path in glob.glob(os.path.join(RES, '**', '*.xml'), recursive=True):
        kind_prefix = os.path.basename(os.path.dirname(path)).split('-')[0]
        name = os.path.basename(path)[:-4]
        if not kind_prefix.startswith('values'):
            available.add((kind_prefix, name))
    # file-based resource types live in folders, values types in the set above
    for path in glob.glob(os.path.join(RES, '**', '*.xml'), recursive=True):
        text = open(path, errors='ignore').read()
        for kind, name in REF.findall(text):
            if kind == 'style' and name.startswith(LIBRARY_PREFIXES):
                continue
            if (kind, name) not in available:
                problems += 1
                print(f'RES REF {os.path.relpath(path, ROOT)}: @{kind}/{name} not found')

    # --- ids used from Java
    ids = layout_ids()
    missing_ids = []
    for path in java_files:
        for match in re.finditer(r'R\.id\.([A-Za-z0-9_]+)', open(path).read()):
            if match.group(1) not in ids:
                missing_ids.append((os.path.relpath(path, ROOT), match.group(1)))
    for path, name in missing_ids:
        problems += 1
        print(f'ID REF {path}: R.id.{name} not declared in any layout')

    # --- R.<type>.<name> references from Java
    for path in java_files:
        for kind, name in JAVA_RES.findall(open(path).read()):
            if kind == 'id':
                continue
            if kind == 'attr' and name in LIBRARY_ATTRS:
                continue
            lookup = name.replace('_', '.') if kind == 'style' else name
            if kind == 'style' and lookup.startswith(LIBRARY_PREFIXES):
                continue
            if (kind, lookup) not in available:
                problems += 1
                print(f'JAVA RES {os.path.relpath(path, ROOT)}: R.{kind}.{name} not found')

    print(f'\nchecked {len(syntax_files)} java files '
          f'({len(test_files)} tests), '
          f'{len(glob.glob(os.path.join(RES, "**", "*.xml"), recursive=True))} xml files')
    print('problems:', problems)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(check())
