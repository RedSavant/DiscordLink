import {
    ContainerBuilder,
    MessageFlags,
    type ButtonInteraction,
    type ChatInputCommandInteraction,
    type ModalSubmitInteraction,
    type AnySelectMenuInteraction,
} from 'discord.js';
import { localeOf } from '../core/i18n';
import { notice } from '../core/ui';

export type Replyable = ChatInputCommandInteraction | ButtonInteraction | AnySelectMenuInteraction | ModalSubmitInteraction;

export function localeFor(interaction: Replyable): string {
    return localeOf(interaction.user.id, interaction.locale);
}

export async function respond(interaction: Replyable, containers: ContainerBuilder[], ephemeral = true): Promise<void> {
    const flags = ephemeral ? MessageFlags.IsComponentsV2 | MessageFlags.Ephemeral : MessageFlags.IsComponentsV2;
    if (interaction.deferred || interaction.replied) {
        await interaction.editReply({ components: containers, flags: MessageFlags.IsComponentsV2, allowedMentions: { parse: [] } });
    } else {
        await interaction.reply({ components: containers, flags, allowedMentions: { parse: [] } });
    }
}

export async function respondNotice(interaction: Replyable, content: string, accent: number): Promise<void> {
    await respond(interaction, [notice(content, accent)]);
}
