import {
    ActionRowBuilder,
    ButtonBuilder,
    ButtonStyle,
    ContainerBuilder,
    MediaGalleryBuilder,
    MediaGalleryItemBuilder,
    type SendableChannels,
} from 'discord.js';
import { client, guild, linkChannelId, linkMode } from '../core/client';
import { withEmoji } from '../core/emoji';
import { lines, t } from '../core/i18n';
import { cfg, state } from '../core/state';
import { color, separator, text } from '../core/ui';

function value(input: unknown): string | undefined {
    return typeof input === 'string' && input.trim() ? input.trim() : undefined;
}

export async function buildLinkMessage(locale = state.defaultLocale): Promise<ContainerBuilder> {
    const mode = linkMode();
    const target = await guild();
    const channelId = linkChannelId();
    const vars = {
        server: target?.name,
        address: value(cfg('linking.message.server-address', '')),
        channel: channelId ? `<#${channelId}>` : undefined,
        minutes: Math.max(1, Math.round(Number(cfg('linking.code.expiration', 300)) / 60)),
        length: cfg('linking.code.length', 6),
    };

    const container = new ContainerBuilder().setAccentColor(color(cfg('linking.message.accent-color', '#5865F2')));
    const image = value(cfg('linking.message.image', ''));
    if (image) {
        container.addMediaGalleryComponents(new MediaGalleryBuilder().addItems(new MediaGalleryItemBuilder().setURL(image)));
    }
    container.addTextDisplayComponents(text(`${t(locale, 'link-message.title', vars)}\n${lines(locale, 'link-message.intro', vars)}`));
    separator(container);
    container.addTextDisplayComponents(text(lines(locale, `link-message.steps.${mode.toLowerCase()}`, vars)));
    const footer = lines(locale, 'link-message.footer', vars);
    if (footer) container.addTextDisplayComponents(text(footer));

    const buttons: ButtonBuilder[] = [];
    if (mode !== 'CHANNEL') {
        buttons.push(withEmoji(new ButtonBuilder()
            .setCustomId('dl:link:modal')
            .setLabel(t(locale, 'link-message.buttons.link').slice(0, 80))
            .setStyle(ButtonStyle.Success), 'link'));
    }
    buttons.push(withEmoji(new ButtonBuilder()
        .setCustomId('dl:link:info')
        .setLabel(t(locale, 'link-message.buttons.info').slice(0, 80))
        .setStyle(ButtonStyle.Secondary), 'info'));
    separator(container);
    container.addActionRowComponents(new ActionRowBuilder<ButtonBuilder>().addComponents(...buttons));
    return container;
}

export async function sendLinkMessage(channelId: string): Promise<void> {
    const channel = await client.channels.fetch(channelId);
    if (!channel?.isSendable()) throw new Error('cannot-send');
    await (channel as SendableChannels).send({
        components: [await buildLinkMessage()],
        flags: 'IsComponentsV2',
        allowedMentions: { parse: [] },
    });
}
