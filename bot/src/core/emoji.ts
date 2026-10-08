import { parseEmoji, type APIMessageComponentEmoji } from 'discord.js';
import { state } from './state';

export function emoji(key: string): string {
    return state.emojis[key] ?? '';
}

export function prefixed(key: string, content: string): string {
    const value = emoji(key);
    return value ? `${value} ${content}` : content;
}

export function componentEmoji(key: string): APIMessageComponentEmoji | undefined {
    const value = emoji(key);
    if (!value) return undefined;
    const parsed = parseEmoji(value);
    if (!parsed) return undefined;
    return parsed.id ? { id: parsed.id, name: parsed.name, animated: parsed.animated } : { name: parsed.name };
}

export function withEmoji<T extends { setEmoji(emoji: APIMessageComponentEmoji): T }>(builder: T, key: string): T {
    const value = componentEmoji(key);
    return value ? builder.setEmoji(value) : builder;
}
