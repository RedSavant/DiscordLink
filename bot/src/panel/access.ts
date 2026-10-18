import { PermissionFlagsBits } from 'discord.js';
import { guildId } from '../core/client';
import { cfg } from '../core/state';
import type { Replyable } from '../commands/reply';

export function canUsePanel(interaction: Replyable): boolean {
    if (!interaction.inGuild()) return false;
    const configured = guildId();
    if (configured && interaction.guildId !== configured) return false;
    if (interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) return true;
    const allowed = cfg<string[]>('panel.allowed-roles', []);
    const roles = interaction.member?.roles;
    const ids: string[] = Array.isArray(roles) ? roles : roles ? [...roles.cache.keys()] : [];
    return ids.some((id) => allowed.includes(id));
}
