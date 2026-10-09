import {
    ContainerBuilder,
    LabelBuilder,
    ModalBuilder,
    TextInputBuilder,
    TextInputStyle,
    type ButtonInteraction,
    type ChatInputCommandInteraction,
    type ModalSubmitInteraction,
} from 'discord.js';
import { t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { Colors, headUrl, text } from '../core/ui';
import { cfg } from '../core/state';
import { localeFor, respond, respondNotice } from './reply';

export async function linkCommand(interaction: ChatInputCommandInteraction): Promise<void> {
    await performLink(interaction, interaction.options.getString('code', true));
}

export async function openLinkModal(interaction: ButtonInteraction): Promise<void> {
    const locale = localeFor(interaction);
    const length = Number(cfg('linking.code.length', 6));
    const modal = new ModalBuilder()
        .setCustomId('dl:link:submit')
        .setTitle(t(locale, 'link-message.modal.title').slice(0, 45))
        .addLabelComponents(new LabelBuilder()
            .setLabel(t(locale, 'link-message.modal.label').slice(0, 45))
            .setDescription(t(locale, 'link-message.modal.description').slice(0, 100))
            .setTextInputComponent(new TextInputBuilder()
                .setCustomId('code')
                .setStyle(TextInputStyle.Short)
                .setRequired(true)
                .setMinLength(Math.min(4, length))
                .setMaxLength(16)
                .setPlaceholder(t(locale, 'link-message.modal.placeholder', { length }).slice(0, 100))));
    await interaction.showModal(modal);
}

export async function linkModalSubmit(interaction: ModalSubmitInteraction): Promise<void> {
    await performLink(interaction, interaction.fields.getTextInputValue('code'));
}

async function performLink(interaction: ChatInputCommandInteraction | ModalSubmitInteraction, code: string): Promise<void> {
    const locale = localeFor(interaction);
    await interaction.deferReply({ flags: 'Ephemeral' });
    try {
        const result = await ipc.request('link.verify', {
            code,
            discordId: interaction.user.id,
            discordName: interaction.user.username,
        });
        const player = result.account?.name ?? '?';
        switch (result.status) {
            case 'linked': {
                const container = new ContainerBuilder()
                    .setAccentColor(Colors.success)
                    .addSectionComponents((section) => section
                        .addTextDisplayComponents(text(t(locale, 'link.success', { player })))
                        .setThumbnailAccessory((thumb) => thumb.setURL(headUrl(result.account.uuid))));
                await respond(interaction, [container]);
                return;
            }
            case 'discord_already_linked':
                await respondNotice(interaction, t(locale, 'link.discord-already-linked', { player }), Colors.warning);
                return;
            case 'player_already_linked':
                await respondNotice(interaction, t(locale, 'link.player-already-linked', { player }), Colors.warning);
                return;
            default:
                await respondNotice(interaction, t(locale, 'link.invalid-code'), Colors.error);
        }
    } catch {
        await respondNotice(interaction, t(locale, 'errors.server-unreachable'), Colors.error);
    }
}

export async function unlinkCommand(interaction: ChatInputCommandInteraction): Promise<void> {
    const locale = localeFor(interaction);
    await interaction.deferReply({ flags: 'Ephemeral' });
    try {
        const result = await ipc.request('link.unlink', {
            discordId: interaction.user.id,
            actor: `@${interaction.user.username}`,
        });
        if (result.unlinked) {
            await respondNotice(interaction, t(locale, 'unlink.success', { player: result.account?.name ?? '?' }), Colors.success);
        } else {
            await respondNotice(interaction, t(locale, 'unlink.not-linked'), Colors.warning);
        }
    } catch {
        await respondNotice(interaction, t(locale, 'errors.server-unreachable'), Colors.error);
    }
}
