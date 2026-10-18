import { ContainerBuilder, SeparatorSpacingSize, TextDisplayBuilder } from 'discord.js';

export function color(hex: unknown, fallback = 0x5865f2): number {
    if (typeof hex !== 'string') return fallback;
    const parsed = Number.parseInt(hex.replace('#', ''), 16);
    return Number.isNaN(parsed) ? fallback : parsed;
}

export function isSnowflake(id: unknown): id is string {
    return typeof id === 'string' && /^\d{15,21}$/.test(id) && !/^0+$/.test(id);
}

export function headUrl(uuid: string): string {
    return `https://mc-heads.net/avatar/${uuid}/96`;
}

export function timestamp(ms: number, style: 'R' | 'F' | 'f' | 'd' | 'D' | 't' = 'R'): string {
    return `<t:${Math.floor(ms / 1000)}:${style}>`;
}

export function text(content: string): TextDisplayBuilder {
    return new TextDisplayBuilder().setContent(content.slice(0, 4000));
}

export function separator(container: ContainerBuilder, divider = true, large = false): ContainerBuilder {
    return container.addSeparatorComponents((sep) =>
        sep.setDivider(divider).setSpacing(large ? SeparatorSpacingSize.Large : SeparatorSpacingSize.Small));
}

export function notice(content: string, accent: number): ContainerBuilder {
    return new ContainerBuilder().setAccentColor(accent).addTextDisplayComponents(text(content));
}

export const Colors = {
    success: 0x57f287,
    error: 0xed4245,
    warning: 0xfee75c,
    info: 0x5865f2,
};
