import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../src/main/resources');
let checks = 0;
function check(condition, message) {
    checks++;
    if (!condition) throw new Error(message);
}
function walk(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap(entry =>
        entry.isDirectory() ? walk(join(directory, entry.name)) : [join(directory, entry.name)]);
}
function pngSize(file) {
    const bytes = readFileSync(file);
    check(bytes.length >= 24 && bytes.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'))
        && bytes.subarray(12, 16).toString() === 'IHDR', `Invalid PNG header: ${file}`);
    const width = bytes.readUInt32BE(16), height = bytes.readUInt32BE(20);
    check(width > 0 && height > 0, `Empty PNG: ${file}`);
    return { width, height };
}
for (const file of walk(join(root, 'assets'))) {
    if (file.endsWith('.png')) pngSize(file);
    if (file.endsWith('.json') || file.endsWith('.mcmeta')) {
        try { JSON.parse(readFileSync(file, 'utf8')); }
        catch (error) { throw new Error(`Invalid packaged JSON ${file}: ${error.message}`); }
        checks++;
    }
}
const iconAtlas = JSON.parse(readFileSync(join(root, 'assets/phaze/msdf/ui_icons.json'), 'utf8'));
const [atlasNamespace, atlasPath] = iconAtlas.atlas.texture.split(':');
const atlasSize = pngSize(join(root, 'assets', atlasNamespace, atlasPath));
check(atlasSize.width === iconAtlas.atlas.width && atlasSize.height === iconAtlas.atlas.height,
    'MSDF icon metadata dimensions must match the actual texture');
const iconIds = new Set();
for (const icon of iconAtlas.icons) {
    check(!iconIds.has(icon.id), `Duplicate MSDF icon: ${icon.id}`);
    iconIds.add(icon.id);
    check(Number.isFinite(icon.contentAspectRatio) && icon.contentAspectRatio > 0,
        `Invalid MSDF icon aspect ratio: ${icon.id}`);
    const bounds = icon.atlasBounds;
    check([bounds.left, bounds.top, bounds.right, bounds.bottom].every(Number.isFinite)
        && bounds.left >= 0 && bounds.top >= 0 && bounds.right <= atlasSize.width
        && bounds.bottom <= atlasSize.height && bounds.left < bounds.right && bounds.top < bounds.bottom,
        `MSDF icon outside its atlas: ${icon.id}`);
}
const sounds = JSON.parse(readFileSync(join(root, 'assets/phaze/sounds.json'), 'utf8'));
for (const [event, definition] of Object.entries(sounds)) {
    for (const sound of definition.sounds) {
        if (typeof sound === 'object' && sound.type === 'event') continue;
        const name = typeof sound === 'string' ? sound : sound.name;
        const [namespace, path] = name.includes(':') ? name.split(':') : ['phaze', name];
        const file = join(root, 'assets', namespace, 'sounds', path + '.ogg');
        check(existsSync(file), `Sound ${event} references missing ${file}`);
        check(readFileSync(file).subarray(0, 4).toString() === 'OggS', `Invalid OGG for ${event}`);
    }
}
for (const folder of ['assets/phaze/textures/modules']) {
    for (const file of walk(join(root, folder)).filter(file => file.endsWith('.png'))) {
        check(existsSync(file + '.mcmeta'), `UI fallback lacks filtering metadata: ${file}`);
        const metadata = JSON.parse(readFileSync(file + '.mcmeta', 'utf8'));
        check(metadata.texture?.blur === true, `UI fallback must opt into linear sampling: ${file}`);
    }
}
check(!existsSync(join(root, 'assets/minecraft/textures/atlas/blocks.png.mcmeta')),
    'UI filtering must not override vanilla world-atlas filtering');
console.log(`Port asset checks passed (${checks}; JSON, PNG/MSDF dimensions, sound references, UI-only filtering)`);
