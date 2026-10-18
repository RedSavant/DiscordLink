import {
    ChannelType,
    InteractionContextType,
    PermissionFlagsBits,
    SlashCommandBuilder,
    type RESTPostAPIChatInputApplicationCommandsJSONBody,
} from 'discord.js';
import { client, guildId, linkMode } from '../core/client';
import { discordCodes, locales, t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { cfg, state } from '../core/state';

function describe(key: string): { base: string; localized: Record<string, string> } {
    const base = t('en_US' in state.languages ? 'en_US' : state.defaultLocale, key).slice(0, 100);
    const localized: Record<string, string> = {};
    for (const locale of locales()) {
        for (const code of discordCodes(locale)) {
            localized[code] = t(locale, key).slice(0, 100);
        }
    }
    return { base, localized };
}

export function commandsJson() {
    const link = describe('commands.link.description');
    const linkCode = describe('commands.link.options.code');
    const unlink = describe('commands.unlink.description');
    const linkinfo = describe('commands.linkinfo.description');
    const linkinfoUser = describe('commands.linkinfo.options.user');
    const lang = describe('commands.lang.description');
    const panel = describe('commands.panel.description');
    const linkmessage = describe('commands.linkmessage.description');
    const linkmessageChannel = describe('commands.linkmessage.options.channel');

    const commands: Array<{ toJSON(): RESTPostAPIChatInputApplicationCommandsJSONBody }> = [
        new SlashCommandBuilder()
            .setName('link')
            .setDescription(link.base)
            .setDescriptionLocalizations(link.localized)
            .setContexts(InteractionContextType.Guild)
            .addStringOption((option) => option
                .setName('code')
                .setDescription(linkCode.base)
                .setDescriptionLocalizations(linkCode.localized)
                .setRequired(true)
                .setMinLength(4)
                .setMaxLength(16)),
        new SlashCommandBuilder()
            .setName('unlink')
            .setDescription(unlink.base)
            .setDescriptionLocalizations(unlink.localized)
            .setContexts(InteractionContextType.Guild),
        new SlashCommandBuilder()
            .setName('linkinfo')
            .setDescription(linkinfo.base)
            .setDescriptionLocalizations(linkinfo.localized)
            .setContexts(InteractionContextType.Guild)
            .addUserOption((option) => option
                .setName('user')
                .setDescription(linkinfoUser.base)
                .setDescriptionLocalizations(linkinfoUser.localized)),
        new SlashCommandBuilder()
            .setName('lang')
            .setDescription(lang.base)
            .setDescriptionLocalizations(lang.localized)
            .setContexts(InteractionContextType.Guild),
    ];

    if (linkMode() === 'CHANNEL') commands.shift();

    const adminOnly = cfg<string[]>('panel.allowed-roles', []).length === 0;
    const linkmessageCommand = new SlashCommandBuilder()
        .setName('linkmessage')
        .setDescription(linkmessage.base)
        .setDescriptionLocalizations(linkmessage.localized)
        .setContexts(InteractionContextType.Guild)
        .addChannelOption((option) => option
            .setName('channel')
            .setDescription(linkmessageChannel.base)
            .setDescriptionLocalizations(linkmessageChannel.localized)
            .addChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement));
    if (adminOnly) linkmessageCommand.setDefaultMemberPermissions(PermissionFlagsBits.Administrator);
    commands.push(linkmessageCommand);

    if (cfg('panel.enabled', true)) {
        const panelCommand = new SlashCommandBuilder()
            .setName('panel')
            .setDescription(panel.base)
            .setDescriptionLocalizations(panel.localized)
            .setContexts(InteractionContextType.Guild);
        if (adminOnly) {
            panelCommand.setDefaultMemberPermissions(PermissionFlagsBits.Administrator);
        }
        commands.push(panelCommand);
    }
    return commands.map((command) => command.toJSON());
}

let lastSignature = '';

export async function registerCommands(force = false): Promise<void> {
    if (!client.isReady()) return;
    const body = commandsJson();
    const signature = JSON.stringify(body);
    if (!force && signature === lastSignature) return;
    try {
        const id = guildId();
        if (id) {
            const target = await client.guilds.fetch(id);
            await target.commands.set(body);
        } else {
            ipc.log('warn', 'guild-id is not configured, registering global commands (may take up to 1 hour to appear).');
            await client.application.commands.set(body);
        }
        lastSignature = signature;
        ipc.log('debug', `Registered ${body.length} slash command(s).`);
    } catch (error: any) {
        ipc.log('error', `Unable to register slash commands: ${error?.message ?? error}`);
    }
}
