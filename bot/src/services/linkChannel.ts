import type { Message } from 'discord.js';
import { linkChannelId, linkMode } from '../core/client';
import { localeOf, t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { cfg } from '../core/state';
import { Colors, notice } from '../core/ui';

const CODE = /^[A-Za-z0-9]{4,16}$/;

export async function handleLinkChannelMessage(message: Message): Promise<void> {
    if (!message.inGuild() || message.author.bot || message.system) return;
    if (linkMode() === 'COMMAND' || message.channelId !== linkChannelId()) return;

    const locale = localeOf(message.author.id, message.guild.preferredLocale);
    const code = message.content.trim().split(/\s+/)[0] ?? '';
    const deleteMessages = cfg('linking.channel.delete-messages', true);
    if (deleteMessages) await message.delete().catch(() => undefined);

    let content: string;
    let accent: number;
    if (!CODE.test(code)) {
        content = t(locale, 'link.channel-hint');
        accent = Colors.warning;
    } else {
        try {
            const result = await ipc.request('link.verify', {
                code,
                discordId: message.author.id,
                discordName: message.author.username,
            });
            const player = result.account?.name ?? '?';
            switch (result.status) {
                case 'linked':
                    content = t(locale, 'link.success', { player });
                    accent = Colors.success;
                    break;
                case 'discord_already_linked':
                    content = t(locale, 'link.discord-already-linked', { player });
                    accent = Colors.warning;
                    break;
                case 'player_already_linked':
                    content = t(locale, 'link.player-already-linked', { player });
                    accent = Colors.warning;
                    break;
                default:
                    content = t(locale, 'link.invalid-code');
                    accent = Colors.error;
            }
        } catch {
            content = t(locale, 'errors.server-unreachable');
            accent = Colors.error;
        }
    }

    const payload = {
        components: [notice(`<@${message.author.id}>\n${content}`, accent)],
        flags: 'IsComponentsV2' as const,
        allowedMentions: { users: [message.author.id] },
    };
    const reply = deleteMessages
        ? await message.channel.send(payload).catch(() => undefined)
        : await message.reply(payload).catch(() => undefined);
    const delay = Number(cfg('linking.channel.reply-delete-after', 10));
    if (reply && delay > 0) {
        setTimeout(() => void reply.delete().catch(() => undefined), delay * 1000);
    }
}
