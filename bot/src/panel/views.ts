import {
    ActionRowBuilder,
    ButtonBuilder,
    ButtonStyle,
    ChannelSelectMenuBuilder,
    ChannelType,
    ContainerBuilder,
    RoleSelectMenuBuilder,
    StringSelectMenuBuilder,
    StringSelectMenuOptionBuilder,
    type Guild,
} from 'discord.js';
import { client, linkChannelId, linkMode, ping, startedAt } from '../core/client';
import { languageFlag, languageName, lines, locales, t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { cfg, state } from '../core/state';
import { color, headUrl, isSnowflake, separator, text, timestamp } from '../core/ui';
import { emoji, prefixed, withEmoji } from '../core/emoji';
import { buildLinkMessage } from '../services/linkMessage';
import type { PanelSession } from './session';

function accent(): number {
    return color(cfg('panel.accent-color', '#5865F2'));
}

function button(id: string, label: string, style: ButtonStyle, emojiKey?: string, disabled = false): ButtonBuilder {
    const builder = new ButtonBuilder().setCustomId(id).setLabel(label.slice(0, 80)).setStyle(style).setDisabled(disabled);
    return emojiKey ? withEmoji(builder, emojiKey) : builder;
}

function row(...buttons: ButtonBuilder[]): ActionRowBuilder<ButtonBuilder> {
    return new ActionRowBuilder<ButtonBuilder>().addComponents(...buttons);
}

function header(locale: string, title: string, notice?: string): ContainerBuilder {
    const container = new ContainerBuilder().setAccentColor(accent());
    container.addTextDisplayComponents(text(`## ${title}\n-# ${t(locale, 'panel.subtitle', { version: state.version })}`));
    if (notice) container.addTextDisplayComponents(text(notice));
    separator(container);
    return container;
}

export async function dashboardView(locale: string, notice?: string): Promise<ContainerBuilder> {
    const container = header(locale, t(locale, 'panel.dashboard.title'), notice);
    let server: Record<string, any> | undefined;
    try {
        server = await ipc.request('server.status');
    } catch {
        server = undefined;
    }

    container.addTextDisplayComponents(text(`### ${t(locale, 'panel.dashboard.bot-title')}\n` + lines(locale, 'panel.dashboard.bot', {
        status: t(locale, 'panel.dashboard.status-online'),
        tag: client.user?.tag ?? '?',
        ping: ping(),
        uptime: timestamp(startedAt, 'R'),
    })));
    container.addTextDisplayComponents(text(`### ${t(locale, 'panel.dashboard.server-title')}\n` + (server
        ? lines(locale, 'panel.dashboard.server', {
            status: t(locale, 'panel.dashboard.status-online'),
            software: server.software,
            version: server.version,
            online: server.online,
            max: server.max,
            tps: server.tps,
            uptime: timestamp(server.startedAt, 'R'),
        })
        : `${lines(locale, 'panel.dashboard.server', { status: t(locale, 'panel.dashboard.status-offline') })}\n-# ${t(locale, 'errors.server-unreachable')}`)));
    if (server) {
        container.addTextDisplayComponents(text(`### ${t(locale, 'panel.dashboard.links-title')}\n` + lines(locale, 'panel.dashboard.links', {
            linked: server.linked,
            pending: server.pending,
        })));
    }
    separator(container);
    container.addActionRowComponents(
        row(
            button('dl:pl:0', t(locale, 'panel.buttons.players'), ButtonStyle.Primary, 'players'),
            button('dl:set', t(locale, 'panel.buttons.settings'), ButtonStyle.Primary, 'settings'),
            button('dl:dash', t(locale, 'panel.buttons.refresh'), ButtonStyle.Secondary, 'refresh'),
        ),
        row(
            button('dl:dash:reload', t(locale, 'panel.buttons.reload'), ButtonStyle.Secondary, 'reload'),
            button('dl:dash:clear', t(locale, 'panel.buttons.clear-codes'), ButtonStyle.Secondary, 'clear-codes'),
            button('dl:lm', t(locale, 'panel.buttons.link-message'), ButtonStyle.Success, 'link-message'),
        ),
    );
    return container;
}

export async function playersView(locale: string, session: PanelSession, notice?: string): Promise<ContainerBuilder> {
    const size = Math.min(25, Math.max(1, Number(cfg('panel.players-per-page', 10))));
    const result = await ipc.request('players.list', { page: session.page, size, filter: session.filter, query: session.query });
    session.page = result.page;

    const container = header(locale, t(locale, 'panel.players.title', { count: result.total }), notice);
    const filterLabel = t(locale, `panel.players.filters.${session.filter}`);
    const info = [t(locale, 'panel.players.info', { page: result.page + 1, pages: result.pages, filter: filterLabel })];
    if (session.query) info.push(t(locale, 'panel.players.search-active', { query: session.query }));
    container.addTextDisplayComponents(text(`-# ${info.join(' · ')}`));

    const items: any[] = result.items ?? [];
    if (items.length === 0) {
        container.addTextDisplayComponents(text(t(locale, 'panel.players.empty')));
    } else {
        const list = items.map((item, index) => t(locale, 'panel.players.line', {
            index: result.page * size + index + 1,
            status: emoji(item.online ? 'online' : 'offline'),
            player: item.name,
            discord: `<@${item.discordId}>`,
            date: timestamp(item.linkedAt, 'd'),
        })).join('\n');
        container.addTextDisplayComponents(text(list));
        const select = new StringSelectMenuBuilder()
            .setCustomId('dl:pl:select')
            .setPlaceholder(t(locale, 'panel.players.select-placeholder'))
            .addOptions(items.map((item) => withEmoji(new StringSelectMenuOptionBuilder()
                .setLabel(String(item.name).slice(0, 100))
                .setDescription(String(item.discordName ?? item.discordId).slice(0, 100))
                .setValue(item.uuid), item.online ? 'online' : 'offline')));
        container.addActionRowComponents(new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(select));
    }

    separator(container);
    container.addActionRowComponents(
        row(
            button(`dl:pl:${result.page - 1}`, t(locale, 'panel.buttons.previous'), ButtonStyle.Secondary, 'previous', result.page <= 0),
            button(`dl:pl:${result.page + 1}`, t(locale, 'panel.buttons.next'), ButtonStyle.Secondary, 'next', result.page >= result.pages - 1),
            button('dl:pl:filter', t(locale, 'panel.buttons.filter', { filter: filterLabel }), ButtonStyle.Secondary, 'filter'),
            button('dl:pl:search', t(locale, 'panel.buttons.search'), ButtonStyle.Primary, 'search'),
        ),
    );
    const bottom = [button('dl:dash', t(locale, 'panel.buttons.back'), ButtonStyle.Secondary, 'home')];
    if (session.query) bottom.push(button('dl:pl:clear', t(locale, 'panel.buttons.clear-search'), ButtonStyle.Danger, 'clear-search'));
    container.addActionRowComponents(row(...bottom));
    return container;
}

export async function playerView(locale: string, uuid: string, notice?: string): Promise<ContainerBuilder> {
    const result = await ipc.request('players.get', { uuid });
    if (!result.found) {
        const container = header(locale, t(locale, 'panel.player.not-found'), notice);
        container.addActionRowComponents(row(button('dl:pl:keep', t(locale, 'panel.buttons.back'), ButtonStyle.Secondary, 'back')));
        return container;
    }
    const account = result.account;
    const container = header(locale, t(locale, 'panel.player.title', { player: account.name }), notice);
    const details = lines(locale, 'panel.player.lines', {
        player: account.name,
        uuid: account.uuid,
        discord: `<@${account.discordId}>`,
        discord_name: account.discordName,
        discord_id: account.discordId,
        date: timestamp(account.linkedAt, 'F'),
        relative: timestamp(account.linkedAt, 'R'),
        language: languageName(account.locale),
        bypass: t(locale, account.bypass || account.listBypass ? 'common.enabled' : 'common.disabled'),
        status: t(locale, account.online ? 'common.online' : 'common.offline'),
    });
    container.addSectionComponents((section) => section
        .addTextDisplayComponents(text(details))
        .setThumbnailAccessory((thumb) => thumb.setURL(headUrl(account.uuid)).setDescription(account.name)));

    separator(container);
    container.addActionRowComponents(row(
        button(`dl:p:unlink:${account.uuid}`, t(locale, 'panel.buttons.unlink'), ButtonStyle.Danger, 'unlink'),
        button(`dl:p:resync:${account.uuid}`, t(locale, 'panel.buttons.resync'), ButtonStyle.Primary, 'resync'),
        button(`dl:p:bypass:${account.uuid}`, t(locale, account.bypass ? 'panel.buttons.bypass-off' : 'panel.buttons.bypass-on'),
            account.bypass ? ButtonStyle.Secondary : ButtonStyle.Success, 'bypass'),
    ));
    const languageSelect = new StringSelectMenuBuilder()
        .setCustomId(`dl:p:lang:${account.uuid}`)
        .setPlaceholder(t(locale, 'panel.player.language-placeholder'))
        .addOptions(locales().slice(0, 25).map((code) => {
            const option = new StringSelectMenuOptionBuilder()
                .setLabel(languageName(code))
                .setDescription(code)
                .setValue(code)
                .setDefault(code === account.locale);
            const flag = languageFlag(code);
            if (flag) option.setEmoji(flag);
            return option;
        }));
    container.addActionRowComponents(new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(languageSelect));
    container.addActionRowComponents(row(
        button('dl:pl:keep', t(locale, 'panel.buttons.back'), ButtonStyle.Secondary, 'back'),
        button(`dl:p:view:${account.uuid}`, t(locale, 'panel.buttons.refresh'), ButtonStyle.Secondary, 'refresh'),
    ));
    return container;
}

export function confirmView(locale: string, uuid: string, player: string): ContainerBuilder {
    const container = header(locale, t(locale, 'panel.confirm.title'));
    container.addTextDisplayComponents(text(t(locale, 'panel.confirm.unlink', { player })));
    container.addActionRowComponents(row(
        button(`dl:p:unlink!:${uuid}`, t(locale, 'panel.buttons.confirm'), ButtonStyle.Danger, 'confirm'),
        button(`dl:p:view:${uuid}`, t(locale, 'panel.buttons.cancel'), ButtonStyle.Secondary, 'cancel'),
    ));
    return container;
}

function mention(id: unknown, kind: 'role' | 'channel', locale: string): string {
    if (!isSnowflake(id)) return t(locale, 'common.none');
    return kind === 'role' ? `<@&${id}>` : `<#${id}>`;
}

export function settingsView(locale: string, guild: Guild | undefined, notice?: string): ContainerBuilder {
    const container = header(locale, t(locale, 'panel.settings.title'), notice);
    const toggles = state.schema.filter((def) => def.type === 'BOOLEAN');
    const label = (id: string) => t(locale, `panel.settings.labels.${id}`);

    const summary = toggles.map((def) => prefixed(cfg(def.path, false) ? 'enabled' : 'disabled', label(def.id))).join('\n');
    container.addTextDisplayComponents(text(`### ${t(locale, 'panel.settings.toggles-title')}\n${summary}`));

    const allowed = cfg<string[]>('panel.allowed-roles', []);
    container.addTextDisplayComponents(text(`### ${t(locale, 'panel.settings.values-title')}\n` + lines(locale, 'panel.settings.values', {
        linked_role: mention(cfg('roles.linked.role-id', ''), 'role', locale),
        online_role: mention(cfg('roles.online.role-id', ''), 'role', locale),
        log_channel: mention(cfg('logs.channel-id', ''), 'channel', locale),
        link_mode: t(locale, `panel.settings.modes.${linkMode()}`),
        link_channel: mention(cfg('linking.channel.channel-id', ''), 'channel', locale),
        panel_roles: allowed.length ? allowed.map((id) => `<@&${id}>`).join(' ') : t(locale, 'common.none'),
        language: languageName(state.defaultLocale),
        code_length: cfg('linking.code.length', 6),
        code_expiration: cfg('linking.code.expiration', 300),
        per_page: cfg('panel.players-per-page', 10),
        interval: cfg('discord.activity.update-interval', 60),
    })));
    separator(container);

    const toggleSelect = new StringSelectMenuBuilder()
        .setCustomId('dl:set:toggle')
        .setPlaceholder(t(locale, 'panel.settings.toggle-placeholder'))
        .addOptions(toggles.slice(0, 25).map((def) => {
            const enabled = cfg(def.path, false);
            return withEmoji(new StringSelectMenuOptionBuilder()
                .setLabel(label(def.id).slice(0, 100))
                .setDescription(t(locale, enabled ? 'panel.settings.click-disable' : 'panel.settings.click-enable').slice(0, 100))
                .setValue(def.id), enabled ? 'enabled' : 'disabled');
        }));
    container.addActionRowComponents(new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(toggleSelect));

    const roleSelect = (id: string, placeholder: string, current: unknown) => {
        const select = new RoleSelectMenuBuilder().setCustomId(id).setPlaceholder(placeholder).setMinValues(0).setMaxValues(1);
        if (isSnowflake(current) && guild?.roles.cache.has(current)) select.setDefaultRoles(current);
        return new ActionRowBuilder<RoleSelectMenuBuilder>().addComponents(select);
    };
    container.addActionRowComponents(roleSelect('dl:set:role:roles-linked-role-id', t(locale, 'panel.settings.linked-role-placeholder'), cfg('roles.linked.role-id', '')));
    container.addActionRowComponents(roleSelect('dl:set:role:roles-online-role-id', t(locale, 'panel.settings.online-role-placeholder'), cfg('roles.online.role-id', '')));

    const channelSelect = new ChannelSelectMenuBuilder()
        .setCustomId('dl:set:channel')
        .setPlaceholder(t(locale, 'panel.settings.channel-placeholder'))
        .setChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement)
        .setMinValues(0)
        .setMaxValues(1);
    const channelId = cfg<string>('logs.channel-id', '');
    if (isSnowflake(channelId) && guild?.channels.cache.has(channelId)) channelSelect.setDefaultChannels(channelId);
    container.addActionRowComponents(new ActionRowBuilder<ChannelSelectMenuBuilder>().addComponents(channelSelect));

    const modeSelect = new StringSelectMenuBuilder()
        .setCustomId('dl:set:mode')
        .setPlaceholder(t(locale, 'panel.settings.mode-placeholder'))
        .addOptions(['COMMAND', 'CHANNEL', 'BOTH'].map((mode) => withEmoji(new StringSelectMenuOptionBuilder()
            .setLabel(t(locale, `panel.settings.modes.${mode}`).slice(0, 100))
            .setDescription(t(locale, `panel.settings.modes-description.${mode}`).slice(0, 100))
            .setValue(mode)
            .setDefault(mode === linkMode()), `mode-${mode.toLowerCase()}`)));
    container.addActionRowComponents(new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(modeSelect));

    const linkChannelSelect = new ChannelSelectMenuBuilder()
        .setCustomId('dl:set:linkchannel')
        .setPlaceholder(t(locale, 'panel.settings.link-channel-placeholder'))
        .setChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement)
        .setMinValues(0)
        .setMaxValues(1);
    const linkChannel = linkChannelId();
    if (linkChannel && guild?.channels.cache.has(linkChannel)) linkChannelSelect.setDefaultChannels(linkChannel);
    container.addActionRowComponents(new ActionRowBuilder<ChannelSelectMenuBuilder>().addComponents(linkChannelSelect));

    const panelRoles = new RoleSelectMenuBuilder()
        .setCustomId('dl:set:panelroles')
        .setPlaceholder(t(locale, 'panel.settings.panel-roles-placeholder'))
        .setMinValues(0)
        .setMaxValues(10);
    const validAllowed = allowed.filter((id) => guild?.roles.cache.has(id));
    if (validAllowed.length) panelRoles.setDefaultRoles(...validAllowed);
    container.addActionRowComponents(new ActionRowBuilder<RoleSelectMenuBuilder>().addComponents(panelRoles));

    const localeSelect = new StringSelectMenuBuilder()
        .setCustomId('dl:set:locale')
        .setPlaceholder(t(locale, 'panel.settings.locale-placeholder'))
        .addOptions(locales().slice(0, 25).map((code) => {
            const option = new StringSelectMenuOptionBuilder()
                .setLabel(languageName(code))
                .setDescription(code)
                .setValue(code)
                .setDefault(code === state.defaultLocale);
            const flag = languageFlag(code);
            if (flag) option.setEmoji(flag);
            return option;
        }));
    container.addActionRowComponents(new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(localeSelect));

    container.addActionRowComponents(row(
        button('dl:set:codes', t(locale, 'panel.buttons.codes'), ButtonStyle.Secondary, 'codes'),
        button('dl:set:reload', t(locale, 'panel.buttons.reload'), ButtonStyle.Secondary, 'reload'),
        button('dl:dash', t(locale, 'panel.buttons.back'), ButtonStyle.Secondary, 'home'),
    ));
    return container;
}

export async function linkMessageView(locale: string, notice?: string): Promise<ContainerBuilder[]> {
    const preview = await buildLinkMessage();
    const container = header(locale, t(locale, 'panel.link-message.title'), notice);
    const linkChannel = linkChannelId();
    container.addTextDisplayComponents(text(lines(locale, 'panel.link-message.description', {
        mode: t(locale, `panel.settings.modes.${linkMode()}`),
        channel: linkChannel ? `<#${linkChannel}>` : t(locale, 'common.none'),
        language: languageName(state.defaultLocale),
    })));
    const select = new ChannelSelectMenuBuilder()
        .setCustomId('dl:lm:send')
        .setPlaceholder(t(locale, 'panel.link-message.channel-placeholder'))
        .setChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement)
        .setMinValues(1)
        .setMaxValues(1);
    container.addActionRowComponents(new ActionRowBuilder<ChannelSelectMenuBuilder>().addComponents(select));
    container.addActionRowComponents(row(
        button('dl:lm:link', t(locale, 'panel.buttons.send-link-channel'), ButtonStyle.Success, 'link-message', !linkChannel),
        button('dl:set', t(locale, 'panel.buttons.settings'), ButtonStyle.Secondary, 'settings'),
        button('dl:dash', t(locale, 'panel.buttons.back'), ButtonStyle.Secondary, 'home'),
    ));
    return [container, preview];
}
