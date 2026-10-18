import {
    LabelBuilder,
    ModalBuilder,
    TextInputBuilder,
    TextInputStyle,
    type ButtonInteraction,
    type AnySelectMenuInteraction,
    type ChatInputCommandInteraction,
    type ContainerBuilder,
    type ModalSubmitInteraction,
} from 'discord.js';
import { guild } from '../core/client';
import { languageName, t } from '../core/i18n';
import { ipc } from '../core/ipc';
import { applyConfig, cfg, setting } from '../core/state';
import { Colors } from '../core/ui';
import { localeFor, respond, respondNotice, type Replyable } from '../commands/reply';
import { panelLog } from '../services/logs';
import { prefixed } from '../core/emoji';
import { canUsePanel } from './access';
import { nextFilter, session } from './session';
import { confirmView, dashboardView, linkMessageView, playersView, playerView, settingsView } from './views';
import { sendLinkMessage } from '../services/linkMessage';
import { linkChannelId, linkMode } from '../core/client';

type ComponentInteraction = ButtonInteraction | AnySelectMenuInteraction | ModalSubmitInteraction;

function actor(interaction: Replyable): string {
    return `@${interaction.user.username} (Discord)`;
}

function ok(locale: string, key: string, vars: Record<string, unknown> = {}): string {
    return prefixed('success', t(locale, key, vars));
}

function errorText(locale: string, error: unknown): string {
    const code = typeof error === 'string' ? error : (error as Error)?.message;
    const translated = code ? t(locale, `panel.errors.${code}`) : '';
    return translated && !translated.startsWith('panel.errors.') ? translated : t(locale, 'panel.errors.generic');
}

function fail(locale: string, error: unknown): string {
    return prefixed('error', errorText(locale, error));
}

type View = ContainerBuilder | ContainerBuilder[];

async function render(interaction: ComponentInteraction, build: () => Promise<View> | View): Promise<void> {
    if (!interaction.deferred && !interaction.replied) await interaction.deferUpdate();
    try {
        const view = await build();
        await interaction.editReply({ components: Array.isArray(view) ? view : [view], allowedMentions: { parse: [] } });
    } catch (error: any) {
        ipc.log('warn', `Panel render failed: ${error?.message ?? error}`);
        const locale = localeFor(interaction);
        await interaction.editReply({ components: [await dashboardView(locale, fail(locale, 'server-unreachable'))] }).catch(() => undefined);
    }
}

async function settings(locale: string, notice?: string): Promise<ContainerBuilder> {
    return settingsView(locale, await guild(), notice);
}

async function setSetting(id: string, value: unknown, interaction: Replyable): Promise<string | undefined> {
    const result = await ipc.request('settings.set', { id, value, actor: actor(interaction) });
    applyConfig(result.config, result.defaultLocale);
    return result.success ? undefined : result.error ?? 'generic';
}

export async function openPanel(interaction: ChatInputCommandInteraction): Promise<void> {
    const locale = localeFor(interaction);
    if (!cfg('panel.enabled', true)) {
        await respondNotice(interaction, t(locale, 'panel.disabled'), Colors.error);
        return;
    }
    if (!canUsePanel(interaction)) {
        await respondNotice(interaction, t(locale, 'panel.no-access'), Colors.error);
        return;
    }
    const ephemeral = cfg('panel.ephemeral', true);
    await interaction.deferReply(ephemeral ? { flags: 'Ephemeral' } : {});
    await respond(interaction, [await dashboardView(locale)], ephemeral);
}

export async function handlePanel(interaction: ComponentInteraction): Promise<void> {
    const locale = localeFor(interaction);
    if (!canUsePanel(interaction) || !cfg('panel.enabled', true)) {
        await interaction.reply({ content: t(locale, 'panel.no-access'), flags: 'Ephemeral' });
        return;
    }
    const [, scope, action = '', arg = ''] = interaction.customId.split(':');
    const userSession = session(interaction.user.id);

    switch (scope) {
        case 'dash': {
            if (action === 'reload') {
                return render(interaction, async () => {
                    await ipc.request('admin.reload', { actor: actor(interaction) });
                    panelLog(interaction.user, 'reload');
                    return dashboardView(locale, ok(locale, 'panel.notices.reloaded'));
                });
            }
            if (action === 'clear') {
                return render(interaction, async () => {
                    const result = await ipc.request('admin.clear-codes', { actor: actor(interaction) });
                    panelLog(interaction.user, 'clear-codes', String(result.removed));
                    return dashboardView(locale, ok(locale, 'panel.notices.codes-cleared', { count: result.removed }));
                });
            }
            return render(interaction, () => dashboardView(locale));
        }

        case 'lm': {
            const target = action === 'send' && interaction.isChannelSelectMenu()
                ? interaction.values[0]
                : action === 'link' ? linkChannelId() : undefined;
            if (!target) return render(interaction, () => linkMessageView(locale));
            return render(interaction, async () => {
                try {
                    await sendLinkMessage(target);
                } catch {
                    return linkMessageView(locale, fail(locale, 'cannot-send'));
                }
                panelLog(interaction.user, 'link-message', `#${target}`, { value: `<#${target}>` });
                return linkMessageView(locale, ok(locale, 'panel.notices.link-message-sent', { channel: `<#${target}>` }));
            });
        }

        case 'pl': {
            if (action === 'search' && interaction.isButton()) {
                const modal = new ModalBuilder()
                    .setCustomId('dl:m:search')
                    .setTitle(t(locale, 'panel.modals.search.title').slice(0, 45))
                    .addLabelComponents(new LabelBuilder()
                        .setLabel(t(locale, 'panel.modals.search.label').slice(0, 45))
                        .setTextInputComponent(new TextInputBuilder()
                            .setCustomId('query')
                            .setStyle(TextInputStyle.Short)
                            .setRequired(false)
                            .setMaxLength(64)
                            .setPlaceholder(t(locale, 'panel.modals.search.placeholder').slice(0, 100))
                            .setValue(userSession.query)));
                await interaction.showModal(modal);
                return;
            }
            if (action === 'filter') {
                userSession.filter = nextFilter(userSession.filter);
                userSession.page = 0;
            } else if (action === 'clear') {
                userSession.query = '';
                userSession.page = 0;
            } else if (action === 'select' && interaction.isStringSelectMenu()) {
                const uuid = interaction.values[0];
                return render(interaction, () => playerView(locale, uuid));
            } else if (action !== 'keep') {
                const page = Number.parseInt(action, 10);
                userSession.page = Number.isNaN(page) ? 0 : Math.max(0, page);
            }
            return render(interaction, () => playersView(locale, userSession));
        }

        case 'p': {
            const uuid = arg;
            if (action === 'view') return render(interaction, () => playerView(locale, uuid));
            if (action === 'unlink') {
                if (cfg('panel.confirm-actions', true)) {
                    return render(interaction, async () => {
                        const result = await ipc.request('players.get', { uuid });
                        return result.found ? confirmView(locale, uuid, result.account.name) : playerView(locale, uuid);
                    });
                }
                return render(interaction, () => unlink(interaction, locale, uuid));
            }
            if (action === 'unlink!') return render(interaction, () => unlink(interaction, locale, uuid));
            if (action === 'resync') {
                return render(interaction, async () => {
                    const result = await ipc.request('players.get', { uuid });
                    if (!result.found) return playerView(locale, uuid);
                    await ipc.request('players.resync', { uuid });
                    panelLog(interaction.user, 'resync', result.account.name);
                    return playerView(locale, uuid, ok(locale, 'panel.notices.resynced', { player: result.account.name }));
                });
            }
            if (action === 'bypass') {
                return render(interaction, async () => {
                    const result = await ipc.request('players.bypass', { uuid });
                    if (!result.found) return playerView(locale, uuid);
                    const player = (await ipc.request('players.get', { uuid })).account?.name ?? uuid;
                    panelLog(interaction.user, result.bypass ? 'bypass-on' : 'bypass-off', player);
                    return playerView(locale, uuid, ok(locale, 'panel.notices.bypass', {
                        player,
                        state: t(locale, result.bypass ? 'common.enabled' : 'common.disabled'),
                    }));
                });
            }
            if (action === 'lang' && interaction.isStringSelectMenu()) {
                const value = interaction.values[0];
                return render(interaction, async () => {
                    const result = await ipc.request('players.lang', { uuid, locale: value });
                    if (!result.success) return playerView(locale, uuid, fail(locale, 'unknown-locale'));
                    const player = (await ipc.request('players.get', { uuid })).account?.name ?? uuid;
                    panelLog(interaction.user, 'language', player, { value: result.locale });
                    return playerView(locale, uuid, ok(locale, 'panel.notices.language', { player, language: languageName(result.locale) }));
                });
            }
            return render(interaction, () => playerView(locale, uuid));
        }

        case 'set': {
            if (action === 'codes' && interaction.isButton()) {
                const input = (id: string, labelKey: string, value: unknown) => new LabelBuilder()
                    .setLabel(t(locale, labelKey).slice(0, 45))
                    .setTextInputComponent(new TextInputBuilder()
                        .setCustomId(id)
                        .setStyle(TextInputStyle.Short)
                        .setRequired(true)
                        .setMaxLength(5)
                        .setValue(String(value)));
                const modal = new ModalBuilder()
                    .setCustomId('dl:m:codes')
                    .setTitle(t(locale, 'panel.modals.codes.title').slice(0, 45))
                    .addLabelComponents(
                        input('linking-code-length', 'panel.modals.codes.length', cfg('linking.code.length', 6)),
                        input('linking-code-expiration', 'panel.modals.codes.expiration', cfg('linking.code.expiration', 300)),
                        input('panel-players-per-page', 'panel.modals.codes.per-page', cfg('panel.players-per-page', 10)),
                        input('discord-activity-update-interval', 'panel.modals.codes.interval', cfg('discord.activity.update-interval', 60)),
                    );
                await interaction.showModal(modal);
                return;
            }
            if (action === 'reload') {
                return render(interaction, async () => {
                    await ipc.request('admin.reload', { actor: actor(interaction) });
                    panelLog(interaction.user, 'reload');
                    return settings(locale, ok(locale, 'panel.notices.reloaded'));
                });
            }
            if (action === 'toggle' && interaction.isStringSelectMenu()) {
                const id = interaction.values[0];
                return render(interaction, async () => {
                    const result = await ipc.request('settings.toggle', { id, actor: actor(interaction) });
                    applyConfig(result.config, result.defaultLocale);
                    if (!result.success) return settings(locale, fail(locale, result.error));
                    const label = t(locale, `panel.settings.labels.${id}`);
                    const value = t(locale, cfg(setting(id)?.path ?? id, false) ? 'common.enabled' : 'common.disabled');
                    panelLog(interaction.user, 'setting', label, { value });
                    return settings(locale, ok(locale, 'panel.notices.setting', { setting: label, value }));
                });
            }
            if (action === 'role' && interaction.isRoleSelectMenu()) {
                const value = interaction.values[0] ?? '';
                return render(interaction, async () => {
                    const error = await setSetting(arg, value, interaction);
                    if (error) return settings(locale, fail(locale, error));
                    const label = t(locale, `panel.settings.labels.${arg}`);
                    const shown = value ? `<@&${value}>` : t(locale, 'common.none');
                    panelLog(interaction.user, 'setting', label, { value: shown });
                    return settings(locale, ok(locale, 'panel.notices.setting', { setting: label, value: shown }));
                });
            }
            if (action === 'channel' && interaction.isChannelSelectMenu()) {
                const value = interaction.values[0] ?? '';
                return render(interaction, async () => {
                    const error = await setSetting('logs-channel-id', value, interaction);
                    if (error) return settings(locale, fail(locale, error));
                    const label = t(locale, 'panel.settings.labels.logs-channel-id');
                    const shown = value ? `<#${value}>` : t(locale, 'common.none');
                    panelLog(interaction.user, 'setting', label, { value: shown });
                    return settings(locale, ok(locale, 'panel.notices.setting', { setting: label, value: shown }));
                });
            }
            if (action === 'mode' && interaction.isStringSelectMenu()) {
                const value = interaction.values[0];
                const previous = linkMode();
                return render(interaction, async () => {
                    const error = await setSetting('linking-mode', value, interaction);
                    if (error) return settings(locale, fail(locale, error));
                    const label = t(locale, 'panel.settings.labels.linking-mode');
                    const shown = t(locale, `panel.settings.modes.${value}`);
                    panelLog(interaction.user, 'setting', label, { value: shown });
                    const restart = (previous === 'COMMAND') !== (value === 'COMMAND') ? `\n-# ${t(locale, 'panel.notices.mode-restart')}` : '';
                    return settings(locale, ok(locale, 'panel.notices.setting', { setting: label, value: shown }) + restart);
                });
            }
            if (action === 'linkchannel' && interaction.isChannelSelectMenu()) {
                const value = interaction.values[0] ?? '';
                return render(interaction, async () => {
                    const error = await setSetting('linking-channel-channel-id', value, interaction);
                    if (error) return settings(locale, fail(locale, error));
                    const label = t(locale, 'panel.settings.labels.linking-channel-channel-id');
                    const shown = value ? `<#${value}>` : t(locale, 'common.none');
                    panelLog(interaction.user, 'setting', label, { value: shown });
                    return settings(locale, ok(locale, 'panel.notices.setting', { setting: label, value: shown }));
                });
            }
            if (action === 'panelroles' && interaction.isRoleSelectMenu()) {
                const values = [...interaction.values];
                return render(interaction, async () => {
                    const error = await setSetting('panel-allowed-roles', values, interaction);
                    if (error) return settings(locale, fail(locale, error));
                    const label = t(locale, 'panel.settings.labels.panel-allowed-roles');
                    const shown = values.length ? values.map((id) => `<@&${id}>`).join(' ') : t(locale, 'common.none');
                    panelLog(interaction.user, 'setting', label, { value: shown });
                    return settings(locale, ok(locale, 'panel.notices.setting', { setting: label, value: shown }));
                });
            }
            if (action === 'locale' && interaction.isStringSelectMenu()) {
                const value = interaction.values[0];
                return render(interaction, async () => {
                    const error = await setSetting('language-default', value, interaction);
                    if (error) return settings(locale, fail(locale, error));
                    const label = t(locale, 'panel.settings.labels.language-default');
                    panelLog(interaction.user, 'setting', label, { value: languageName(value) });
                    return settings(locale, ok(locale, 'panel.notices.setting', { setting: label, value: languageName(value) }));
                });
            }
            return render(interaction, () => settings(locale));
        }

        case 'm': {
            if (!interaction.isModalSubmit()) return;
            if (action === 'search') {
                userSession.query = interaction.fields.getTextInputValue('query').trim();
                userSession.page = 0;
                return render(interaction, () => playersView(locale, userSession));
            }
            if (action === 'codes') {
                const ids = ['linking-code-length', 'linking-code-expiration', 'panel-players-per-page', 'discord-activity-update-interval'];
                return render(interaction, async () => {
                    const errors: string[] = [];
                    const changed: string[] = [];
                    for (const id of ids) {
                        const raw = interaction.fields.getTextInputValue(id).trim();
                        const value = Number.parseInt(raw, 10);
                        if (String(cfg(setting(id)?.path ?? id, '')) === raw) continue;
                        const label = t(locale, `panel.settings.labels.${id}`);
                        const error = Number.isNaN(value) ? 'invalid-value' : await setSetting(id, value, interaction);
                        if (error) errors.push(`${label} : ${errorText(locale, error)}`);
                        else {
                            changed.push(label);
                            panelLog(interaction.user, 'setting', label, { value: String(value) });
                        }
                    }
                    const notice = errors.length
                        ? errors.map((line) => prefixed('error', line)).join('\n')
                        : changed.length ? ok(locale, 'panel.notices.saved') : undefined;
                    return settings(locale, notice);
                });
            }
            return;
        }
    }
}

async function unlink(interaction: Replyable, locale: string, uuid: string): Promise<ContainerBuilder> {
    const result = await ipc.request('players.unlink', { uuid, actor: actor(interaction) });
    if (!result.unlinked) return playerView(locale, uuid);
    panelLog(interaction.user, 'unlink', result.account.name);
    return playersView(locale, session(interaction.user.id), ok(locale, 'panel.notices.unlinked', { player: result.account.name }));
}
