import type { Json } from './ipc';

export type SettingType = 'BOOLEAN' | 'INTEGER' | 'SNOWFLAKE' | 'SNOWFLAKE_LIST' | 'LOCALE';

export interface SettingDef {
    id: string;
    path: string;
    type: SettingType;
    min?: number;
    max?: number;
}

export interface LanguageBundle {
    meta: Json;
    discord: Json;
}

export interface ServerSnapshot {
    online: number;
    max: number;
    linked: number;
    pending: number;
}

export const state = {
    initialized: false,
    version: '0.0.0',
    config: {} as Json,
    languages: {} as Record<string, LanguageBundle>,
    defaultLocale: 'en_US',
    schema: [] as SettingDef[],
    discordLocales: new Map<string, string>(),
    emojis: {} as Record<string, string>,
    snapshot: { online: 0, max: 0, linked: 0, pending: 0 } as ServerSnapshot,
};

export function applyInit(payload: Json): void {
    state.version = payload.version ?? state.version;
    state.config = payload.config ?? {};
    state.languages = payload.languages ?? {};
    state.defaultLocale = payload.defaultLocale ?? 'en_US';
    state.schema = payload.schema ?? [];
    state.discordLocales = new Map(Object.entries(payload.discordLocales ?? {}));
    state.emojis = payload.emojis ?? {};
    state.initialized = true;
}

export function applyConfig(config: Json, defaultLocale?: string): void {
    state.config = config;
    if (defaultLocale) state.defaultLocale = defaultLocale;
}

export function cfg<T>(path: string, fallback: T): T {
    let current: any = state.config;
    for (const part of path.split('.')) {
        if (current == null || typeof current !== 'object') return fallback;
        current = current[part];
    }
    return (current ?? fallback) as T;
}

export function setting(path: string): SettingDef | undefined {
    return state.schema.find((def) => def.path === path || def.id === path);
}
