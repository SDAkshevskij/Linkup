import {CloudError, Supabase} from './cloud.js';

export async function cloudStep(service, operation, timeout = 12000) {
    const controller = new AbortController();
    let timer;
    try {
        return await Promise.race([
            operation(controller.signal),
            new Promise((_, reject) => {
                timer = setTimeout(() => {
                    reject(new Error('Сервер не ответил вовремя'));
                    controller.abort();
                }, timeout);
            })
        ]);
    } catch (error) {
        let detail = error.message;
        if (error instanceof TypeError) detail = 'Сеть или сервер недоступны. Проверьте адрес и подключение';
        if (error instanceof CloudError) {
            const hints = {
                400: 'Запрос отклонён. Проверьте параметры и данные входа',
                401: 'Проверьте ключ, данные входа или срок действия токена',
                403: 'Доступ запрещён. Проверьте права доступа',
                404: 'Ресурс не найден. Проверьте адрес и схему проекта',
                429: 'Слишком много запросов. Повторите позже'
            };
            detail = hints[error.status] || 'Сервер отклонил запрос';
            detail += ' (HTTP ' + error.status + ')';
        }
        const result=new Error(service + ' · ' + detail);
        result.code=error instanceof CloudError?`http-${error.status}`:error instanceof TypeError?'webview-network':'request';
        throw result;
    } finally {
        clearTimeout(timer);
    }
}

export function projectUrl(value) {
    const raw = value.trim();
    if (!raw) return '';
    let url;
    try { url = new URL(raw); } catch { throw new Error('Supabase · Введите полный HTTPS-адрес проекта'); }
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash
            || url.pathname !== '/') {
        throw new Error('Supabase · Используйте HTTPS-адрес проекта без пути и параметров');
    }
    return url.origin;
}

export async function signIn(config, password, update) {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(config.email) || !password) {
        throw new Error('Supabase · Введите email и пароль');
    }
    const client = new Supabase(config.url, config.key, null, () => {});
    try{await cloudStep('Supabase', signal => client.login(config.email,password,signal));}
    catch(error){error.phase='Вход';throw error;}
    update('Supabase · Проверяем схему…');
    try{await cloudStep('Supabase', async signal => {
        const response = await client.request('linkup_items?select=id&limit=1', {signal});
        if (!Array.isArray(await response.json())) throw new Error('Не удалось проверить таблицу LinkUp');
    });}catch(error){error.phase='Проверка таблицы';throw error;}
    return client.session;
}

export async function verifyDisk(client, disk, provider, token, update) {
    const service = provider === 'yandex' ? 'Яндекс Диск' : 'Google Drive';
    if (!token.trim()) throw new Error(service + ' · Введите OAuth access token');
    update(service + ' · Проверяем токен…');
    const identity = await cloudStep(service, async signal => {
        await disk.connect(token);
        if (provider === 'yandex') return disk.identity(signal);
        const response = await disk.request('https://www.googleapis.com/drive/v3/about?fields=user(permissionId)', {signal});
        const data = await response.json();
        if (!data.user?.permissionId) throw new Error('Не удалось определить аккаунт');
        return disk.clientId + ':' + data.user.permissionId;
    });
    update('Supabase · Проверяем привязку диска…');
    await cloudStep('Supabase', async signal => {
        await client.request('linkup_disk_accounts?on_conflict=user_id,provider', {
            signal, method: 'POST', headers: {Prefer: 'resolution=ignore-duplicates,return=minimal'},
            body: JSON.stringify({user_id: client.session.user.id, provider, drive_identity: identity})
        });
        const response = await client.request('linkup_disk_accounts?select=drive_identity&provider=eq.' + provider, {signal});
        const rows = await response.json();
        if (rows[0]?.drive_identity !== identity) {
            throw new Error('Подключите тот же аккаунт диска и OAuth-приложение, что на первом устройстве');
        }
    });
}
