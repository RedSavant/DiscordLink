import { ContainerBuilder, type User } from 'discord.js';
import { client } from '../core/client';
import { lines, t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { cfg, state } from '../core/state';
import { color, headUrl, isSnowflake, separator, text, timestamp } from '../core/ui';

const COLOR_KEYS: Record<string, string> = {
    join: 'join',
    quit: 'quit',
    link: 'link',
    unlink: 'unlink',
    gui: 'admin',
    panel: 'admin',
};

function enabled(event: string): boolean {
    if (!cfg('logs.enabled', true)) return false;
    const key = event === 'gui' ? 'gui-actions' : event === 'panel' ? 'panel-actions' : event;
    return cfg(`logs.events.${key}`, true);
}

export async function sendLog(event: string, fields: Record<string, string>): Promise<void> {
    if (!enabled(event)) return;
    const channelId = cfg<string>('logs.channel-id', '');
    if (!isSnowflake(channelId) || !client.isReady()) return;

    const locale = state.defaultLocale;
    const vars: Record<string, string> = { ...fields };
    vars.discord = fields.discordId ? `<@${fields.discordId}>` : t(locale, 'logs.not-linked');
    if (fields.actorId) vars.actor = `<@${fields.actorId}>`;
    if (fields.action) vars.action = t(locale, `logs.${event}.actions.${fields.action}`, vars);

    const container = new ContainerBuilder().setAccentColor(color(cfg(`logs.colors.${COLOR_KEYS[event] ?? 'admin'}`, '#5865F2')));
    const title = t(locale, `logs.${event}.title`, vars);
    const body = lines(locale, `logs.${event}.lines`, vars);
    const content = `### ${title}\n${body}`;
    if (fields.uuid) {
        container.addSectionComponents((section) =>
            section.addTextDisplayComponents(text(content)).setThumbnailAccessory((thumb) => thumb.setURL(headUrl(fields.uuid))));
    } else {
        container.addTextDisplayComponents(text(content));
    }
    separator(container, true);
    container.addTextDisplayComponents(text(`-# ${timestamp(Date.now(), 'F')}`));

    try {
        const channel = await client.channels.fetch(channelId);
        if (channel?.isSendable()) {
            await channel.send({ components: [container], flags: 'IsComponentsV2', allowedMentions: { parse: [] } });
        }
    } catch (error: any) {
        ipc.log('warn', `Unable to send log: ${error?.message ?? error}`);
    }
}

export function panelLog(user: User, action: string, target = '-', extra: Record<string, string> = {}): void {
    void sendLog('panel', { actorId: user.id, action, target, ...extra });
}
