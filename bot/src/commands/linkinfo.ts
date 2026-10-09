import { ContainerBuilder, type ButtonInteraction, type ChatInputCommandInteraction, type User } from 'discord.js';
import { languageName, lines, t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { Colors, headUrl, text, timestamp } from '../core/ui';
import { localeFor, respond, respondNotice } from './reply';

export async function linkinfoCommand(interaction: ChatInputCommandInteraction): Promise<void> {
    await showLinkInfo(interaction, interaction.options.getUser('user') ?? interaction.user);
}

export async function linkInfoButton(interaction: ButtonInteraction): Promise<void> {
    await showLinkInfo(interaction, interaction.user);
}

async function showLinkInfo(interaction: ChatInputCommandInteraction | ButtonInteraction, user: User): Promise<void> {
    const locale = localeFor(interaction);
    const self = user.id === interaction.user.id;
    await interaction.deferReply({ flags: 'Ephemeral' });
    try {
        const result = await ipc.request('link.info', { discordId: user.id });
        if (!result.linked) {
            await respondNotice(interaction, self ? t(locale, 'linkinfo.self-not-linked') : t(locale, 'linkinfo.not-linked', { user: `<@${user.id}>` }), Colors.warning);
            return;
        }
        const account = result.account;
        const content = `### ${t(locale, 'linkinfo.title', { player: account.name })}\n` + lines(locale, 'linkinfo.lines', {
            player: account.name,
            uuid: account.uuid,
            discord: `<@${account.discordId}>`,
            date: timestamp(account.linkedAt, 'F'),
            relative: timestamp(account.linkedAt, 'R'),
            language: languageName(account.locale),
            status: t(locale, account.online ? 'common.online' : 'common.offline'),
        });
        const container = new ContainerBuilder()
            .setAccentColor(Colors.info)
            .addSectionComponents((section) => section
                .addTextDisplayComponents(text(content))
                .setThumbnailAccessory((thumb) => thumb.setURL(headUrl(account.uuid))));
        await respond(interaction, [container]);
    } catch {
        await respondNotice(interaction, t(locale, 'errors.server-unreachable'), Colors.error);
    }
}
