import { Client, GatewayIntentBits, Guild, Partials } from 'discord.js';
import { cfg } from './state';
import { isSnowflake } from './ui';

export const client = new Client({
    intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers],
    partials: [Partials.GuildMember],
});

export const startedAt = Date.now();

export function guildId(): string | undefined {
    const id = process.env.DISCORD_GUILD_ID || cfg<string>('discord.guild-id', '');
    return isSnowflake(id) ? id : undefined;
}

export async function guild(): Promise<Guild | undefined> {
    const id = guildId();
    if (!id || !client.isReady()) return undefined;
    return client.guilds.cache.get(id) ?? (await client.guilds.fetch(id).catch(() => undefined));
}

export function ping(): number {
    const value = client.ws.ping;
    return Number.isFinite(value) && value >= 0 ? Math.round(value) : 0;
}

export function linkMode(): 'COMMAND' | 'CHANNEL' | 'BOTH' {
    const mode = String(cfg('linking.mode', 'COMMAND')).toUpperCase();
    return mode === 'CHANNEL' || mode === 'BOTH' ? mode : 'COMMAND';
}

export function linkChannelId(): string | undefined {
    const id = cfg<string>('linking.channel.channel-id', '');
    return isSnowflake(id) ? id : undefined;
}

export function linkChannelName(): string | undefined {
    const id = linkChannelId();
    if (!id || !client.isReady()) return undefined;
    const channel = client.channels.cache.get(id);
    return channel && 'name' in channel && channel.name ? channel.name : undefined;
}
