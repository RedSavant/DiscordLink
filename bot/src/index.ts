import { Events, GatewayIntentBits, IntentsBitField, MessageFlags, type Interaction } from 'discord.js';
import { langCommand, langSelect } from './commands/lang';
import { linkCommand, linkModalSubmit, openLinkModal, unlinkCommand } from './commands/link';
import { linkmessageCommand } from './commands/linkmessage';
import { linkInfoButton, linkinfoCommand } from './commands/linkinfo';
import { registerCommands } from './commands/definitions';
import { client, guild, guildId, linkChannelName, linkMode, ping, startedAt } from './core/client';
import { ipc } from './core/ipc';
import { prefixed } from './core/emoji';
import { t, localeOf } from './core/i18n';
import { applyInit, cfg, state } from './core/state';
import { handlePanel, openPanel } from './panel/router';
import { resyncAll, syncMember } from './services/roles';
import { sendLog } from './services/logs';
import { handleLinkChannelMessage } from './services/linkChannel';
import { updatePresence } from './services/presence';

const token = process.env.DISCORD_TOKEN;

if (!token) {
    ipc.fatal('DISCORD_TOKEN environment variable is missing');
}

let loggedIn = false;

ipc.handle('init', async (payload) => {
    applyInit(payload);
    if (!loggedIn) {
        loggedIn = true;
        login();
    } else {
        await registerCommands();
        updatePresence();
    }
});

ipc.handle('config.update', async (payload) => {
    applyInit(payload);
    await registerCommands();
    updatePresence();
});

ipc.handle('roles.sync', async (data) => {
    return { synced: await syncMember(data as any) };
});

ipc.handle('log', async (data) => {
    await sendLog(data.event, data.fields ?? {});
});

ipc.handle('status', async (data) => {
    state.snapshot = { online: data.online, max: data.max, linked: data.linked, pending: data.pending };
    updatePresence();
    const target = await guild();
    return {
        ready: client.isReady(),
        tag: client.user?.tag,
        ping: ping(),
        guildName: target?.name,
        linkChannelName: linkChannelName(),
        startedAt,
    };
});

client.once(Events.ClientReady, async (ready) => {
    ipc.log('info', `Logged in as ${ready.user.tag}`);
    const target = await guild();
    if (!guildId()) {
        ipc.log('warn', 'discord.guild-id is missing or invalid in config.yml.');
    } else if (!target) {
        ipc.log('warn', `The bot is not a member of the guild ${guildId()}.`);
    }
    await registerCommands(true);
    updatePresence();
    ipc.emit('ready', {
        tag: ready.user.tag,
        id: ready.user.id,
        guildName: target?.name,
        linkChannelName: linkChannelName(),
        ping: ping(),
    });
    if (cfg('roles.resync-on-startup', true)) {
        try {
            const synced = await resyncAll();
            ipc.log('info', `Resynced roles of ${synced} linked member(s).`);
        } catch (error: any) {
            ipc.log('warn', `Role resync failed: ${error?.message ?? error}`);
        }
    }
});

client.on(Events.InteractionCreate, (interaction) => {
    void route(interaction).catch(async (error: any) => {
        ipc.log('error', `Interaction failed: ${error?.stack ?? error}`);
        if (!interaction.isRepliable()) return;
        const locale = localeOf(interaction.user.id, interaction.locale);
        const content = prefixed('error', t(locale, 'errors.generic'));
        try {
            if (interaction.deferred || interaction.replied) await interaction.followUp({ content, flags: MessageFlags.Ephemeral });
            else await interaction.reply({ content, flags: MessageFlags.Ephemeral });
        } catch {
            return;
        }
    });
});

async function route(interaction: Interaction): Promise<void> {
    if (!state.initialized) return;
    if (interaction.isChatInputCommand()) {
        switch (interaction.commandName) {
            case 'link': return linkCommand(interaction);
            case 'unlink': return unlinkCommand(interaction);
            case 'linkinfo': return linkinfoCommand(interaction);
            case 'lang': return langCommand(interaction);
            case 'panel': return openPanel(interaction);
            case 'linkmessage': return linkmessageCommand(interaction);
        }
        return;
    }
    if (interaction.isButton() && interaction.customId === 'dl:link:modal') return openLinkModal(interaction);
    if (interaction.isButton() && interaction.customId === 'dl:link:info') return linkInfoButton(interaction);
    if (interaction.isModalSubmit() && interaction.customId === 'dl:link:submit') return linkModalSubmit(interaction);
    if (interaction.isStringSelectMenu() && interaction.customId === 'dl:lang') {
        return langSelect(interaction);
    }
    if ((interaction.isButton() || interaction.isAnySelectMenu() || interaction.isModalSubmit()) && interaction.customId.startsWith('dl:')) {
        return handlePanel(interaction);
    }
}

client.on(Events.MessageCreate, (message) => {
    if (!state.initialized) return;
    void handleLinkChannelMessage(message).catch((error: any) => ipc.log('warn', `Link channel error: ${error?.message ?? error}`));
});

client.on(Events.ShardDisconnect, (event) => {
    if (event.code === 4004) ipc.fatal('Discord token is invalid in config.yml !');
});

client.on(Events.Error, (error) => ipc.log('error', error.message));
client.on(Events.Warn, (message) => ipc.log('warn', message));

function login(): void {
    const intents = new IntentsBitField([GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers]);
    if (linkMode() !== 'COMMAND') intents.add(GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent);
    client.options.intents = intents;
    client.login(token).catch((err: any) => {
        if (err?.code === 'TokenInvalid' || err?.status === 401) {
            ipc.fatal('Discord token is invalid in config.yml !');
            return;
        }
        if (String(err?.message ?? '').includes('disallowed intents')) {
            ipc.fatal(linkMode() === 'COMMAND'
                ? 'Enable the "Server Members Intent" in the Discord Developer Portal (Bot > Privileged Gateway Intents).'
                : 'Enable the "Server Members Intent" and "Message Content Intent" in the Discord Developer Portal (Bot > Privileged Gateway Intents).');
            return;
        }
        ipc.log('error', `Failed to login to Discord: ${err?.message ?? err}`);
        process.exit(1);
    });
}

process.on('unhandledRejection', (reason: any) => ipc.log('error', `Unhandled rejection: ${reason?.stack ?? reason}`));

ipc.emit('boot');
