"""Small architectural guardrails; Android lint and tests cover behavior."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/singam/lionlibrary'
failures = []
for path in root.rglob('*.kt'):
    source = path.read_text(encoding='utf-8')
    relative = path.relative_to(root)
    if relative.parts[0] == 'domain' and path.name != 'LaunchPlayerUseCase.kt':
        if re.search(r'^import (?:android\.|androidx\.|com\.singam\.lionlibrary\.(?:data|presentation)\.)', source, re.M):
            failures.append(f'{relative}: domain must remain independent of platform/data/presentation')
    if path.name.endswith('ViewModel.kt') and re.search(r'^import com\.singam\.lionlibrary\.data\.', source, re.M):
        failures.append(f'{relative}: ViewModels must use domain contracts')
    if 'fallbackToDestructiveMigration' in source:
        failures.append(f'{relative}: destructive customer database upgrade')
    for screen in re.finditer(r'fun \w+Screen\((.*?)\)\s*\{', source, re.S):
        if 'ViewModel' in screen[1]:
            failures.append(f'{relative}: Screen must not receive a ViewModel')
    if path.name in ('ScannedFile.kt', 'ParsedFile.kt'):
        failures.append(f'{relative}: documented unused legacy model returned')

if failures:
    print('\n'.join(failures))
    sys.exit(1)
print('PASS domain boundaries, ViewModel dependencies, Screen parameters and safe migration guardrails')
