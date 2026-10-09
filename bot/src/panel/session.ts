export type PlayerFilter = 'all' | 'online' | 'offline';

export interface PanelSession {
    page: number;
    filter: PlayerFilter;
    query: string;
}

const sessions = new Map<string, PanelSession>();

export function session(userId: string): PanelSession {
    let current = sessions.get(userId);
    if (!current) {
        current = { page: 0, filter: 'all', query: '' };
        sessions.set(userId, current);
    }
    return current;
}

export function nextFilter(filter: PlayerFilter): PlayerFilter {
    return filter === 'all' ? 'online' : filter === 'online' ? 'offline' : 'all';
}
