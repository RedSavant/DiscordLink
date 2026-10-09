import {
    ActionRowBuilder,
    ContainerBuilder,
    StringSelectMenuBuilder,
    StringSelectMenuOptionBuilder,
    type ChatInputCommandInteraction,
    type StringSelectMenuInteraction,
} from 'discord.js';
import { prefixed, withEmoji } from '../core/emoji';
import { languageFlag, languageName, locales, t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { state } from '../core/state';
import { Colors, text } from '../core/ui';
import { localeFor, respond } from './reply';

const AUTO = 'auto';

function view(locale: string, userId: string, message?: string): ContainerBuilder {
    const chosen = state.discordLocales.get(userId);
    const current = chosen ? languageName(chosen) : t(locale, 'lang.auto');
    const select = new StringSelectMenuBuilder()
        .setCustomId('dl:lang')
        .setPlaceholder(t(locale, 'lang.placeholder'))
        .addOptions(
            withEmoji(new StringSelectMenuOptionBuilder()
                .setLabel(t(locale, 'lang.auto'))
                .setDescription(t(locale, 'lang.auto-description'))
                .setValue(AUTO)
                .setDefault(!chosen), 'language-auto'),
            ...locales().slice(0, 24).map((code) => {
                const option = new StringSelectMenuOptionBuilder()
                    .setLabel(languageName(code))
                    .setDescription(code)
                    .setValue(code)
                    .setDefault(chosen === code);
                const flag = languageFlag(code);
                if (flag) option.setEmoji(flag);
                return option;
            }),
        );
    const container = new ContainerBuilder()
        .setAccentColor(Colors.info)
        .addTextDisplayComponents(text(`### ${t(locale, 'lang.title')}\n${t(locale, 'lang.current', { language: current })}`));
    if (message) container.addTextDisplayComponents(text(message));
    return container.addActionRowComponents(new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(select));
}

export async function langCommand(interaction: ChatInputCommandInteraction): Promise<void> {
    await respond(interaction, [view(localeFor(interaction), interaction.user.id)]);
}

export async function langSelect(interaction: StringSelectMenuInteraction): Promise<void> {
    const value = interaction.values[0];
    try {
        const result = await ipc.request('discord.lang', {
            discordId: interaction.user.id,
            locale: value === AUTO ? '' : value,
        });
        if (result.success) {
            if (value === AUTO) state.discordLocales.delete(interaction.user.id);
            else state.discordLocales.set(interaction.user.id, result.locale);
        }
        const locale = localeFor(interaction);
        const language = value === AUTO ? t(locale, 'lang.auto') : languageName(value);
        await interaction.update({ components: [view(locale, interaction.user.id, prefixed('success', t(locale, 'lang.changed', { language })))] });
    } catch {
        const locale = localeFor(interaction);
        await interaction.update({ components: [view(locale, interaction.user.id, prefixed('error', t(locale, 'errors.server-unreachable')))] });
    }
}
