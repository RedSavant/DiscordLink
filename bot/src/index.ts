import { Client, GatewayCapabilityBits, GatewayIntentBits, Partials } from "discord.js";
import { IPCManager } from "./core/ipc";

const token = process.env.DISCORD_TOKEN;
const guildId = process.env.DISCORD_GUILD_ID;

if (!token) {
    console.log(JSON.stringify({ error: 'DISCORD_TOKEN environment variable is missing' }));
    process.exit(1);
}

console.log('[DiscordLink] [Bot] Initializing Discord Bot...')

const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMembers,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent
    ],
    partials: [Partials.Channel, Partials.Message, Partials.GuildMember]
});

const ipc = new IPCManager();

ipc.registerHandler('assign_role', async (data) => {
    const { discordId, roleId } = data;

    if (!guildId) throw new Error('DISCORD_GUILD_ID is not configured');
    if (!discordId || !roleId) throw new Error('Missing discordId or roleId');

    const guild = await client.guilds.fetch(guildId);
    const member = await guild.members.fetch(discordId);

    await member.roles.add(roleId);

    return { assigned: true };
});

client.once('clientReady', () => {
    console.log(`[DiscordLink] [Bot] Bot logged successfully as ${client.user?.tag}`);
});

const reportInvalidToken = () => {
    const message = JSON.stringify({ error: 'Discord token is invalid in config.yml !', fatal: true });
    process.stdout.write(message + '\n', () => process.exit(1));
};

client.on('shardDisconnect', (closeEvent) => {
    if (closeEvent.code === 4004) reportInvalidToken();
});

client.login(token).catch((err) => {
    if (err?.code === 'TokenInvalid' || err?.status === 401) {
        reportInvalidToken();
        return;
    }
    console.error('[DiscordLink] [Bot] Failed to login to Discord Gateway:', err);
});