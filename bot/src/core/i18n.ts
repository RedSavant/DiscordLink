import { Locale } from 'discord.js';
import { cfg, state } from './state';

const DISCORD_LOCALES = new Set<string>(Object.values(Locale));

function lookup(locale: string, key: string): unknown {
    let current: any = state.languages[locale]?.discord;
    for (const part of key.split('.')) {
        if (current == null || typeof current !== 'object') return undefined;
        current = current[part];
    }
    return current;
}

export function raw(locale: string, key: string): string | undefined {
    for (const candidate of [locale, state.defaultLocale, 'en_US']) {
        const value = lookup(candidate, key);
        if (Array.isArray(value)) return value.join('\n');
        if (typeof value === 'string') return value;
    }
    return undefined;
}

export function format(text: string, vars: Record<string, unknown> = {}): string {
    return text.replace(/\{e:([a-zA-Z0-9_-]+)\}/g, (_, key: string) => state.emojis[key] ?? '').replace(/\{([a-zA-Z0-9_-]+)\}/g, (match, name: string) =>
        vars[name] === undefined || vars[name] === null ? match : String(vars[name]));
}

export function t(locale: string, key: string, vars: Record<string, unknown> = {}): string {
    const value = raw(locale, key);
    return value === undefined ? key : format(value, vars);
}

export function lines(locale: string, key: string, vars: Record<string, unknown> = {}): string {
    const value = raw(locale, key);
    if (value === undefined) return '';
    return value
        .split('\n')
        .map((line) => format(line, vars))
        .filter((line) => !/\{[a-zA-Z0-9_-]+\}/.test(line))
        .join('\n');
}

export function locales(): string[] {
    return Object.keys(state.languages);
}

export function languageName(locale: string): string {
    const meta = state.languages[locale]?.meta ?? {};
    return meta.name ?? locale;
}

export function languageFlag(locale: string): string | undefined {
    return state.languages[locale]?.meta?.flag;
}

export function discordCodes(locale: string): string[] {
    const configured: string[] = state.languages[locale]?.meta?.['discord-locales'] ?? [];
    const derived = [locale.replace('_', '-'), locale.split('_')[0]];
    return [...new Set([...configured, ...derived])].filter((code) => DISCORD_LOCALES.has(code));
}

export function fromDiscord(discordLocale: string | null | undefined): string | undefined {
    if (!discordLocale) return undefined;
    for (const locale of locales()) {
        if (discordCodes(locale).includes(discordLocale)) return locale;
    }
    const language = discordLocale.split('-')[0].toLowerCase();
    return locales().find((locale) => locale.split('_')[0].toLowerCase() === language);
}

export function localeOf(userId: string, discordLocale?: string | null): string {
    const chosen = state.discordLocales.get(userId);
    if (chosen && state.languages[chosen]) return chosen;
    if (cfg('language.auto-detect-discord', true)) {
        const detected = fromDiscord(discordLocale);
        if (detected) return detected;
    }
    return state.defaultLocale;
}
