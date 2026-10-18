import type { ChatInputCommandInteraction } from 'discord.js';
import { linkChannelId } from '../core/client';
import { prefixed } from '../core/emoji';
import { t } from '../core/i18n';
import { Colors } from '../core/ui';
import { canUsePanel } from '../panel/access';
import { panelLog } from '../services/logs';
import { sendLinkMessage } from '../services/linkMessage';
import { localeFor, respondNotice } from './reply';

export async function linkmessageCommand(interaction: ChatInputCommandInteraction): Promise<void> {
    const locale = localeFor(interaction);
    if (!canUsePanel(interaction)) {
        await respondNotice(interaction, t(locale, 'panel.no-access'), Colors.error);
        return;
    }
    const channelId = interaction.options.getChannel('channel')?.id ?? linkChannelId() ?? interaction.channelId;
    await interaction.deferReply({ flags: 'Ephemeral' });
    try {
        await sendLinkMessage(channelId);
        panelLog(interaction.user, 'link-message', `#${channelId}`, { value: `<#${channelId}>` });
        await respondNotice(interaction, prefixed('success', t(locale, 'panel.notices.link-message-sent', { channel: `<#${channelId}>` })), Colors.success);
    } catch {
        await respondNotice(interaction, prefixed('error', t(locale, 'panel.errors.cannot-send')), Colors.error);
    }
}
