import * as readline from 'node:readline';

export type Json = Record<string, any>;

type RequestHandler = (data: Json) => Promise<Json | void> | Json | void;

interface PendingRequest {
    resolve: (value: Json) => void;
    reject: (error: Error) => void;
    timer: NodeJS.Timeout;
}

export class IPC {
    private readonly handlers = new Map<string, RequestHandler>();
    private readonly pending = new Map<number, PendingRequest>();
    private nextId = 0;

    constructor() {
        const rl = readline.createInterface({ input: process.stdin, terminal: false });
        rl.on('line', (line) => void this.onLine(line));
        rl.on('close', () => process.exit(0));
    }

    handle(type: string, handler: RequestHandler): void {
        this.handlers.set(type, handler);
    }

    request<T extends Json = Json>(type: string, data: Json = {}, timeoutMs = 15000): Promise<T> {
        const id = ++this.nextId;
        return new Promise<T>((resolve, reject) => {
            const timer = setTimeout(() => {
                this.pending.delete(id);
                reject(new Error(`IPC request "${type}" timed out`));
            }, timeoutMs);
            this.pending.set(id, { resolve: resolve as (value: Json) => void, reject, timer });
            this.write({ kind: 'req', id, type, data });
        });
    }

    emit(type: string, data: Json = {}): void {
        this.write({ kind: 'evt', type, data });
    }

    log(level: 'debug' | 'info' | 'warn' | 'error', message: string): void {
        this.write({ kind: 'log', level, message });
    }

    fatal(message: string): void {
        process.stdout.write(JSON.stringify({ kind: 'fatal', message }) + '\n', () => process.exit(1));
    }

    private write(payload: Json): void {
        process.stdout.write(JSON.stringify(payload) + '\n');
    }

    private async onLine(line: string): Promise<void> {
        let message: Json;
        try {
            message = JSON.parse(line);
        } catch {
            this.log('warn', `Invalid IPC payload: ${line.slice(0, 120)}`);
            return;
        }

        if (message.kind === 'res') {
            const pending = this.pending.get(message.id);
            if (!pending) return;
            this.pending.delete(message.id);
            clearTimeout(pending.timer);
            if (message.ok) pending.resolve(message.data ?? {});
            else pending.reject(new Error(message.error ?? 'Unknown error'));
            return;
        }

        if (message.kind !== 'req') return;

        const handler = this.handlers.get(message.type);
        if (!handler) {
            this.write({ kind: 'res', id: message.id, ok: false, error: `Unknown request type: ${message.type}` });
            return;
        }
        try {
            const result = await handler(message.data ?? {});
            this.write({ kind: 'res', id: message.id, ok: true, data: result ?? {} });
        } catch (error: any) {
            this.write({ kind: 'res', id: message.id, ok: false, error: error?.message ?? 'Internal error' });
        }
    }
}

export const ipc = new IPC();
