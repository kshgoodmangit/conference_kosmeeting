import { cp, mkdir, rm } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sourceDirectory = resolve(frontendDirectory, 'node_modules', 'ckeditor4');
const customPluginsDirectory = resolve(frontendDirectory, 'ckeditor-plugins');
const targetDirectory = resolve(frontendDirectory, 'public', 'vendor', 'ckeditor4');

await rm(targetDirectory, { recursive: true, force: true });
await mkdir(targetDirectory, { recursive: true });

const runtimeDirectories = ['assets', 'lang', 'plugins', 'skins', 'vendor'];
const runtimeFiles = ['ckeditor.js', 'config.js', 'contents.css', 'styles.js', 'LICENSE.md'];

for (const directory of runtimeDirectories) {
    await cp(resolve(sourceDirectory, directory), resolve(targetDirectory, directory), { recursive: true });
}

for (const file of runtimeFiles) {
    await cp(resolve(sourceDirectory, file), resolve(targetDirectory, file));
}

for (const plugin of ['safeyoutube']) {
    await cp(
        resolve(customPluginsDirectory, plugin),
        resolve(targetDirectory, 'plugins', plugin),
        { recursive: true }
    );
}
