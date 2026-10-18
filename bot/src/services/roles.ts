import type { GuildMember } from 'discord.js';
import { guild } from '../core/client';
import { ipc } from '../core/ipc';
import { cfg } from '../core/state';
import { isSnowflake } from '../core/ui';

export interface RoleSync {
    discordId: string;
    name?: string;
    linked?: boolean;
    online?: boolean;
}

async function setRole(member: GuildMember, roleId: unknown, present: boolean): Promise<void> {
    if (!isSnowflake(roleId)) return;
    const has = member.roles.cache.has(roleId);
    if (present && !has) await member.roles.add(roleId, 'DiscordLink');
    if (!present && has) await member.roles.remove(roleId, 'DiscordLink');
}

export async function syncMember(sync: RoleSync): Promise<boolean> {
    const target = await guild();
    if (!target) return false;
    const member = await target.members.fetch(sync.discordId).catch(() => undefined);
    if (!member) return false;

    try {
        if (sync.linked !== undefined && cfg('roles.linked.enabled', true)) {
            if (sync.linked || cfg('roles.linked.remove-on-unlink', true)) {
                await setRole(member, cfg('roles.linked.role-id', ''), sync.linked);
            }
        }
        if (sync.online !== undefined && cfg('roles.online.enabled', true)) {
            if (sync.online || sync.linked === false || cfg('roles.online.remove-on-quit', true)) {
                await setRole(member, cfg('roles.online.role-id', ''), sync.online);
            }
        }
        if (cfg('roles.sync-nickname', false) && sync.linked !== undefined && member.manageable) {
            const nickname = sync.linked && sync.name ? sync.name : null;
            if (member.nickname !== nickname) await member.setNickname(nickname, 'DiscordLink');
        }
        return true;
    } catch (error: any) {
        ipc.log('warn', `Unable to sync roles of ${sync.discordId}: ${error?.message ?? error}`);
        return false;
    }
}

export async function resyncAll(): Promise<number> {
    const { accounts } = await ipc.request<{ accounts: any[] }>('accounts.all');
    let synced = 0;
    for (const account of accounts ?? []) {
        if (await syncMember({ discordId: account.discordId, name: account.name, linked: true, online: account.online })) {
            synced++;
        }
    }
    return synced;
}
