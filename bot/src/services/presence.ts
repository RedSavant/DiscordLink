import { ActivityType } from 'discord.js';
import { client } from '../core/client';
import { format } from '../core/i18n';
import { cfg, state } from '../core/state';

const TYPES: Record<string, ActivityType> = {
    PLAYING: ActivityType.Playing,
    WATCHING: ActivityType.Watching,
    LISTENING: ActivityType.Listening,
    COMPETING: ActivityType.Competing,
};

export function updatePresence(): void {
    if (!client.isReady()) return;
    if (!cfg('discord.activity.enabled', true)) {
        client.user.setPresence({ activities: [] });
        return;
    }
    const type = TYPES[String(cfg('discord.activity.type', 'WATCHING')).toUpperCase()] ?? ActivityType.Watching;
    const name = format(cfg('discord.activity.text', '{online}/{max}'), { ...state.snapshot }).slice(0, 128);
    client.user.setPresence({ activities: [{ name, type }] });
}
