import * as readline from 'readline';

export interface IPCMessage {
    id: number;
    type: string;
    [key: string]: any;
}

export interface IPCResponse {
    id: number;
    success: boolean;
    error?: string;
    [key: string]: any;
}

type MessageHandler = (message: IPCMessage) => Promise<Record<string, any> | void>;

export class IPCManager {
    private handlers: Map<string, MessageHandler> = new Map();
    private rl: readline.Interface;

    constructor() {
        this.rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout,
            terminal: false
        });

        this.rl.on('line', (line: string) => {
            void this.handleLine(line);
        });
    }

    on(type: string, handler: MessageHandler): void {
        this.handlers.set(type, handler);
    }

    public registerHandler(type: string, handler: MessageHandler) {
        this.handlers.set(type, handler);
    }

    private async handleLine(line: string): Promise<void> {
        let data: Partial<IPCMessage>;
        try {
            data = JSON.parse(line) as Partial<IPCMessage>;
        } catch {
            this.sendError(-1, 'Invalid JSON format');
            return;
        }

        const { id, type } = data;
        if (typeof id !== 'number' || typeof type !== 'string') {
            this.sendError(typeof id === 'number' ? id : -1, 'Missing "id" or "type" in IPC message');
            return;
        }

        const handler = this.handlers.get(type);
        if (!handler) {
            this.sendError(id, `Unknown message type: ${type}`);
            return;
        }

        try {
            const result = await handler(data as IPCMessage);
            this.sendResponse({
                id,
                success: true,
                ...(result || {})
            });
        } catch (error: any) {
            this.sendResponse({
                id,
                success: false,
                error: error.message || 'Internal error'
            });
        }
    }

    private sendResponse(response: IPCResponse): void {
        process.stdout.write(JSON.stringify(response) + '\n');
    }

    private sendError(id: number, message: string): void {
        this.sendResponse({
            id,
            success: false,
            error: message
        });
    }
}
